package com.juge.app.pay

import com.juge.app.net.ServerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 支付服务端 API 封装（调用 lkl-pay-server 的支付宝模块）。
 *
 * 服务端职责：加签、下单、查单、验签——App 端永远不接触应用私钥与证书。
 * 支付结果的权威来源：服务端主动查单 + 支付宝异步通知。
 * 服务端地址统一由 [ServerClient.baseUrl] 决定。
 */
object AlipayPayApi {

    /** 服务端明确返回的业务失败，message 为可直接展示给用户的中文提示 */
    class ApiException(message: String, val code: String = "") : Exception(message) {
        /** 429：本窗口调用次数超限，应当退避而不是当失败停手 */
        val isRateLimited: Boolean
            get() = code == CODE_RATE_LIMITED

        /** 503 CONFIG_UNAVAILABLE：服务端支付宝密钥没配好，重试无用，要联系客服 */
        val isConfigUnavailable: Boolean
            get() = code == CODE_CONFIG_UNAVAILABLE

        /** 503 RECOVERY_UNAVAILABLE：服务端未配置 alipay.pid，找回功能整体不可用 */
        val isRecoveryUnavailable: Boolean
            get() = code == CODE_RECOVERY_UNAVAILABLE
    }

    /**
     * 下单：拿到 orderStr 后交给 [AlipayPay.pay] 拉起收银台。
     *
     * 只传商品 ID、不传金额——金额由服务端商品目录决定。
     * 若客户端可传金额，任何人都能以 1 分钱下单并取得一笔真实「已支付」的订单。
     *
     * 也不传任何用户标识：这笔钱算谁的，由支付宝回调里的买家 user_id 决定。
     */
    suspend fun createOrder(productId: String): Result<OrderCreated> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply { put("productId", productId) }
                val resp = ServerClient.post("/api/alipay/create", body)
                if (!resp.isOk) throw failure(resp, "下单失败")
                val data = resp.body.getJSONObject("data")
                OrderCreated(
                    outTradeNo = data.getString("out_trade_no"),
                    orderStr = data.getString("order_str"),
                )
            }
        }

    /**
     * 查单：trade_status 为 TRADE_SUCCESS / TRADE_FINISHED 才算真正支付成功。
     *
     * 订单号由服务端生成（不可枚举），查单接口不要求身份。
     */
    suspend fun queryOrder(outTradeNo: String): Result<OrderStatus> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = ServerClient.get(
                    "/api/alipay/query",
                    params = mapOf("outTradeNo" to outTradeNo),
                )
                parseOrderStatus(outTradeNo, resp)
            }
        }

    /**
     * 把「HTTP 状态 + 响应体」翻译成一个带 code 的异常。
     *
     * code 的优先级：响应体里的业务 code（服务端会带 RATE_LIMITED /
     * CONFIG_UNAVAILABLE / RECOVERY_UNAVAILABLE），没有的话按状态码兜底。
     * 这一步是原实现缺的：它把 429 和 200+success:false 当成同一件事，
     * 于是退款、限流、配置缺失在客户端全都退化成「查单失败」四个字。
     */
    internal fun failure(resp: ServerClient.Response, fallback: String): ApiException {
        val bodyCode = resp.code
        val code = bodyCode.ifBlank {
            when (resp.httpStatus) {
                429 -> CODE_RATE_LIMITED
                503 -> CODE_CONFIG_UNAVAILABLE
                404 -> CODE_ORDER_MISSING
                else -> ""
            }
        }
        val message = resp.message.ifBlank {
            when (resp.httpStatus) {
                429 -> "操作过于频繁，请稍后再试"
                503 -> "服务暂时不可用，请稍后重试"
                else -> fallback
            }
        }
        return ApiException(message, code)
    }

    /** 已付状态须由服务端完成本地订单/金额核验后以 success=true 返回，不另增 validated 字段。 */
    internal fun parseOrderStatus(outTradeNo: String, resp: ServerClient.Response): OrderStatus {
        if (!resp.isOk) throw failure(resp, "查单失败")
        val data = resp.body.getJSONObject("data")
        return OrderStatus(
            outTradeNo = outTradeNo,
            tradeStatus = data.optString("trade_status", ""),
            totalAmount = data.optString("total_amount", ""),
            code = data.optString("code", ""),
            subCode = data.optString("sub_code", ""),
            credential = data.optString("credential", "").takeIf { it.isNotBlank() },
        )
    }

    data class OrderCreated(val outTradeNo: String, val orderStr: String)

    data class OrderStatus(
        val outTradeNo: String,
        val tradeStatus: String,
        val totalAmount: String,
        val code: String,
        val subCode: String,
        /**
         * 服务端确认已支付后随查单结果一并返回的权益凭证。
         *
         * 走到这一步意味着服务端已经核过「订单存在 + 金额一致 + 本地状态为 PAID」，
         * 所以这里拿到的是可直接交给 TrialManager 的授权。为 null 表示本次查单
         * 还没到已支付结论，或服务端凭证密钥不可用。
         */
        val credential: String? = null,
    ) {
        val isPaid: Boolean
            get() = tradeStatus == "TRADE_SUCCESS" || tradeStatus == "TRADE_FINISHED"

        /** 已关闭的终态单不再可能付款，恢复队列应移除。 */
        val isClosed: Boolean
            get() = tradeStatus == "TRADE_CLOSED"

        /** 交易不存在：订单未创建或已超过有效期，属正常状态而非故障 */
        val isNotExist: Boolean
            get() = code == "40004" && subCode == "ACQ.TRADE_NOT_EXIST"
    }

    const val CODE_RATE_LIMITED = "RATE_LIMITED"
    const val CODE_CONFIG_UNAVAILABLE = "CONFIG_UNAVAILABLE"
    const val CODE_RECOVERY_UNAVAILABLE = "RECOVERY_UNAVAILABLE"
    const val CODE_ORDER_MISSING = "ORDER_MISSING"
}
