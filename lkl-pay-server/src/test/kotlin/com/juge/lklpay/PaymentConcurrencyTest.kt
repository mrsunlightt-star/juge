package com.juge.lklpay

import com.juge.lklpay.domain.PayOrder
import com.juge.lklpay.domain.UserAccount
import com.juge.lklpay.repository.PayOrderRepository
import com.juge.lklpay.repository.UserAccountRepository
import com.juge.lklpay.service.LoginThrottle
import com.juge.lklpay.service.PasswordHasher
import com.juge.lklpay.service.PayOrderService
import com.juge.lklpay.service.PayOrderService.MarkPaidOutcome
import com.juge.lklpay.service.UserService
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.invocation.InvocationOnMock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 独立线程、真实事务和 H2；Spy 只控制时序，所有读取、CAS、flush、提交都走真实数据库。 */
@SpringBootTest(properties = [
    "spring.config.location=optional:classpath:/local-payment-test.properties",
    "spring.datasource.url=jdbc:h2:mem:payment-concurrency;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.jpa.hibernate.ddl-auto=create-drop",
])
class PaymentConcurrencyTest {
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var payService: PayOrderService
    @MockitoSpyBean private lateinit var users: UserAccountRepository
    @MockitoSpyBean private lateinit var orders: PayOrderRepository
    @MockitoSpyBean private lateinit var throttle: LoginThrottle
    @PersistenceContext private lateinit var em: EntityManager

