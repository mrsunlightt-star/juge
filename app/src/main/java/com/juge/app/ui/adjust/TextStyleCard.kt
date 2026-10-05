package com.juge.app.ui.adjust

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juge.app.data.WidgetFont
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.CheckChip
import com.juge.app.ui.SaveOutcome
import com.juge.app.ui.ThickTrackSlider
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.selectBlue

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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.EditNote,
                    contentDescription = null,
                    tint = accentBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "文本内容与字形定制",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentBlue
                )
            }

            // 文本框输入（最多 500 字）
            OutlinedTextField(
                value = selectedContent,
                onValueChange = {
                    if (it.length <= 500) {
                        onContentStateChange(it)
                        onStyleChange(selectedWidgetId, selectedReminderId, it, selectedStyle)
                    }
                },
                placeholder = { Text("输入桌面小组件上显示的金句", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                modifier = Modifier
                    .fillMaxWidth(),
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF1E293B),
                    unfocusedTextColor = Color(0xFF1E293B),
                    focusedBorderColor = Color(0xFF0F766E),
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    cursorColor = Color(0xFF0F766E)
                )
            )

            // 字体选择 (横滚动)
            Text("选择金句字体", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WidgetFont.values().forEach { fnt ->
                    FilterChip(
                        selected = selectedStyle.font == fnt,
                        onClick = {
                            val newStyle = selectedStyle.copy(font = fnt)
                            onStyleStateChange(newStyle)
                            onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                        },
                        label = { Text(fnt.displayName, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = Color(0xFF64748B),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFFF1F5F9),
                            selectedContainerColor = selectBlue
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedStyle.font == fnt,
                            borderColor = Color(0xFFE2E8F0),
                            selectedBorderColor = selectBlue
                        )
                    )
                }
            }

            // 字体大小调节 Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("字体大小: ${selectedStyle.fontSizeSp.toInt()} sp", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
            }
            ThickTrackSlider(
                value = selectedStyle.fontSizeSp,
                onValueChange = {
                    val newStyle = selectedStyle.copy(fontSizeSp = it)
                    onStyleStateChange(newStyle)
                    onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                },
                valueRange = 12.0f..48.0f,
                modifier = Modifier.fillMaxWidth().height(24.dp)
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
            Text("对齐方式", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
            val alignments = listOf(
                "LEFT" to "左对齐",
                "CENTER" to "居中对齐",
                "RIGHT" to "右对齐",
                "JUSTIFY" to "两端对齐"
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
}
