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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.accentLightBlue
import com.juge.app.ui.theme.borderBlue
import com.juge.app.ui.theme.cardBg
import com.juge.app.ui.theme.dangerRed
import com.juge.app.ui.theme.selectBlue
import com.juge.app.ui.theme.surfaceMuted
import com.juge.app.ui.theme.surfaceSubtle
import com.juge.app.ui.theme.textGray
import com.juge.app.ui.theme.textTertiary
import kotlin.math.roundToInt

/** 预设颜色一栏末尾的「+」按钮，点击后打开取色弹窗。视觉 34dp，热区 48dp。 */
@Composable
fun AddColorPresetButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    corner: Dp = 10.dp,
    borderColor: Color = borderBlue,
    contentColor: Color = textGray
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(corner))
                .background(surfaceMuted)
                .border(1.dp, borderColor, RoundedCornerShape(corner))
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(size * 0.5f)
            )
        }
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
        shape = AppShape.dialog,
        containerColor = cardBg,
        title = { AppDialogTitle("删除这个预设颜色？") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(AppShape.badge)
                        .background(Color(color))
                        .border(1.dp, borderBlue, AppShape.badge)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "删除后该颜色会从预设栏移除，不影响已应用的颜色。",
                    style = AppType.body,
                    color = textGray
                )
            }
        },
        confirmButton = {
            AppTextButton(text = "删除", onClick = onConfirm, color = dangerRed)
        },
        dismissButton = {
            AppTextButton(text = "取消", onClick = onDismiss)
        }
    )
}

/** 主体四周透明的形状（信纸/撕纸/牛皮/猫咪趴/书架）无法设置背景色时的说明弹窗 */
@Composable
fun BackgroundColorBlockedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = AppShape.dialog,
        containerColor = cardBg,
        title = { AppDialogTitle("此组件无法设置背景颜色") },
        text = {
            Text(
                text = "当前风格的主体四周是透明的（如信纸、撕纸、牛皮手账、猫咪趴、书架），" +
                    "设置背景色会在主体外围露出一圈卡片背景，因此该风格不提供背景色调整。\n" +
                    "如需更换底色，可切换到其他组件风格。",
                style = AppType.body,
                color = textGray
            )
        },
        confirmButton = {
            AppTextButton(text = "我知道了", onClick = onDismiss, color = accentBlue)
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
                .clip(AppShape.dialog)
                .background(cardBg)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.controlGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppDialogTitle(text = title, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = textTertiary)
                }
            }

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(AppShape.card)
                    .background(Color(picked))
                    .border(1.dp, borderBlue, AppShape.card)
            )

            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.iconGap)) {
                AppSectionLabel("颜色编码")
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
                    shape = AppShape.button,
                    textStyle = AppType.body,
                    modifier = Modifier.fillMaxWidth(),
                    colors = appTextFieldColors(container = surfaceSubtle)
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
                AppSecondaryButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                AppPrimaryButton(
                    text = "添加",
                    onClick = { onConfirm(picked) },
                    modifier = Modifier.weight(1f),
                    containerColor = selectBlue
                )
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
            Text(label, style = AppType.label, color = textGray)
            Text(valueText, style = AppType.label, color = accentLightBlue)
        }
        GradientTrackSlider(
            gradient = gradient,
            value = value,
            valueRange = valueRange,
            onValueChange = onValueChange
        )
    }
}
