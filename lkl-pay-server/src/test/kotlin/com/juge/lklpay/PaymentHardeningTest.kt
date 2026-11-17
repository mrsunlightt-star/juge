package com.juge.lklpay

import com.juge.lklpay.config.PayProperties
import com.juge.lklpay.domain.PayOrder
import com.juge.lklpay.service.PayApiThrottle
import com.juge.lklpay.service.PayOrderService
import com.juge.lklpay.service.Sensitive
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 本次审查修复项的回归测试。
 *
 * 每条都对应报告里一条具体缺陷，命名直接写「什么不能被绕过」——
 * 以后有人想删这些断言，得先说服自己那条缺陷不再存在。
 */
@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:hardening-test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
])
class PaymentHardeningTest {

    @Autowired
    private lateinit var service: PayOrderService

    @Autowired
    private lateinit var payProperties: PayProperties

    @Autowired
    private lateinit var orders: com.juge.lklpay.repository.PayOrderRepository

    // ── P1-4：买家登录名必须脱敏后才入库 ──

    @Test
    fun `手机号登录名按前三后四脱敏`() {
        assertEquals("138****1234", Sensitive.maskLogonId("13812341234"))
    }

    @Test
    fun `邮箱登录名只保留域名`() {
        assertEquals("abc***@example.com", Sensitive.maskLogonId("abcdef@example.com"))
    }

    @Test
    fun `过短的登录名全部打码`() {
        assertEquals("**", Sensitive.maskLogonId("ab"))
        assertEquals("****", Sensitive.maskLogonId("abcd"))
    }

    @Test
    fun `空登录名脱敏后仍是 null 而不是空串`() {
        assertNull(Sensitive.maskLogonId(null))
        assertNull(Sensitive.maskLogonId(""))
        assertNull(Sensitive.maskLogonId("   "))
    }

    @Test
    fun `通知留档里的买家登录名同样被脱敏`() {
        val raw = "out_trade_no=T1&buyer_logon_id=13812341234&trade_status=TRADE_SUCCESS&total_amount=3.99"
        val masked = Sensitive.maskNotifyRaw(raw)
        assertTrue("13812341234" !in masked, "原始手机号不能出现在留档里：$masked")
        assertTrue("buyer_logon_id=138****1234" in masked)
        // 其余字段必须原样保留，否则留档失去对账价值
        assertTrue("out_trade_no=T1" in masked)
        assertTrue("total_amount=3.99" in masked)
    }

    @Test
    fun `落库的 buyerLogonId 与 notifyRaw 都不含原始登录名`() {
        val no = "T_MASK_ON_WRITE"
        service.recordCreated(no, PayOrderService.PRODUCT_ID, "句阁 PRO 会员", 399)

        service.markPaid(
            outTradeNo = no,
            tradeNo = "TRADE_MASK",
            buyerUserId = "2088000000000001",
            buyerLogonId = "13900009999",
            paidAmountYuan = "3.99",
            notifyRaw = "out_trade_no=$no&buyer_logon_id=13900009999&total_amount=3.99",
        )

        val order = assertNotNull(service.find(no))
        assertTrue("13900009999" !in (order.buyerLogonId ?: ""), "入库字段必须是脱敏值：${order.buyerLogonId}")
        assertTrue("13900009999" !in (order.notifyRaw ?: ""), "留档里也不能有原始值：${order.notifyRaw}")
        assertEquals("139****9999", order.buyerLogonId)
    }

    // ── P1-5：找回必须同时校验商品与金额 ──

    @Test
    fun `找回只认当前商品与当前价格的已支付订单`() {
        val buyer = "2088000000000002"
        val expectedFen = assertNotNull(payProperties.products[PayOrderService.PRODUCT_ID]).amountFen

        // ① 正常单：应命中
        paidOrder("T_RECOVER_OK", buyer, PayOrderService.PRODUCT_ID, expectedFen)
        // ② 别的商品：即使已支付也不能解锁 PRO
        paidOrder("T_RECOVER_OTHER_PRODUCT", buyer, "some_other_sku", expectedFen)
        // ③ 旧价单（曾按 1.99 售出的历史单）：金额对不上当前目录价
        paidOrder("T_RECOVER_OLD_PRICE", buyer, PayOrderService.PRODUCT_ID, 199)
        // ④ 未支付单
        service.recordCreated("T_RECOVER_UNPAID", PayOrderService.PRODUCT_ID, "句阁 PRO 会员", expectedFen)

        val hits = service.findPaidByBuyer(buyer)

        assertEquals(listOf("T_RECOVER_OK"), hits.map { it.outTradeNo }, "只有①该命中，②③④都必须被过滤")
    }

