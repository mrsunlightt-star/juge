package com.juge.lklpay

import com.juge.lklpay.domain.PayOrder
import com.juge.lklpay.service.PayOrderService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 订单状态流转的幂等性与金额核对。
 *
 * 这两条是资金安全的关键：异步通知会重投多次，金额则决定「1 分钱能否买到 PRO」。
 */
@SpringBootTest(properties = ["spring.datasource.url=jdbc:h2:mem:payorder-test;DB_CLOSE_DELAY=-1"])
class PayOrderServiceTest {

    @Autowired
    private lateinit var service: PayOrderService

    @Test
    fun `重复通知只落一次已支付`() {
        val no = "T_IDEMPOTENT"
        service.recordCreated(no, "pro_permanent", "句阁 PRO 会员", 199)

        val first = service.markPaid(no, "TRADE_1", "BUYER_1", "138****0000", "1.99")
        val second = service.markPaid(no, "TRADE_1", "BUYER_1", "138****0000", "1.99")

        assertEquals(PayOrderService.MarkPaidOutcome.Marked, first)
        assertEquals(PayOrderService.MarkPaidOutcome.AlreadyPaid, second)
        assertEquals(PayOrder.STATUS_PAID, service.find(no)!!.status)
    }

    @Test
    fun `金额不符时拒绝置为已支付`() {
        val no = "T_MISMATCH"
        service.recordCreated(no, "pro_permanent", "句阁 PRO 会员", 199)

        val outcome = service.markPaid(no, "TRADE_2", "BUYER_2", null, "0.01")

        assertEquals(PayOrderService.MarkPaidOutcome.AmountMismatch, outcome)
        val order = service.find(no)!!
        assertEquals(PayOrder.STATUS_CREATED, order.status)
        assertNull(order.paidAt)
    }

    @Test
    fun `订单不存在时不抛异常`() {
        assertEquals(
            PayOrderService.MarkPaidOutcome.OrderMissing,
            service.markPaid("T_NOT_EXIST", "TRADE_3", null, null, "1.99"),
        )
    }

    @Test
    fun `关闭未支付订单`() {
        val no = "T_CLOSED"
        service.recordCreated(no, "pro_permanent", "句阁 PRO 会员", 199)

        assertEquals(PayOrderService.MarkPaidOutcome.Marked, service.markClosed(no))
        assertEquals(PayOrder.STATUS_CLOSED, service.find(no)!!.status)
    }

    @Test
    fun `已支付订单不会被关闭覆盖`() {
        val no = "T_PAID_THEN_CLOSE"
        service.recordCreated(no, "pro_permanent", "句阁 PRO 会员", 199)
        service.markPaid(no, "TRADE_4", "BUYER_4", null, "1.99")

        service.markClosed(no)

        assertEquals(PayOrder.STATUS_PAID, service.find(no)!!.status)
    }
}