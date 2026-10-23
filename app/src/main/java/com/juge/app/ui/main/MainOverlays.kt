package com.juge.app.ui.main

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.juge.app.ReminderWidgetProvider
import com.juge.app.account.AccountDialog
import com.juge.app.data.LegalDocs
import com.juge.app.data.TrialManager
import com.juge.app.pay.ProPurchase
import com.juge.app.ui.legal.LegalDocDialog
import kotlinx.coroutines.launch

/**
 * 主界面之上的三个浮层：PRO 激活弹窗、账号弹窗、协议全文弹窗。
 *
 * 从 MainAppScreen 原样搬出（第 3/3 步拆 MainActivity）：付费流程与激活回灌写在这里，
 * 但激活状态、弹窗开关、账号昵称仍由调用方持有，只通过回调写回。
 */
@Composable
fun MainOverlays(
    showProDialog: Boolean,
    isActivated: Boolean,
    isPaying: Boolean,
    accountName: String?,
    activity: ComponentActivity,
    trialManager: TrialManager,
    onActivatedChange: (Boolean) -> Unit,
    onPayingChange: (Boolean) -> Unit,
    onOpenAccount: () -> Unit,
    onProDialogDismiss: () -> Unit,
    showAccountDialog: Boolean,
    onAccountDismiss: () -> Unit,
    legalDoc: String?,
    onLegalDismiss: () -> Unit
) {
    val context = LocalContext.current

    if (showProDialog) {
        ProActivationDialog(
            isActivated = isActivated,
            isPaying = isPaying,
            accountName = accountName,
            onActivate = {
                if (!isPaying) {
                    onPayingChange(true)
                    activity.lifecycleScope.launch {
                        when (val outcome = ProPurchase.purchase(activity)) {
                            is ProPurchase.Outcome.Paid -> {
                                trialManager.activate(TrialManager.PAY_METHOD_ALIPAY)
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
            onOpenAccount = onOpenAccount,
            onDismiss = { if (!isPaying) onProDialogDismiss() }
        )
    }

    if (showAccountDialog) {
        AccountDialog(
            onProConfirmed = {
                // 只在本机未激活时才写入「账号找回」记录。登录成功不代表本机没有 PRO：
                // 已用支付宝在这台机器买过的用户，激活记录里留着真实付费时间/方式，
                // 无条件覆盖会让「我的」页的数据失真；更糟的是日后该账号服务端失去 PRO
                // 时会被误判为「账号找回的权益」遭整体撤销（见 MainAppScreen 的账号同步分支）
                if (trialManager.isActivated()) {
                    // 已激活就什么都不用做，权益不会更多也不会更少
                    return@AccountDialog
                }
                trialManager.activate(TrialManager.PAY_METHOD_ACCOUNT)
                onActivatedChange(true)
                ReminderWidgetProvider.triggerUpdateAllWidgets(context)
                Toast.makeText(context, "🎉 已通过账号找回 PRO，全部风格已解锁！", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                onAccountDismiss()
            },
        )
    }

    legalDoc?.let { doc ->
        LegalDocDialog(
            title = LegalDocs.titleOf(doc),
            body = LegalDocs.bodyOf(doc),
            onDismiss = onLegalDismiss
        )
    }
}
