package com.juge.app.ui.main

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.juge.app.data.CrashReporter
import com.juge.app.data.TrialManager
import com.juge.app.pay.ProPurchase
import com.juge.app.ui.AppDialog
import com.juge.app.ui.AppDialogSurface
import com.juge.app.ui.AppPrimaryButton
import com.juge.app.ui.AppSectionCard
import com.juge.app.ui.AppSecondaryButton
import com.juge.app.ui.AppText
import com.juge.app.ui.AppTextButton
import com.juge.app.ui.theme.AppTextRole
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.premiumGoldDeep
import com.juge.app.ui.theme.successGreen
import com.juge.app.ui.theme.textTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 激活时间只用于这张卡展示：本地时区、中文习惯、精确到分钟 */
private const val ACTIVATION_TIME_PATTERN = "yyyy年M月d日 HH:mm"

/**
 * 激活时间格式化（纯函数，便于单测）：毫秒时间戳 → 「2026年10月30日 21:30」。
 *
 * 每次新建 [SimpleDateFormat]：共享实例不是线程安全的，一张卡只格式化一次，
 * 这点开销远小于留一个随时会踩的并发陷阱。
 */
internal fun formatActivationTime(activatedAt: Long): String =
    SimpleDateFormat(ACTIVATION_TIME_PATTERN, Locale.CHINA).format(Date(activatedAt))

/**
 * 会员状态卡：告诉用户「我到底买没买、什么时候买的、是不是永久」。
 *
 * 为什么需要它：权益改成绑支付宝账号之后，本机没有账号页可看。
 * 用户换机找回、或者在付费墙外想确认状态时，需要一个明确的落点——
 * 只有顶栏一枚 PRO 徽章，说服力不够（用户会怀疑是缓存/试用）。
 *
 * 数据只读本地 [TrialManager.activationRecord]，**不联网**：
 * 已激活是本地事实，没必要为了展示它去请求服务器；真正需要联网的是「找回」，
 * 那一步的结论最终也会落到同一份本地记录上。
 *
 * 样式与 [ProActivationDialog] 同一套语言：金色徽标 + hero 标题 + 白卡明细，
 * 只是这里不卖东西，所以主按钮是「知道了」而不是支付。
 */
@Composable
fun ProStatusDialog(
    show: Boolean,
    isActivated: Boolean,
    record: TrialManager.ActivationRecord?,
    isRecovering: Boolean,
    isExportingQuotes: Boolean,
    onRecover: () -> Unit,
    onExportQuotes: () -> Unit,
    onOpenPro: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // 只有本机确实存过崩溃记录才显示导出入口：绝大多数用户看不到额外节点，
    // 隐私同意弹窗 / 主界面的 UI 基线因此不受影响；每次打开弹窗重新查一次目录。
    var hasCrashReports by remember(show) {
        mutableStateOf(CrashReporter.pendingReports(context).isNotEmpty())
    }
    AppDialog(
        visible = show,
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AppDialogSurface(scrollable = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = premiumGoldDeep,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                AppText(text = "PRO 会员", role = AppTextRole.hero)
            }

            if (isActivated) {
                AppText(
                    text = "已激活 · 永久有效",
                    role = AppTextRole.bodyStrong,
                    color = successGreen,
                )

                AppSectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DetailRow(
                            // 换机找回时这里记的是「当初付款的时间」（服务端返回 paid_at），
                            // 不是本次找回的时间——用户问的是「我什么时候买的」。
                            label = "购买时间",
                            value = record?.let { formatActivationTime(it.activatedAt) }
                                ?: "更早版本激活，未记录",
                        )
                        DetailRow(
                            label = "激活方式",
                            value = record?.payMethod ?: "未记录",
                        )
                        DetailRow(label = "有效期", value = "永久，无订阅费用")
                    }
                }

                AppText(
                    text = "权益绑定付款时使用的支付宝账号。更换设备或重装应用后，" +
                        "点「已购买过？用支付宝找回」即可恢复，不需要账号和密码。",
                    role = AppTextRole.micro,
                    color = textTertiary,
                )

                AppSecondaryButton(
                    text = if (isRecovering) "正在核验…" else "已购买过？用支付宝找回",
                    onClick = onRecover,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRecovering,
                    contentColor = accentBlue,
                )

                AppText(
                    text = "将文库中的全部金句按分类汇总，保存为 Markdown 文件。",
                    role = AppTextRole.micro,
                    color = textTertiary,
                )
                AppSecondaryButton(
                    text = if (isExportingQuotes) "正在导出…" else "导出全部金句 (.md)",
                    onClick = onExportQuotes,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isExportingQuotes,
                    contentColor = accentBlue,
                )

                AppPrimaryButton(
                    text = "知道了",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                AppText(text = "当前是免费版", role = AppTextRole.bodySecondary)
                AppText(
                    text = "解锁全部内置风格与自定义背景图，${ProPurchase.PRICE_TEXT} 一次性买断，永久有效。",
                    role = AppTextRole.micro,
                    color = textTertiary,
                )
                AppPrimaryButton(
                    text = "支付宝支付 ${ProPurchase.PRICE_TEXT}",
                    onClick = onOpenPro,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextButton(
                    text = "暂不需要",
                    onClick = onDismiss,
                    color = textTertiary,
                )
            }

            // 崩溃诊断落盘之后必须有出口，否则用户反馈「组件突然没了」时
            // 我们依然拿不到任何现场。只在有记录时出现，避免打扰正常用户。
            if (hasCrashReports) {
                Spacer(modifier = Modifier.height(8.dp))
                AppText(
                    text = "检测到上次运行的崩溃记录。导出后发给客服可帮助定位问题；" +
                        "记录只存在本机，不会自动上传。",
                    role = AppTextRole.micro,
                    color = textTertiary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppTextButton(
                        text = "导出诊断信息",
                        onClick = { shareCrashReport(context) },
                        color = textTertiary,
                    )
                    AppTextButton(
                        text = "清除记录",
                        onClick = {
                            CrashReporter.clear(context)
                            hasCrashReports = false
                        },
                        color = textTertiary,
                    )
                }
            }
        }
    }
}

/**
 * 只在本机把崩溃文本交给系统分享面板，由用户自己决定发给谁。
 * 不上传、不写外部存储、不申请额外权限——这也是隐私政策里的承诺。
 */
private fun shareCrashReport(context: Context) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "句阁 崩溃诊断信息")
        putExtra(Intent.EXTRA_TEXT, CrashReporter.buildReport(context))
    }
    val chooser = Intent.createChooser(send, "导出诊断信息")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // 某些定制系统没有可处理 ACTION_SEND 的组件，不能直接让弹窗崩掉
    runCatching { context.startActivity(chooser) }
}

/** 明细行：左标签右值，值右对齐，长文案换行不挤标签 */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        AppText(text = label, role = AppTextRole.label)
        Spacer(modifier = Modifier.width(12.dp))
        AppText(
            text = value,
            role = AppTextRole.bodyStrong,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
