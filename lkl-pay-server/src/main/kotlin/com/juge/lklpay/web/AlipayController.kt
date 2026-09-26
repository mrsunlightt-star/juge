package com.juge.lklpay.web

import com.juge.lklpay.config.PayProperties
import com.juge.lklpay.service.AlipayPayService
import com.juge.lklpay.service.PayOrderService
import com.juge.lklpay.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/alipay")
class AlipayController(
    private val alipayPayService: AlipayPayService,
    private val payOrderService: PayOrderService,
    private val payProperties: PayProperties,
    private val userService: UserService,
) {

    private val log = LoggerFactory.getLogger(AlipayController::class.java)

    /**
     * 下单请求。
     *
     * 刻意只接收商品 ID、不接收金额：金额由服务端商品目录决定。
     * 若允许客户端传金额，任何人都能以 1 分钱下单并取得一笔真实「已支付」的订单。
     */
    data class CreateOrderRequest(
        /** 商品 ID，取值见 application.yml 的 pay.products */
        val productId: String,
        /**
         * 可选的登录令牌。带着它下单，支付成功后 PRO 会开到对应账号上，
         * 换机登录即可找回；不带则按游客处理，PRO 只落在本机。
         * 令牌无效不报错——过期登录不该拦住付款。
         */
        val token: String? = null,
    )

    /**
     * 下单：返回 orderStr，App 端调用 `PayTask.payV2(orderStr)` 拉起支付宝。
     */
    @PostMapping("/create")
    fun create(@RequestBody req: CreateOrderRequest): ResponseEntity<*> {
        val product = payProperties.products[req.productId]
            ?: return ResponseEntity.badRequest()
                .body(mapOf("success" to false, "message" to "未知商品：${req.productId}"))

        return try {
            val result = alipayPayService.createAppOrder(product.amountFen, product.subject)
            val userId = userService.resolveOptionalUserId(req.token)
            payOrderService.recordCreated(
                outTradeNo = result["out_trade_no"] as String,
                productId = req.productId,
                subject = product.subject,
                amountFen = product.amountFen,
                userId = userId,
            )
            ResponseEntity.ok(mapOf("success" to true, "data" to result))
        } catch (e: IllegalArgumentException) {
            log.warn("支付宝下单参数非法：{}", e.message)
            ResponseEntity.badRequest().body(mapOf("success" to false, "message" to e.message))
        } catch (e: Exception) {
            log.error("支付宝下单异常", e)
            ResponseEntity.internalServerError().body(mapOf("success" to false, "message" to e.message))
        }
    }

    /**
     * 主动查单：App 端同步返回码为 8000 / 6004，或未收到异步通知时，以此接口结论为准。
     *
     * 查单结果是权威结论，顺带补齐本地订单状态——异步通知可能延迟甚至丢失。
     */
    @GetMapping("/query")
    fun query(@RequestParam outTradeNo: String): ResponseEntity<*> {
        return try {
            val result = alipayPayService.queryOrder(outTradeNo)
            reconcile(outTradeNo, result)
            ResponseEntity.ok(mapOf("success" to true, "data" to result))
        } catch (e: Exception) {
            log.error("支付宝查单异常 outTradeNo={}", outTradeNo, e)
            ResponseEntity.internalServerError().body(mapOf("success" to false, "message" to e.message))
        }
    }

    /**
     * 支付宝异步通知回调（与开放平台「应用网关」同址：/api/alipay/notify）。
     *
     * 处理顺序不可调换：**先验签 → 再处理业务（幂等）→ 返回 "success"**。
     * 返回非 "success" 时支付宝会按策略重投（25 小时内约 8 次）。
     *
     * 验签通过后一律回 "success"：业务侧异常（订单不存在、金额不符）重投也不会自愈，
     * 只会刷日志，因此改以 ERROR 日志 + 订单留档供人工介入。
     */
    @PostMapping("/notify")
    fun notify(@RequestParam params: Map<String, String>): ResponseEntity<String> {
        if (params.isEmpty()) {
            log.warn("支付宝异步通知参数为空，请检查 Content-Type 是否为 application/x-www-form-urlencoded")
            return ResponseEntity.ok("failure")
        }

        if (!alipayPayService.verifyNotify(params)) {
            log.warn("支付宝异步通知验签失败 outTradeNo={}", params["out_trade_no"])
            return ResponseEntity.ok("failure")
        }

        val outTradeNo = params["out_trade_no"]
        if (outTradeNo.isNullOrBlank()) {
            log.warn("支付宝异步通知缺少 out_trade_no，无法处理")
            return ResponseEntity.ok("success")
        }

        when (params["trade_status"]) {
            "TRADE_SUCCESS", "TRADE_FINISHED" -> handlePaidNotify(outTradeNo, params)
            "TRADE_CLOSED" -> {
                log.info("支付宝交易已关闭 outTradeNo={}", outTradeNo)
                payOrderService.markClosed(outTradeNo)
            }
            else -> log.info(
                "支付宝通知非终态，仅确认接收 tradeStatus={} outTradeNo={}",
                params["trade_status"], outTradeNo,
            )
        }

        return ResponseEntity.ok("success")
    }

    private fun handlePaidNotify(outTradeNo: String, params: Map<String, String>) {
        val outcome = payOrderService.markPaid(
            outTradeNo = outTradeNo,
            tradeNo = params["trade_no"],
            buyerUserId = params["buyer_user_id"],
            buyerLogonId = params["buyer_logon_id"],
            paidAmountYuan = params["total_amount"],
            notifyRaw = params.entries.joinToString("&") { "${it.key}=${it.value}" },
        )
        when (outcome) {
            PayOrderService.MarkPaidOutcome.Marked ->
                log.info(
                    "异步通知已落单 outTradeNo={} tradeNo={} buyerUserId={}",
                    outTradeNo, params["trade_no"], params["buyer_user_id"],
                )

            PayOrderService.MarkPaidOutcome.AlreadyPaid ->
                log.info("重复异步通知，已幂等忽略 outTradeNo={}", outTradeNo)

            PayOrderService.MarkPaidOutcome.OrderMissing ->
                log.error("异步通知对应订单不存在，需人工核查 outTradeNo={}", outTradeNo)

            PayOrderService.MarkPaidOutcome.AmountMismatch ->
                log.error(
                    "异步通知金额与订单不符，需人工介入 outTradeNo={} 通知金额={}",
                    outTradeNo, params["total_amount"],
                )
        }
    }

    /** 用查单结果补齐本地状态，避免「支付宝已收款、本地仍是待支付」 */
    private fun reconcile(outTradeNo: String, result: Map<String, Any?>) {
        when (result["trade_status"]) {
            "TRADE_SUCCESS", "TRADE_FINISHED" -> payOrderService.markPaid(
                outTradeNo = outTradeNo,
                tradeNo = result["trade_no"] as? String,
                buyerUserId = result["buyer_user_id"] as? String,
                buyerLogonId = result["buyer_logon_id"] as? String,
                paidAmountYuan = result["total_amount"] as? String,
            )

            "TRADE_CLOSED" -> payOrderService.markClosed(outTradeNo)
        }
    }
}