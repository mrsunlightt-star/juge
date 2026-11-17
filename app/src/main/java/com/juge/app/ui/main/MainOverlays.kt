package com.juge.app.ui.main

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.juge.app.ReminderWidgetProvider
import com.juge.app.data.LegalDocs
import com.juge.app.data.TrialManager
import com.juge.app.pay.PayFlow
import com.juge.app.ui.legal.LegalDocDialog

/**
 * 主界面之上的两个浮层：PRO 激活弹窗、协议全文弹窗。
 *
 * 权益既不绑设备也不绑账号，绑的是**付款人的支付宝账号**。
 * 所以这里只有两个动作：买（支付宝收银台）、已买过（支付宝授权核验）。
 * 没有任何登录态要维护，也就没有「换了手机登不上」这条失败路径。
 *
 * 声明顺序 = 窗口层级：协议全文最后声明——从付费墙里点协议要能盖住当前这一层。
 *
 * **流程本身不在这里跑**：收银台与授权都是阻塞调用，原先挂在
 * `activity.lifecycleScope` 上，旋转即中断（见 [PayFlow] 的类注释）。
 * 现在这里只做三件事：读 [PayFlow.state] 置忙、把点击转成 [PayFlow] 的启动调用、
 * 收 [PayFlow.events] 显示结果。
 */
@Composable
fun MainOverlays(
    showProDialog: Boolean,
    isActivated: Boolean,
    activity: ComponentActivity,
    onActivatedChange: (Boolean) -> Unit,
    onProDialogDismiss: () -> Unit,
    onRequestOpenPro: () -> Unit,
    showStatusDialog: Boolean,
    activationRecord: TrialManager.ActivationRecord?,
    onStatusDismiss: () -> Unit,
    legalDoc: String?,
    onLegalDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // collectAsStateWithLifecycle：界面不可见时停止收集。这里订阅的虽是进程级
    // StateFlow，但退到后台后每次状态变化仍会把重组排进主线程队列——不省内存，
    // 省的是不可见界面上的无用帧。真正不能在后台跑的是流程本身，那个在 PayFlow 里。
    val flowState by PayFlow.state.collectAsStateWithLifecycle()
    val isPaying = flowState == PayFlow.State.Paying
    val isRecovering = flowState == PayFlow.State.Recovering

    // 结果事件只投递一次；旋转期间完成的那一笔会在重建后被补投到这里。
    LaunchedEffect(Unit) {
        for (event in PayFlow.events) {
            when (event) {
                is PayFlow.Event.Activated -> {
                    onActivatedChange(true)
                    onProDialogDismiss()
                    // 桌面组件上未激活时的提示位图需要重绘
                    ReminderWidgetProvider.triggerUpdateAllWidgets(context)
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
                }
                is PayFlow.Event.Notice ->
                    Toast.makeText(context, event.text, Toast.LENGTH_LONG).show()
            }
        }
    }

    ProActivationDialog(
        show = showProDialog,
        isActivated = isActivated,
        isPaying = isPaying,
        isRecovering = isRecovering,
        onActivate = { PayFlow.startPurchase(activity) },
        onRecover = { PayFlow.startRecovery(activity) },
        onDismiss = { if (!isPaying && !isRecovering) onProDialogDismiss() }
    )

    // 会员状态卡：只在用户点顶栏 PRO 徽章时出现，纯本地读取，不触网。
    ProStatusDialog(
        show = showStatusDialog,
        isActivated = isActivated,
        record = activationRecord,
        onOpenPro = {
            onStatusDismiss()
            onRequestOpenPro()
        },
        onDismiss = onStatusDismiss,
    )

    val doc = legalDoc
    LegalDocDialog(
        show = doc != null,
        title = if (doc != null) LegalDocs.titleOf(doc) else "",
        body = if (doc != null) LegalDocs.bodyOf(doc) else "",
        onDismiss = onLegalDismiss
    )
}
