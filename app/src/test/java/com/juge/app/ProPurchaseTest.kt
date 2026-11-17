package com.juge.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.Signature
import android.os.Looper
import com.juge.app.data.TrialManager
import com.juge.app.net.ServerClient
import com.juge.app.pay.AlipayPay
import com.juge.app.pay.AlipayPayApi
import com.juge.app.pay.ProPurchase
import java.io.File
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 只注入本地 API/收银台替身；实际使用 TrialManager 与队列的 SharedPreferences 提交路径。
 *
 * v3 之后「已支付」还要求服务端凭证：查单替身返回的 [AlipayPayApi.OrderStatus]
 * 必须带一段用测试密钥签出来的 credential，否则激活会被 [TrialManager] 拒掉。
 * 这本身是一条有价值的回归——它保证「只把 trade_status 改成 TRADE_SUCCESS」
 * 骗不过去。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class ProPurchaseTest {
    private lateinit var context: Context
    private lateinit var activity: Activity
    private lateinit var pending: CommitProbe
    private lateinit var activation: CommitProbe
    private lateinit var trial: TrialManager

    @Before
    @Suppress("DEPRECATION")
    fun setUp() {
        val app: Context = RuntimeEnvironment.getApplication()
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        info.signatures = arrayOf(Signature("purchase-test-signature".toByteArray()))
        shadowOf(app.packageManager).installPackage(info)
        pending = CommitProbe(app.getSharedPreferences("pro_purchase_prefs", Context.MODE_PRIVATE))
        activation = CommitProbe(app.getSharedPreferences("trial_activation_v2_prefs", Context.MODE_PRIVATE))
        pending.backing.edit().clear().commit()
        activation.backing.edit().clear().commit()
        context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = when (name) {
                "pro_purchase_prefs" -> pending
                "trial_activation_v2_prefs" -> activation
                else -> super.getSharedPreferences(name, mode)
            }
        }
        activity = object : Activity() {
            override fun getApplicationContext(): Context = context
        }
        TestEntitlements.installTestPublicKey()
        trial = newTrialManager()
    }

    @After
    fun tearDown() {
        TestEntitlements.clearTestPublicKey()
    }

    @Test
    fun `激活提交失败不返回 Paid 且 pending 留待重试`() = runBlocking {
        seed("paid")
        activation.allowCommit = false

        val outcome = ProPurchase.recoverPending(context, dependencies())

        assertTrue(outcome is ProPurchase.Outcome.Failed)
        assertEquals(listOf("paid"), orders())
        assertEquals(1, activation.commits)
        assertEquals(0, pending.commits)
        assertFalse(newTrialManager().isActivated())

        activation.allowCommit = true
        assertEquals(ProPurchase.Outcome.Paid, ProPurchase.recoverPending(context, dependencies()))
        assertEquals(emptyList<String>(), orders())
        assertTrue(newTrialManager().isActivated())
    }

    @Test
    fun `购买先提交 pending 再唤支付 先提交激活再消费订单且全部提交在 IO`() = runBlocking {
        val events = Collections.synchronizedList(mutableListOf<String>())
        pending.onCommit = {
            assertNotEquals(Looper.getMainLooper().thread, Thread.currentThread())
            events.add("pending")
        }
        activation.onCommit = {
            assertNotEquals(Looper.getMainLooper().thread, Thread.currentThread())
            assertEquals(listOf("paid"), orders())
            events.add("activation")
        }
        val deps = dependencies().copy(pay = { _, _ ->
            assertEquals(listOf("paid"), orders())
            events.add("pay")
            AlipayPay.STATUS_SUCCESS
        })

        assertEquals(ProPurchase.Outcome.Paid, ProPurchase.purchase(activity, deps))

        assertEquals(listOf("pending", "pay", "activation", "pending"), events)
        assertEquals(emptyList<String>(), orders())
        assertEquals(0, activation.applies)
        assertEquals(0, pending.applies)
        assertEquals(TrialManager.PAY_METHOD_ALIPAY, newTrialManager().activationRecord()?.payMethod)
    }

    @Test
    fun `购买确认付款但激活提交失败时保留订单`() = runBlocking {
        activation.allowCommit = false
        val deps = dependencies().copy(pay = { _, _ -> AlipayPay.STATUS_SUCCESS })

        assertTrue(ProPurchase.purchase(activity, deps) is ProPurchase.Outcome.Failed)
        assertEquals(listOf("paid"), orders())
        assertEquals(1, pending.commits) // 只有唤支付前的追加，没有删除。
    }

    @Test
    fun `pending 提交失败不得唤支付或查单`() = runBlocking {
        pending.allowCommit = false
        val deps = dependencies().copy(
            pay = { _, _ -> error("订单未落盘不得唤支付") },
            queryOrder = { _ -> error("订单未落盘不得查单") },
        )

        assertTrue(ProPurchase.purchase(activity, deps) is ProPurchase.Outcome.Failed)
        assertEquals(1, pending.commits)
        assertEquals(0, activation.commits)
    }

    @Test
    fun `内存已有 pending 也必须重新确认 commit 成功才唤支付`() = runBlocking {
        // Android 的失败 commit 可能已改变内存；不能因发现重复单号而直接视为可靠落盘。
        seed("paid")
        pending.allowCommit = false
        val deps = dependencies().copy(pay = { _, _ -> error("不能跳过持久化确认") })

        assertTrue(ProPurchase.purchase(activity, deps) is ProPurchase.Outcome.Failed)
        assertEquals(1, pending.commits)
    }

    @Test
    fun `关闭与不存在的订单清理 等待付款和网络失败订单保留`() = runBlocking {
        seed("closed", "missing", "waiting", "offline")
        val deps = dependencies().copy(
            queryOrder = { order ->
                when (order) {
                    "closed" -> Result.success(status(order, "TRADE_CLOSED"))
                    "missing" -> Result.success(status(order, "").copy(code = "40004", subCode = "ACQ.TRADE_NOT_EXIST"))
                    "waiting" -> Result.success(status(order, "WAIT_BUYER_PAY"))
                    else -> Result.failure(IllegalStateException("offline"))
                }
            },
            activate = { _, _ -> error("终态未付款单不得激活") },
        )

        assertEquals(ProPurchase.Outcome.Unpaid(""), ProPurchase.recoverPending(context, deps))
        assertEquals(listOf("waiting", "offline"), orders())
    }

    @Test
    fun `限流立即停止本轮并轮转 后面的已付单下轮优先恢复`() = runBlocking {
        seed("waiting", "limited", "paid")
        val queried = mutableListOf<String>()
        val deps = dependencies().copy(queryOrder = { order ->
            queried.add(order)
            when (order) {
                "waiting" -> Result.success(status(order, "WAIT_BUYER_PAY"))
                "limited" -> Result.failure(AlipayPayApi.ApiException("稍后重试", "RATE_LIMITED"))
                else -> Result.success(status(order))
            }
        })

        assertTrue(ProPurchase.recoverPending(context, deps) is ProPurchase.Outcome.Unpaid)
        assertEquals(listOf("waiting", "limited"), queried)
        assertEquals(listOf("paid", "waiting", "limited"), orders())

        queried.clear()
        assertEquals(ProPurchase.Outcome.Paid, ProPurchase.recoverPending(context, deps))
        assertEquals(listOf("paid"), queried)
        assertEquals(listOf("waiting", "limited"), orders())
    }

    @Test
    fun `购买确认阶段遇到限流不继续轮询且保留订单`() = runBlocking {
        var queries = 0
        val deps = dependencies().copy(
            pay = { _, _ -> AlipayPay.STATUS_SUCCESS },
            queryOrder = { _ ->
                queries++
                Result.failure(AlipayPayApi.ApiException("稍后重试", "RATE_LIMITED"))
            },
        )

        assertTrue(ProPurchase.purchase(activity, deps) is ProPurchase.Outcome.Unpaid)
        assertEquals(1, queries)
        assertEquals(listOf("paid"), orders())
        assertEquals(0, activation.commits)
    }

    @Test
    fun `恢复旧单同时追加新单不会因旧队列快照丢失新订单`() = runBlocking {
        seed("old")
        val querying = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val recovery = async {
            ProPurchase.recoverPending(context, dependencies().copy(queryOrder = { order ->
                querying.complete(Unit)
                resume.await()
                Result.success(status(order))
            }))
        }
        querying.await()
        val purchase = ProPurchase.purchase(activity, dependencies()) // 新 paid 单取消支付，必须继续保留。
        assertTrue(purchase is ProPurchase.Outcome.Unpaid)
        resume.complete(Unit)

        assertEquals(ProPurchase.Outcome.Paid, recovery.await())
        assertEquals(listOf("paid"), orders())
    }

    @Test
    fun `并发追加订单不覆盖其他未支付订单且受容量上限约束`() = runBlocking {
        // 24 个并发购买：互斥后串行执行，队列不应串号或丢写；
        // 但队列有条数上限，最终保留的是最后写入的那批。
        (1..24).map { index ->
            async(Dispatchers.Default) {
                ProPurchase.purchase(activity, dependencies().copy(createOrder = { _ ->
                    Result.success(AlipayPayApi.OrderCreated("order-$index", "test-order"))
                }))
            }
        }.awaitAll()

        val kept = orders()
        assertEquals("队列必须被上限截住，否则 prefs 会无限增长", ProPurchase.MAX_PENDING_ORDERS, kept.size)
        assertTrue("留在队列里的必须是真实下过的单号", (1..24).map { "order-$it" }.toSet().containsAll(kept))
        assertEquals("不能有重复条目", kept.size, kept.toSet().size)
    }

    @Test
    fun `互斥保证同一时刻只有一次购买在跑`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val concurrent = java.util.concurrent.atomic.AtomicInteger(0)
        val maxConcurrent = java.util.concurrent.atomic.AtomicInteger(0)
        var first = true

        val deps = dependencies().copy(
            pay = { _, _ ->
                val now = concurrent.incrementAndGet()
                maxConcurrent.updateAndGet { maxOf(it, now) }
                if (first) {
                    first = false
                    gate.complete(Unit)
                }
                concurrent.decrementAndGet()
                AlipayPay.STATUS_CANCELED
            },
        )
        val a = async(Dispatchers.Default) { ProPurchase.purchase(activity, deps) }
        val b = async(Dispatchers.Default) { ProPurchase.purchase(activity, deps) }
        gate.await()
        a.await(); b.await()

        assertEquals("两次购买必须串行，否则会生成两个订单并让用户付两次", 1, maxConcurrent.get())
    }

    @Test
    fun `过期的待查订单在读取时被丢弃`() = runBlocking {
        // 写入一条 30 小时前的订单（超过 24 小时 TTL）与一条刚写入的
        val stale = System.currentTimeMillis() - 30L * 60 * 60 * 1000
        val fresh = System.currentTimeMillis() - 60_000L
        pending.backing.edit()
            .putString("pending_order_no", "veryold:$stale,recent:$fresh")
            .commit()
        val queried = mutableListOf<String>()
        val deps = dependencies().copy(queryOrder = { order ->
            queried.add(order)
            Result.success(status(order, "WAIT_BUYER_PAY"))
        })

        ProPurchase.recoverPending(context, deps)

        assertEquals("过期订单不该再占一次查单配额", listOf("recent"), queried)
        assertEquals(listOf("recent"), orders())
    }

    @Test
    fun `旧格式队列条目不会被误判为过期`() = runBlocking {
        // 升级安装：prefs 里是裸单号，没有时间戳。不能当成 0 从而立刻清掉。
        seed("legacy")
        val queried = mutableListOf<String>()
        val deps = dependencies().copy(queryOrder = { order ->
            queried.add(order)
            Result.success(status(order, "WAIT_BUYER_PAY"))
        })

        ProPurchase.recoverPending(context, deps)

        assertEquals(listOf("legacy"), queried)
        assertEquals(listOf("legacy"), orders())
    }

    @Test
    fun `业务核验失败即使包含成功交易字段也不能解析为已付`() {
        val response = json(200,
            JSONObject()
                .put("success", false)
                .put("code", "INVALID_ORDER")
                .put("data", JSONObject().put("trade_status", "TRADE_SUCCESS")))

        val result = runCatching { AlipayPayApi.parseOrderStatus("paid", response) }

        assertTrue(result.exceptionOrNull() is AlipayPayApi.ApiException)
    }

    @Test
    fun `解析查单响应保留限流业务码`() {
        val result = runCatching {
            AlipayPayApi.parseOrderStatus("pending", json(429,
                JSONObject()
                    .put("success", false)
                    .put("code", "RATE_LIMITED")
                    .put("message", "操作过于频繁")))
        }

        assertTrue((result.exceptionOrNull() as? AlipayPayApi.ApiException)?.isRateLimited == true)
    }

    /**
     * 回归：HTTP 429 但响应体是空的（例如错误页被 nginx 换成 HTML）时，
     * 仍要能从状态码识别出「被限流」，而不是笼统报「查单失败」。
     * 原实现只解析响应体、丢掉状态码，这一整类信息都不可见。
     */
    @Test
    fun `响应体为空时按 HTTP 状态码还原失败语义`() {
        val rateLimited = runCatching { AlipayPayApi.parseOrderStatus("x", json(429, JSONObject())) }
            .exceptionOrNull()
        assertTrue((rateLimited as AlipayPayApi.ApiException).isRateLimited)

        val unavailable = runCatching { AlipayPayApi.parseOrderStatus("x", json(503, JSONObject())) }
            .exceptionOrNull()
        assertTrue((unavailable as AlipayPayApi.ApiException).isConfigUnavailable)

        val missing = runCatching { AlipayPayApi.parseOrderStatus("x", json(404, JSONObject())) }
            .exceptionOrNull()
        assertEquals(AlipayPayApi.CODE_ORDER_MISSING, (missing as AlipayPayApi.ApiException).code)
    }

    @Test
    fun `关闭状态与支付状态明确区分且保留已有查询字段契约`() {
        val closed = AlipayPayApi.parseOrderStatus("closed", json(200, JSONObject()
            .put("success", true)
            .put("data", JSONObject().put("trade_status", "TRADE_CLOSED"))))
        assertTrue(closed.isClosed)
        assertFalse(closed.isPaid)
        assertFalse(closed.isNotExist)
        for (tradeStatus in listOf("TRADE_SUCCESS", "TRADE_FINISHED")) {
            val paid = AlipayPayApi.parseOrderStatus("paid", json(200, JSONObject()
                .put("success", true)
                .put("data", JSONObject().put("trade_status", tradeStatus))))
            assertTrue(paid.isPaid)
            assertFalse(paid.isClosed)
        }
    }

    /**
     * P1-2：展示价是客户端常量、扣款价是服务端配置，两者之间原本没有任何自动校验。
     * 项目历史上真的改过一次价（￥1.99 → ￥3.99），漏改展示价的后果是应用商店
     * 判定「标价与实际支付金额不符」。单测跑在仓库内，直接把两份值对齐。
     */
    @Test
    fun `应用内展示价必须与服务端 amount-fen 一致`() {
        // Gradle 单测的工作目录可能是仓库根或 :app 模块目录，逐级向上找
        val yml = listOf(
            File("lkl-pay-server/src/main/resources/application.yml"),
            File("../lkl-pay-server/src/main/resources/application.yml"),
            File("../../lkl-pay-server/src/main/resources/application.yml"),
        ).firstOrNull { it.isFile }
            ?: error("找不到服务端 application.yml；当前工作目录=${File(".").absolutePath}")

        val amountFen = Regex("""amount-fen:\s*(\d+)""")
            .find(yml.readText())?.groupValues?.get(1)?.toLong()
            ?: error("application.yml 中缺少 amount-fen")

        val shown = Regex("""(\d+)\.(\d{2})""").find(ProPurchase.PRICE_TEXT)
            ?: error("PRICE_TEXT 不是 ￥x.yy 形式: ${ProPurchase.PRICE_TEXT}")
        val shownFen = shown.groupValues[1].toLong() * 100 + shown.groupValues[2].toLong()

        assertEquals(
            "展示价 ${ProPurchase.PRICE_TEXT} 与服务端定价 $amountFen 分不一致，" +
                "改价时必须同时改 ProPurchase.PRICE_TEXT 与 application.yml",
            amountFen,
            shownFen,
        )
    }

    private fun dependencies() = ProPurchase.Dependencies(
        createOrder = { _ -> Result.success(AlipayPayApi.OrderCreated("paid", "test-order")) },
        queryOrder = { order -> Result.success(status(order)) },
        pay = { _, _ -> AlipayPay.STATUS_CANCELED },
        activate = { _, credential ->
            trial.activateWithEntitlement(credential, TrialManager.PAY_METHOD_ALIPAY)
        },
    )

    /**
     * 已支付的查单结果。
     *
     * credential 只在「真正已支付」时才该有——这里按 tradeStatus 判断，
     * 未付款/关闭的单不带凭证，避免测试替身比生产实现更宽松。
     */
    private fun status(
        order: String,
        tradeStatus: String = "TRADE_SUCCESS",
        credential: String? = if (tradeStatus == "TRADE_SUCCESS" || tradeStatus == "TRADE_FINISHED") {
            TestEntitlements.sign()
        } else {
            null
        },
    ) = AlipayPayApi.OrderStatus(order, tradeStatus, "3.99", "10000", "", credential)

    private fun seed(vararg orders: String) {
        pending.backing.edit().putString("pending_order_no", orders.joinToString(",")).commit()
    }

    /** 读出队列里的单号（剥掉条目里的写入时间戳）。 */
    private fun orders(): List<String> = pending.getString("pending_order_no", null)
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.map { it.substringBefore(':') }
        .orEmpty()

    private fun json(status: Int, body: JSONObject) = ServerClient.Response(status, body)

    // 独立实例模拟重新读取持久化记录，不修改生产单例或全局依赖。
    private fun newTrialManager(): TrialManager = TrialManager::class.java
        .getDeclaredConstructor(Context::class.java)
        .apply { isAccessible = true }
        .newInstance(context)

    private class CommitProbe(val backing: SharedPreferences) : SharedPreferences by backing {
        var allowCommit = true
        var commits = 0
        var applies = 0
        var onCommit: () -> Unit = {}

        override fun edit(): SharedPreferences.Editor {
            val delegate = backing.edit()
            return object : SharedPreferences.Editor by delegate {
                override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { delegate.putString(key, value) }
                override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { delegate.putBoolean(key, value) }
                override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { delegate.putLong(key, value) }
                override fun remove(key: String): SharedPreferences.Editor = apply { delegate.remove(key) }
                override fun commit(): Boolean {
                    commits++
                    onCommit()
                    return allowCommit && delegate.commit()
                }
                override fun apply() {
                    applies++
                    delegate.apply()
                }
            }
        }
    }
}
