package com.juge.app.pay

import android.app.Activity
import com.juge.app.JugeApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 收银台 / 找回流程的进程级状态机。
 *
 * ## 为什么不能挂在 Activity 上
 *
 * 原来这两条流程跑在 `activity.lifecycleScope.launch` 里：
 * 拉起支付宝 → 用户旋转屏幕（或折叠屏展开、改系统字号触发重建）→
 * Activity 被销毁，协程在下一个挂起点被取消。
 * 此时支付宝那边**人还在付钱**，App 这边已经没有任何人接结果了：
 * 用户付完回来，界面停在支付前的样子，`isPaying` 也随旧 composition 一起蒸发，
 * 新 Activity 上按钮重新可点 —— 用户可能再付一次。
 *
 * 唯一还能兜住的是 pending 队列（下次冷启动的 [ProPurchase.recoverPending]），
 * 但那要等下次启动，用户当下看到的是「钱扣了，PRO 没到」。
 *
 * ## 现在怎么分工
 *
 * - **状态**（[state]）放进程级：旋转前后读到的是同一个 `Paying`，按钮不会复活。
 * - **执行**挂 [JugeApplication.appScope]：Activity 重建不会掐掉在途的收银台协程。
 * - **结果**走 [events] 单向下发：新 composition 用 `for (event in events)` 收，
 *   所以「旋转期间完成的支付」会在重建后被补投一次，而不是被丢掉。
 *
 * 线程：收银台与查单都在 IO 上；这里只做状态流转，不做阻塞调用。
 */
object PayFlow {

    /** 当前正在跑哪条流程。界面据此置忙、并阻止第二条流程同时进入。 */
    sealed interface State {
        data object Idle : State
        data object Paying : State
        data object Recovering : State
    }

    /** 一次性结果。界面只负责显示与刷新，不再自己做业务判断。 */
    sealed interface Event {
        /** 权益已落盘：界面刷新激活态、关掉付费墙、重绘桌面组件。 */
        data class Activated(val text: String) : Event

        /** 需要告知用户的一句话（失败原因、未查到记录等）。 */
        data class Notice(val text: String) : Event
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * 单消费者通道。
     *
     * 用 Channel 而不是 SharedFlow(replay=1)：结果只该被投递一次。
     * replay 会让「上次会话的 Toast」在每次重建时重新弹一遍。
     * 没有收集者时消息留在缓冲区里，等新 composition 起来再取。
     */
    private val _events = Channel<Event>(capacity = Channel.BUFFERED)
    val events: Channel<Event> = _events

    /**
     * 两条流程的可替换入口。
     *
     * 生产恒为默认实现；测试用它把「真的去拉起收银台」换成一个能立刻返回的闭包。
     * 之所以做成可替换字段而不是构造参数：本类是 object（要跨 composition 存活），
     * 没有构造点可注入。同 `EntitlementVerifier.testPublicKeyOverride` 的取舍。
     */
    internal class Runner(
        val purchase: suspend (Activity) -> ProPurchase.Outcome = { ProPurchase.purchase(it) },
        val recover: suspend (Activity) -> AlipayRecovery.Outcome = { AlipayRecovery.recover(it) },
    )

    internal var runner: Runner = Runner()

    private var appScope: CoroutineScope? = null

    /** 由 [JugeApplication.onCreate] 调用；未初始化时退回进程内自带 scope，保证不 NPE。 */
    fun init(application: JugeApplication) {
        appScope = application.appScope
    }

    /** 测试用：换成可控 scope（如 UnconfinedTestDispatcher），并清空残留状态。 */
    internal fun resetForTest(scope: CoroutineScope?, runner: Runner = Runner()) {
        appScope = scope
        this.runner = runner
        _state.value = State.Idle
        while (_events.tryReceive().isSuccess) Unit
    }

    private fun scope(): CoroutineScope =
        appScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO).also { appScope = it }

    /** 买。重复调用（双击、旋转后再点）会被 [state] 挡住，不会并行下两单。 */
    fun startPurchase(activity: Activity) {
        if (_state.value != State.Idle) {
            Timber.i("PayFlow: 已有流程在跑（%s），忽略本次购买请求", _state.value)
            return
        }
        _state.value = State.Paying
        scope().launch {
            val outcome = runCatching { runner.purchase(activity) }
                .getOrElse { error ->
                    Timber.e(error, "PayFlow: 购买流程异常")
                    ProPurchase.Outcome.Failed("支付流程出错，请稍后重试")
                }
            when (outcome) {
                // Paid 已保证激活落盘，这里只发事件，不重复写 prefs。
                ProPurchase.Outcome.Paid -> _events.send(Event.Activated("🎉 PRO 已激活，全部风格已解锁！"))
                is ProPurchase.Outcome.Unpaid -> if (outcome.message.isNotEmpty()) {
                    _events.send(Event.Notice(outcome.message))
                }
                is ProPurchase.Outcome.Failed -> _events.send(Event.Notice(outcome.message))
            }
            _state.value = State.Idle
        }
    }

    /** 已买过 → 用支付宝授权核验。与购买共享同一个互斥状态。 */
    fun startRecovery(activity: Activity) {
        if (_state.value != State.Idle) {
            Timber.i("PayFlow: 已有流程在跑（%s），忽略本次找回请求", _state.value)
            return
        }
        _state.value = State.Recovering
        scope().launch {
            val outcome = runCatching { runner.recover(activity) }
                .getOrElse { error ->
                    Timber.e(error, "PayFlow: 找回流程异常")
                    AlipayRecovery.Outcome.Failed("找回失败，请稍后重试")
                }
            when (outcome) {
                AlipayRecovery.Outcome.Restored ->
                    _events.send(Event.Activated("🎉 已找回你的 PRO，全部风格已解锁！"))

                AlipayRecovery.Outcome.NotFound -> _events.send(
                    Event.Notice(
                        "没有查到该支付宝账号的购买记录。" +
                            "如果你是用家人或朋友的账号付款的，请发邮件到 " +
                            "${com.juge.app.data.LegalDocs.EMAIL} 帮你人工找回",
                    ),
                )

                AlipayRecovery.Outcome.Canceled -> Unit

                is AlipayRecovery.Outcome.Failed -> _events.send(Event.Notice(outcome.message))
            }
            _state.value = State.Idle
        }
    }
}
