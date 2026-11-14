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
        // 服务端 HTTP 429 的响应体保留 RATE_LIMITED；ServerClient 会读取 errorStream。
        val isRateLimited: Boolean get() = code == "RATE_LIMITED"
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
                if (!resp.optBoolean("success")) {
                    throw ApiException(resp.optString("message").ifBlank { "下单失败" }, resp.optString("code"))
                }
                val data = resp.getJSONObject("data")
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

    /** 已付状态须由服务端完成本地订单/金额核验后以 success=true 返回，不另增 validated 字段。 */
    internal fun parseOrderStatus(outTradeNo: String, resp: JSONObject): OrderStatus {
        if (!resp.optBoolean("success")) {
            throw ApiException(resp.optString("message").ifBlank { "查单失败" }, resp.optString("code"))
        }
        val data = resp.getJSONObject("data")
        return OrderStatus(
            outTradeNo = outTradeNo,
            tradeStatus = data.optString("trade_status", ""),
            totalAmount = data.optString("total_amount", ""),
            code = data.optString("code", ""),
            subCode = data.optString("sub_code", ""),
        )
    }

    data class OrderCreated(val outTradeNo: String, val orderStr: String)

    data class OrderStatus(
        val outTradeNo: String,
        val tradeStatus: String,
        val totalAmount: String,
        val code: String,
        val subCode: String,
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
}