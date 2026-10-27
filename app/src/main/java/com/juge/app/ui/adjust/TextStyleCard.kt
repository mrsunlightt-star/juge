package com.juge.app.ui.adjust

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.juge.app.data.WidgetFont
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.AppFilterChip
import com.juge.app.ui.AppNumberStepperRow
import com.juge.app.ui.AppSectionCard
import com.juge.app.ui.AppSectionHeader
import com.juge.app.ui.AppSectionLabel
import com.juge.app.ui.AppText
import com.juge.app.ui.CheckChip
import com.juge.app.ui.SaveOutcome
import com.juge.app.ui.appTextFieldColors
import com.juge.app.ui.horizontalControlUsage
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppTextRole
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.surfaceSubtle
import com.juge.app.ui.theme.textTertiary
import kotlin.math.roundToInt

/** 文本内容与字形定制卡片：金句输入、字体、字号、加粗/倾斜/阴影、对齐方式。 */
@Composable
internal fun TextStyleCard(
    selectedStyle: WidgetStyle,
    selectedContent: String,
    selectedWidgetId: Int,
    selectedReminderId: Long,
    onStyleStateChange: (WidgetStyle) -> Unit,
    onContentStateChange: (String) -> Unit,
    onStyleChange: (Int, Long, String, WidgetStyle) -> SaveOutcome
) {
    // 所有步进器共用：先更新内存态（预览立即跟手），再按原有防抖链路落库
    fun applyStyle(newStyle: WidgetStyle) {
        onStyleStateChange(newStyle)
        onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
    }
    AppSectionCard(modifier = Modifier.fillMaxWidth()) {
        AppSectionHeader(title = "文本内容与字形定制")

        // 金句输入（最多 500 字）
        OutlinedTextField(
            value = selectedContent,
            onValueChange = {
                if (it.length <= 500) {
                    onContentStateChange(it)
                    onStyleChange(selectedWidgetId, selectedReminderId, it, selectedStyle)
                }
            },
            placeholder = {
                AppText(
                    text = "输入桌面小组件上显示的金句",
                    role = AppTextRole.bodyTertiary
                )
            },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            shape = AppShape.button,
            textStyle = AppType.body,
            colors = appTextFieldColors()
        )

        // 字体选择（横滚动）
        AppSectionLabel("选择金句字体")
        val fontRowScroll = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 行滚动了就上报“用掉了这次横向位移”，页面手势层据此让位
                .horizontalControlUsage(fontRowScroll)
                .horizontalScroll(fontRowScroll),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.iconGap)
        ) {
            WidgetFont.values().forEach { fnt ->
                AppFilterChip(
                    selected = selectedStyle.font == fnt,
                    onClick = {
                        val newStyle = selectedStyle.copy(font = fnt)
                        onStyleStateChange(newStyle)
                        onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                    },
                    label = fnt.displayName,
                    containerColor = surfaceSubtle
                )
            }
        }

        // 字号 / 行距 / 字间距：数值行 + 步进器（长按连发）。
        // 不再用横向滑条——滑条会和页面级右滑返回抢手势（见 AppNumberStepperRow 注释）。
        AppNumberStepperRow(
            label = "字体大小",
            value = selectedStyle.fontSizeSp,
            valueRange = 12f..48f,
            step = 1f,
            format = { "${it.roundToInt()} sp" },
            snap = { it.roundToInt().toFloat() },
            onValueChange = { applyStyle(selectedStyle.copy(fontSizeSp = it)) },
        )
        AppNumberStepperRow(
            label = "行距",
            // 倍数，1.0 = 字体默认行高；与渲染端 lineSpacingMultiplier 同一口径
            value = selectedStyle.lineSpacingMultiplier,
            valueRange = 0.5f..3f,
            step = 0.1f,
            format = { "%.1fx".format(it) },
            snap = { (it * 10f).roundToInt() / 10f },
            onValueChange = { applyStyle(selectedStyle.copy(lineSpacingMultiplier = it)) },
        )
        AppNumberStepperRow(
            label = "字间距",
            // Paint.letterSpacing 的 em 单位，0 = 跟随字体默认
            value = selectedStyle.letterSpacing,
            valueRange = 0f..0.5f,
            step = 0.05f,
            format = { "%.2f em".format(it) },
            snap = { (it * 100f).roundToInt() / 100f },
            onValueChange = { applyStyle(selectedStyle.copy(letterSpacing = it)) },
        )
        AppText(
            text = "长按 − / + 可连续调整",
            role = AppTextRole.caption,
        )

        // 加粗 / 倾斜 / 文字阴影：带勾选框的胶囊选项
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.itemGap)
        ) {
            CheckChip(
                checked = selectedStyle.fontBold,
                label = "加粗",
                onClick = {
                    val newStyle = selectedStyle.copy(fontBold = !selectedStyle.fontBold)
                    onStyleStateChange(newStyle)
                    onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                }
            )
            CheckChip(
                checked = selectedStyle.fontItalic,
                label = "倾斜",
                onClick = {
                    val newStyle = selectedStyle.copy(fontItalic = !selectedStyle.fontItalic)
                    onStyleStateChange(newStyle)
                    onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                }
            )
            CheckChip(
                checked = selectedStyle.shadow.enabled,
                label = "文字阴影",
                onClick = {
                    // 统一阴影设置：模糊度 1，水平/垂直偏移 0，颜色跟随字体颜色
                    val newStyle = selectedStyle.copy(
                        shadow = selectedStyle.shadow.copy(
                            enabled = !selectedStyle.shadow.enabled,
                            color = selectedStyle.fontColor,
                            radius = 1f,
                            dx = 0f,
                            dy = 0f
                        )
                    )
                    onStyleStateChange(newStyle)
                    onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                }
            )
        }

        // 文字对齐方式：3+1 双排——四个胶囊在窄屏上比单行更易点，两端对齐单独成行
        AppSectionLabel("对齐方式")
        val alignments = listOf(
            "LEFT" to "左对齐",
            "CENTER" to "居中对齐",
            "RIGHT" to "右对齐",
            "JUSTIFY" to "两端对齐"
        )
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.iconGap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.iconGap)) {
                alignments.take(3).forEach { (alignKey, label) ->
                    CheckChip(
                        checked = selectedStyle.textAlign.uppercase(java.util.Locale.ROOT) == alignKey,
                        label = label,
                        onClick = {
                            val newStyle = selectedStyle.copy(textAlign = alignKey)
                            onStyleStateChange(newStyle)
                            onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                        }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.iconGap)) {
                alignments.drop(3).forEach { (alignKey, label) ->
                    CheckChip(
                        checked = selectedStyle.textAlign.uppercase(java.util.Locale.ROOT) == alignKey,
                        label = label,
                        onClick = {
                            val newStyle = selectedStyle.copy(textAlign = alignKey)
                            onStyleStateChange(newStyle)
                            onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                        }
                    )
                }
            }
        }
    }
}
