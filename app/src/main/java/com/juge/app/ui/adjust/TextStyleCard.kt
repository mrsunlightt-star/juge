package com.juge.app.ui.adjust

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.juge.app.data.WidgetFont
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.AppFilterChip
import com.juge.app.ui.AppSectionCard
import com.juge.app.ui.AppSectionHeader
import com.juge.app.ui.AppSectionLabel
import com.juge.app.ui.CheckChip
import com.juge.app.ui.SaveOutcome
import com.juge.app.ui.ThickTrackSlider
import com.juge.app.ui.appTextFieldColors
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.surfaceSubtle
import com.juge.app.ui.theme.textTertiary

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
    AppSectionCard(modifier = Modifier.fillMaxWidth()) {
        AppSectionHeader(icon = Icons.Filled.EditNote, title = "文本内容与字形定制")

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
                Text(
                    text = "输入桌面小组件上显示的金句",
                    style = AppType.body,
                    color = textTertiary
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
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

        // 字体大小调节
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppSectionLabel("字体大小: ${selectedStyle.fontSizeSp.toInt()} sp")
        }
        ThickTrackSlider(
            value = selectedStyle.fontSizeSp,
            onValueChange = {
                val newStyle = selectedStyle.copy(fontSizeSp = it)
                onStyleStateChange(newStyle)
                onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
            },
            valueRange = 12.0f..48.0f,
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        )

        // 加粗 / 倾斜 / 文字阴影：带勾选框的胶囊选项
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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

        // 文字对齐方式：3+1 双排——两端对齐单独一行，避免整行横向滚动与页签左右滑动手势冲突
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
