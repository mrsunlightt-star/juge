package com.juge.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.Signature
import android.os.Looper
import com.juge.app.data.TrialManager
import com.juge.app.pay.AlipayPay
import com.juge.app.pay.AlipayPayApi
import com.juge.app.pay.ProPurchase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 只注入本地 API/收银台替身；实际使用 TrialManager 与队列的 SharedPreferences 提交路径。 */
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
        trial = newTrialManager()
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
            activate = { error("终态未付款单不得激活") },
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
    fun `并发追加订单不覆盖其他未支付订单`() = runBlocking {
        (1..24).map { index ->
            async(Dispatchers.Default) {
                ProPurchase.purchase(activity, dependencies().copy(createOrder = { _ ->
                    Result.success(AlipayPayApi.OrderCreated("order-$index", "test-order"))
                }))
            }
        }.awaitAll()

        assertEquals((1..24).map { "order-$it" }.toSet(), orders().toSet())
        assertEquals(24, orders().size)
    }

    @Test
    fun `业务核验失败即使包含成功交易字段也不能解析为已付`() {
        val response = JSONObject()
            .put("success", false)
            .put("code", "INVALID_ORDER")
            .put("data", JSONObject().put("trade_status", "TRADE_SUCCESS"))

        val result = runCatching { AlipayPayApi.parseOrderStatus("paid", response) }

        assertTrue(result.exceptionOrNull() is AlipayPayApi.ApiException)
    }

    @Test
    fun `解析查单响应保留限流业务码`() {
        val result = runCatching {
            AlipayPayApi.parseOrderStatus("pending", JSONObject()
                .put("success", false)
                .put("code", "RATE_LIMITED")
                .put("message", "操作过于频繁"))
        }

        assertTrue((result.exceptionOrNull() as? AlipayPayApi.ApiException)?.isRateLimited == true)
    }

    @Test
    fun `关闭状态与支付状态明确区分且保留已有查询字段契约`() {
        val closed = AlipayPayApi.parseOrderStatus("closed", JSONObject()
            .put("success", true)
            .put("data", JSONObject().put("trade_status", "TRADE_CLOSED")))
        assertTrue(closed.isClosed)
        assertFalse(closed.isPaid)
        assertFalse(closed.isNotExist)
        for (tradeStatus in listOf("TRADE_SUCCESS", "TRADE_FINISHED")) {
            val paid = AlipayPayApi.parseOrderStatus("paid", JSONObject()
                .put("success", true)
                .put("data", JSONObject().put("trade_status", tradeStatus)))
            assertTrue(paid.isPaid)
            assertFalse(paid.isClosed)
        }
    }

    private fun dependencies() = ProPurchase.Dependencies(
        createOrder = { _ -> Result.success(AlipayPayApi.OrderCreated("paid", "test-order")) },
        queryOrder = { order -> Result.success(status(order)) },
        pay = { _, _ -> AlipayPay.STATUS_CANCELED },
        activate = { trial.activate(TrialManager.PAY_METHOD_ALIPAY) },
    )

    private fun status(order: String, tradeStatus: String = "TRADE_SUCCESS") =
        AlipayPayApi.OrderStatus(order, tradeStatus, "1.99", "10000", "")

    private fun seed(vararg orders: String) {
        pending.backing.edit().putString("pending_order_no", orders.joinToString(",")).commit()
    }

    private fun orders(): List<String> = pending.getString("pending_order_no", null)
        ?.split(',')?.filter { it.isNotEmpty() }.orEmpty()

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
