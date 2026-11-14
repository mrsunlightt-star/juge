package com.juge.app

import androidx.activity.ComponentActivity
import com.juge.app.pay.AlipayRecovery
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 找回流程的编排逻辑：什么时候写本地激活、写入的是什么时间。
 *
 * 这里把授权、服务端核验、激活全部换成注入的假实现——
 * 真实 SDK 与网关无法在单测里跑，但「命中 → 用哪个时间激活」
 * 这个决策必须锁住：写错时间会让会员卡显示成"今天刚买"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlipayRecoveryFlowTest {

    private val activity: ComponentActivity =
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    @Test
    fun `找回成功后用服务端返回的购买时间激活`() = runBlocking {
        val paidAt = 1_700_000_000_000L
        var recordedAt: Long? = null
        var activated = false

        val outcome = AlipayRecovery.recover(
            activity,
            AlipayRecovery.Dependencies(
                fetchAuthInfo = { Result.success("auth-info") },
                authorize = { _, _ -> "auth-code" },
                confirmPurchase = { Result.success(AlipayRecovery.PurchaseRecord(found = true, paidAt = paidAt)) },
                activate = { _, at -> activated = true; recordedAt = at; true },
            ),
        )

        assertEquals(AlipayRecovery.Outcome.Restored, outcome)
        assertTrue(activated)
        assertEquals(paidAt, recordedAt)
    }

    @Test
    fun `服务端没给购买时间时退化为当前时间而不是 1970`() = runBlocking {
        var recordedAt: Long? = null

        AlipayRecovery.recover(
            activity,
            AlipayRecovery.Dependencies(
                fetchAuthInfo = { Result.success("auth-info") },
                authorize = { _, _ -> "auth-code" },
                confirmPurchase = { Result.success(AlipayRecovery.PurchaseRecord(found = true, paidAt = null)) },
                activate = { _, at -> recordedAt = at; true },
            ),
        )

        assertTrue("必须兜底成当前时间", (recordedAt ?: 0L) > 1_600_000_000_000L)
    }

    @Test
    fun `查不到已支付订单时不写本地激活`() = runBlocking {
        var activated = false

        val outcome = AlipayRecovery.recover(
            activity,
            AlipayRecovery.Dependencies(
                fetchAuthInfo = { Result.success("auth-info") },
                authorize = { _, _ -> "auth-code" },
                confirmPurchase = { Result.success(AlipayRecovery.PurchaseRecord(found = false, paidAt = null)) },
                activate = { _, _ -> activated = true; true },
            ),
        )

        assertEquals(AlipayRecovery.Outcome.NotFound, outcome)
        assertTrue("没查到就不能激活", !activated)
    }

    @Test
    fun `用户关掉授权页按取消处理且不请求服务端`() = runBlocking {
        var queried = false

        val outcome = AlipayRecovery.recover(
            activity,
            AlipayRecovery.Dependencies(
                fetchAuthInfo = { Result.success("auth-info") },
                authorize = { _, _ -> null },
                confirmPurchase = { queried = true; Result.success(AlipayRecovery.PurchaseRecord(true, null)) },
                activate = { _, _ -> true },
            ),
        )

        assertEquals(AlipayRecovery.Outcome.Canceled, outcome)
        assertTrue("取消授权不应再打服务端", !queried)
    }

    @Test
    fun `取 authInfo 失败时给出可读提示且不拉起授权`() = runBlocking {
        var authorized = false

        val outcome = AlipayRecovery.recover(
            activity,
            AlipayRecovery.Dependencies(
                fetchAuthInfo = { Result.failure(IllegalStateException("boom")) },
                authorize = { _, _ -> authorized = true; "x" },
                confirmPurchase = { Result.success(AlipayRecovery.PurchaseRecord(true, null)) },
                activate = { _, _ -> true },
            ),
        )

        assertTrue(outcome is AlipayRecovery.Outcome.Failed)
        assertTrue("没拿到 authInfo 不该拉起支付宝", !authorized)
    }
}
