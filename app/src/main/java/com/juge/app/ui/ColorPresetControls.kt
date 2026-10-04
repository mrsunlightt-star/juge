package com.juge.app.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

private val SelectBlue = Color(0xFF42B8EC)

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

/** 主体四周透明的形状（信纸/撕纸/牛皮/猫咪趴/书架）无法设置背景色时的说明弹窗 */
@Composable
fun BackgroundColorBlockedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("此组件无法设置背景颜色", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
        },
        text = {
            Text(
                "当前风格的主体四周是透明的（如信纸、撕纸、牛皮手账、猫咪趴、书架），" +
                    "设置背景色会在主体外围露出一圈卡片背景，因此该风格不提供背景色调整。\n" +
                    "如需更换底色，可切换到其他组件风格。",
                fontSize = 13.sp,
                color = Color(0xFF64748B),
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("我知道了", color = Color(0xFF0F766E), fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * 取色弹窗：既可直接填写颜色编码，也可用色相 / 饱和度 / 明度三档调节。
 * 两种输入双向联动，确认后把颜色交给调用方保存。
 */
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
    var hexInput by remember { mutableStateOf(toHexColor(initialColor)) }
    val picked = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color(0xFF94A3B8))
                }
            }

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(picked))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("颜色编码", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { raw ->
                        // 只接受 # 与十六进制字符，最长 #RRGGBB
                        val filtered = raw.filter { it == '#' || it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
                        val capped = filtered.take(7)
                        hexInput = capped
                        parseHexColor(capped)?.let { parsed ->
                            val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(parsed, it) }
                            hue = hsv[0]
                            saturation = hsv[1]
                            value = hsv[2]
                        }
                    },
                    singleLine = true,
                    // 十六进制含字母，指定 ASCII 键盘并关闭联想，避免输入法把字母吞进候选词
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        autoCorrect = false,
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF1E293B),
                        focusedContainerColor = Color(0xFFF1F5F9),
                        unfocusedContainerColor = Color(0xFFF1F5F9),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = SelectBlue
                    )
                )
            }

            GradientSlider(
                label = "色相 H",
                valueText = hue.roundToInt().toString(),
                gradient = listOf(
                    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                ),
                value = hue,
                valueRange = 0f..360f,
                onValueChange = { newHue ->
                    hue = newHue
                    hexInput = toHexColor(android.graphics.Color.HSVToColor(floatArrayOf(newHue, saturation, value)))
                }
            )
            GradientSlider(
                label = "饱和度 S",
                valueText = (saturation * 100).roundToInt().toString(),
                gradient = listOf(
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0f, value))),
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, value)))
                ),
                value = saturation,
                valueRange = 0f..1f,
                onValueChange = { newSaturation ->
                    saturation = newSaturation
                    hexInput = toHexColor(android.graphics.Color.HSVToColor(floatArrayOf(hue, newSaturation, value)))
                }
            )
            GradientSlider(
                label = "明度 V",
                valueText = (value * 100).roundToInt().toString(),
                gradient = listOf(
                    Color.Black,
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 1f)))
                ),
                value = value,
                valueRange = 0f..1f,
                onValueChange = { newValue ->
                    value = newValue
                    hexInput = toHexColor(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, newValue)))
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("取消", color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = { onConfirm(picked) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SelectBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("添加", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** 把不透明颜色格式化为 #RRGGBB */
private fun toHexColor(color: Int): String = "#%06X".format(0xFFFFFF and color)

/** 解析 #RGB / #RRGGBB / #AARRGGBB，非法输入返回 null */
private fun parseHexColor(input: String): Int? {
    val digits = input.removePrefix("#").trim()
    if (digits.length != 3 && digits.length != 6 && digits.length != 8) return null
    if (!digits.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
    return try {
        android.graphics.Color.parseColor("#$digits")
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun GradientSlider(
    label: String,
    valueText: String,
    gradient: List<Color>,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
            Text(valueText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
        }
        GradientTrackSlider(
            gradient = gradient,
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}