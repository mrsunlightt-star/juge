package com.juge.lklpay.service

import com.juge.lklpay.config.EntitlementProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * PRO 权益凭证签发器。
 *
 * ## 为什么需要它
 *
 * 原先客户端判断「是不是 PRO」只有一个本地布尔：`TrialManager` 读三个相同的
 * SharedPreferences 键 + 一个用包签名当种子的 checksum，全对就放行。这套东西的
 * 密钥材料就在 APK 里——反编译拿到签名证书、照抄 `computeChecksum` 的算法，
 * 手工写六个键就是永久 PRO。对 3.99 元的买断产品，这属于「防君子不防高手」，
 * 但用户要求加固，于是把「值不值得放行」的判断从「本地算得出来」改成
 * 「服务端签得出来」。
 *
 * ## 凭证长什么样
 *
 * ```
 * v1.<base64url(payload JSON)>.<base64url(RS256 签名)>
 * ```
 *
 * payload 字段（顺序固定，便于客户端与测试复算）：
 * - `v`          凭证格式版本，当前 1
 * - `productId`  商品 ID，当前 pro_permanent
 * - `buyerHash`  SHA-256(支付宝用户号) 的 hex，**不落明文的用户号**
 * - `paidAt`     最早付款时间（毫秒）
 * - `issuedAt`   签发时间（毫秒）
 * - `nonce`      随机串，让同一份权益每次签发结果不同（便于日志对账，不做重放防护）
 *
 * ## 密钥从哪来
 *
 * `certs/entitlement_private_key.pem`（PKCS#8，仅服务端持有，`.gitignore` 里的 `certs/`
 * 已经把它挡在仓库外）。客户端只内置对应的公钥，公钥泄露无法伪造签名。
 *
 * 与支付宝那三把钥匙**完全独立**：换支付宝密钥、换 APPID 都不影响已发出的凭证。
 */
@Service
class EntitlementSigner(private val props: EntitlementProperties) {

    private val log = LoggerFactory.getLogger(EntitlementSigner::class.java)

    private val privateKey: PrivateKey by lazy { loadPrivateKey(props.privateKeyPath) }

    /** 签发是否可用。密钥读不出来时 `/recover` 仍能回答「找到/没找到」，只是不带凭证。 */
    val ready: Boolean by lazy {
        runCatching { privateKey }.isSuccess.also { ok ->
            if (!ok) log.error("权益凭证私钥不可用（{}），/recover 将不带凭证返回", props.privateKeyPath)
        }
    }

    /**
     * 给一笔已确认的购买签发凭证。调用方负责保证「确实付过款」。
     *
     * @param buyerUserId 支付宝用户号，只参与哈希，不写入任何返回体
     * @param productId   商品 ID
     * @param paidAt      最早付款时间（毫秒）
     */
    fun issue(buyerUserId: String, productId: String, paidAt: Long): String {
        val payload = buildString {
            append("{\"v\":1")
            append(",\"productId\":\"").append(productId.escapeJson()).append('"')
            append(",\"buyerHash\":\"").append(sha256Hex(buyerUserId)).append('"')
            append(",\"paidAt\":").append(paidAt)
            append(",\"issuedAt\":").append(System.currentTimeMillis())
            append(",\"nonce\":\"").append(randomNonce()).append('"')
            append('}')
        }
        val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)
        val signature = Signature.getInstance(SIGN_ALGORITHM).run {
            initSign(privateKey)
            update(payloadBytes)
            sign()
        }
        val encoded = Base64.getUrlEncoder().withoutPadding()
        return "${PREFIX}.${encoded.encodeToString(payloadBytes)}.${encoded.encodeToString(signature)}"
    }

    /** 只暴露给测试与运维排查：公钥指纹，与客户端内置的公钥应完全一致。 */
    fun publicKeyFingerprint(): String =
        runCatching {
            val pub = KeyFactory.getInstance("RSA").generatePublic(
                java.security.spec.X509EncodedKeySpec(
                    Base64.getMimeDecoder().decode(
                        readPem(props.publicKeyPath),
                    ),
                ),
            )
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(pub.encoded)
                .let { Base64.getEncoder().encodeToString(it) }
        }.getOrElse { "unavailable" }

    private fun loadPrivateKey(path: String): PrivateKey {
        val der = Base64.getMimeDecoder().decode(readPem(path))
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
    }

    private fun readPem(path: String): String {
        val content = Files.readString(Path.of(path))
        val single = content
            .replace(Regex("-----BEGIN [A-Z ]+-----"), "")
            .replace(Regex("-----END [A-Z ]+-----"), "")
            .filter { !it.isWhitespace() }
        check(single.isNotEmpty()) { "凭证密钥文件为空：$path" }
        return single
    }

    private fun sha256Hex(value: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun randomNonce(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(
            java.security.SecureRandom().generateSeed(12),
        )

    private fun String.escapeJson(): String = buildString {
        for (c in this@escapeJson) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
    }

    companion object {
        /** 凭证前缀，客户端据此拒绝未知格式，避免把任意字符串当凭证解析 */
        const val PREFIX = "v1"

        /** 与服务端其它签名保持一致的 SHA256withRSA（支付宝叫 RSA2） */
        const val SIGN_ALGORITHM = "SHA256withRSA"
    }
}
