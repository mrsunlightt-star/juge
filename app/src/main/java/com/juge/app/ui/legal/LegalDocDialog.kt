package com.juge.app.ui.legal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.juge.app.ui.AppDialog
import com.juge.app.ui.AppText
import com.juge.app.ui.AppDialogHeader
import com.juge.app.ui.AppPrimaryButton
import com.juge.app.ui.theme.AppTextRole
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppShadows
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.cardBg

/**
 * 《用户协议》/《隐私政策》/《字体许可》全文查看器。
 * 合规要求：条款必须能在应用内完整查阅，且可随时关闭返回。
 */
@Composable
fun LegalDocDialog(show: Boolean, title: String, body: String, onDismiss: () -> Unit) {
    AppDialog(
        visible = show,
        onDismissRequest = onDismiss,
        // 与首启弹窗同一套宽度处理：平台默认宽度在手机上只有约 280dp，
        // 「连读几十行」的协议全文挤在这点宽度里每行十几个字，非常难读。
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp)
                .fillMaxHeight(0.85f)
                .padding(vertical = 12.dp)
                // 不画 1dp 描边（2026-10 改版）：卡片边界改由「cardBg 比压暗背景亮一档」
                // 加 AppShadows.dialog 弥散阴影表达，与其它弹窗一致。
                .shadow(
                    elevation = AppShadows.dialog,
                    shape = AppShape.dialog,
                    clip = false,
                    ambientColor = AppShadows.ambientShadow,
                    spotColor = AppShadows.spotShadow,
                ),
            shape = AppShape.dialog,
            colors = CardDefaults.cardColors(containerColor = cardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppSpacing.cardPadding)
            ) {
                // 与取色 / 分类管理等高内容子页面同一颗头部（标题 + 右侧 X）
                AppDialogHeader(title = title, onClose = onDismiss)

                // 标题与正文之间不再画分割线：改由 12dp 间距分组（结构性分割线一律去掉）
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    AppText(
                        text = body,
                        role = AppTextRole.bodySecondary
                    )
                }

                AppPrimaryButton(
                    text = "我知道了",
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }
        }
    }
}
