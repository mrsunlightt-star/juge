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
    class ApiException(message: String) : Exception(message)

    /**
     * 下单：拿到 orderStr 后交给 [AlipayPay.pay] 拉起收银台。
     *
     * 只传商品 ID、不传金额——金额由服务端商品目录决定。
     * 若客户端可传金额，任何人都能以 1 分钱下单并取得一笔真实「已支付」的订单。
     *
     * [token] 为已登录账号的令牌，带上它这笔订单的 PRO 会开到账号上；
     * 不带则按游客处理，PRO 只落在本机。
     */
    suspend fun createOrder(productId: String, token: String? = null): Result<OrderCreated> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("productId", productId)
                    if (!token.isNullOrBlank()) put("token", token)
                }
                val resp = ServerClient.post("/api/alipay/create", body)
                if (!resp.optBoolean("success")) {
                    throw ApiException(resp.optString("message").ifBlank { "下单失败" })
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
     * [token] 为当前登录令牌：订单归属账号时服务端会校验查询者身份，
     * 未登录（游客订单）传 null 即可。
     */
    suspend fun queryOrder(outTradeNo: String, token: String? = null): Result<OrderStatus> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = ServerClient.get(
                    "/api/alipay/query",
                    token = token,
                    params = mapOf("outTradeNo" to outTradeNo),
                )
                if (!resp.optBoolean("success")) {
                    throw ApiException(resp.optString("message").ifBlank { "查单失败" })
                }
                val data = resp.getJSONObject("data")
                OrderStatus(
                    outTradeNo = outTradeNo,
                    tradeStatus = data.optString("trade_status", ""),
                    totalAmount = data.optString("total_amount", ""),
                    code = data.optString("code", ""),
                    subCode = data.optString("sub_code", ""),
                )
            }
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

        /** 交易不存在：订单未创建或已超过有效期，属正常状态而非故障 */
        val isNotExist: Boolean
            get() = code == "40004" && subCode == "ACQ.TRADE_NOT_EXIST"
    }
}