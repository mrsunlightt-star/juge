package com.juge.app.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.juge.app.pay.ProPurchase
import com.juge.app.ui.theme.selectBlue

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
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE5E7EB))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PRO 会员", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E293B))
                }
                Text("${ProPurchase.PRICE_TEXT} 一次性买断 · 永久有效", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "解锁全部内置卡片风格，后续新增免费更新",
                        "自定义背景图（本地照片）无限制使用",
                        "多组件独立配置，一次激活永久保留"
                    ).forEach { benefit ->
                        Text("✓ $benefit", fontSize = 12.sp, color = Color(0xFF4B5563))
                    }
                }

                if (isActivated) {
                    Text("✨ 您已是 PRO 会员，感谢支持！", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .shadow(2.dp, RoundedCornerShape(12.dp))
                            .background(selectBlue, RoundedCornerShape(12.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("好的", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("点击下方按钮，将拉起支付宝完成支付。", fontSize = 11.sp, color = Color(0xFF9CA3AF))
                    Button(
                        onClick = onActivate,
                        enabled = !isPaying,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .shadow(4.dp, RoundedCornerShape(12.dp))
                            .background(selectBlue, RoundedCornerShape(12.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isPaying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("支付确认中…", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        } else {
                            Text("支付宝支付 ${ProPurchase.PRICE_TEXT}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    Text("支付成功后自动激活，无需手动操作。", fontSize = 11.sp, color = Color(0xFF9CA3AF))
                    TextButton(onClick = onDismiss, enabled = !isPaying) {
                        Text("暂不需要", color = Color(0xFF9CA3AF), fontSize = 12.sp)
                    }
                }

                // 账号入口常驻两种状态：不登录是常态，所以只做引导，不做拦截
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF1F5F9)))
                TextButton(onClick = onOpenAccount, enabled = !isPaying) {
                    Text(
                        if (accountName.isNullOrBlank()) {
                            "账号登录 · 换手机也能找回 PRO"
                        } else {
                            "账号：$accountName"
                        },
                        color = Color(0xFF0284C7),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
