package com.juge.lklpay.web

import com.juge.lklpay.config.PayProperties
import com.juge.lklpay.domain.PayOrder
import com.juge.lklpay.service.AlipayIdentityService
import com.juge.lklpay.service.AlipayPayService
import com.juge.lklpay.service.PayApiThrottle
import com.juge.lklpay.service.PayOrderService
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
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
    private val identityService: AlipayIdentityService,
    private val payApiThrottle: PayApiThrottle,
) {

    private val log = LoggerFactory.getLogger(AlipayController::class.java)

    /**
     * 下单请求。
     *
     * 刻意只接收商品 ID、不接收金额：金额由服务端商品目录决定。
     * 若允许客户端传金额，任何人都能以 1 分钱下单并取得一笔真实「已支付」的订单。
     *
     * 也不接收任何用户标识：权益归属走支付宝回调里的买家 user_id，
     * 不依赖 App 侧登录态。
     */
    data class CreateOrderRequest(
        /** 商品 ID，取值见 application.yml 的 pay.products */
        val productId: String,
    )

    /**
     * 下单：返回 orderStr，App 端调用 `PayTask.payV2(orderStr)` 拉起支付宝。
     */
    @PostMapping("/create")
    fun create(@RequestBody req: CreateOrderRequest, request: HttpServletRequest): ResponseEntity<*> {
        if (!payApiThrottle.tryConsume("create", clientIp(request), PayApiThrottle.CREATE_LIMIT_PER_MINUTE)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(mapOf("success" to false, "code" to "RATE_LIMITED", "message" to "操作过于频繁，请稍后再试"))
        }

        val product = payProperties.products[req.productId]
            ?: return ResponseEntity.badRequest()
                .body(mapOf("success" to false, "message" to "未知商品：${req.productId}"))

        return try {
            val result = alipayPayService.createAppOrder(product.amountFen, product.subject)
            payOrderService.recordCreated(
                outTradeNo = result["out_trade_no"] as String,
                productId = req.productId,
                subject = product.subject,
                amountFen = product.amountFen,
            )
            ResponseEntity.ok(mapOf("success" to true, "data" to result))
        } catch (e: IllegalArgumentException) {
            log.warn("支付宝下单参数非法：{}", e.message)
            ResponseEntity.badRequest().body(mapOf("success" to false, "message" to e.message))
        } catch (e: Exception) {
            log.error("支付宝下单异常", e)
            // 异常细节（支付宝网关错误、SQL 异常文本等）只进服务端日志，不回传客户端
            ResponseEntity.internalServerError().body(mapOf("success" to false, "message" to "服务暂时不可用，请稍后重试"))
        }
    }

    /**
     * 主动查单：App 端同步返回码为 8000 / 6004，或未收到异步通知时，以此接口结论为准。
     *
     * 查单结果是权威结论，顺带补齐本地订单状态——异步通知可能延迟甚至丢失。
     *
     * 订单号由服务端生成（时间戳 + UUID 片段，不可枚举），且响应只回传
     * 客户端确认支付所需的最小字段，买家标识等交易细节不外泄。
     */
    @GetMapping("/query")
    fun query(
        @RequestParam outTradeNo: String,
        request: HttpServletRequest,
    ): ResponseEntity<*> {
        if (!payApiThrottle.tryConsume("query", clientIp(request), PayApiThrottle.QUERY_LIMIT_PER_MINUTE)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(mapOf("success" to false, "code" to "RATE_LIMITED", "message" to "操作过于频繁，请稍后再试"))
        }

        payOrderService.find(outTradeNo)
            ?: return queryError(HttpStatus.NOT_FOUND, "ORDER_MISSING", "订单不存在")

        return try {
            val result = alipayPayService.queryOrder(outTradeNo)
            reconcile(outTradeNo, result)
                ?: ResponseEntity.ok(mapOf("success" to true, "data" to publicQueryFields(result)))
        } catch (e: Exception) {
            log.error("支付宝查单异常 outTradeNo={}", outTradeNo, e)
            ResponseEntity.internalServerError().body(mapOf("success" to false, "message" to "服务暂时不可用，请稍后重试"))
        }
    }

    /** 只回传客户端确认支付所需的最小字段 */
    private fun publicQueryFields(result: Map<String, Any?>): Map<String, Any?> = mapOf(
        "out_trade_no" to result["out_trade_no"],
        "trade_status" to result["trade_status"],
        "total_amount" to result["total_amount"],
        "code" to result["code"],
        "sub_code" to result["sub_code"],
    )

    /**
     * 取来源 IP：优先 X-Forwarded-For 最左值，再 X-Real-IP。
     * 前提：Nginx 必须覆写（而非透传）X-Forwarded-For，否则客户端可伪造该头绕过限流。
     */
    private fun clientIp(request: HttpServletRequest): String? {
        request.getHeader("X-Forwarded-For")
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        return request.getHeader("X-Real-IP")?.trim()?.takeIf { it.isNotEmpty() }
            ?: request.remoteAddr?.takeIf { it.isNotEmpty() }
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
    fun notify(@RequestParam params: Map<String, String>, request: HttpServletRequest): ResponseEntity<String> {
        if (!payApiThrottle.tryConsume("notify", clientIp(request), PayApiThrottle.NOTIFY_LIMIT_PER_MINUTE)) {
            // 回 "failure" 而不是 429：非 "success" 会让支付宝按官方节奏重投，
            // 正常通知最多被推迟，不会丢单。真正的防伪靠下面的验签，限流只是防洪。
            log.warn("异步通知触发限流，已要求支付宝重投")
            return ResponseEntity.ok("failure")
        }

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

            PayOrderService.MarkPaidOutcome.NotPaid ->
                log.error("异步通知未能将本地订单转为已支付，需人工核查 outTradeNo={}", outTradeNo)

            PayOrderService.MarkPaidOutcome.OrderMissing ->
                log.error("异步通知对应订单不存在，需人工核查 outTradeNo={}", outTradeNo)

            PayOrderService.MarkPaidOutcome.AmountMismatch ->
                log.error(
                    "异步通知金额与订单不符，需人工介入 outTradeNo={} 通知金额={}",
                    outTradeNo, params["total_amount"],
                )

            PayOrderService.MarkPaidOutcome.AmountMissing ->
                log.error(
                    "异步通知缺少可解析金额，需人工介入 outTradeNo={} 通知金额={}",
                    outTradeNo, params["total_amount"],
                )
        }
    }

    /** 返回 null 才允许回传网关状态；拒绝落单时不能让 App 看到可激活的成功响应。 */
    private fun reconcile(outTradeNo: String, result: Map<String, Any?>): ResponseEntity<*>? {
        val status = result["trade_status"]
        val paid = status == "TRADE_SUCCESS" || status == "TRADE_FINISHED"
        if (!paid && status != "TRADE_CLOSED") return null
        if (result["code"] != "10000" || result["out_trade_no"] != outTradeNo) {
            log.error("查单结果未通过网关状态或订单号核对 outTradeNo={}", outTradeNo)
            return queryError(HttpStatus.BAD_GATEWAY, "QUERY_RESULT_INVALID", "支付查询结果校验失败，请稍后重试")
        }
        if (!paid) {
            payOrderService.markClosed(outTradeNo)
            return null
        }

        val outcome = payOrderService.markPaid(
            outTradeNo = outTradeNo,
            tradeNo = result["trade_no"] as? String,
            buyerUserId = result["buyer_user_id"] as? String,
            buyerLogonId = result["buyer_logon_id"] as? String,
            paidAmountYuan = result["total_amount"] as? String,
        )
        when (outcome) {
            PayOrderService.MarkPaidOutcome.AmountMissing ->
                return queryError(HttpStatus.CONFLICT, "AMOUNT_MISSING", "支付结果缺少有效金额，无法确认支付")
            PayOrderService.MarkPaidOutcome.AmountMismatch ->
                return queryError(HttpStatus.CONFLICT, "AMOUNT_MISMATCH", "支付金额与订单不符，无法确认支付")
            PayOrderService.MarkPaidOutcome.OrderMissing ->
                return queryError(HttpStatus.NOT_FOUND, "ORDER_MISSING", "订单不存在")
            PayOrderService.MarkPaidOutcome.NotPaid ->
                return queryError(HttpStatus.CONFLICT, "ORDER_NOT_PAID", "本地订单未确认支付，请联系客服核查")
            PayOrderService.MarkPaidOutcome.Marked, PayOrderService.MarkPaidOutcome.AlreadyPaid -> Unit
        }
        val confirmed = payOrderService.find(outTradeNo)
            ?: return queryError(HttpStatus.NOT_FOUND, "ORDER_MISSING", "订单不存在")
        if (confirmed.status != PayOrder.STATUS_PAID) {
            return queryError(HttpStatus.CONFLICT, "ORDER_NOT_PAID", "本地订单未确认支付，请联系客服核查")
        }
        return null
    }

    /**
     * 换手机第一步：取一段签好名的 authInfo，交给 App 的 `AuthTask.authV2` 拉起支付宝授权。
     *
     * 签名在服务端完成，App 只拿到一串能直接交给 SDK 的字符串——应用私钥永不出服务器。
     * 未配置 `alipay.pid` 时返回 503，而不是给 App 一段必然被支付宝拒绝的授权串。
     */
    @PostMapping("/auth-info")
    fun authInfo(): ResponseEntity<*> =
        if (!identityService.isConfigured()) {
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                mapOf(
                    "success" to false,
                    "code" to "RECOVERY_UNAVAILABLE",
                    "message" to "找回功能暂不可用，请联系客服",
                ),
            )
        } else {
            runCatching { identityService.buildAuthInfo() }.fold(
                onSuccess = { ResponseEntity.ok(mapOf("success" to true, "data" to mapOf("auth_info" to it))) },
                onFailure = {
                    log.error("生成 authInfo 失败", it)
                    ResponseEntity.internalServerError()
                        .body(mapOf("success" to false, "message" to "服务暂时不可用，请稍后重试"))
                },
            )
        }

    /** 找回请求：App 授权拿到的 auth_code（一次性，短时有效） */
    data class RecoverRequest(val authCode: String = "")

    /**
     * 换手机第二步：用 auth_code 换出支付宝用户号，反查是否买过。
     *
     * 只返回「找到 / 没找到」与最早购买时间，**不签发任何令牌**——
     * 权益验证的结论由 App 自己写进本地激活记录，服务端不维护登录态，
     * 也就没有账号可以注销、没有密码可以泄露。
     */
    @PostMapping("/recover")
    fun recover(@RequestBody req: RecoverRequest, request: HttpServletRequest): ResponseEntity<*> {
        if (!payApiThrottle.tryConsume("recover", clientIp(request), PayApiThrottle.RECOVER_LIMIT_PER_MINUTE)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(mapOf("success" to false, "code" to "RATE_LIMITED", "message" to "操作过于频繁，请稍后再试"))
        }
        if (req.authCode.isBlank()) {
            return ResponseEntity.badRequest()
                .body(mapOf("success" to false, "code" to "INVALID_ARGUMENT", "message" to "授权信息为空"))
        }

        val buyerUserId = identityService.exchangeUserId(req.authCode)
            ?: return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                mapOf(
                    "success" to false,
                    "code" to "AUTH_FAILED",
                    "message" to "支付宝授权校验失败，请重试或联系客服",
                ),
            )

        val paid = payOrderService.findPaidByBuyer(buyerUserId)
        val earliestPaidAt = paid.mapNotNull { it.paidAt }.minOrNull()
        log.info("找回查询 buyerUserId={} 命中已支付订单 {} 笔", buyerUserId, paid.size)

        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "data" to mapOf(
                    "found" to paid.isNotEmpty(),
                    "paid_at" to earliestPaidAt,
                    "count" to paid.size,
                ),
            ),
        )
    }

    private fun queryError(status: HttpStatus, code: String, message: String): ResponseEntity<*> =
        ResponseEntity.status(status).body(mapOf("success" to false, "code" to code, "message" to message))
}
