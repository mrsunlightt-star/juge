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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            // 分类标题**不带尺寸后缀**：一行里各款风格的最佳显示尺寸本就各不相同
            // （见 WidgetStyle.bestDisplaySize），挂一个尺寸会误导。
                    // 1. 经典风格（含唯一免费款「纯色圆角」，其余为会员专属）
                    PresetRow(
                        presets = WidgetStyle.CLASSIC_PRESETS,
                        title = "经典风格",
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
                        title = "萌宠风格 · 会员专属",
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

                    // 3. 明信片风格（精选卡片插画）
                    // 标题由行自己渲染（与经典/萌宠行同款），**不再写在这里**——
                    // 2e9f34b 拆分 AdjustTabContent 时，正是因为标题散在调用方，整行被搬掉后标题也一起丢了。
                    // 不带尺寸后缀：这一行混着各自独立的固定尺寸，挂一个尺寸会误导（见 PostcardPresetsRow 注释）；
                    // 也不写「会员专属」——行首的「青年雕塑」是免费款，免费/付费由条目自己的角标标（同经典行）
                    PostcardPresetsRow(
                        title = "明信片风格",
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
