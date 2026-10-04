package com.juge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val controlBlue = Color(0xFF42B8EC) // 与主界面 selectBlue 同源的晴空蓝
private val trackGray = Color(0xFFE6E6E6) // 滑条未激活轨道的中性灰（设计稿取色，不带蓝调）
private val chipBgGray = Color(0xFFF1F5F9) // 未选中选项胶囊的底色
private val chipTextInk = Color(0xFF334155) // 未选中选项胶囊的文字色

/** 白色圆形滑块：16dp + 柔和阴影，全部滑条共用同一颗 */
@Composable
private fun SliderThumb(enabled: Boolean) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .shadow(3.dp, CircleShape, clip = false)
            .background(if (enabled) Color.White else Color(0xFFCBD5E1), CircleShape)
    )
}

/**
 * 统一样式滑条：5dp 厚胶囊轨道 + 白色圆形滑块（带柔和阴影）。
 * 主编辑面板、快捷面板全部走这一个实现；自定义轨道后 steps 的刻度点不再绘制。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThickTrackSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true
) {
    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span > 0f) ((value - valueRange.start) / span).coerceIn(0f, 1f) else 0f
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        modifier = modifier,
        thumb = { SliderThumb(enabled) },
        track = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(trackGray)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (enabled) fraction else 0f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(controlBlue)
                )
            }
        }
    )
}

/**
 * 渐变轨道滑条（取色用）：5dp 渐变胶囊轨道 + 同款白色滑块。
 * 轨道本身即取色区域，因此不画激活段，触控热区保持 26dp 高。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradientTrackSlider(
    gradient: List<Color>,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(gradient))
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
            thumb = { SliderThumb(true) },
            track = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                )
            }
        )
    }
}

/**
 * 带勾选框的胶囊选项（加粗/倾斜/文字阴影/对齐方式）：
 * 未选中为浅灰胶囊 + 蓝框空心勾选框，选中变蓝底白字 + 白框对勾。
 */
@Composable
fun CheckChip(
    checked: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (checked) controlBlue else chipBgGray)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (checked) Color.Transparent else Color.White)
                .border(2.dp, if (checked) Color.White else controlBlue, RoundedCornerShape(5.dp))
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
            color = if (checked) Color.White else chipTextInk,
            maxLines = 1
        )
    }
}
