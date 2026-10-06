package com.juge.app.ui.adjust

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juge.app.ReminderWidgetProvider
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.PresetRow
import com.juge.app.ui.SaveOutcome
import com.juge.app.ui.theme.accentBlue

/** 组件风格卡片：经典/萌宠横滑预设 + 明信片（含插画）行。 */
@Composable
internal fun StylePresetCard(
    selectedStyle: WidgetStyle,
    selectedContent: String,
    isActivated: Boolean,
    selectedWidgetId: Int,
    selectedReminderId: Long,
    onSelectPreset: (Int, Long, WidgetStyle) -> Unit,
    onStyleStateChange: (WidgetStyle) -> Unit,
    onStyleChange: (Int, Long, String, WidgetStyle) -> SaveOutcome
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Palette,
                    contentDescription = null,
                    tint = accentBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "组件风格",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentBlue
                )
            }

            // 风格预设区：按分类展示（经典风格 → 萌宠风格 → 明信片风格）
            // 间距统一交给外层 Column 的 spacedBy(10.dp)，这里不再叠加 Spacer/分割线
            // 分类标题尾部统一附加卡片尺寸（如 4×2 / 4×4），取组件**声明的默认尺寸**：
            // 与上方预览区同一来源，用户拉伸过桌面组件时标题才不会与预览比例对不上
            val widgetSizeStr = ReminderWidgetProvider.getWidgetDeclaredSizeString(context, selectedWidgetId)
            val sizeLabel = widgetSizeStr.replace("x", "×").replace("*", "×")
                    // 1. 经典风格（含唯一免费款「纯色圆角」，其余为会员专属）
                    PresetRow(
                        presets = WidgetStyle.CLASSIC_PRESETS,
                        title = "经典风格 · $sizeLabel",
                        onPresetClick = { _, preset ->
                            val newPresetStyle = preset.copy(
                                backgroundOpacity = selectedStyle.backgroundOpacity,
                                cornerRadiusDp = selectedStyle.cornerRadiusDp
                            )
                            previewOrApplyPreset(newPresetStyle, isActivated, onStyleStateChange) {
                                onSelectPreset(selectedWidgetId, selectedReminderId, it)
                            }
                        },
                        showSelectionBorder = false,
                        contentScale = ContentScale.Fit,
                        imageContentDescription = { index, _ -> "Preset Style ${index + 1}" }
                    )
                    // 2. 萌宠风格（位于经典与明信片之间）
                    PresetRow(
                        presets = WidgetStyle.PET_PRESETS,
                        title = "萌宠风格 · 会员专属 · $sizeLabel",
                        onPresetClick = { _, preset ->
                            val newPresetStyle = preset.copy(
                                backgroundOpacity = selectedStyle.backgroundOpacity,
                                cornerRadiusDp = selectedStyle.cornerRadiusDp
                            )
                            previewOrApplyPreset(newPresetStyle, isActivated, onStyleStateChange) {
                                onSelectPreset(selectedWidgetId, selectedReminderId, it)
                            }
                        },
                        showSelectionBorder = false,
                        contentScale = ContentScale.Fit,
                        imageContentDescription = { index, _ -> "Preset Style ${index + 1}" }
                    )

                    // 精选卡片插画
                    // 明信片风格固定为 4×4（竖版上下分割）/ 4×3（书香书架），不随当前组件尺寸变化
                    PostcardPresetsRow(
                        selectedStyle = selectedStyle,
                        selectedContent = selectedContent,
                        isActivated = isActivated,
                        selectedWidgetId = selectedWidgetId,
                        selectedReminderId = selectedReminderId,
                        onSelectPreset = onSelectPreset,
                        onStyleStateChange = onStyleStateChange,
                        onStyleChange = onStyleChange
                    )
        }
    }
}
