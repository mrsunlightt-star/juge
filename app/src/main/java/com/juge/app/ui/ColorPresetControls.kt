package com.juge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** 预设颜色一栏末尾的「+」按钮，点击后打开取色弹窗 */
@Composable
fun AddColorPresetButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    corner: Dp = 9.dp,
    borderColor: Color = Color(0xFFE2E8F0),
    contentColor: Color = Color(0xFF64748B)
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, borderColor, RoundedCornerShape(corner))
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "添加预设颜色",
            tint = contentColor,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/** 长按自定义色板后的删除确认弹窗 */
@Composable
fun DeleteColorPresetDialog(
    color: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("删除这个预设颜色？", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
        },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(color))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(7.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("删除后该颜色会从预设栏移除，不影响已应用的颜色。", fontSize = 13.sp, color = Color(0xFF64748B))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color(0xFF64748B))
            }
        }
    )
}

/** 取色弹窗：色相 / 饱和度 / 明度三档调节，确认后把颜色交给调用方保存 */
@Composable
fun ColorPickerDialog(
    title: String,
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val initialHsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) }
    }
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1]) }
    var value by remember { mutableStateOf(initialHsv[2]) }
    val picked = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(picked))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            )

            GradientSlider(
                label = "色相",
                gradient = listOf(
                    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                ),
                value = hue,
                valueRange = 0f..360f,
                onValueChange = { hue = it }
            )
            GradientSlider(
                label = "饱和度",
                gradient = listOf(
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0f, value))),
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, value)))
                ),
                value = saturation,
                valueRange = 0f..1f,
                onValueChange = { saturation = it }
            )
            GradientSlider(
                label = "明度",
                gradient = listOf(
                    Color.Black,
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 1f)))
                ),
                value = value,
                valueRange = 0f..1f,
                onValueChange = { value = it }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("取消", color = Color(0xFF64748B))
                }
                Button(
                    onClick = { onConfirm(picked) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2DD4BF),
                        contentColor = Color(0xFF134E4A)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("添加", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun GradientSlider(
    label: String,
    gradient: List<Color>,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(gradient))
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}