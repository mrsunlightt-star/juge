package com.juge.app.ui.legal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

/**
 * 首启隐私弹窗：先告知、后同意。
 *
 * 正文摘要必须如实覆盖**全部**个人信息处理场景（本地存储 / 相册选择 / 可选账号 /
 * 支付宝支付 / 撤回同意），不能只说「纯本地」——账号与支付确实会把信息交给
 * 我们的服务器与支付宝 SDK，摘要里不写就等于没告知。改代码里的数据实践时，
 * 这里与 [LegalDocs.PRIVACY_POLICY] 必须同步更新。
 */
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

                // 摘要与全文入口在小屏或系统放大字号下可能超高，做成可滚动区域，
                // 保证「同意 / 不同意」两个按钮始终留在屏内
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
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
                        Text(
                            text = "《开源许可》",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentLightBlue,
                            modifier = Modifier.clickable { openDoc = "license" }
                        )
                    }

                    Text(
                        text = "1. 您的金句内容、分类、样式与背景图片全部保存在设备本地，本应用不收集、不上传，也未接入广告或统计分析 SDK。\n" +
                            "2. 自定义背景图通过系统图片选择器挑选，仅在您主动选择确认后才会导入，未经选择不会读取相册中的任何内容。\n" +
                            "3. 账号（可选）：仅用于换机找回会员权益。注册/登录会通过 HTTPS 向我们的服务器传输您设置的用户名与口令，口令加盐派生后保存，不保存明文。\n" +
                            "4. 支付由支付宝完成，本应用不接触您的银行卡号、支付密码或支付宝账号；支付宝 SDK 仅在您主动点击支付时启动，详见《隐私政策》第六节第 3 项。\n" +
                            "5. 撤回同意：随时在系统设置中清除应用数据或卸载本应用即可，撤回后需重新同意才能继续使用。",
                        fontSize = 13.sp,
                        color = Color(0xFF6B7280),
                        lineHeight = 18.sp
                    )
                }

                Text(
                    text = "运营主体：${LegalDocs.ENTITY} ｜ 备案号：${LegalDocs.ICP_LICENSE}\n" +
                        "如有疑问或需注销账号，请联系 ${LegalDocs.EMAIL}",
                    fontSize = 11.sp,
                    color = Color(0xFF9CA3AF),
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 16.dp)
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
