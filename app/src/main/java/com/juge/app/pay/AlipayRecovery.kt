package com.juge.app.pay

import android.app.Activity
import android.content.Context
import com.alipay.sdk.app.AuthTask
import com.juge.app.data.TrialManager
import com.juge.app.net.ServerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.URLDecoder

/**
 * 换手机后的权益找回：用支付宝账号证明「我就是当初付款的人」。
 *
 * 全流程只有两步，没有任何账号概念：
 *   1. 向自己的服务端取一段签好名的 authInfo（应用私钥只在服务端）
 *   2. 交给支付宝 SDK 拉起授权，拿回 auth_code 再交回服务端换 user_id
 * 服务端用 user_id 反查订单，命中即**签发一段权益凭证**（见 [com.juge.app.data.EntitlementVerifier]），
 * App 验签通过才写入本地激活状态。
 *
 * 这条路能成立的前提：付款时支付宝会把买家 user_id 带进支付结果，
 * 服务端已把它存进订单（PayOrder.buyerUserId）。
 */
object AlipayRecovery {

    sealed interface Outcome {
        /** 服务端确认这个支付宝账号买过，权益已恢复 */
        data object Restored : Outcome

        /** 授权成功，但这个支付宝账号没有任何已支付订单 */
        data object NotFound : Outcome

        /** 用户中途取消授权 */
        data object Canceled : Outcome

        /** 流程性失败（网络、服务未配置、授权失败） */
        data class Failed(val message: String) : Outcome
    }

    /** 完整找回；授权是阻塞调用，内部切到 IO，可在主线程调用。 */
    suspend fun recover(activity: Activity): Outcome = recover(activity, Dependencies())

    /**
     * 服务端核验结论。
     *
     * [paidAt] 是**当初付款的时间**（服务端取该支付宝账号最早一笔已支付订单），
     * 不是本次找回的时间。换机找回时要把它写进本地记录，
     * 否则会员卡会显示成「今天刚开通」，用户会以为买的是新的。
     *
     * [credential] 是服务端签名的权益凭证。它是「能不能落本地」的唯一依据：
     * 服务端说 found=true 只代表「这个支付宝账号买过」，凭证才代表
     * 「服务端愿意把这份权益授予这台设备」，而凭证伪造不了。
     * 服务端凭证密钥不可用时这里是 null —— 那种情况下不激活，让用户看到提示，
     * 好过给一个「查得到但用不了」的假成功。
     */
    internal data class PurchaseRecord(
        val found: Boolean,
        val paidAt: Long?,
        val credential: String?,
    )

    internal data class Dependencies(
        val fetchAuthInfo: suspend () -> Result<String> = ::fetchAuthInfo,
        val authorize: (Activity, String) -> String? = ::authorize,
        val confirmPurchase: suspend (String) -> Result<PurchaseRecord> = ::confirmPurchase,
        val activate: (Context, Long, String) -> Boolean = { context, paidAt, credential ->
            TrialManager.getInstance(context).activateWithEntitlement(
                credential = credential,
                payMethod = TrialManager.PAY_METHOD_RECOVER,
                activatedAt = paidAt,
            )
        },
    )

