package com.juge.lklpay

import com.juge.lklpay.config.EntitlementProperties
import com.juge.lklpay.service.EntitlementSigner
import com.juge.lklpay.service.PayOrderService
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 权益凭证签发器的回归测试。
 *
 * 这里**用公钥独立复算一遍签名**，而不是调 [EntitlementSigner] 自己的私有方法——
 * 要证明的是「客户端拿公钥能验过」，不是「服务端自己觉得对」。
 * 客户端那边的验签在 `app/src/main/java/com/juge/app/data/EntitlementVerifier.kt`，
 * 两端各写一遍、只有互相对得上才算通。
 *
 * 密钥刻意用 `src/test/resources/` 里的**测试密钥对**，不用 `certs/` 里的生产密钥：
 * 生产私钥被 `.gitignore` 挡在仓库外，干净 clone / CI 上读不到，
 * 早先这么写会让 `assertTrue(signer.ready)` 直接红。测试密钥与客户端
 * `app/src/test/resources/entitlement-test-*.pem` 是同一把，下面有逐字节断言。
 */
class EntitlementSignerTest {

    private val props = EntitlementProperties(
        privateKeyPath = "src/test/resources/entitlement-test-private.pem",
        publicKeyPath = "src/test/resources/entitlement-test-public.pem",
    )
    private val signer = EntitlementSigner(props)

    private fun publicKeyPem(): String = Files.readString(Path.of(props.publicKeyPath))

    /** 去掉 PEM 头尾与空白，只比 DER 正文；避免换行风格差异造成假红 */
    private fun normalizePem(pem: String): String = pem
        .replace(Regex("-----BEGIN [A-Z ]+-----"), "")
        .replace(Regex("-----END [A-Z ]+-----"), "")
        .filter { !it.isWhitespace() }

