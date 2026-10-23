package com.juge.app.ui.adjust

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.AddColorPresetButton
import com.juge.app.ui.AppDivider
import com.juge.app.ui.AppSectionCard
import com.juge.app.ui.AppSectionHeader
import com.juge.app.ui.AppSectionLabel
import com.juge.app.ui.SaveOutcome
import com.juge.app.ui.theme.AppMotion
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.borderBlue
import com.juge.app.ui.theme.selectBlue

/**
 * 色彩与质感艺术卡片：字体颜色 / 背景颜色预设。
 *
 * 预设的增删状态由调用方持有，这里只负责渲染与请求回调。
 */
@Composable
internal fun ColorTextureCard(
    selectedStyle: WidgetStyle,
    selectedContent: String,
    selectedWidgetId: Int,
    selectedReminderId: Long,
    fontColorPresets: List<Int>,
    backgroundColorPresets: List<Int>,
    onStyleStateChange: (WidgetStyle) -> Unit,
    onStyleChange: (Int, Long, String, WidgetStyle) -> SaveOutcome,
    onRequestAddFontColor: () -> Unit,
    onRequestAddBackgroundColor: () -> Unit,
    onRequestDeleteFontColor: (Int) -> Unit,
    onRequestDeleteBackgroundColor: (Int) -> Unit,
    onBackgroundColorBlocked: () -> Unit
) {
    AppSectionCard(modifier = Modifier.fillMaxWidth()) {
        AppSectionHeader(icon = Icons.Filled.Brush, title = "色彩与质感艺术")

        // 4.1. 字体颜色设置
        AppSectionLabel("字体颜色")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                fontColorPresets.forEach { parsedColor ->
                    ColorSwatch(
                        color = parsedColor,
                        selected = selectedStyle.fontColor == parsedColor,
                        onClick = {
                            val newStyle = selectedStyle.copy(fontColor = parsedColor)
                            onStyleStateChange(newStyle)
                            onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                        },
                        onLongClick = { onRequestDeleteFontColor(parsedColor) }
                    )
                }
            }
            AddColorPresetButton(onClick = { onRequestAddFontColor() })
        }
        AppDivider()

        // 4.2. 卡片背景颜色设置
        // 主体四周透明的形状不支持背景色：控件常驻置灰，点击弹窗说明原因
        // （与"外框圆角（此形状无需调整）"保持同一套处理方式）
        val canSetBackgroundColor = WidgetStyle.supportsBackgroundColor(selectedStyle.shape)
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.alpha(if (canSetBackgroundColor) 1f else 0.45f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.itemGap)
            ) {
                AppSectionLabel(
                    if (canSetBackgroundColor) {
                        "小组件背景颜色"
                    } else {
                        "小组件背景颜色（此形状无法设置）"
                    }
                )

                // 预设背景色彩
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        backgroundColorPresets.forEach { colorInt ->
                            ColorSwatch(
                                color = colorInt,
                                selected = selectedStyle.backgroundColor == colorInt,
                                onClick = {
                                    val newStyle = selectedStyle.copy(backgroundColor = colorInt)
                                    onStyleStateChange(newStyle)
                                    onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                                },
                                onLongClick = { onRequestDeleteBackgroundColor(colorInt) }
                            )
                        }
                    }
                    AddColorPresetButton(onClick = { onRequestAddBackgroundColor() })
                }
            }
            if (!canSetBackgroundColor) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .combinedClickable(onClick = onBackgroundColorBlocked)
                )
            }
        }
    }
}

/** 色板方块：选中态用蓝色描边，宽度与颜色都平滑过渡 */
@Composable
private fun ColorSwatch(
    color: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 3.dp else 1.dp,
        animationSpec = AppMotion.standard(),
        label = "swatchBorderWidth"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) selectBlue else borderBlue,
        animationSpec = AppMotion.color,
        label = "swatchBorderColor"
    )
    // 视觉仍是 34dp 色块，外层 48dp 补足触控热区；相邻色块的热区不重叠
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(AppShape.chip)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(AppShape.chip)
                .background(Color(color))
                .border(width = borderWidth, color = borderColor, shape = AppShape.chip)
        )
    }
}