    internal suspend fun recover(activity: Activity, dependencies: Dependencies): Outcome {
        val authInfo = dependencies.fetchAuthInfo().getOrElse { e ->
            Timber.e(e, "AlipayRecovery: 取 authInfo 失败")
            return Outcome.Failed(
                (e as? AlipayPayApi.ApiException)?.message?.takeIf { it.isNotBlank() }
                    ?: "找回服务暂时不可用，请稍后重试或联系客服"
            )
        }

        val authCode = withContext(Dispatchers.IO) { dependencies.authorize(activity, authInfo) }
        if (authCode.isNullOrBlank()) {
            // 授权页被用户关掉 / 未安装支付宝：都不算错误，按取消处理
            return Outcome.Canceled
        }

        val record = dependencies.confirmPurchase(authCode).getOrElse { e ->
            Timber.e(e, "AlipayRecovery: 核验购买记录失败")
            return Outcome.Failed(
                (e as? AlipayPayApi.ApiException)?.message?.takeIf { it.isNotBlank() }
                    ?: "核验购买记录失败，请稍后重试"
            )
        }
        if (!record.found) return Outcome.NotFound

        val credential = record.credential
        if (credential.isNullOrBlank()) {
            // 服务端确实查到了订单，但没有带凭证：只可能是服务端凭证私钥没配好。
            // 这时不能「先放行再说」—— 本地状态一旦写下去，下次启动仍然会被判未激活，
            // 用户看到的是「找回成功但会员没生效」，比直接报错更难排查。
            Timber.w("AlipayRecovery: 服务端返回 found=true 但未带权益凭证")
            return Outcome.Failed("权益已确认但服务端暂时无法签发凭证，请稍后重试或联系客服")
        }

        // 服务端没给购买时间（老订单或字段缺失）时退回当前时间：
        // 宁可显示"今天开通"，也不能把 1970 写进会员卡。
        val paidAt = record.paidAt ?: System.currentTimeMillis()
        return withContext(Dispatchers.IO) {
            if (dependencies.activate(activity, paidAt, credential)) Outcome.Restored
            else Outcome.Failed("权益已确认，但本地保存失败，请检查存储空间后重试")
        }
    }

    // ==================== 服务端接口 ====================

    private suspend fun fetchAuthInfo(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = ServerClient.post("/api/alipay/auth-info", JSONObject())
            if (!resp.isOk) {
                // 503 RECOVERY_UNAVAILABLE 时 message 已经是「找回功能暂不可用，请联系客服」
                throw AlipayPayApi.failure(resp, "找回服务暂时不可用")
            }
            resp.body.getJSONObject("data").getString("auth_info")
        }
    }

    private suspend fun confirmPurchase(authCode: String): Result<PurchaseRecord> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply { put("authCode", authCode) }
            val resp = ServerClient.post("/api/alipay/recover", body)
            if (!resp.isOk) throw AlipayPayApi.failure(resp, "核验失败，请稍后重试")
            val data = resp.body.getJSONObject("data")
            PurchaseRecord(
                found = data.optBoolean("found", false),
                // 服务端没给或给了 null 时保持 null，由 activate 用当前时间兜底
                paidAt = if (data.isNull("paid_at")) null
                else data.optLong("paid_at", 0L).takeIf { it > 0L },
                credential = data.optString("credential", "").takeIf { it.isNotBlank() },
            )
        }
    }

    // ==================== 支付宝授权 ====================

    /**
     * 拉起支付宝授权页并等待用户操作结束。
     *
     * `AuthTask.authV2` 与 `PayTask.payV2` 一样是阻塞调用，必须放在 IO 线程。
     * 返回 auth_code；用户取消或失败返回 null。
     */
    private fun authorize(activity: Activity, authInfo: String): String? = try {
        val result = AuthTask(activity).authV2(authInfo, true)
        parseAuthCode(result)
    } catch (e: Exception) {
        Timber.e(e, "AlipayRecovery: authV2 调用异常")
        null
    }

    /**
     * 解析授权结果。
     *
     * 不同 SDK 版本返回格式不一致：老版本给 `result_code=200&auth_code=xxx` 这样的表单串，
     * 新版本给 JSON。两种都认，避免升级 SDK 后静默失效。
     */
    internal fun parseAuthCode(result: Map<String, String>?): String? {
        if (result.isNullOrEmpty()) return null
        val payload = result["result"]?.takeIf { it.isNotBlank() } ?: return null

        // 新版 SDK 返回 JSON；老版本返回表单串。先按 JSON 试，失败再按表单解析。
        runCatching { JSONObject(payload).optString("auth_code") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return payload.split('&')
            .mapNotNull { pair ->
                val index = pair.indexOf('=')
                if (index <= 0) null
                else pair.substring(0, index) to
                    runCatching { URLDecoder.decode(pair.substring(index + 1), "UTF-8") }.getOrDefault("")
            }
            .firstOrNull { it.first == "auth_code" }
            ?.second
            ?.takeIf { it.isNotBlank() }
    }
}
