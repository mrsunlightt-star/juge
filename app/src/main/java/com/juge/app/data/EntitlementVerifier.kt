package com.juge.app.data

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * PRO 权益凭证校验器。
 *
 * ## 它解决什么问题
 *
 * 加固之前，「是不是会员」完全由本机算出来：三个布尔键 + 一个用包签名当盐的 checksum。
 * 盐和算法都在 APK 里，反编译照着抄一遍，往 SharedPreferences 里写六个键，
 * 就是永久 PRO —— 连支付宝都不用打开。
 *
 * 现在真正的授权来自**服务端签的一段声明**：
 *
 * ```
 * v1.<base64url(payload)>.<base64url(RS256 签名)>
 * ```
 *
 * payload 里带 productId / buyerHash / paidAt / issuedAt / nonce，签名用服务端的私钥做。
 * App 只内置**公钥**，公钥公开无所谓 —— 没有私钥就签不出能通过校验的串。
 * 于是「伪造授权」从「改几个本地键」变成「破解 2048 位 RSA」，防线终于落在
 * 攻击者拿不到的那一侧。
 *
 * ## 边界（必须说清楚）
 *
 * - 验签只回答「这段声明是不是服务端签的」，不回答「这台设备有没有付过钱」；
 *   后者由服务端在签发前用支付宝 user_id 反查订单得出结论。
 * - 因此凭证**可以**被同一个用户复制到第二台设备上。产品定位本来就是
 *   「一台设备买一次、换机时用支付宝账号找回」，不做设备绑定，这里不额外加锁。
 * - 公钥更换 = 已发出的凭证全部失效。换密钥前必须先让老版本发完新凭证。
 */
object EntitlementVerifier {

    /** 与 lkl-pay-server/certs/entitlement_public_key.pem 完全一致的 PKCS#8 公钥（DER 的 Base64） */
    internal const val BUILTIN_PUBLIC_KEY_PEM = """
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA27WdNag5ZrqVHGFCAnGV
JoRqHaux9WVxYtZQ0r41fwwqw3eIFr+PgsQny1pc6GIKXdaCqNFYuq4V3jKOsH9L
3o5ZnekEXgannGbYBH3tqYmR+P56YLhg0XT3+GOLQXzJiowWI8nFzCNMr2a1MpNO
koDUKn/DP6t3oPdH9YRw6cFMz4gS2Edf0oGpgAZ2nrlckSvFEtEvUWseNbgZNdBE
uEJBzMahixhKkx28KRmAsK9rxrW+6J/H7pqZdUEclCfnYGB/KsV+5KG2ukYF4MQ6
xtawNxjVGwucJahOyjchgE2WM8Ru6qaKFUBHLSWWpIEg9FyxGj8zXKiG0X6QsRG/
tQIDAQAB
"""

    /** 凭证格式前缀。不认识的版本直接拒，不做「尽力解析」 */
    const val PREFIX = "v1"

    /** 当前唯一在售商品。凭证里的 productId 必须与它一致才认 */
    const val PRODUCT_ID = "pro_permanent"

    /** 签名算法，与服务端 EntitlementSigner.SIGN_ALGORITHM 一致 */
    private const val SIGN_ALGORITHM = "SHA256withRSA"

    /**
     * 凭证内容。只保留 UI 用得上的字段，不含支付宝用户号明文。
     */
    data class Entitlement(
        val productId: String,
        /** SHA-256(支付宝用户号) 的 hex，仅用于日志/客服对账，不参与放行 */
        val buyerHash: String,
        val paidAt: Long,
        val issuedAt: Long,
    )

    /**
     * 仅供单元测试：把内置公钥换成测试密钥对的公钥。
     *
     * 生产路径恒为 null（没人写它）。之所以留这个口子，是因为测试必须能签出
     * **这个进程认得的**凭证 —— 把生产私钥拷进 test/resources 才是真的把私钥入库。
     */
    internal var testPublicKeyOverride: String? = null

    /** 解析并验签。任何一步不成立都返回 null，调用方按「没有权益」处理。 */
    fun verify(
        credential: String?,
        publicKeyPem: String = testPublicKeyOverride ?: BUILTIN_PUBLIC_KEY_PEM,
    ): Entitlement? {
        if (credential.isNullOrBlank()) return null
        val parts = credential.split('.')
        if (parts.size != 3 || parts[0] != PREFIX) return null

        val payloadBytes = decodeUrlSafe(parts[1]) ?: return null
        val signatureBytes = decodeUrlSafe(parts[2]) ?: return null

        if (!signatureValid(payloadBytes, signatureBytes, publicKeyPem)) return null

        return runCatching {
            val json = JSONObject(String(payloadBytes, StandardCharsets.UTF_8))
            Entitlement(
                productId = json.optString("productId", ""),
                buyerHash = json.optString("buyerHash", ""),
                paidAt = json.optLong("paidAt", 0L),
                issuedAt = json.optLong("issuedAt", 0L),
            )
        }.getOrNull()?.takeIf { it.productId == PRODUCT_ID }
    }

    private fun signatureValid(payload: ByteArray, signature: ByteArray, publicKeyPem: String): Boolean =
        runCatching {
            Signature.getInstance(SIGN_ALGORITHM).run {
                initVerify(loadPublicKey(publicKeyPem))
                update(payload)
                verify(signature)
            }
        }.getOrElse { false }

    /**
     * 解析公钥。
     *
     * 内置常量是裸 DER Base64（没有 PEM 头），但测试 seam 传进来的可能是完整的 PEM，
     * 所以先剥掉 `-----BEGIN/END PUBLIC KEY-----` 两行再解码——
     * 少了这一步，测试密钥的 PEM 头会被当成 Base64 正文，解码出一堆噪声字节，
     * 报出来的却是「验签失败」，极难排查。
     */
    private fun loadPublicKey(pem: String): PublicKey {
        val body = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .filter { !it.isWhitespace() }
        val der = Base64.decode(body, Base64.DEFAULT)
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
    }

    private fun decodeUrlSafe(value: String): ByteArray? =
        runCatching {
            // 服务端用 withoutPadding 编码：补齐到 4 的倍数，DEFAULT 才能解
            val padded = value + "=".repeat((4 - value.length % 4) % 4)
            Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP)
        }.getOrNull()

    /** 只给测试与客服排查用：内置公钥的 SHA-256 指纹（Base64） */
    fun publicKeyFingerprint(): String = runCatching {
        val key = loadPublicKey(testPublicKeyOverride ?: BUILTIN_PUBLIC_KEY_PEM)
        Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(key.encoded), Base64.NO_WRAP)
    }.getOrElse { "unavailable" }
}
