package com.juge.lklpay.service

import com.alipay.api.AlipayClient
import com.alipay.api.DefaultAlipayClient
import com.alipay.api.internal.util.AlipaySignature
import com.alipay.api.request.AlipaySystemOauthTokenRequest
import com.juge.lklpay.config.AlipayProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.util.UUID

/**
 * 支付宝身份核验：把「一次授权」翻译成「一个支付宝用户号」。
 *
 * 存在的意义：PRO 权益不绑账号，绑的是**付款人的支付宝用户号**。
 * 用户换设备后，只要能证明「我就是当初付款的那个支付宝账号」，
 * 就能凭 [com.juge.lklpay.domain.PayOrder.buyerUserId] 反查出订单、恢复权益。
 *
 * 两步：
 *   1. [buildAuthInfo] 产出给 App SDK 的签名串（`alipay.open.auth.sdk.code.get`）
 *   2. [exchangeUserId] 拿 App 回传的 auth_code 换 user_id（`alipay.system.oauth.token`）
 *
 * 签名必须在这里做——应用私钥只在服务端，App 端永远不接触。
 */
@Service
class AlipayIdentityService(private val props: AlipayProperties) {

    private val log = LoggerFactory.getLogger(AlipayIdentityService::class.java)

    private val appPrivateKey: String by lazy { AlipayKeyLoader.load(props.privateKeyPath) }
    private val alipayPublicKey: String by lazy { AlipayKeyLoader.load(props.alipayPublicKeyPath) }

    private val client: AlipayClient by lazy {
        DefaultAlipayClient(
            props.gatewayUrl,
            props.appId,
            appPrivateKey,
            props.format,
            props.charset,
            alipayPublicKey,
            props.signType,
        )
    }

    /** 找回功能是否具备运行条件（缺 PID 时不放行，避免给 App 一个必然失败的授权串） */
    fun isConfigured(): Boolean = props.appId.isNotBlank() && props.pid.isNotBlank()

    /**
     * 生成 App 内授权所需的 authInfo。
     *
     * 参数集合固定（支付宝「获取会员信息」授权码模式的约定），
     * `target_id` 每次随机，避免同一串被重放。
     *
     * 签名规则沿用官方 demo：按 key 升序拼 `k=v&k=v`，排除 `sign` 参与签名，
     * 签完再把 sign 追加回去并对所有值做 URL 编码。
     */
    fun buildAuthInfo(): String {
        check(isConfigured()) { "找回功能未配置：请在 application.yml 填好 alipay.pid" }

        val params = sortedMapOf(
            "apiname" to "com.alipay.account.auth",
            "app_id" to props.appId,
            "app_name" to "mc",
            "auth_type" to "AUTHACCOUNT",
            "biz_type" to "openservice",
            "methodname" to "alipay.open.auth.sdk.code.get",
            "pid" to props.pid,
            "product_id" to "APP_FAST_LOGIN",
            "scope" to "kuaijie",
            "sign_type" to props.signType,
            "target_id" to UUID.randomUUID().toString().replace("-", "").take(16),
        )

        val signContent = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        val sign = AlipaySignature.rsaSign(signContent, appPrivateKey, props.charset, props.signType)
        params["sign"] = sign

        return params.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }
    }

    /**
     * auth_code → 支付宝用户号。
     *
     * 失败返回 null 而不是抛异常：找回是可选能力，调用方只需区分
     * 「查证失败」与「查证成功但没有订单」两种情况。
     */
    fun exchangeUserId(authCode: String): String? {
        val request = AlipaySystemOauthTokenRequest().apply {
            grantType = "authorization_code"
            code = authCode
        }
        return try {
            val response = client.execute(request)
            if (!response.isSuccess || response.userId.isNullOrBlank()) {
                log.warn(
                    "支付宝授权换 user_id 失败 code={} subCode={} msg={}",
                    response.code, response.subCode, response.subMsg ?: response.msg,
                )
                null
            } else {
                response.userId
            }
        } catch (e: Exception) {
            // 不记录 authCode 本身：它是可换取用户身份的一次性凭据
            log.error("支付宝授权换 user_id 异常", e)
            null
        }
    }
}
