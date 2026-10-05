package com.juge.app.ui.legal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.juge.app.data.LegalDocs
import com.juge.app.ui.theme.accentLightBlue
import com.juge.app.ui.theme.borderBlue
import com.juge.app.ui.theme.cardBg
import com.juge.app.ui.theme.selectBlue
import com.juge.app.ui.theme.textGray

@Composable
fun PrivacyPolicyDialog(onAgree: () -> Unit, onReject: () -> Unit) {
    // 当前展开的协议全文：null / "user" / "privacy"
    var openDoc by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, borderBlue)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "用户服务与隐私协议提示",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F2937),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = "感谢您使用《句阁》！我们非常重视您的隐私与个人信息保护。在您使用本应用的服务（包括金句展示、自定义背景与桌面组件刷新等）之前，请仔细阅读以下协议：",
                    fontSize = 13.sp,
                    color = Color(0xFF6B7280),
                    lineHeight = 18.sp
                )

                // 协议全文入口：合规要求条款必须能在应用内完整查阅
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "《用户协议》",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentLightBlue,
                        modifier = Modifier.clickable { openDoc = "user" }
                    )
                    Text(
                        text = "《隐私政策》",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentLightBlue,
                        modifier = Modifier.clickable { openDoc = "privacy" }
                    )
                }

                Text(
                    text = "1. 本应用为纯本地小部件应用，您的自定义提醒与偏好配置全部保存在您的设备本地中，我们不会收集或向第三方服务器传输您的任何金句数据。\n2. 自定义背景图通过系统图片选择器挑选，图片仅在您主动选择确认后才会导入本应用，未经您选择不会读取相册中的任何内容。\n\n如您同意以上协议，请点击“同意”开始使用我们的服务。",
                    fontSize = 13.sp,
                    color = Color(0xFF6B7280),
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, borderBlue),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textGray)
                    ) {
                        Text("不同意并退出", fontSize = 13.sp)
                    }

                    Button(
                        onClick = onAgree,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = selectBlue, contentColor = Color.White)
                    ) {
                        Text("同意并继续", fontSize = 13.sp, color = Color.White)
                    }
                }
            }
        }
    }

    openDoc?.let { doc ->
        LegalDocDialog(
            title = LegalDocs.titleOf(doc),
            body = LegalDocs.bodyOf(doc),
            onDismiss = { openDoc = null }
        )
    }
}
