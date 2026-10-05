package com.juge.app.ui.adjust

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.AddColorPresetButton
import com.juge.app.ui.SaveOutcome
import com.juge.app.ui.theme.accentBlue
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
                    imageVector = Icons.Filled.Brush,
                    contentDescription = null,
                    tint = accentBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "色彩与质感艺术",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentBlue
                )
            }

            // 4.1. 字体颜色设置
            Text("字体颜色", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    fontColorPresets.forEach { parsedColor ->
                        val isSelected = selectedStyle.fontColor == parsedColor
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(parsedColor))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) selectBlue else Color(0xFFE2E8F0),
                                    shape = RoundedCornerShape(9.dp)
                                )
                                .combinedClickable(
                                    onClick = {
                                        val newStyle = selectedStyle.copy(fontColor = parsedColor)
                                        onStyleStateChange(newStyle)
                                        onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                                    },
                                    onLongClick = {
                                        onRequestDeleteFontColor(parsedColor)
                                    }
                                )
                        )
                    }
                }
                AddColorPresetButton(onClick = { onRequestAddFontColor() })
            }
            HorizontalDivider(color = Color(0xFFF1F5F9))

            // 4.2. 卡片背景颜色设置
            // 主体四周透明的形状不支持背景色：控件常驻置灰，点击弹窗说明原因
            // （与"外框圆角（此形状无需调整）"保持同一套处理方式）
            val canSetBackgroundColor = WidgetStyle.supportsBackgroundColor(selectedStyle.shape)
            Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.alpha(if (canSetBackgroundColor) 1f else 0.45f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
            Text(
                text = if (canSetBackgroundColor) "小组件背景颜色" else "小组件背景颜色（此形状无法设置）",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (canSetBackgroundColor) Color(0xFF64748B) else Color(0xFF94A3B8)
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    backgroundColorPresets.forEach { colorInt ->
                        val isSelected = selectedStyle.backgroundColor == colorInt
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(colorInt))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) selectBlue else Color(0xFFE2E8F0),
                                    shape = RoundedCornerShape(9.dp)
                                )
                                .combinedClickable(
                                    onClick = {
                                        val newStyle = selectedStyle.copy(backgroundColor = colorInt)
                                        onStyleStateChange(newStyle)
                                        onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                                    },
                                    onLongClick = {
                                        onRequestDeleteBackgroundColor(colorInt)
                                    }
                                )
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
                        .clickable { onBackgroundColorBlocked() }
                )
            }
            }
        }
    }
}
