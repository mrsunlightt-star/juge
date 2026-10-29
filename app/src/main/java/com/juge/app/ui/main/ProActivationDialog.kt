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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.juge.app.pay.ProPurchase
import com.juge.app.ui.AppDialog
import com.juge.app.ui.AppText
import com.juge.app.ui.AppDialogSurface
import com.juge.app.ui.AppPrimaryButton
import com.juge.app.ui.AppTextButton
import com.juge.app.ui.theme.AppTextRole
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.premiumGoldDeep
import com.juge.app.ui.theme.textTertiary

@Composable
fun ProActivationDialog(
    show: Boolean,
    isActivated: Boolean,
    isPaying: Boolean,
    accountName: String?,
    onActivate: () -> Unit,
    onOpenAccount: () -> Unit,
    onDismiss: () -> Unit
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
                AppText(
                    text = "PRO 会员",
                    role = AppTextRole.hero
                )
            }
            AppText(
                text = "${ProPurchase.PRICE_TEXT} 一次性买断 · 永久有效",
                role = AppTextRole.bodyStrongAccent
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "解锁全部内置卡片风格，后续新增免费更新",
                    "自定义背景图（本地照片）无限制使用",
                    "多组件独立配置，一次激活永久保留"
                ).forEach { benefit ->
                    AppText(
                        text = "✓ $benefit",
                        role = AppTextRole.bodySecondary
                    )
                }
            }

            if (isActivated) {
                AppText(
                    text = "✨ 您已是 PRO 会员，感谢支持！",
                    role = AppTextRole.bodyStrongAccent
                )
                AppPrimaryButton(
                    text = "好的",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                AppText(
                    text = "点击下方按钮，将拉起支付宝完成支付。",
                    role = AppTextRole.micro
                )
                AppPrimaryButton(
                    text = if (isPaying) "支付确认中…" else "支付宝支付 ${ProPurchase.PRICE_TEXT}",
                    onClick = onActivate,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isPaying,
                    loading = isPaying
                )
                AppText(
                    text = "支付成功后自动激活，无需手动操作。",
                    role = AppTextRole.micro
                )
                AppTextButton(
                    text = "暂不需要",
                    onClick = onDismiss,
                    enabled = !isPaying,
                    color = textTertiary
                )
            }

            // 账号入口常驻两种状态：不登录是常态，所以只做引导，不做拦截。
            // 2026-10 改版去掉这里的分割线：分组感由 AppDialogSurface 的 spacedBy 间距承担，
            // 不再画线（`showDivider` 式的 AppDivider 属于结构性分割线，一律去掉）。
            AppTextButton(
                text = if (accountName.isNullOrBlank()) {
                    "账号登录 · 换手机也能找回 PRO"
                } else {
                    "账号：$accountName"
                },
                onClick = onOpenAccount,
                enabled = !isPaying,
                color = accentBlue
            )
        }
    }
}
