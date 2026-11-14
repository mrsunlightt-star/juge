package com.juge.app.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.juge.app.data.TrialManager
import com.juge.app.pay.ProPurchase
import com.juge.app.ui.AppDialog
import com.juge.app.ui.AppDialogSurface
import com.juge.app.ui.AppPrimaryButton
import com.juge.app.ui.AppSectionCard
import com.juge.app.ui.AppText
import com.juge.app.ui.AppTextButton
import com.juge.app.ui.theme.AppTextRole
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
    onOpenPro: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        visible = show,
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AppDialogSurface {
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
        }
    }
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
