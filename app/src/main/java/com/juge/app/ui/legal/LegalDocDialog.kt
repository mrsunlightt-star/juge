package com.juge.app.ui.legal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.juge.app.ui.theme.borderBlue
import com.juge.app.ui.theme.cardBg
import com.juge.app.ui.theme.selectBlue
import com.juge.app.ui.theme.textGray

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
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, borderBlue)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F2937)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = textGray,
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
                        fontSize = 13.sp,
                        color = Color(0xFF4B5563),
                        lineHeight = 21.sp
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = selectBlue, contentColor = Color.White)
                ) {
                    Text("我知道了", fontSize = 13.sp, color = Color.White)
                }
            }
        }
    }
}
