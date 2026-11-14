package com.juge.lklpay

import com.juge.lklpay.config.PayProperties
import com.juge.lklpay.domain.PayOrder
import com.juge.lklpay.service.AlipayIdentityService
import com.juge.lklpay.service.AlipayPayService
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/** 仅使用 MockMvc 与模拟网关，不启动支付客户端、不读取密钥、不发网络请求。 */
class AlipayControllerTest {
    private val alipay = mock(AlipayPayService::class.java)
    private val orders = mock(PayOrderService::class.java)
    private val identity = mock(AlipayIdentityService::class.java)
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
            AlipayController(alipay, orders, PayProperties(), identity, PayApiThrottle())
        ).build()
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
        givenPaidResult("TRADE_SUCCESS", "1.99", MarkPaidOutcome.OrderMissing)
        assertRejected(404, "ORDER_MISSING")
    }

    @ParameterizedTest
    @ValueSource(strings = ["TRADE_SUCCESS", "TRADE_FINISHED"])
    fun `闭单没有转为 PAID 就不能激活`(tradeStatus: String) {
        `when`(orders.find(NO)).thenReturn(order(PayOrder.STATUS_CLOSED))
        givenPaidResult(tradeStatus, "1.99", MarkPaidOutcome.NotPaid)
        assertRejected(409, "ORDER_NOT_PAID")
    }

    @ParameterizedTest
    @ValueSource(strings = ["TRADE_SUCCESS", "TRADE_FINISHED"])
    fun `正常付款保持原响应字段并只暴露公开数据`(tradeStatus: String) {
        `when`(orders.find(NO)).thenReturn(order(PayOrder.STATUS_CREATED), order(PayOrder.STATUS_PAID))
        givenPaidResult(tradeStatus, "1.99", MarkPaidOutcome.Marked)
        assertPaid(tradeStatus)
        verify(orders).markPaid(NO, "TRADE_TEST", "BUYER_TEST", null, "1.99")
    }

    @Test
    fun `已付款且校验通过的重复查询仍成功`() {
        `when`(orders.find(NO)).thenReturn(order(PayOrder.STATUS_PAID))
        givenPaidResult("TRADE_SUCCESS", "1.99", MarkPaidOutcome.AlreadyPaid)
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
        givenPaidResult("TRADE_SUCCESS", "1.99", MarkPaidOutcome.Marked)
        assertRejected(409, "ORDER_NOT_PAID")
    }

    @Test
    fun `幂等结果也不能把 CLOSED 当成 PAID`() {
        `when`(orders.find(NO)).thenReturn(order(PayOrder.STATUS_CLOSED))
        givenPaidResult("TRADE_SUCCESS", "1.99", MarkPaidOutcome.AlreadyPaid)
        assertRejected(409, "ORDER_NOT_PAID")
    }

    @Test
    fun `二次确认时缺少订单也不能激活`() {
        `when`(orders.find(NO)).thenReturn(order(PayOrder.STATUS_CREATED), null)
        givenPaidResult("TRADE_SUCCESS", "1.99", MarkPaidOutcome.Marked)
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
            .andExpect(jsonPath("$.data.total_amount").value("1.99"))
            .andExpect(jsonPath("$.data.code").value("10000"))
            .andExpect(jsonPath("$.data.buyer_user_id").doesNotExist())
            .andExpect(jsonPath("$.data.trade_no").doesNotExist())
    }

    private fun order(status: String) = PayOrder(outTradeNo = NO, amountFen = 199, status = status)

    private fun paidResult(tradeStatus: String = "TRADE_SUCCESS", amount: String? = "1.99"): Map<String, Any?> = mapOf(
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
