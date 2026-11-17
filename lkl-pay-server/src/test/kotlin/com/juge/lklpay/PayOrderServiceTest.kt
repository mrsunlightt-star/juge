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
@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:payorder-test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
])
class PayOrderServiceTest {

    @Autowired
    private lateinit var service: PayOrderService

    @Test
    fun `重复通知只落一次已支付`() {
        val no = "T_IDEMPOTENT"
        service.recordCreated(no, "pro_permanent", "句阁 PRO 会员", 399)

        // 传网关返回的**原始值**给 markPaid，由它在入库前脱敏；
        // 早先这里直接传 "138****0000"（已是脱敏样式），等于绕过被测逻辑。
        val first = service.markPaid(no, "TRADE_1", "BUYER_1", "13800001234", "3.99")
        val second = service.markPaid(no, "TRADE_1", "BUYER_1", "13800001234", "3.99")

        assertEquals(PayOrderService.MarkPaidOutcome.Marked, first)
        assertEquals(PayOrderService.MarkPaidOutcome.AlreadyPaid, second)
        assertEquals(PayOrder.STATUS_PAID, service.find(no)!!.status)
    }

    @Test
    fun `金额不符时拒绝置为已支付`() {
        val no = "T_MISMATCH"
        service.recordCreated(no, "pro_permanent", "句阁 PRO 会员", 399)

        val outcome = service.markPaid(no, "TRADE_2", "BUYER_2", null, "0.01")

        assertEquals(PayOrderService.MarkPaidOutcome.AmountMismatch, outcome)
        val order = service.find(no)!!
        assertEquals(PayOrder.STATUS_CREATED, order.status)
        assertNull(order.paidAt)
    }

    @Test
    fun `金额缺失或不可解析时同样拒绝置为已支付`() {
        // 防改价是最后一道闸：拿不到金额时绝不能「跳过核对直接放行」
        val noNull = "T_AMOUNT_NULL"
        val noBad = "T_AMOUNT_BAD"
        service.recordCreated(noNull, "pro_permanent", "句阁 PRO 会员", 399)
        service.recordCreated(noBad, "pro_permanent", "句阁 PRO 会员", 399)

        assertEquals(
            PayOrderService.MarkPaidOutcome.AmountMissing,
            service.markPaid(noNull, "TRADE_5", "BUYER_5", null, null),
        )
        assertEquals(
            PayOrderService.MarkPaidOutcome.AmountMissing,
            service.markPaid(noBad, "TRADE_6", "BUYER_6", null, "not-a-number"),
        )
        assertEquals(PayOrder.STATUS_CREATED, service.find(noNull)!!.status)
        assertEquals(PayOrder.STATUS_CREATED, service.find(noBad)!!.status)
    }

    @Test
    fun `订单不存在时不抛异常`() {
        assertEquals(
            PayOrderService.MarkPaidOutcome.OrderMissing,
            service.markPaid("T_NOT_EXIST", "TRADE_3", null, null, "3.99"),
        )
    }

    @Test
    fun `并发重复通知也只有一次真正落单`() {
        // 支付宝会对同一笔订单并发重投；若状态变更仍是「先读后写」，
        // 多条线程都会读到 CREATED 并各自发货。这里检查 compare-and-set 真的只有一个赢家。
        val no = "T_CONCURRENT"
        service.recordCreated(no, "pro_permanent", "句阁 PRO 会员", 399)

        val threads = 4
        val pool = java.util.concurrent.Executors.newFixedThreadPool(threads)
        val startGate = java.util.concurrent.CountDownLatch(1)
        val futures = (1..threads).map {
            pool.submit<PayOrderService.MarkPaidOutcome> {
                startGate.await()
                service.markPaid(no, "TRADE_C", "BUYER_C", null, "3.99")
            }
        }
        startGate.countDown()
        val outcomes = futures.map { it.get(30, java.util.concurrent.TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(1, outcomes.count { it == PayOrderService.MarkPaidOutcome.Marked }, "只能有一个线程真正落单")
        assertEquals(
            threads - 1,
            outcomes.count { it == PayOrderService.MarkPaidOutcome.AlreadyPaid },
            "其余线程必须被幂等短路",
        )
        assertEquals(PayOrder.STATUS_PAID, service.find(no)!!.status)
    }

    @Test
    fun `关闭未支付订单`() {
        val no = "T_CLOSED"
        service.recordCreated(no, "pro_permanent", "句阁 PRO 会员", 399)

        assertEquals(PayOrderService.MarkPaidOutcome.Marked, service.markClosed(no))
        assertEquals(PayOrder.STATUS_CLOSED, service.find(no)!!.status)
    }

    @Test
    fun `已支付订单不会被关闭覆盖`() {
        val no = "T_PAID_THEN_CLOSE"
        service.recordCreated(no, "pro_permanent", "句阁 PRO 会员", 399)
        service.markPaid(no, "TRADE_4", "BUYER_4", null, "3.99")

        service.markClosed(no)

        assertEquals(PayOrder.STATUS_PAID, service.find(no)!!.status)
    }

    @Test
    fun `关闭订单不能被当作幂等付款成功`() {
        val no = "T_CLOSED_THEN_PAY"
        service.recordCreated(no, "pro_permanent", "句阁 PRO 会员", 399)
        service.markClosed(no)

        assertEquals(PayOrderService.MarkPaidOutcome.NotPaid, service.markPaid(no, "TRADE_X", null, null, "3.99"))
        assertEquals(PayOrderService.MarkPaidOutcome.NotPaid, service.markClosed(no))
        val order = service.find(no)!!
        assertEquals(PayOrder.STATUS_CLOSED, order.status)
        assertNull(order.paidAt)
        assertNull(order.tradeNo)
    }

    @Test
    fun `已支付订单的重复结果仍需核对金额`() {
        val no = "T_PAID_AMOUNT_CHECK"
        service.recordCreated(no, "pro_permanent", "句阁 PRO 会员", 399)
        service.markPaid(no, "TRADE_VALID", null, null, "3.99")
        val paidAt = service.find(no)!!.paidAt

        assertEquals(PayOrderService.MarkPaidOutcome.AmountMissing, service.markPaid(no, "OTHER", null, null, null))
        assertEquals(PayOrderService.MarkPaidOutcome.AmountMismatch, service.markPaid(no, "OTHER", null, null, "0.01"))
        assertEquals(PayOrderService.MarkPaidOutcome.AlreadyPaid, service.markPaid(no, "TRADE_VALID", null, null, "3.990"))
        val order = service.find(no)!!
        assertEquals(PayOrder.STATUS_PAID, order.status)
        assertEquals(paidAt, order.paidAt)
        assertEquals("TRADE_VALID", order.tradeNo)
    }

    @Test
    fun `金额不能截断小数或溢出后恰好匹配订单`() {
        // 18446744073709551815 分截断到 Long 后恰好是 199，必须精确转换。
        listOf("1.999", "184467440737095518.15", "", "NaN").forEachIndexed { index, amount ->
            val no = "T_INEXACT_AMOUNT_$index"
            service.recordCreated(no, "pro_permanent", "句阁 PRO 会员", 399)
            assertEquals(PayOrderService.MarkPaidOutcome.AmountMissing, service.markPaid(no, null, null, null, amount))
            assertEquals(PayOrder.STATUS_CREATED, service.find(no)!!.status)
            assertNull(service.find(no)!!.paidAt)
        }
    }

    @Test
    fun `关闭不存在的订单不会报告已付款`() {
        assertEquals(PayOrderService.MarkPaidOutcome.OrderMissing, service.markClosed("T_CLOSE_MISSING"))
    }
}
