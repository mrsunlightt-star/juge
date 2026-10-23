package com.juge.app.ui.adjust

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.AppSectionCard
import com.juge.app.ui.AppSectionHeader
import com.juge.app.ui.PresetRow
import com.juge.app.ui.SaveOutcome
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.textGray

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
    AppSectionCard(modifier = Modifier.fillMaxWidth()) {
        AppSectionHeader(icon = Icons.Filled.Palette, title = "组件风格")

        // 风格预设区：按分类展示（经典风格 → 萌宠风格 → 明信片风格）
        // 间距统一交给外层 Column 的 spacedBy，这里不再叠加 Spacer/分割线
        // 分类标题**不带尺寸后缀**：一行里各款风格的最佳显示尺寸本就各不相同
        // （见 WidgetStyle.bestDisplaySize），挂一个尺寸会误导。
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.itemGap)) {
            // 1. 经典风格（含免费款「纯色圆角」，其余为会员专属；另一款免费「青年雕塑」在明信片行首）
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
                // 选中态三行统一（与明信片行一致）：3dp 蓝色描边 + 名称变蓝。
                // 不能像以前那样关掉——用户会以为经典/萌宠行没有选中反馈
                isSelected = { preset ->
                    preset.presetId != null && preset.presetId == selectedStyle.presetId
                },
                showSelectionBorder = true,
                nameColor = { isPro, isSelected ->
                    if (isSelected || !isPro) accentBlue else textGray
                },
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
                isSelected = { preset ->
                    preset.presetId != null && preset.presetId == selectedStyle.presetId
                },
                showSelectionBorder = true,
                nameColor = { isPro, isSelected ->
                    if (isSelected || !isPro) accentBlue else textGray
                },
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