    @Test
    fun `登录先读到非 PRO 而付款先提交时不会覆盖权益且返回最新快照`() {
        val user = account("login_race")
        val no = createOrder("T_LOGIN_RACE", user)
        val pause = Pause()
        doAnswer { invocation ->
            assertFalse(em.find(UserAccount::class.java, user.id).pro)
            pause.stopHere()
            proceedWithSpy(invocation)
        }.`when`(throttle).clear(user.username)
        val pool = Executors.newSingleThreadExecutor()
        try {
            val startedAt = System.currentTimeMillis()
            val login = pool.submit<UserService.Session> { userService.login(user.username, PASSWORD) }
            pause.awaitReached()
            assertEquals(MarkPaidOutcome.Marked, payService.markPaid(no, "TRADE_LOGIN", null, null, "1.99"))
            val activatedAt = users.findById(user.id!!).orElseThrow().proActivatedAt
            pause.resume()
            val session = login.get(30, TimeUnit.SECONDS)

            assertTrue(session.user.pro, "登录响应不能返回验密时缓存的 pro=false")
            assertEquals(no, session.user.proOutTradeNo)
            assertEquals(activatedAt, session.user.proActivatedAt)
            assertTrue(assertNotNull(session.user.lastLoginAt) >= startedAt)
            val stored = users.findById(user.id!!).orElseThrow()
            assertTrue(stored.pro)
            assertEquals(session.user.lastLoginAt, stored.lastLoginAt)
            assertEquals(activatedAt, stored.proActivatedAt)
            assertEquals(no, stored.proOutTradeNo)
            assertTrue(userService.authenticate(session.token).pro)
            assertEquals(MarkPaidOutcome.AlreadyPaid, payService.markPaid(no, "TRADE_LOGIN", null, null, "1.99"))
            val repeated = users.findById(user.id!!).orElseThrow()
            assertEquals(activatedAt, repeated.proActivatedAt)
            assertEquals(stored.lastLoginAt, repeated.lastLoginAt)
        } finally {
            pause.resume()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `付款持有旧登录时间时授予权益不覆盖并发登录`() {
        val user = account("payment_race")
        val no = createOrder("T_PAYMENT_RACE", user)
        val pause = Pause()
        doAnswer { invocation ->
            // 人为保留真实受管的旧快照，验证字段级更新及 clear 而非整行 dirty flush。
            val stale = em.find(UserAccount::class.java, user.id)
            assertEquals(1L, stale.lastLoginAt)
            pause.stopHere()
            val affected = proceedWithSpy(invocation)
            assertFalse(em.contains(stale), "批量更新后必须清除旧账号快照")
            affected
        }.`when`(users).grantProIfNotGranted(anyLong(), anyLong(), anyString())
        val pool = Executors.newSingleThreadExecutor()
        try {
            val payment = pool.submit<MarkPaidOutcome> { payService.markPaid(no, "TRADE_PAYMENT", null, null, "1.99") }
            pause.awaitReached()
            val login = userService.login(user.username, PASSWORD)
            assertTrue(assertNotNull(login.user.lastLoginAt) > 1L)
            pause.resume()
            assertEquals(MarkPaidOutcome.Marked, payment.get(30, TimeUnit.SECONDS))
            val stored = users.findById(user.id!!).orElseThrow()
            assertTrue(stored.pro)
            assertEquals(login.user.lastLoginAt, stored.lastLoginAt)
            assertEquals(no, stored.proOutTradeNo)
            assertEquals(payService.find(no)!!.paidAt, stored.proActivatedAt)
        } finally {
            pause.resume()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `同一账号多笔付款并发时只授予一次权益且不改登录时间`() {
        val user = account("two_orders")
        val orderNos = listOf(createOrder("T_TWO_1", user), createOrder("T_TWO_2", user))
        val ready = CountDownLatch(2)
        val resume = CountDownLatch(1)
        val affectedRows = ConcurrentLinkedQueue<Int>()
        doAnswer { invocation ->
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive())
            assertFalse(em.find(UserAccount::class.java, user.id).pro)
            ready.countDown()
            assertTrue(resume.await(30, TimeUnit.SECONDS))
            (proceedWithSpy(invocation) as Int).also { affectedRows.add(it) }
        }.`when`(users).grantProIfNotGranted(anyLong(), anyLong(), anyString())
        val pool = Executors.newFixedThreadPool(2)
        try {
            val futures = orderNos.map { no ->
                pool.submit<MarkPaidOutcome> { payService.markPaid(no, "TRADE_$no", null, null, "1.99") }
            }
            assertTrue(ready.await(30, TimeUnit.SECONDS), "两笔付款须同时持有未开通快照")
            resume.countDown()
            futures.forEach { assertEquals(MarkPaidOutcome.Marked, it.get(30, TimeUnit.SECONDS)) }
            assertEquals(listOf(0, 1), affectedRows.sorted(), "权益 CAS 必须只有一个赢家")
            val stored = users.findById(user.id!!).orElseThrow()
            assertTrue(stored.pro)
            assertEquals(1L, stored.lastLoginAt)
            assertTrue(stored.proOutTradeNo in orderNos)
            assertEquals(payService.find(stored.proOutTradeNo!!)!!.paidAt, stored.proActivatedAt)
            orderNos.forEach { assertEquals(PayOrder.STATUS_PAID, payService.find(it)!!.status) }
        } finally {
            resume.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `绑定账号的并发重复通知只有一次落单和开通`() {
        val user = account("duplicate_race")
        val no = createOrder("T_BOUND_DUPLICATES", user)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val futures = (1..4).map {
                pool.submit<MarkPaidOutcome> {
                    assertTrue(start.await(30, TimeUnit.SECONDS))
                    payService.markPaid(no, "TRADE_DUPLICATE", null, null, "1.99")
                }
            }
            start.countDown()
            val outcomes = futures.map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(1, outcomes.count { it == MarkPaidOutcome.Marked })
            assertEquals(3, outcomes.count { it == MarkPaidOutcome.AlreadyPaid })
            val stored = users.findById(user.id!!).orElseThrow()
            assertTrue(stored.pro)
            assertEquals(no, stored.proOutTradeNo)
            assertEquals(payService.find(no)!!.paidAt, stored.proActivatedAt)
            assertEquals(1L, stored.lastLoginAt)
            verify(users, times(1)).grantProIfNotGranted(user.id!!, stored.proActivatedAt!!, no)
        } finally {
            start.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `付款读取 CREATED 后闭单先提交则 CAS 失败不能伪报 AlreadyPaid`() {
        val user = account("close_wins")
        val no = createOrder("T_CLOSE_WINS", user)
        val pause = Pause()
        doAnswer { invocation ->
            assertEquals(PayOrder.STATUS_CREATED, em.find(PayOrder::class.java, no).status)
            pause.stopHere()
            proceedWithSpy(invocation)
        }.`when`(orders).markPaidIfCreated(anyString(), anyString(), anyString(), anyLong(), any(), any(), any(), any())
        val pool = Executors.newSingleThreadExecutor()
        try {
            val payment = pool.submit<MarkPaidOutcome> { payService.markPaid(no, "TRADE_LATE", null, null, "1.99") }
            pause.awaitReached()
            assertEquals(MarkPaidOutcome.Marked, payService.markClosed(no))
            pause.resume()
            assertEquals(MarkPaidOutcome.NotPaid, payment.get(30, TimeUnit.SECONDS))
            assertEquals(PayOrder.STATUS_CLOSED, payService.find(no)!!.status)
            assertEquals(null, payService.find(no)!!.paidAt)
            assertFalse(users.findById(user.id!!).orElseThrow().pro)
        } finally {
            pause.resume()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `闭单持有 CREATED 旧快照时不能覆盖先提交的付款`() {
        val user = account("payment_wins")
        val no = createOrder("T_PAYMENT_WINS", user)
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
            assertEquals(MarkPaidOutcome.Marked, payService.markPaid(no, "TRADE_FIRST", null, null, "1.99"))
            pause.resume()
            assertEquals(MarkPaidOutcome.AlreadyPaid, close.get(30, TimeUnit.SECONDS))
            val paid = payService.find(no)!!
            assertEquals(PayOrder.STATUS_PAID, paid.status)
            assertEquals("TRADE_FIRST", paid.tradeNo)
            assertTrue(users.findById(user.id!!).orElseThrow().pro)
            assertEquals(paid.paidAt, users.findById(user.id!!).orElseThrow().proActivatedAt)
        } finally {
            pause.resume()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `注销解绑不覆盖并发付款的状态和交易信息`() {
        val session = userService.register("delete_race", PASSWORD, null)
        val no = createOrder("T_DELETE_RACE", session.user)
        val pause = Pause()
        doAnswer { invocation ->
            assertEquals(PayOrder.STATUS_CREATED, em.find(PayOrder::class.java, no).status)
            pause.stopHere()
            proceedWithSpy(invocation)
        }.`when`(orders).unbindUser(anyLong())
        val pool = Executors.newSingleThreadExecutor()
        try {
            val deletion = pool.submit { userService.deleteAccount(session.token) }
            pause.awaitReached()
            assertEquals(MarkPaidOutcome.Marked, payService.markPaid(no, "TRADE_DELETE", "BUYER_DELETE", null, "1.99"))
            val paidAt = payService.find(no)!!.paidAt
            pause.resume()
            deletion.get(30, TimeUnit.SECONDS)
            val stored = payService.find(no)!!
            assertEquals(PayOrder.STATUS_PAID, stored.status)
            assertEquals("TRADE_DELETE", stored.tradeNo)
            assertEquals("BUYER_DELETE", stored.buyerUserId)
            assertEquals(paidAt, stored.paidAt)
            assertEquals(null, stored.userId)
            assertFalse(users.existsById(session.user.id!!))
        } finally {
            pause.resume()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        }
    }

    // JDK 仓库代理通过默认 Answer 委托真实实例，callRealMethod 无法执行接口上的抽象方法。
    private fun proceedWithSpy(invocation: InvocationOnMock): Any? =
        mockingDetails(invocation.mock).mockCreationSettings.defaultAnswer.answer(invocation)

    private fun account(username: String): UserAccount = users.saveAndFlush(
        UserAccount(username = username, passwordHash = PasswordHasher.hash(PASSWORD), createdAt = 1, lastLoginAt = 1)
    )

    private fun createOrder(no: String, user: UserAccount): String {
        payService.recordCreated(no, "pro_permanent", "Test PRO", 199, user.id)
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
        const val PASSWORD = "local-test-password"
    }
}
