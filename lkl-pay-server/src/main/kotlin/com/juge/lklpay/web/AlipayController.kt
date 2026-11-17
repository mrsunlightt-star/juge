package com.juge.lklpay.web

import com.juge.lklpay.config.AlipayConfigStatus
import com.juge.lklpay.config.PayProperties
import com.juge.lklpay.domain.PayOrder
import com.juge.lklpay.service.AlipayIdentityService
import com.juge.lklpay.service.AlipayPayService
import com.juge.lklpay.service.EntitlementSigner
import com.juge.lklpay.service.PayApiThrottle
import com.juge.lklpay.service.PayOrderService
import com.juge.lklpay.service.Sensitive
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
    private val configStatus: AlipayConfigStatus,
    private val entitlementSigner: EntitlementSigner,
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
        if (!configStatus.keyReady) return unavailable("下单")

        if (!payApiThrottle.tryConsume("create", clientIp(request), PayApiThrottle.CREATE_LIMIT_PER_MINUTE)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(mapOf("success" to false, "code" to "RATE_LIMITED", "message" to "操作过于频繁，请稍后再试"))
        }

        val product = payProperties.products[req.productId]
            ?: return ResponseEntity.badRequest()
                .body(mapOf("success" to false, "message" to "未知商品：${safe(req.productId)}"))

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
        if (!configStatus.keyReady) return unavailable("查单")

        if (!payApiThrottle.tryConsume("query", clientIp(request), PayApiThrottle.QUERY_LIMIT_PER_MINUTE)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(mapOf("success" to false, "code" to "RATE_LIMITED", "message" to "操作过于频繁，请稍后再试"))
        }

        payOrderService.find(outTradeNo)
            ?: return queryError(HttpStatus.NOT_FOUND, "ORDER_MISSING", "订单不存在")

        return try {
            val result = alipayPayService.queryOrder(outTradeNo)
            reconcile(outTradeNo, result)
                ?: ResponseEntity.ok(mapOf("success" to true, "data" to confirmedPaidFields(outTradeNo, result)))
        } catch (e: Exception) {
            log.error("支付宝查单异常 outTradeNo={}", safe(outTradeNo), e)
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
     * 查单确认已支付后返回的字段：在最小字段之上多一个权益凭证。
     *
     * 为什么凭证可以走这条无鉴权接口：
     *   - 订单号由服务端生成（时间戳 + UUID 片段），不是可枚举的序号，持有它本身就是
     *     「这笔交易的相关方」的弱证明；查单接口原本也已凭它返回 trade_status 与金额。
     *   - 凭证的内容只声明「订单 outTradeNo 已支付、属于 productId、付款时间 X」，
     *     由服务端私钥签名。它**不含**任何可用于登录或调用其它接口的凭据。
     *   - 拿不到订单号就签不出凭证；拿到订单号的人本来也就知道这笔订单已付。
     * 这是「付款后当场激活」这条路必须的一步：不能只靠 /recover 签发，
     * 否则每买一次都要用户再走一遍支付宝授权。
     */
    private fun confirmedPaidFields(outTradeNo: String, result: Map<String, Any?>): Map<String, Any?> {
        val base = publicQueryFields(result).toMutableMap()

        // reconcile() 对「交易还没付款」同样返回 null（它只是没拒绝），
        // 所以走到这里**不代表已支付**。签发凭证前必须自己再确认一次：
        // 少这一步，一个 WAIT_BUYER_PAY 的订单查一次就能换到 PRO 凭证。
        // 两处都要看——网关侧状态与本地落库状态，任一处不成立都不签。
        val tradeStatus = result["trade_status"]
        val gatewaySaysPaid = tradeStatus == "TRADE_SUCCESS" || tradeStatus == "TRADE_FINISHED"
        val order = payOrderService.find(outTradeNo)
        val persistentlyPaid = order?.status == PayOrder.STATUS_PAID
        if (!gatewaySaysPaid || !persistentlyPaid) return base

        val paidAt = order.paidAt ?: System.currentTimeMillis()
        val buyerUserId = order.buyerUserId
        if (buyerUserId.isNullOrBlank()) {
            // 买家标识缺失（历史数据 / 通知未到）：签不出有意义的 buyerHash，
            // 但 buyerHash 在客户端只用于日志与客服对账、不参与放行，
            // 所以用订单号占位不会削弱授权，也不会让「这笔已付款」落不到设备上。
            log.warn("查单确认已支付但订单缺少 buyerUserId，凭证 buyerHash 用订单号占位 outTradeNo={}", safe(outTradeNo))
        }
        if (configStatus.entitlementReady) {
            runCatching {
                entitlementSigner.issue(
                    buyerUserId = buyerUserId?.takeIf { it.isNotBlank() } ?: outTradeNo,
                    // 客户端 EntitlementVerifier 只认 PRODUCT_ID 这一个常量。
                    // 两条签发路径（查单 / 找回）必须给同一个值，不能一条用订单里的
                    // productId、另一条用常量——将来加新商品时这里会悄悄验不过。
                    productId = PayOrderService.PRODUCT_ID,
                    paidAt = paidAt,
                )
            }.onFailure { log.error("查单确认已支付但凭证签发失败 outTradeNo={}", safe(outTradeNo), it) }
                .getOrNull()
                ?.let { base["credential"] = it }
        }
        return base
    }

    /**
     * 取来源 IP。
     *
     * 只有在**直连对端是可信反代**（回环地址，即本机的 nginx）时才采信转发头；
     * 否则一律用 TCP 层的 remoteAddr——安全组一旦被放开 8081，伪造 XFF 也绕不过限流。
     *
     * 采信时取 `X-Forwarded-For` 的**最后一个值**，而不是第一个：
     *   - nginx 按本文档配置覆写为 $remote_addr 时，只有一个值，取哪个都对；
     *   - 万一有人按旧文档写成 $proxy_add_x_forwarded_for（追加），最左值完全由客户端
     *     控制，任何人都能靠加一个请求头拿到无限配额；最后一个值才是 nginx 追加的真实来源。
     * 旧实现取第一个值，正是这个可被绕过的写法。
     */
    private fun clientIp(request: HttpServletRequest): String? {
        val direct = request.remoteAddr?.takeIf { it.isNotBlank() }
        if (direct == null || !isTrustedProxy(direct)) return direct

        request.getHeader("X-Forwarded-For")
            ?.split(",")
            ?.map { it.trim() }
            ?.lastOrNull { it.isNotEmpty() }
            ?.let { return it }

        request.getHeader("X-Real-IP")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return direct
    }

    /** 可信反代：只认本机回环。以后若加 LB，把它的地址加进来。 */
    private fun isTrustedProxy(addr: String): Boolean =
        addr == "127.0.0.1" || addr == "0:0:0:0:0:0:0:1" || addr == "::1" || addr.startsWith("127.")

    /**
     * 日志安全：统一委托给 [Sensitive.safeLog]，与限流桶、金额校验日志用同一套净化规则。
     * 动机见 `Sensitive.safeLog` 的注释：验签失败分支要在验签之前记参数，
     * 而参数完全受外部控制，必须防 `\r\n` 伪造日志行。
     */
    private fun safe(value: Any?): String = Sensitive.safeLog(value)

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
        // 密钥不可用时无法验签，此时**不能**回 success——那等于把一笔真实付款丢掉且不留痕。
        // 回 failure 让支付宝重投，等密钥修好后自然补上。
        if (!configStatus.keyReady) return ResponseEntity.ok("failure")

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
            // 这里必须记：验签失败的请求不会进业务分支，不记就完全没有线索。
            // 参数完全受控，所以经过 safe() 处理，避免用换行伪造日志行。
            log.warn(
                "支付宝异步通知验签失败 outTradeNo={} appId={} sellerId={}",
                safe(params["out_trade_no"]), safe(params["app_id"]), safe(params["seller_id"]),
            )
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
                log.info("支付宝交易已关闭 outTradeNo={}", safe(outTradeNo))
                payOrderService.markClosed(outTradeNo)
            }
            else -> log.info(
                "支付宝通知非终态，仅确认接收 tradeStatus={} outTradeNo={}",
                safe(params["trade_status"]), safe(outTradeNo),
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
                    safe(outTradeNo), safe(params["trade_no"]), safe(params["buyer_user_id"]),
                )

            PayOrderService.MarkPaidOutcome.AlreadyPaid ->
                log.info("重复异步通知，已幂等忽略 outTradeNo={}", safe(outTradeNo))

            PayOrderService.MarkPaidOutcome.NotPaid ->
                log.error("异步通知未能将本地订单转为已支付，需人工核查 outTradeNo={}", safe(outTradeNo))

            PayOrderService.MarkPaidOutcome.OrderMissing ->
                log.error("异步通知对应订单不存在，需人工核查 outTradeNo={}", safe(outTradeNo))

            PayOrderService.MarkPaidOutcome.AmountMismatch ->
                log.error(
                    "异步通知金额与订单不符，需人工介入 outTradeNo={} 通知金额={}",
                    safe(outTradeNo), safe(params["total_amount"]),
                )

            PayOrderService.MarkPaidOutcome.AmountMissing ->
                log.error(
                    "异步通知缺少可解析金额，需人工介入 outTradeNo={} 通知金额={}",
                    safe(outTradeNo), safe(params["total_amount"]),
                )
        }
    }

    /** 返回 null 才允许回传网关状态；拒绝落单时不能让 App 看到可激活的成功响应。 */
    private fun reconcile(outTradeNo: String, result: Map<String, Any?>): ResponseEntity<*>? {
        val status = result["trade_status"]
        val paid = status == "TRADE_SUCCESS" || status == "TRADE_FINISHED"
        if (!paid && status != "TRADE_CLOSED") return null
        if (result["code"] != "10000" || result["out_trade_no"] != outTradeNo) {
            log.error("查单结果未通过网关状态或订单号核对 outTradeNo={}", safe(outTradeNo))
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
     *
     * 门控顺序与其余四个端点一致：**配置检查 → 限流 → 干活**。
     * 反过来会有两个后果：pid 未配时本端点恒 503，却白白消耗调用方的配额；
     * 第 11 次请求会返回 429 把「配置缺失」这个真正的原因盖掉，
     * 而客户端是按 `RECOVERY_UNAVAILABLE` 显示联系客服文案的。
     */
    @PostMapping("/auth-info")
    fun authInfo(request: HttpServletRequest): ResponseEntity<*> {
        if (!configStatus.recoveryReady) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                mapOf(
                    "success" to false,
                    "code" to "RECOVERY_UNAVAILABLE",
                    "message" to "找回功能暂不可用，请联系客服",
                ),
            )
        }
        // 这是唯一会「纯 CPU 干活」的端点：每次调用做一次 RSA 签名，没有网络请求。
        // 原先漏了限流，等于给出一个零成本的签名放大器。
        if (!payApiThrottle.tryConsume("auth-info", clientIp(request), PayApiThrottle.AUTH_INFO_LIMIT_PER_MINUTE)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(mapOf("success" to false, "code" to "RATE_LIMITED", "message" to "操作过于频繁，请稍后再试"))
        }
        return runCatching { identityService.buildAuthInfo() }.fold(
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
     * 换手机第二步：用 auth_code 换出支付宝用户号，反查是否买过，命中则签发权益凭证。
     *
     * **不签发任何登录态**：响应里没有令牌、没有会话、没有可注销的账号，
     * 服务端也不记录「谁在哪台设备上」。凭证是一段自包含的签名声明
     * （格式见 [EntitlementSigner]），客户端用内置公钥验签，验不过就当没买过。
     *
     * 为什么不再让客户端自己写本地激活记录：那样「是不是 PRO」的判断完全发生在
     * 攻击者可控的进程里——改 prefs、重算 checksum 即可永久激活。加上凭证之后，
     * 本地那三个键只负责「不要每次都验签」，真正的授权来自一段伪造不出来的签名。
     *
     * 凭证密钥不可用时仍返回 found/paid_at，只是不带 `credential`：
     * 用户能知道「买过」，但落不到新设备上，App 会提示联系客服。
     */
    @PostMapping("/recover")
    fun recover(@RequestBody req: RecoverRequest, request: HttpServletRequest): ResponseEntity<*> {
        if (!configStatus.recoveryReady) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                mapOf(
                    "success" to false,
                    "code" to "RECOVERY_UNAVAILABLE",
                    "message" to "找回功能暂不可用，请联系客服",
                ),
            )
        }
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
        log.info("找回查询 buyerUserId={} 命中已支付订单 {} 笔", safe(buyerUserId), paid.size)

        // 命中已付款订单才签发凭证。paidAt 缺失（历史数据）用当前时间兜底：
        // 凭证里的 paidAt 只用于展示「什么时候买的」，不是授权依据。
        val credential = if (paid.isNotEmpty() && configStatus.entitlementReady) {
            runCatching {
                entitlementSigner.issue(
                    buyerUserId = buyerUserId,
                    productId = PayOrderService.PRODUCT_ID,
                    paidAt = earliestPaidAt ?: System.currentTimeMillis(),
                )
            }.onFailure { log.error("权益凭证签发失败，本次找回不带凭证返回", it) }.getOrNull()
        } else {
            null
        }

        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "data" to buildMap<String, Any?> {
                    put("found", paid.isNotEmpty())
                    put("paid_at", earliestPaidAt)
                    put("count", paid.size)
                    put("product_id", PayOrderService.PRODUCT_ID)
                    if (credential != null) put("credential", credential)
                },
            ),
        )
    }

    private fun unavailable(action: String): ResponseEntity<*> {
        log.error("{}被拒绝：支付宝密钥未就绪，请检查启动日志中的配置自检结论", action)
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(mapOf("success" to false, "code" to "CONFIG_UNAVAILABLE", "message" to "服务暂时不可用，请稍后重试"))
    }

    private fun queryError(status: HttpStatus, code: String, message: String): ResponseEntity<*> =
        ResponseEntity.status(status).body(mapOf("success" to false, "code" to code, "message" to message))
}
