package com.juge.app.ui.main

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.juge.app.ReminderWidgetProvider
import com.juge.app.data.LegalDocs
import com.juge.app.pay.AlipayRecovery
import com.juge.app.pay.ProPurchase
import com.juge.app.ui.legal.LegalDocDialog
import kotlinx.coroutines.launch

/**
 * 主界面之上的两个浮层：PRO 激活弹窗、协议全文弹窗。
 *
 * 权益既不绑设备也不绑账号，绑的是**付款人的支付宝账号**。
 * 所以这里只有两个动作：买（支付宝收银台）、已买过（支付宝授权核验）。
 * 没有任何登录态要维护，也就没有「换了手机登不上」这条失败路径。
 *
 * 声明顺序 = 窗口层级：协议全文最后声明——从付费墙里点协议要能盖住当前这一层。
 */
@Composable
fun MainOverlays(
    showProDialog: Boolean,
    isActivated: Boolean,
    isPaying: Boolean,
    isRecovering: Boolean,
    activity: ComponentActivity,
    onActivatedChange: (Boolean) -> Unit,
    onPayingChange: (Boolean) -> Unit,
    onRecoveringChange: (Boolean) -> Unit,
    onProDialogDismiss: () -> Unit,
    legalDoc: String?,
    onLegalDismiss: () -> Unit,
) {
    val context = LocalContext.current

    ProActivationDialog(
        show = showProDialog,
        isActivated = isActivated,
        isPaying = isPaying,
        isRecovering = isRecovering,
        onActivate = {
            if (!isPaying && !isRecovering) {
                onPayingChange(true)
                activity.lifecycleScope.launch {
                    when (val outcome = ProPurchase.purchase(activity)) {
                        is ProPurchase.Outcome.Paid -> {
                            // Paid 已保证激活落盘，这里只更新界面，不能重复覆盖激活记录。
                            onActivatedChange(true)
                            onProDialogDismiss()
                            // 桌面组件上未激活时的提示位图需要重绘
                            ReminderWidgetProvider.triggerUpdateAllWidgets(context)
                            Toast.makeText(context, "🎉 PRO 已激活，全部风格已解锁！", Toast.LENGTH_SHORT).show()
                        }

                        is ProPurchase.Outcome.Unpaid -> if (outcome.message.isNotEmpty()) {
                            Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
                        }

                        is ProPurchase.Outcome.Failed -> {
                            Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
                        }
                    }
                    onPayingChange(false)
                }
            }
        },
        onRecover = {
            if (!isPaying && !isRecovering) {
                onRecoveringChange(true)
                activity.lifecycleScope.launch {
                    when (val outcome = AlipayRecovery.recover(activity)) {
                        AlipayRecovery.Outcome.Restored -> {
                            onActivatedChange(true)
                            onProDialogDismiss()
                            ReminderWidgetProvider.triggerUpdateAllWidgets(context)
                            Toast.makeText(context, "🎉 已找回你的 PRO，全部风格已解锁！", Toast.LENGTH_SHORT).show()
                        }

                        // 最常见的原因是「家人朋友代付」——那笔订单的 buyer_user_id 属于付款人。
                        // 这里必须给出可执行的下一步，否则用户只会以为钱白花了。
                        AlipayRecovery.Outcome.NotFound -> Toast.makeText(
                            context,
                            "没有查到该支付宝账号的购买记录。如果你是用家人或朋友的账号付款的，请发邮件到 ${LegalDocs.EMAIL} 帮你人工找回",
                            Toast.LENGTH_LONG
                        ).show()

                        AlipayRecovery.Outcome.Canceled -> Unit

                        is AlipayRecovery.Outcome.Failed ->
                            Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
                    }
                    onRecoveringChange(false)
                }
            }
        },
        onDismiss = { if (!isPaying && !isRecovering) onProDialogDismiss() }
    )

    val doc = legalDoc
    LegalDocDialog(
        show = doc != null,
        title = if (doc != null) LegalDocs.titleOf(doc) else "",
        body = if (doc != null) LegalDocs.bodyOf(doc) else "",
        onDismiss = onLegalDismiss
    )
}
