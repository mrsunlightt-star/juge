package com.juge.app

import android.app.Activity
import com.juge.app.pay.AlipayRecovery
import com.juge.app.pay.PayFlow
import com.juge.app.pay.ProPurchase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * 收银台流程的进程级状态机回归。
 *
 * 这里要钉死的是一件事：**流程不属于任何一次 composition**。
 * 原来它挂在 `activity.lifecycleScope` 上，旋转一次就没了；
 * 现在状态在 [PayFlow] 里、执行在 Application 的 scope 上。
 *
 * 用真实 Activity 实例做参数占位（流程本身被 [PayFlow.Runner] 替身接管，不会真拉起支付宝）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class PayFlowTest {

    private lateinit var activity: Activity
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        activity = Activity()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        PayFlow.resetForTest(null)
    }

    @Test
    fun `购买期间状态为进行中且完成后回到空闲`() {
        PayFlow.resetForTest(scope, PayFlow.Runner(purchase = { ProPurchase.Outcome.Paid }))

        PayFlow.startPurchase(activity)
        // Unconfined 下协程同步跑完，但事件仍留在通道里等界面来取
        assertEquals(PayFlow.State.Idle, PayFlow.state.value)

        val event = runBlocking { withTimeout(1_000) { PayFlow.events.receive() } }
        assertTrue("支付成功要下发 Activated 事件，而不是让界面自己判断", event is PayFlow.Event.Activated)
    }

    @Test
    fun `流程进行中再次点击不会并行发起第二单`() {
        val started = AtomicInteger(0)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        // 用一个真的会挂起的 dispatcher，让第一次调用停在 gate 上
        PayFlow.resetForTest(CoroutineScope(SupervisorJob() + Dispatchers.Default), PayFlow.Runner(
            purchase = {
                started.incrementAndGet()
                gate.await()
                ProPurchase.Outcome.Unpaid("")
            },
        ))
        PayFlow.startPurchase(activity)
        assertEquals(PayFlow.State.Paying, PayFlow.state.value)

        // 旋转后「新界面」再点一次
        PayFlow.startPurchase(activity)

        assertEquals("第二次点击必须被状态挡住，不能在支付宝里开两个收银台", 1, started.get())
        gate.complete(Unit)
    }

    @Test
    fun `购买与找回共享同一个互斥状态`() {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        PayFlow.resetForTest(CoroutineScope(SupervisorJob() + Dispatchers.Default), PayFlow.Runner(
            purchase = { gate.await(); ProPurchase.Outcome.Unpaid("") },
            recover = { Activity(); AlipayRecovery.Outcome.Canceled },
        ))

        PayFlow.startPurchase(activity)
        assertEquals(PayFlow.State.Paying, PayFlow.state.value)

        PayFlow.startRecovery(activity)
        assertEquals("购买在跑的时候不能同时进找回", PayFlow.State.Paying, PayFlow.state.value)

        gate.complete(Unit)
    }

    @Test
    fun `流程抛异常时状态不会永久卡在进行中`() {
        PayFlow.resetForTest(scope, PayFlow.Runner(purchase = { error("boom") }))

        PayFlow.startPurchase(activity)

        // 异常被 runCatching 吃掉并转成 Notice，状态必须回到 Idle，
        // 否则用户从此再也点不动购买按钮
        assertEquals(PayFlow.State.Idle, PayFlow.state.value)
        val event = runBlocking { withTimeout(1_000) { PayFlow.events.receive() } }
        assertTrue(event is PayFlow.Event.Notice)
    }

    @Test
    fun `找回成功同样下发激活事件`() {
        PayFlow.resetForTest(scope, PayFlow.Runner(recover = { AlipayRecovery.Outcome.Restored }))

        PayFlow.startRecovery(activity)

        val event = runBlocking { withTimeout(1_000) { PayFlow.events.receive() } }
        assertTrue(event is PayFlow.Event.Activated)
    }

    @Test
    fun `没查到时给出去找客服的可执行下一步`() {
        PayFlow.resetForTest(scope, PayFlow.Runner(recover = { AlipayRecovery.Outcome.NotFound }))

        PayFlow.startRecovery(activity)

        val event = runBlocking { withTimeout(1_000) { PayFlow.events.receive() } }
        val notice = event as? PayFlow.Event.Notice
        assertNotNull("没查到不能静默，否则用户以为钱白花了", notice)
        assertTrue(
            "提示里要带联系方式：${notice!!.text}",
            notice.text.contains(com.juge.app.data.LegalDocs.EMAIL),
        )
    }

    @Test
    fun `取消授权不产生任何提示`() {
        PayFlow.resetForTest(scope, PayFlow.Runner(recover = { AlipayRecovery.Outcome.Canceled }))

        PayFlow.startRecovery(activity)

        assertEquals(PayFlow.State.Idle, PayFlow.state.value)
        // 通道里不该有事件；tryReceive 拿到 null 或失败都算对
        assertTrue(
            "用户自己取消的，不该弹任何东西",
            runBlocking { PayFlow.events.tryReceive() }.isFailure,
        )
    }
}
