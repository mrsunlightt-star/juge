package com.juge.lklpay

import com.juge.lklpay.config.AlipayConfigStatus
import com.juge.lklpay.config.PayProperties
import com.juge.lklpay.domain.PayOrder
import com.juge.lklpay.service.AlipayIdentityService
import com.juge.lklpay.service.AlipayPayService
import com.juge.lklpay.service.EntitlementSigner
import com.juge.lklpay.service.PayApiThrottle
import com.juge.lklpay.service.PayOrderService
import com.juge.lklpay.service.PayOrderService.MarkPaidOutcome
import com.juge.lklpay.web.AlipayController
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/** 仅使用 MockMvc 与模拟网关，不启动支付客户端、不读取密钥、不发网络请求。 */
class AlipayControllerTest {
    private val alipay = mock(AlipayPayService::class.java)
    private val orders = mock(PayOrderService::class.java)
    private val identity = mock(AlipayIdentityService::class.java)
    private val configStatus = mock(AlipayConfigStatus::class.java)
    private val entitlementSigner = mock(EntitlementSigner::class.java)
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
            AlipayController(
                alipay, orders, PayProperties(), identity, PayApiThrottle(), configStatus, entitlementSigner,
            )
        ).build()
        // 默认按「配置就绪」跑，让每个用例只关心自己的断言；配置缺口有专门用例覆盖
        `when`(configStatus.keyReady).thenReturn(true)
        `when`(configStatus.recoveryReady).thenReturn(true)
        `when`(orders.find(NO)).thenReturn(order(PayOrder.STATUS_CREATED))
    }

    @ParameterizedTest
    @ValueSource(strings = ["TRADE_SUCCESS", "TRADE_FINISHED"])
    fun `金额缺失不能向 App 报支付成功`(tradeStatus: String) {
        givenPaidResult(tradeStatus, null, MarkPaidOutcome.AmountMissing)
        assertRejected(409, "AMOUNT_MISSING")
    }

    @ParameterizedTest
    @ValueSource(strings = ["TRADE_SUCCESS", "TRADE_FINISHED"])
    fun `金额不符不能向 App 报支付成功`(tradeStatus: String) {
        givenPaidResult(tradeStatus, "0.01", MarkPaidOutcome.AmountMismatch)
        assertRejected(409, "AMOUNT_MISMATCH")
    }

    @Test
    fun `没有本地订单时不代客查询网关`() {
        `when`(orders.find(NO)).thenReturn(null)
        assertRejected(404, "ORDER_MISSING")
        verifyNoInteractions(alipay)
    }

    @Test
    fun `落单时发现订单消失也拒绝成功`() {
        givenPaidResult("TRADE_SUCCESS", "3.99", MarkPaidOutcome.OrderMissing)
        assertRejected(404, "ORDER_MISSING")
    }

    @ParameterizedTest
    @ValueSource(strings = ["TRADE_SUCCESS", "TRADE_FINISHED"])
    fun `闭单没有转为 PAID 就不能激活`(tradeStatus: String) {
        `when`(orders.find(NO)).thenReturn(order(PayOrder.STATUS_CLOSED))
        givenPaidResult(tradeStatus, "3.99", MarkPaidOutcome.NotPaid)
        assertRejected(409, "ORDER_NOT_PAID")
    }

    @ParameterizedTest
    @ValueSource(strings = ["TRADE_SUCCESS", "TRADE_FINISHED"])
    fun `正常付款保持原响应字段并只暴露公开数据`(tradeStatus: String) {
        `when`(orders.find(NO)).thenReturn(order(PayOrder.STATUS_CREATED), order(PayOrder.STATUS_PAID))
        givenPaidResult(tradeStatus, "3.99", MarkPaidOutcome.Marked)
        assertPaid(tradeStatus)
        verify(orders).markPaid(NO, "TRADE_TEST", "BUYER_TEST", null, "3.99")
    }

    @Test
    fun `已付款且校验通过的重复查询仍成功`() {
        `when`(orders.find(NO)).thenReturn(order(PayOrder.STATUS_PAID))
        givenPaidResult("TRADE_SUCCESS", "3.99", MarkPaidOutcome.AlreadyPaid)
        assertPaid("TRADE_SUCCESS")
    }

    @Test
    fun `已付款也不能忽略本次金额缺失`() {
        `when`(orders.find(NO)).thenReturn(order(PayOrder.STATUS_PAID))
        givenPaidResult("TRADE_SUCCESS", null, MarkPaidOutcome.AmountMissing)
        assertRejected(409, "AMOUNT_MISSING")
    }

    @Test
    fun `已付款也不能忽略本次金额不符`() {
        `when`(orders.find(NO)).thenReturn(order(PayOrder.STATUS_PAID))
        givenPaidResult("TRADE_SUCCESS", "0.01", MarkPaidOutcome.AmountMismatch)
        assertRejected(409, "AMOUNT_MISMATCH")
    }

    @Test
    fun `即使服务报告成功也必须确认本地 PAID`() {
        givenPaidResult("TRADE_SUCCESS", "3.99", MarkPaidOutcome.Marked)
        assertRejected(409, "ORDER_NOT_PAID")
    }

    @Test
    fun `幂等结果也不能把 CLOSED 当成 PAID`() {
        `when`(orders.find(NO)).thenReturn(order(PayOrder.STATUS_CLOSED))
        givenPaidResult("TRADE_SUCCESS", "3.99", MarkPaidOutcome.AlreadyPaid)
        assertRejected(409, "ORDER_NOT_PAID")
    }

    @Test
    fun `二次确认时缺少订单也不能激活`() {
        `when`(orders.find(NO)).thenReturn(order(PayOrder.STATUS_CREATED), null)
        givenPaidResult("TRADE_SUCCESS", "3.99", MarkPaidOutcome.Marked)
        assertRejected(404, "ORDER_MISSING")
    }

    @ParameterizedTest
    @ValueSource(strings = ["OTHER_ORDER", ""])
    fun `响应订单号不符或为空时拒绝落单`(returnedNo: String) {
        `when`(alipay.queryOrder(NO)).thenReturn(paidResult() + ("out_trade_no" to returnedNo))
        assertRejected(502, "QUERY_RESULT_INVALID")
        verify(orders, never()).markPaid(anyString(), any(), any(), any(), any(), any())
    }

    @Test
    fun `响应缺少订单号时拒绝落单`() {
        `when`(alipay.queryOrder(NO)).thenReturn(paidResult() - "out_trade_no")
        assertRejected(502, "QUERY_RESULT_INVALID")
        verify(orders, never()).markPaid(anyString(), any(), any(), any(), any(), any())
    }

    @Test
    fun `网关业务失败但携带成功状态也不放行`() {
        `when`(alipay.queryOrder(NO)).thenReturn(paidResult() + ("code" to "40004"))
        assertRejected(502, "QUERY_RESULT_INVALID")
        verify(orders, never()).markPaid(anyString(), any(), any(), any(), any(), any())
    }

    @Test
    fun `待付款响应保持正常且不落付款`() {
        `when`(alipay.queryOrder(NO)).thenReturn(paidResult() + ("trade_status" to "WAIT_BUYER_PAY"))
        mvc.perform(get("/api/alipay/query").param("outTradeNo", NO))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.trade_status").value("WAIT_BUYER_PAY"))
        verify(orders, never()).markPaid(anyString(), any(), any(), any(), any(), any())
    }

    // ── 查单确认已支付后必须带可验签的权益凭证（服务端签发 / 客户端验签契约）──

    @Test
    fun `查单确认已支付时带出权益凭证`() {
        `when`(configStatus.entitlementReady).thenReturn(true)
        `when`(entitlementSigner.issue(anyString(), anyString(), anyLong())).thenReturn("v1.payload.sig")
        givenPaidResult("TRADE_SUCCESS", "3.99", MarkPaidOutcome.Marked)
        `when`(orders.find(NO)).thenReturn(order(PayOrder.STATUS_PAID))

        mvc.perform(get("/api/alipay/query").param("outTradeNo", NO))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.credential").value("v1.payload.sig"))
    }

    @Test
    fun `凭证签发不可用时查单照常成功但不带凭证`() {
        `when`(configStatus.entitlementReady).thenReturn(false)
        givenPaidResult("TRADE_SUCCESS", "3.99", MarkPaidOutcome.Marked)
        `when`(orders.find(NO)).thenReturn(order(PayOrder.STATUS_PAID))

        mvc.perform(get("/api/alipay/query").param("outTradeNo", NO))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.credential").doesNotExist())
        verify(entitlementSigner, never()).issue(anyString(), anyString(), anyLong())
    }

    @Test
    fun `待付款的查单不签发凭证`() {
        `when`(configStatus.entitlementReady).thenReturn(true)
        `when`(alipay.queryOrder(NO)).thenReturn(paidResult() + ("trade_status" to "WAIT_BUYER_PAY"))

        mvc.perform(get("/api/alipay/query").param("outTradeNo", NO))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.credential").doesNotExist())
        verify(entitlementSigner, never()).issue(anyString(), anyString(), anyLong())
    }

    // ── P1-14：auth-info 是唯一纯 CPU 端点，必须限流 ──

    @Test
    fun `auth-info 连续调用会被限流`() {
        `when`(identity.buildAuthInfo()).thenReturn("app_id=1&sign=x")
        val limit = PayApiThrottle.AUTH_INFO_LIMIT_PER_MINUTE
        repeat(limit) {
            mvc.perform(post("/api/alipay/auth-info")).andExpect(status().isOk)
        }

        mvc.perform(post("/api/alipay/auth-info"))
            .andExpect(status().`is`(429))
            .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
        // 被限流的那一次不能再做一次 RSA 签名
        verify(identity, times(limit)).buildAuthInfo()
    }

    @Test
    fun `auth-info 未配置 pid 时返回 503 而不是必然被拒的授权串`() {
        `when`(configStatus.recoveryReady).thenReturn(false)

        mvc.perform(post("/api/alipay/auth-info"))
            .andExpect(status().`is`(503))
            .andExpect(jsonPath("$.code").value("RECOVERY_UNAVAILABLE"))
        verify(identity, never()).buildAuthInfo()
    }

    // ── P1-13：限流取 IP 必须只信可信反代的转发头 ──

    @Test
    fun `直连对端不可信时忽略伪造的转发头`() {
        // 直连 remoteAddr 不是回环（MockMvc 默认 127.0.0.1，这里显式改成公网地址），
        // 那么客户端自己塞的 X-Forwarded-For 必须被无视，否则换个头就换一个限流桶。
        val limit = PayApiThrottle.QUERY_LIMIT_PER_MINUTE
        repeat(limit) {
            mvc.perform(
                get("/api/alipay/query").param("outTradeNo", NO)
                    .with { request ->
                        request.remoteAddr = "203.0.113.7"
                        request.addHeader("X-Forwarded-For", "10.0.0.$it")
                        request
                    },
            )
        }

        mvc.perform(
            get("/api/alipay/query").param("outTradeNo", NO)
                .with { request ->
                    request.remoteAddr = "203.0.113.7"
                    request.addHeader("X-Forwarded-For", "10.9.9.9")
                    request
                },
        ).andExpect(status().`is`(429))
    }

    @Test
    fun `可信反代转发时按真实客户端 IP 分桶`() {
        val limit = PayApiThrottle.QUERY_LIMIT_PER_MINUTE
        // 全部经由本机 nginx（remoteAddr = 127.0.0.1），但 XFF 里是不同客户端
        repeat(limit + 5) { index ->
            mvc.perform(
                get("/api/alipay/query").param("outTradeNo", NO)
                    .with { request ->
                        request.remoteAddr = "127.0.0.1"
                        request.addHeader("X-Forwarded-For", "198.51.100.$index")
                        request
                    },
            ).andExpect(status().isOk)
        }
    }

    private fun givenPaidResult(tradeStatus: String, amount: String?, outcome: MarkPaidOutcome) {
        `when`(alipay.queryOrder(NO)).thenReturn(paidResult(tradeStatus, amount))
        `when`(orders.markPaid(NO, "TRADE_TEST", "BUYER_TEST", null, amount)).thenReturn(outcome)
    }

    private fun assertRejected(httpStatus: Int, code: String) {
        mvc.perform(get("/api/alipay/query").param("outTradeNo", NO))
            .andExpect(status().`is`(httpStatus))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(jsonPath("$.trade_status").doesNotExist())
    }

    private fun assertPaid(tradeStatus: String) {
        mvc.perform(get("/api/alipay/query").param("outTradeNo", NO))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.out_trade_no").value(NO))
            .andExpect(jsonPath("$.data.trade_status").value(tradeStatus))
            .andExpect(jsonPath("$.data.total_amount").value("3.99"))
            .andExpect(jsonPath("$.data.code").value("10000"))
            .andExpect(jsonPath("$.data.buyer_user_id").doesNotExist())
            .andExpect(jsonPath("$.data.trade_no").doesNotExist())
    }

    private fun order(status: String) = PayOrder(outTradeNo = NO, amountFen = 399, status = status)

    private fun paidResult(tradeStatus: String = "TRADE_SUCCESS", amount: String? = "3.99"): Map<String, Any?> = mapOf(
        "out_trade_no" to NO,
        "trade_no" to "TRADE_TEST",
        "buyer_user_id" to "BUYER_TEST",
        "trade_status" to tradeStatus,
        "total_amount" to amount,
        "code" to "10000",
    )

    private companion object {
        const val NO = "T_CONTROLLER"
    }
}
