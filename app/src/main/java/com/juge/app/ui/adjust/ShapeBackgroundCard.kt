package com.juge.app.ui.adjust

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juge.app.data.ImageScaleMode
import com.juge.app.data.WidgetShape
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.SaveOutcome
import com.juge.app.ui.ThickTrackSlider
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.selectBlue
import java.io.File

/** 物理形状与背景材质卡片：圆角、不透明度、自定义背景图与填充模式。 */
@Composable
internal fun ShapeBackgroundCard(
    isActivated: Boolean,
    selectedStyle: WidgetStyle,
    selectedContent: String,
    selectedWidgetId: Int,
    selectedReminderId: Long,
    onStyleStateChange: (WidgetStyle) -> Unit,
    onStyleChange: (Int, Long, String, WidgetStyle) -> SaveOutcome,
    onPickImage: () -> Unit
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
                    imageVector = Icons.Filled.Wallpaper,
                    contentDescription = null,
                    tint = accentBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "物理形状与背景材质",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentBlue
                )
            }

            // 背景圆角尺寸 Slider (适用于卡片类形状)
            // 无论形状是否可调圆角都常驻渲染，避免切换形状时控件移除导致列表高度突变跳动（“页面自动上滑”）
            val canAdjustCorner = canAdjustCardCorner(selectedStyle.shape)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (canAdjustCorner) {
                        "外框圆角大小: ${selectedStyle.cornerRadiusDp.toInt()} dp"
                    } else {
                        "外框圆角（此形状无需调整）"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canAdjustCorner) Color(0xFF334155) else Color(0xFF94A3B8)
                )
            }
            ThickTrackSlider(
                value = selectedStyle.cornerRadiusDp,
                onValueChange = {
                    val newStyle = selectedStyle.copy(cornerRadiusDp = it)
                    onStyleStateChange(newStyle)
                    onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                },
                valueRange = 0.0f..30.0f,
                steps = 29,
                enabled = canAdjustCorner,
                modifier = Modifier.fillMaxWidth().height(24.dp)
            )

            // 背景不透明度 Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("背景不透明度: ${(selectedStyle.backgroundOpacity * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
            }
            ThickTrackSlider(
                value = selectedStyle.backgroundOpacity,
                onValueChange = {
                    val newStyle = selectedStyle.copy(backgroundOpacity = it)
                    onStyleStateChange(newStyle)
                    onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                },
                valueRange = 0.0f..1.0f,
                modifier = Modifier.fillMaxWidth().height(24.dp)
            )

            HorizontalDivider(color = Color(0xFFF1F5F9))

            // 上传本地背景图
            Text("自定义背景图", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedStyle.backgroundImagePath.isNullOrEmpty()) {
                    Button(
                        onClick = {
                            if (!isActivated) {
                                Toast.makeText(context, "👑 上传自定义背景图为 PRO 专属功能，请先激活！", Toast.LENGTH_SHORT).show()
                            } else {
                                onPickImage()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2E8F0), contentColor = accentBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ 选择本地图片", color = accentBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { onPickImage() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("更换图片", color = Color(0xFF1E293B), fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            try {
                                selectedStyle.backgroundImagePath?.let { File(it).delete() }
                            } catch (e: Exception) {}
                            val newStyle = selectedStyle.copy(backgroundImagePath = null)
                            onStyleStateChange(newStyle)
                            onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("清除背景图", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            // 如果包含背景图，选择平铺模式
            if (!selectedStyle.backgroundImagePath.isNullOrEmpty()) {
                Text("图片填充模式", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ImageScaleMode.values().forEach { mode ->
                        val modeDesc = when (mode) {
                            ImageScaleMode.STRETCH -> "拉伸"
                            ImageScaleMode.CENTER_CROP -> "裁剪"
                            ImageScaleMode.CENTER_FIT -> "完整"
                            ImageScaleMode.CENTER_CROP_TOP -> "铺满"
                            ImageScaleMode.TILE -> "平铺"
                        }
                        FilterChip(
                            selected = selectedStyle.bgImageScaleMode == mode,
                            onClick = {
                                val newStyle = selectedStyle.copy(bgImageScaleMode = mode)
                                onStyleStateChange(newStyle)
                                onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                            },
                            label = { Text(modeDesc, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = Color(0xFF64748B),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFFF1F5F9),
                                selectedContainerColor = selectBlue
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedStyle.bgImageScaleMode == mode,
                                borderColor = Color(0xFFE2E8F0),
                                selectedBorderColor = selectBlue
                            )
                        )
                    }
                }
            }

        }
    }
}

/**
 * 「外框圆角大小」滑条是否可用。
 *
 * 两个理由会让滑条失去落点：
 * 1. **渲染层强制直角**（`ShapeTraits.SQUARE_CORNER_SHAPES`）：整幅插画或素材自带的框角
 *    被圆角裁切会切掉主体，渲染时不读用户的圆角值——滑条能动但画面上没有任何变化；
 * 2. 形状本身没有可调的外框（椭圆/撕纸/书架各自的外轮廓另有来源）。
 *
 * 名单**必须覆盖第 1 类**，`ShapeCornerSliderTest` 会逐形状核对这一点：
 * 这份名单历史上漏登记过三次（竹林熊猫、萌宠乐园、可爱四小只），
 * 表现都是「滑条能动、拖动无反应」。
 */
internal fun canAdjustCardCorner(shape: WidgetShape): Boolean =
    shape != WidgetShape.ELLIPSE &&
        // 书香书架本身就是组件、四周透明，没有外框可调圆角
        shape != WidgetShape.BOOKSHELF &&
        shape != WidgetShape.TORN_PAPER &&
        // 以下全部在渲染层强制直角：整幅插画/自带框角的素材，裁圆角会切掉剑身、
        // 毛绒小树、实物模型、竹框、毛毡框、头像条与蜡笔框
        shape != WidgetShape.GIANT_SWORD &&
        shape != WidgetShape.PLUSH_FOREST &&
        shape != WidgetShape.SUBOR_CONSOLE &&
        shape != WidgetShape.SPRING_DOG &&
        shape != WidgetShape.CRAYON_FRAME &&
        shape != WidgetShape.PANDA_BAMBOO &&
        shape != WidgetShape.PET_PARK &&
        shape != WidgetShape.CUTE_FOUR_KIDS