    /** 按客户端的方式验签：拆三段、URL-safe 解码、X509 公钥、SHA256withRSA。 */
    private fun verifyLikeClient(credential: String): ByteArray? {
        val parts = credential.split('.')
        if (parts.size != 3 || parts[0] != "v1") return null
        val decoder = Base64.getUrlDecoder()
        val payload = runCatching { decoder.decode(parts[1]) }.getOrNull() ?: return null
        val signature = runCatching { decoder.decode(parts[2]) }.getOrNull() ?: return null
        val body = publicKeyPem()
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .filter { !it.isWhitespace() }
        val key = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(body)))
        val ok = runCatching {
            Signature.getInstance("SHA256withRSA").run {
                initVerify(key)
                update(payload)
                verify(signature)
            }
        }.getOrDefault(false)
        return if (ok) payload else null
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    @Test
    fun `两端测试密钥是同一把 防止各自漂移`() {
        val server = normalizePem(publicKeyPem())
        val client = normalizePem(
            Files.readString(Path.of("../app/src/test/resources/entitlement-test-public.pem"))
        )
        assertEquals(server, client, "服务端与客户端必须用同一把测试密钥，否则跨端验签用例没有意义")
    }

    /**
     * 生产公钥与客户端内置常量的一致性：只有本机放了 `certs/` 才校验。
     * 干净 clone / CI 上没有生产密钥时直接通过（不能让测试依赖未入库文件）。
     * 这条守的是「换凭证密钥 = 已发出的凭证全部失效」这个 P0 级事故。
     */
    @Test
    fun `生产公钥若存在 必须等于客户端内置公钥`() {
        val prod = Path.of("certs/entitlement_public_key.pem")
        if (!Files.exists(prod)) return

        val source = Files.readString(
            Path.of("../app/src/main/java/com/juge/app/data/EntitlementVerifier.kt")
        )
        val builtin = Regex("""BUILTIN_PUBLIC_KEY_PEM\s*=\s*"{3}(.*?)"{3}""", RegexOption.DOT_MATCHES_ALL)
            .find(source)?.groupValues?.get(1)
            ?: error("客户端源码里找不到 BUILTIN_PUBLIC_KEY_PEM 常量")
        assertEquals(
            normalizePem(Files.readString(prod)),
            normalizePem(builtin),
            "服务端签发密钥与客户端验签公钥不匹配：所有已发出的权益凭证都会验不过",
        )
    }

    @Test
    fun `签发的凭证能被公钥验过`() {
        assertTrue(signer.ready, "测试密钥对应能加载；检查 src/test/resources 是否存在")

        val credential = signer.issue("2088000000000001", PayOrderService.PRODUCT_ID, 1_760_000_000_000L)

        assertNotNull(verifyLikeClient(credential), "客户端用公钥必须能验过：$credential")
        assertTrue(credential.startsWith("v1."), "格式版本前缀必须是 v1")
        assertEquals(2, credential.count { it == '.' }, "必须有且只有两个分隔点")
    }

    @Test
    fun `凭证里不含支付宝用户号明文`() {
        val userId = "2088123456789012"
        val credential = signer.issue(userId, PayOrderService.PRODUCT_ID, 1_760_000_000_000L)
        val payload = String(verifyLikeClient(credential)!!, StandardCharsets.UTF_8)

        assertFalse(userId in payload, "买家 user_id 不能出现在凭证里：$payload")
        assertTrue(sha256Hex(userId) in payload, "必须带 buyerHash 供对账：$payload")
    }

    @Test
    fun `同一份权益两次签发的凭证不同`() {
        val a = signer.issue("2088000000000001", PayOrderService.PRODUCT_ID, 1_760_000_000_000L)
        val b = signer.issue("2088000000000001", PayOrderService.PRODUCT_ID, 1_760_000_000_000L)

        assertTrue(a != b, "nonce 必须让每次签发结果不同，否则日志与客服无法区分是哪一次")
        assertNotNull(verifyLikeClient(a))
        assertNotNull(verifyLikeClient(b))
    }

    @Test
    fun `改动 payload 或签名都会验签失败`() {
        val credential = signer.issue("2088000000000001", PayOrderService.PRODUCT_ID, 1_760_000_000_000L)
        val parts = credential.split('.')

        // 改 payload 的一个 Base64 字符
        val tamperedPayload = parts[1].dropLast(1) + (if (parts[1].last() == 'A') 'B' else 'A')
        assertNull(
            verifyLikeClient("${parts[0]}.$tamperedPayload.${parts[2]}"),
            "改 payload 必须验不过",
        )

        // 改签名中间的一个 Base64 字符。
        // 不能改最后一个字符：2048 位签名是 256 字节 = 342 个 Base64 字符，
        // 末字符只有高位有效，动它的低位解出来的字节可能完全一样，这条断言会假绿。
        val signature = parts[2]
        val middle = signature.length / 2
        val tamperedSig = signature.substring(0, middle) +
            (if (signature[middle] == 'A') 'B' else 'A') +
            signature.substring(middle + 1)
        assertNull(
            verifyLikeClient("${parts[0]}.${parts[1]}.$tamperedSig"),
            "改签名必须验不过",
        )
    }

    @Test
    fun `私钥不可用时 ready 为 false 而不是抛异常拖垮启动`() {
        val broken = EntitlementSigner(EntitlementProperties(privateKeyPath = "certs/does-not-exist.pem"))

        assertFalse(broken.ready, "读不到私钥必须自报不可用，这样 Controller 才会挡在签发之前")
        assertNotNull(
            runCatching { broken.issue("2088", PayOrderService.PRODUCT_ID, 1L) }.exceptionOrNull(),
            "ready=false 之后才轮到 issue 抛，两者顺序不能反",
        )
    }

    @Test
    fun `凭证里带上了商品与时间`() {
        val paidAt = 1_760_000_123_456L
        val credential = signer.issue("2088000000000001", PayOrderService.PRODUCT_ID, paidAt)
        val payload = String(verifyLikeClient(credential)!!, StandardCharsets.UTF_8)

        assertTrue("\"productId\":\"${PayOrderService.PRODUCT_ID}\"" in payload, payload)
        assertTrue("\"paidAt\":$paidAt" in payload, payload)
        assertTrue("\"v\":1" in payload, payload)
    }
}
