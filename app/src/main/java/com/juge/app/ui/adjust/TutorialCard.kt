package com.juge.app.ui.adjust

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.juge.app.ui.AppDivider
import com.juge.app.ui.AppPrimaryButton
import com.juge.app.ui.AppSectionCard
import com.juge.app.ui.findHostActivity
import com.juge.app.ui.theme.AppMotion
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.textGray
import com.juge.app.ui.theme.textWhite

/**
 * 桌面组件添加教程卡片。
 *
 * [isExpanded] 折叠状态由调用方持有（从桌面快捷方式进入时默认展开）；
 * [fromShortcut] 为 true 时附带「回到桌面」按钮，配合一键添加不可用时的引导。
 */
@Composable
internal fun TutorialCard(
    isExpanded: Boolean,
    fromShortcut: Boolean,
    onToggleExpand: () -> Unit
) {
    val context = LocalContext.current
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = AppMotion.standard(),
        label = "tutorialChevron"
    )
    AppSectionCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = AppSpacing.cardPadding,
        animateSize = false,
    ) {
        // 头部与展开区包在同一个 Column 里：AnimatedVisibility 收起时是零高度，
        // 若作为 AppSectionCard 的直接子项，卡片的内层 spacedBy 仍会留出一条空隙。
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(vertical = 15.dp)
                    .semantics {
                        stateDescription = if (isExpanded) "已展开" else "已收起"
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Widgets,
                        contentDescription = null,
                        tint = textWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(AppSpacing.iconGap))
                    Text(
                        text = "桌面组件添加教程",
                        style = AppType.sectionTitle,
                        color = textWhite
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isExpanded) "收起" else "展开",
                        style = AppType.caption,
                        color = accentBlue
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = accentBlue,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(chevronRotation)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = AppMotion.emphasized(), expandFrom = Alignment.Top) +
                    fadeIn(animationSpec = AppMotion.standard()),
                exit = shrinkVertically(animationSpec = AppMotion.emphasized(), shrinkTowards = Alignment.Top) +
                    fadeOut(animationSpec = AppMotion.fast())
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.itemGap)) {
                    Spacer(modifier = Modifier.height(AppSpacing.itemGap))
                    Text(
                        text = "国产手机系统的桌面组件添加入口比较隐蔽，且大多不允许 App 内一键添加到桌面，请在桌面通过系统入口手动添加：",
                        style = AppType.bodyStrong,
                        color = textGray
                    )
                    Text(
                        text = "通用步骤：回到桌面 -> 双指捏合（或长按桌面空白处）-> 点击“添加小部件 / 卡片 / 窗口小工具” -> 找到「句阁 / DeskQuotes」-> 拖动到桌面。",
                        style = AppType.bodyStrong,
                        color = accentBlue
                    )

                    // 从桌面快捷方式引导进入时附带一键回桌面：
                    // 系统组件列表页没有对外入口，回桌面长按是唯一路径，帮用户省掉切回桌面的操作
                    if (fromShortcut) {
                        AppPrimaryButton(
                            text = "回到桌面，长按空白处添加",
                            onClick = { context.findHostActivity()?.moveTaskToBack(true) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(AppSpacing.tight))
                    AppDivider()
                    Spacer(modifier = Modifier.height(AppSpacing.tight))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Lightbulb,
                            contentDescription = null,
                            tint = textWhite,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.iconGap))
                        Text(
                            text = "常见国产手机添加与权限教程：",
                            style = AppType.bodyStrong,
                            color = textWhite
                        )
                    }
                    Text(
                        text = "• 华为 / 鸿蒙系统：在桌面双指捏合 -> 选择“服务卡片” -> 滑动到最下方选择“窗口小工具” -> 找到“句阁 / DeskQuotes”拖动到桌面。\n" +
                            "• 小米 / HyperOS / MIUI：桌面双指捏合 -> 点击“添加小部件” -> 搜索“句阁”或滑动选择安卓原生小部件添加。\n" +
                            "• OPPO / VIVO：桌面双指捏合 -> 点击“卡片 / 插件” -> 选择“句阁”添加。\n" +
                            "• 点击桌面组件没有反应？：请确保在手机的“应用设置 -> 权限管理”中，为本应用开启【后台弹出界面】和【悬浮窗】权限，防止系统拦截从桌面组件打开主界面的跳转。",
                        style = AppType.body,
                        color = textGray
                    )
                }
            }
        }
    }
}