    @Test
    fun `别的买家的订单不会出现在找回结果里`() {
        paidOrder("T_RECOVER_MINE", "2088000000000003", PayOrderService.PRODUCT_ID, 399)
        paidOrder("T_RECOVER_THEIRS", "2088000000000004", PayOrderService.PRODUCT_ID, 399)

        assertEquals(
            listOf("T_RECOVER_MINE"),
            service.findPaidByBuyer("2088000000000003").map { it.outTradeNo },
        )
    }

    // ── P1-16：僵尸订单必须能被清掉，且只清 CREATED ──

    @Test
    fun `清理只删过期的未支付订单`() {
        val retentionMillis = 48L * 60 * 60 * 1000
        val now = System.currentTimeMillis()

        // 直接落库以控制 createdAt：过期的（49 小时前）、新鲜的（1 小时前）、已支付的
        val stale = "T_CLEANUP_STALE"
        val fresh = "T_CLEANUP_FRESH"
        val paid = "T_CLEANUP_PAID"
        orders.save(
            PayOrder(
                outTradeNo = stale,
                productId = PayOrderService.PRODUCT_ID,
                subject = "句阁 PRO 会员",
                amountFen = 399,
                createdAt = now - 49L * 60 * 60 * 1000,
            )
        )
        orders.save(
            PayOrder(
                outTradeNo = fresh,
                productId = PayOrderService.PRODUCT_ID,
                subject = "句阁 PRO 会员",
                amountFen = 399,
                createdAt = now - 60L * 60 * 1000,
            )
        )
        paidOrder(paid, "2088000000000009", PayOrderService.PRODUCT_ID, 399)

        val removed = service.cleanupStaleCreated(retentionMillis)

        assertEquals(1, removed, "只有那一笔 49 小时前的未支付单该被删")
        assertNull(service.find(stale), "过期未支付单必须被删")
        assertNotNull(service.find(fresh), "1 小时前的未支付单还在保留期内，不能删")
        assertNotNull(service.find(paid), "PAID 是资产凭证，绝不能删")
    }

    // ── P1-13：限流在并发下不能被放大 ──

    @Test
    fun `并发请求不会因为判定在锁外而超额放行`() {
        val throttle = PayApiThrottle()
        val limit = 10
        val threads = 8
        val perThread = 20
        val pool = java.util.concurrent.Executors.newFixedThreadPool(threads)
        val startGate = java.util.concurrent.CountDownLatch(1)

        val futures = (1..threads).map {
            pool.submit<Int> {
                startGate.await()
                var allowed = 0
                repeat(perThread) {
                    if (throttle.tryConsume("concurrent", "1.2.3.4", limit)) allowed++
                }
                allowed
            }
        }
        startGate.countDown()
        val total = futures.sumOf { it.get(30, java.util.concurrent.TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(limit, total, "同一窗口内放行次数必须恰好等于限额，不能因并发竞态被放大")
    }

    private fun paidOrder(no: String, buyer: String, productId: String, amountFen: Long) {
        service.recordCreated(no, productId, "句阁 PRO 会员", amountFen)
        val fenToYuan = java.math.BigDecimal(amountFen).movePointLeft(2).setScale(2).toPlainString()
        val outcome = service.markPaid(no, "TRADE_$no", buyer, null, fenToYuan)
        assertEquals(PayOrderService.MarkPaidOutcome.Marked, outcome, "$no 应当能置为已支付")
        assertEquals(PayOrder.STATUS_PAID, service.find(no)?.status)
    }
}
