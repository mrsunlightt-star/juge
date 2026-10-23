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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.juge.app.pay.ProPurchase
import com.juge.app.ui.AppDialogSurface
import com.juge.app.ui.AppDivider
import com.juge.app.ui.AppPrimaryButton
import com.juge.app.ui.AppTextButton
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.accentLightBlue
import com.juge.app.ui.theme.premiumGoldDeep
import com.juge.app.ui.theme.textGray
import com.juge.app.ui.theme.textPrimary
import com.juge.app.ui.theme.textTertiary

@Composable
fun ProActivationDialog(
    isActivated: Boolean,
    isPaying: Boolean,
    accountName: String?,
    onActivate: () -> Unit,
    onOpenAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AppDialogSurface {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = premiumGoldDeep,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PRO 会员",
                    style = AppType.hero,
                    color = textPrimary
                )
            }
            Text(
                text = "${ProPurchase.PRICE_TEXT} 一次性买断 · 永久有效",
                style = AppType.bodyStrong,
                color = accentBlue
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "解锁全部内置卡片风格，后续新增免费更新",
                    "自定义背景图（本地照片）无限制使用",
                    "多组件独立配置，一次激活永久保留"
                ).forEach { benefit ->
                    Text(
                        text = "✓ $benefit",
                        style = AppType.body,
                        color = textGray
                    )
                }
            }

            if (isActivated) {
                Text(
                    text = "✨ 您已是 PRO 会员，感谢支持！",
                    style = AppType.bodyStrong,
                    color = accentBlue
                )
                AppPrimaryButton(
                    text = "好的",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = "点击下方按钮，将拉起支付宝完成支付。",
                    style = AppType.micro,
                    color = textTertiary
                )
                AppPrimaryButton(
                    text = if (isPaying) "支付确认中…" else "支付宝支付 ${ProPurchase.PRICE_TEXT}",
                    onClick = onActivate,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isPaying,
                    loading = isPaying
                )
                Text(
                    text = "支付成功后自动激活，无需手动操作。",
                    style = AppType.micro,
                    color = textTertiary
                )
                AppTextButton(
                    text = "暂不需要",
                    onClick = onDismiss,
                    enabled = !isPaying,
                    color = textTertiary
                )
            }

            // 账号入口常驻两种状态：不登录是常态，所以只做引导，不做拦截
            AppDivider()
            AppTextButton(
                text = if (accountName.isNullOrBlank()) {
                    "账号登录 · 换手机也能找回 PRO"
                } else {
                    "账号：$accountName"
                },
                onClick = onOpenAccount,
                enabled = !isPaying,
                color = accentLightBlue
            )
        }
    }
}
