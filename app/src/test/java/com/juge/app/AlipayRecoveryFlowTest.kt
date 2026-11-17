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
 * 找回流程的编排逻辑：什么时候写本地激活、写入的是什么时间、用的是哪段凭证。
 *
 * 这里把授权、服务端核验、激活全部换成注入的假实现——
 * 真实 SDK 与网关无法在单测里跑，但「命中 → 用哪个时间、哪段凭证激活」
 * 这些决策必须锁住：写错时间会让会员卡显示成"今天刚买"，
 * 而漏传凭证会让激活在下次启动时静默失效。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlipayRecoveryFlowTest {

    private val activity: ComponentActivity =
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    private val credential = TestEntitlements.sign()

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
                confirmPurchase = {
                    Result.success(
                        AlipayRecovery.PurchaseRecord(found = true, paidAt = paidAt, credential = credential)
                    )
                },
                activate = { _, at, _ -> activated = true; recordedAt = at; true },
            ),
        )

        assertEquals(AlipayRecovery.Outcome.Restored, outcome)
        assertTrue(activated)
        assertEquals(paidAt, recordedAt)
    }

    @Test
    fun `激活时拿到的是服务端签发的那段凭证`() = runBlocking {
        var seen: String? = null

        AlipayRecovery.recover(
            activity,
            AlipayRecovery.Dependencies(
                fetchAuthInfo = { Result.success("auth-info") },
                authorize = { _, _ -> "auth-code" },
                confirmPurchase = {
                    Result.success(
                        AlipayRecovery.PurchaseRecord(found = true, paidAt = 1L, credential = credential)
                    )
                },
                activate = { _, _, received -> seen = received; true },
            ),
        )

        assertEquals(
            "凭证必须原样传进 activate —— 换一段就是换一份授权",
            credential,
            seen,
        )
    }

    @Test
    fun `服务端没给购买时间时退化为当前时间而不是 1970`() = runBlocking {
        var recordedAt: Long? = null

        AlipayRecovery.recover(
            activity,
            AlipayRecovery.Dependencies(
                fetchAuthInfo = { Result.success("auth-info") },
                authorize = { _, _ -> "auth-code" },
                confirmPurchase = {
                    Result.success(AlipayRecovery.PurchaseRecord(found = true, paidAt = null, credential = credential))
                },
                activate = { _, at, _ -> recordedAt = at; true },
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
                confirmPurchase = {
                    Result.success(AlipayRecovery.PurchaseRecord(found = false, paidAt = null, credential = null))
                },
                activate = { _, _, _ -> activated = true; true },
            ),
        )

        assertEquals(AlipayRecovery.Outcome.NotFound, outcome)
        assertTrue("没查到就不能激活", !activated)
    }

    @Test
    fun `查到订单但服务端没签发凭证时明确失败而不是假装成功`() = runBlocking {
        var activated = false

        val outcome = AlipayRecovery.recover(
            activity,
            AlipayRecovery.Dependencies(
                fetchAuthInfo = { Result.success("auth-info") },
                authorize = { _, _ -> "auth-code" },
                confirmPurchase = {
                    Result.success(AlipayRecovery.PurchaseRecord(found = true, paidAt = 1L, credential = null))
                },
                activate = { _, _, _ -> activated = true; true },
            ),
        )

        // 放行会写下一个「下次启动就失效」的本地状态，用户看到的是"找回成功但会员没生效"，
        // 比直接报错更难排查 —— 所以这里必须是 Failed。
        assertTrue("没有凭证不能激活", !activated)
        assertTrue(outcome is AlipayRecovery.Outcome.Failed)
        assertTrue((outcome as AlipayRecovery.Outcome.Failed).message.contains("凭证"))
    }

    @Test
    fun `用户关掉授权页按取消处理且不请求服务端`() = runBlocking {
        var queried = false

        val outcome = AlipayRecovery.recover(
            activity,
            AlipayRecovery.Dependencies(
                fetchAuthInfo = { Result.success("auth-info") },
                authorize = { _, _ -> null },
                confirmPurchase = { queried = true; Result.success(AlipayRecovery.PurchaseRecord(true, null, null)) },
                activate = { _, _, _ -> true },
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
                confirmPurchase = { Result.success(AlipayRecovery.PurchaseRecord(true, null, null)) },
                activate = { _, _, _ -> true },
            ),
        )

        assertTrue(outcome is AlipayRecovery.Outcome.Failed)
        assertTrue("没拿到 authInfo 不该拉起支付宝", !authorized)
    }

    @Test
    fun `凭证缺失时不会被当成 null 字符串写进本地`() = runBlocking {
        var seen: String? = "sentinel"

        AlipayRecovery.recover(
            activity,
            AlipayRecovery.Dependencies(
                fetchAuthInfo = { Result.success("auth-info") },
                authorize = { _, _ -> "auth-code" },
                confirmPurchase = {
                    Result.success(AlipayRecovery.PurchaseRecord(found = true, paidAt = 1L, credential = "  "))
                },
                activate = { _, _, received -> seen = received; true },
            ),
        )

        assertEquals("空白凭证必须在到达 activate 之前就被拦下", "sentinel", seen)
    }
}
