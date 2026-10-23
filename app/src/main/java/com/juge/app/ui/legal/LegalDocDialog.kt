package com.juge.app.ui.legal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.juge.app.ui.AppDialogTitle
import com.juge.app.ui.AppPrimaryButton
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.borderBlue
import com.juge.app.ui.theme.cardBg
import com.juge.app.ui.theme.textGray
import com.juge.app.ui.theme.textTertiary

/**
 * 《用户协议》/《隐私政策》/《开源许可》全文查看器。
 * 合规要求：条款必须能在应用内完整查阅，且可随时关闭返回。
 */
@Composable
fun LegalDocDialog(title: String, body: String, onDismiss: () -> Unit) {
    Dialog(
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
                .padding(vertical = 12.dp),
            shape = AppShape.dialog,
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, borderBlue)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AppSpacing.cardPadding)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppDialogTitle(title)
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = textTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(
                    color = borderBlue,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = body,
                        style = AppType.body,
                        color = textGray
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
