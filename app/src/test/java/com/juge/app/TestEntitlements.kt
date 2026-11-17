package com.juge.app

import android.util.Base64
import com.juge.app.data.EntitlementVerifier
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

/**
 * 测试用的权益凭证签发器。
 *
 * 用 `app/src/test/resources/entitlement-test-private.pem` 这把**测试专用**私钥签名——
 * 它与 App 内置公钥不是一对，所以必须在测试里通过
 * [EntitlementVerifier.testPublicKeyOverride] 把内置公钥换成对应的测试公钥，
 * 否则 App 不认识测试签出来的凭证。
 *
 * 为什么不直接用生产私钥：那意味着把 `certs/entitlement_private_key.pem` 复制进
 * `src/test/resources`，等于把生产私钥纳入构建输入与 CI 缓存。测试要验证的是
 * 「签名链路通不通」，与具体是哪把密钥无关。
 */
internal object TestEntitlements {

    private const val PAYLOAD_VERSION = 1
    private const val SIGN_ALGORITHM = "SHA256withRSA"

    private val privateKey: PrivateKey by lazy {
        val pem = readResource("/entitlement-test-private.pem")
        val body = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .filter { !it.isWhitespace() }
        KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(Base64.decode(body, Base64.DEFAULT)))
    }

    val publicKeyPem: String by lazy { readResource("/entitlement-test-public.pem") }

    /** 把内置公钥切成测试公钥，让本进程认测试签出来的凭证。 */
    fun installTestPublicKey() {
        EntitlementVerifier.testPublicKeyOverride = publicKeyPem
    }

    fun clearTestPublicKey() {
        EntitlementVerifier.testPublicKeyOverride = null
    }

    /**
     * 签一段合法凭证。
     *
     * 默认参数给的是「一个买过 pro_permanent 的人」，各用例按需覆盖。
     */
    fun sign(
        productId: String = EntitlementVerifier.PRODUCT_ID,
        buyerHash: String = sha256Hex("2088000000000001"),
        paidAt: Long = 1_760_000_000_000L,
        issuedAt: Long = 1_760_000_100_000L,
        version: Int = PAYLOAD_VERSION,
    ): String {
        val payload = JSONObject()
            .put("v", version)
            .put("productId", productId)
            .put("buyerHash", buyerHash)
            .put("paidAt", paidAt)
            .put("issuedAt", issuedAt)
            .put("nonce", "testnonce000")
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        val signature = Signature.getInstance(SIGN_ALGORITHM).run {
            initSign(privateKey)
            update(payload)
            sign()
        }
        val encoder = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        return buildString {
            append(EntitlementVerifier.PREFIX)
            append('.')
            append(Base64.encodeToString(payload, encoder))
            append('.')
            append(Base64.encodeToString(signature, encoder))
        }
    }

    /**
     * 签一段**签名本身合法、但内容被改过**的凭证：
     * 先签 A 再把 payload 换成 B。用来验证「验签覆盖了整个 payload」，
     * 而不只是「格式对」。
     */
    fun signThenTamper(productId: String = EntitlementVerifier.PRODUCT_ID): String {
        val genuine = sign(productId = productId)
        val parts = genuine.split('.')
        val forgedPayload = JSONObject()
            .put("v", PAYLOAD_VERSION)
            .put("productId", productId)
            .put("buyerHash", sha256Hex("attacker"))
            .put("paidAt", 0L)
            .put("issuedAt", 0L)
            .put("nonce", "forged")
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        val encoder = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        return "${parts[0]}.${Base64.encodeToString(forgedPayload, encoder)}.${parts[2]}"
    }

    /** 另起一把密钥（不是 App 认识的那把）签名：模拟「自己造了个签发方」。 */
    fun signWithForeignKey(productId: String = EntitlementVerifier.PRODUCT_ID): String {
        val generator = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val foreignKey = generator.generateKeyPair().private
        val payload = JSONObject()
            .put("v", PAYLOAD_VERSION)
            .put("productId", productId)
            .put("buyerHash", sha256Hex("attacker"))
            .put("paidAt", 1L)
            .put("issuedAt", 1L)
            .put("nonce", "foreign")
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        val signature = Signature.getInstance(SIGN_ALGORITHM).run {
            initSign(foreignKey)
            update(payload)
            sign()
        }
        val encoder = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        return "${EntitlementVerifier.PREFIX}.${Base64.encodeToString(payload, encoder)}." +
            Base64.encodeToString(signature, encoder)
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    private fun readResource(path: String): String =
        TestEntitlements::class.java.getResourceAsStream(path)?.bufferedReader()?.readText()
            ?: error("缺少测试资源 $path")
}
