package com.juge.app.ui.legal

import com.juge.app.ui.AppDialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.juge.app.data.LegalDocs
import com.juge.app.ui.AppFormGroup
import com.juge.app.ui.AppScrollbar
import com.juge.app.ui.AppFormRow
import com.juge.app.ui.AppText
import com.juge.app.ui.AppDialogTitle
import com.juge.app.ui.AppPrimaryButton
import com.juge.app.ui.AppSecondaryButton
import com.juge.app.ui.theme.AppTextRole
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppShadows
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.cardBg
import com.juge.app.ui.theme.textGray
import com.juge.app.ui.theme.textPrimary
import com.juge.app.ui.theme.textTertiary
import com.juge.app.ui.theme.rememberAppHaptics

/** 弹窗里可点开的三份文件；[key] 与 [LegalDocs] 的取文对应 */
private data class LegalEntry(val key: String, val label: String, val icon: ImageVector)

private val LEGAL_ENTRIES = listOf(
    LegalEntry("user", "《用户协议》", Icons.AutoMirrored.Filled.MenuBook),
    LegalEntry("privacy", "《隐私政策》", Icons.Filled.PrivacyTip),
    LegalEntry("license", "《字体许可》", Icons.Filled.Code),
)

/**
 * 正文摘要逐条成列。
 *
 * 之前是整段文字里塞换行：折行后的续行顶到最左边、和序号错开，五条读下来像一坨。
 * 拆成列表后序号独占一条对齐栏，续行自然缩进。
 *
 * 内容必须如实覆盖**全部**个人信息处理场景，改动见 [PrivacyPolicyDialog] 的说明。
 */
private val SUMMARY_ITEMS = listOf(
    "您的金句内容、分类、样式与背景图片全部保存在设备本地，本应用不收集、不上传，也未接入广告或统计分析 SDK。",
    "自定义背景图通过系统图片选择器挑选，仅在您主动选择确认后才会导入，未经选择不会读取相册中的任何内容。",
    "账号（可选）：仅用于换机找回会员权益。注册/登录会通过 HTTPS 向我们的服务器传输您设置的用户名与口令，口令加盐派生后保存，不保存明文。",
    "支付由支付宝完成，本应用不接触您的银行卡号、支付密码或支付宝账号；支付宝 SDK 仅在您主动点击支付时启动，详见《隐私政策》第六节第 3 项。",
    "撤回同意：随时在系统设置中清除应用数据或卸载本应用即可，撤回后需重新同意才能继续使用。",
)

/**
 * 首启隐私弹窗：先告知、后同意。
 *
 * 正文摘要必须如实覆盖**全部**个人信息处理场景（本地存储 / 相册选择 / 可选账号 /
 * 支付宝支付 / 撤回同意），不能只说「纯本地」——账号与支付确实会把信息交给
 * 我们的服务器与支付宝 SDK，摘要里不写就等于没告知。改代码里的数据实践时，
 * 这里与 [LegalDocs.PRIVACY_POLICY] 必须同步更新。
 */
@Composable
fun PrivacyPolicyDialog(show: Boolean, onAgree: () -> Unit, onReject: () -> Unit) {
    // 当前展开的协议全文：null / "user" / "privacy"
    var openDoc by remember { mutableStateOf<String?>(null) }
    val haptics = rememberAppHaptics()

    // 弹窗整体瘦身：标题与按钮固定，中间内容（简介 + 协议入口 + 摘要 + 备案信息）
    // 合并成一个限高滚动区，滚动上限约为屏幕 45%——全文可完整读（合规要求），
    // 但弹窗不再撑到接近全屏。用比例而不是写死 dp：小屏叠上系统放大字号也不会
    // 把按钮顶出屏幕，按钮恰恰是这个弹窗唯一不能藏起来的东西。
    val contentMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.45f).dp
    val scrollState = rememberScrollState()

    AppDialog(
        visible = show,
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            // 关掉平台默认宽度：Compose 的 Dialog 默认把内容锁在约 280dp（传统弹窗宽度），
            // 手机上就成了一条瘦长条——正文挤成 15 个字一行、按钮文字「不同意并退出」被折成两行。
            // 屏幕宽度本来就富裕，改成自己按 92% 撑开；上限 420dp 是给平板/折叠屏兜底，
            // 免得在大屏上拉成一整条横幅。
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp)
                .padding(vertical = 16.dp)
                .shadow(
                    elevation = AppShadows.dialog,
                    shape = AppShape.dialog,
                    clip = false,
                    ambientColor = AppShadows.ambientShadow,
                    spotColor = AppShadows.spotShadow,
                ),
            shape = AppShape.dialog,
            colors = CardDefaults.cardColors(containerColor = cardBg),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppDialogTitle(
                    text = "用户服务与隐私协议提示",
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 中间滚动区：简介、协议入口、摘要、备案信息一起滚。
                // 滚动条是独立覆盖层（AppScrollbar）：不随内容滚动、不压文字，
                // 右缘 24dp 触摸带可直接按住拖动滚动内容。
                Box(modifier = Modifier.heightIn(max = contentMaxHeight)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(end = 14.dp)
                    ) {
                    AppText(
                        text = "感谢您使用《句阁》！我们非常重视您的隐私与个人信息保护。在您使用本应用的服务（包括金句展示、自定义背景与桌面组件刷新等）之前，请仔细阅读以下协议：",
                        role = AppTextRole.bodySecondary
                    )

                // 摘要逐条成列，序号独占一条对齐栏
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SUMMARY_ITEMS.forEachIndexed { index, item ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            AppText(
                                text = "${index + 1}.",
                                role = AppTextRole.bodySecondary,
                                modifier = Modifier.width(20.dp)
                            )
                            AppText(
                                text = item,
                                role = AppTextRole.bodySecondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                    // 协议全文入口：合规要求条款必须能在应用内完整查阅。
                // 竖排成清单而不是一行并排三个——并排时三个书名号挤在一起像小标题，
                // 看不出可以点开；整行可点 + 右侧箭头是「还有下一层」的通用写法。
                // iOS 分组列表：图标 + 标签 + 右箭头，行分隔线由 AppFormRow 画
                AppFormGroup(modifier = Modifier.padding(top = 16.dp)) {
                    LEGAL_ENTRIES.forEachIndexed { index, entry ->
                        AppFormRow(
                            label = entry.label,
                            onClick = { openDoc = entry.key },
                        ) {
                            Icon(
                                imageVector = entry.icon,
                                contentDescription = null,
                                tint = accentBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(AppSpacing.iconGap))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = textTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 三行分开写：挤成一段自动折行时，备案号会被从中间断开
                    AppText(
                        text = "运营主体：${LegalDocs.ENTITY}\n" +
                            "备案号：${LegalDocs.ICP_LICENSE}\n" +
                            "如有疑问或需注销账号，请联系 ${LegalDocs.EMAIL}",
                        role = AppTextRole.micro,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 16.dp)
                    )
                }
                    AppScrollbar(
                        state = scrollState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(top = 4.dp, bottom = 4.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppSecondaryButton(
                        text = "不同意并退出",
                        onClick = onReject,
                        modifier = Modifier.weight(1f)
                    )

                    AppPrimaryButton(
                        text = "同意并继续",
                        onClick = onAgree,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    val doc = openDoc
    LegalDocDialog(
        show = doc != null,
        title = if (doc != null) LegalDocs.titleOf(doc) else "",
        body = if (doc != null) LegalDocs.bodyOf(doc) else "",
        onDismiss = { openDoc = null }
    )
}
