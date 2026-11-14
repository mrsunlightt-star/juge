package com.juge.lklpay

import com.juge.lklpay.domain.PayOrder
import com.juge.lklpay.repository.PayOrderRepository
import com.juge.lklpay.service.PayOrderService
import com.juge.lklpay.service.PayOrderService.MarkPaidOutcome
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.invocation.InvocationOnMock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 订单状态机的并发与幂等验证。
 *
 * 权益不再落到账号上（PRO 归属由 App 侧凭支付宝 user_id 找回），
 * 因此这里只验证**钱的部分不能被并发写坏**：重复通知只能流转一次、
 * 闭单与付款竞争不能互相覆盖、金额不符一律拒绝。
 *
 * 独立线程 + 真实事务 + H2；Spy 只控制时序，读取、CAS、flush、提交全走真实数据库。
 */
@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:payment-concurrency;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.jpa.hibernate.ddl-auto=create-drop",
])
class PaymentConcurrencyTest {
    @Autowired private lateinit var payService: PayOrderService
    @MockitoSpyBean private lateinit var orders: PayOrderRepository
    @PersistenceContext private lateinit var em: EntityManager

    @Test
    fun `同一订单的并发重复通知只有一次流转成功`() {
        val no = createOrder("T_DUPLICATES")
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val futures = (1..4).map {
                pool.submit<MarkPaidOutcome> {
                    assertTrue(start.await(30, TimeUnit.SECONDS))
                    payService.markPaid(no, "TRADE_DUP", "BUYER_DUP", null, AMOUNT_YUAN)
                }
            }
            start.countDown()
            val outcomes = futures.map { it.get(30, TimeUnit.SECONDS) }

            assertEquals(1, outcomes.count { it == MarkPaidOutcome.Marked }, "只能有一个赢家")
            assertEquals(3, outcomes.count { it == MarkPaidOutcome.AlreadyPaid })
            assertEquals(PayOrder.STATUS_PAID, payService.find(no)!!.status)
            assertEquals("BUYER_DUP", payService.find(no)!!.buyerUserId)
        } finally {
            start.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `付款读到 CREATED 后闭单先提交则 CAS 失败不能伪报已付`() {
        val no = createOrder("T_CLOSE_WINS")
        val pause = Pause()
        doAnswer { invocation ->
            assertEquals(PayOrder.STATUS_CREATED, em.find(PayOrder::class.java, no).status)
            pause.stopHere()
            proceedWithSpy(invocation)
        }.`when`(orders).markPaidIfCreated(anyString(), anyString(), anyString(), anyLong(), any(), any(), any(), any())

        val pool = Executors.newSingleThreadExecutor()
        try {
            val payment = pool.submit<MarkPaidOutcome> { payService.markPaid(no, "TRADE_LATE", null, null, AMOUNT_YUAN) }
            pause.awaitReached()
            assertEquals(MarkPaidOutcome.Marked, payService.markClosed(no))
            pause.resume()

            assertEquals(MarkPaidOutcome.NotPaid, payment.get(30, TimeUnit.SECONDS))
            assertEquals(PayOrder.STATUS_CLOSED, payService.find(no)!!.status)
            assertNull(payService.find(no)!!.paidAt)
        } finally {
            pause.resume()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `闭单持有 CREATED 旧快照时不能覆盖先提交的付款`() {
        val no = createOrder("T_PAYMENT_WINS")
        val pause = Pause()
        doAnswer { invocation ->
            assertEquals(PayOrder.STATUS_CREATED, em.find(PayOrder::class.java, no).status)
            pause.stopHere()
            proceedWithSpy(invocation)
        }.`when`(orders).markClosedIfCreated(anyString(), anyString(), anyString())

        val pool = Executors.newSingleThreadExecutor()
        try {
            val close = pool.submit<MarkPaidOutcome> { payService.markClosed(no) }
            pause.awaitReached()
            assertEquals(MarkPaidOutcome.Marked, payService.markPaid(no, "TRADE_FIRST", null, null, AMOUNT_YUAN))
            pause.resume()

            assertEquals(MarkPaidOutcome.AlreadyPaid, close.get(30, TimeUnit.SECONDS))
            val paid = payService.find(no)!!
            assertEquals(PayOrder.STATUS_PAID, paid.status)
            assertEquals("TRADE_FIRST", paid.tradeNo)
        } finally {
            pause.resume()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `金额与订单不符时拒绝置为已支付`() {
        val no = createOrder("T_AMOUNT_MISMATCH")

        assertEquals(MarkPaidOutcome.AmountMismatch, payService.markPaid(no, "TRADE_X", "BUYER_X", null, "0.01"))
        assertEquals(PayOrder.STATUS_CREATED, payService.find(no)!!.status)
        assertNull(payService.find(no)!!.paidAt)
    }

    @Test
    fun `金额缺失时拒绝置为已支付`() {
        val no = createOrder("T_AMOUNT_MISSING")

        assertEquals(MarkPaidOutcome.AmountMissing, payService.markPaid(no, "TRADE_Y", "BUYER_Y", null, null))
        assertEquals(MarkPaidOutcome.AmountMissing, payService.markPaid(no, "TRADE_Y", "BUYER_Y", null, "not-a-number"))
        assertEquals(PayOrder.STATUS_CREATED, payService.find(no)!!.status)
    }

    @Test
    fun `按买家找回命中该买家的全部已支付订单`() {
        val first = createOrder("T_RECOVER_1")
        val second = createOrder("T_RECOVER_2")
        val other = createOrder("T_RECOVER_OTHER")
        val closed = createOrder("T_RECOVER_CLOSED")

        payService.markPaid(first, "TRADE_R1", BUYER, null, AMOUNT_YUAN)
        payService.markPaid(second, "TRADE_R2", BUYER, null, AMOUNT_YUAN)
        payService.markPaid(other, "TRADE_OTHER", "BUYER_OTHER", null, AMOUNT_YUAN)
        payService.markClosed(closed)

        val found = payService.findPaidByBuyer(BUYER).map { it.outTradeNo }.sorted()
        assertEquals(listOf(first, second).sorted(), found, "只应命中该买家的已支付订单")
        assertTrue(payService.findPaidByBuyer("BUYER_NOBODY").isEmpty())
        assertFalse(found.contains(closed), "已关闭的订单不能用来恢复权益")
        assertFalse(found.contains(other), "别人的订单不能用来恢复权益")
    }

    @Test
    fun `不同买家各自只能找回自己的订单`() {
        val a = createOrder("T_OWNER_A")
        val b = createOrder("T_OWNER_B")
        payService.markPaid(a, "TRADE_A", "BUYER_A", null, AMOUNT_YUAN)
        payService.markPaid(b, "TRADE_B", "BUYER_B", null, AMOUNT_YUAN)

        assertEquals(listOf(a), payService.findPaidByBuyer("BUYER_A").map { it.outTradeNo })
        assertEquals(listOf(b), payService.findPaidByBuyer("BUYER_B").map { it.outTradeNo })
    }

    // JDK 仓库代理通过默认 Answer 委托真实实例，callRealMethod 无法执行接口上的抽象方法。
    private fun proceedWithSpy(invocation: InvocationOnMock): Any? =
        mockingDetails(invocation.mock).mockCreationSettings.defaultAnswer.answer(invocation)

    private fun createOrder(no: String): String {
        payService.recordCreated(no, "pro_permanent", "Test PRO", AMOUNT_FEN)
        return no
    }

    private class Pause {
        private val reached = CountDownLatch(1)
        private val proceed = CountDownLatch(1)

        fun stopHere() {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive())
            reached.countDown()
            assertTrue(proceed.await(30, TimeUnit.SECONDS), "等待另一事务提交超时")
        }

        fun awaitReached() = assertTrue(reached.await(30, TimeUnit.SECONDS), "事务未到达预期并发点")
        fun resume() = proceed.countDown()
    }

    private companion object {
        const val AMOUNT_FEN = 399L
        const val AMOUNT_YUAN = "3.99"
        const val BUYER = "2088000000000001"
    }
}
