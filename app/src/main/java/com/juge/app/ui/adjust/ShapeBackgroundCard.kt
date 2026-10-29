package com.juge.app.ui.adjust

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.juge.app.data.ImageScaleMode
import com.juge.app.data.WidgetShape
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.AppDangerButton
import com.juge.app.ui.AppFilterChip
import com.juge.app.ui.AppNumberStepperRow
import com.juge.app.ui.AppSecondaryButton
import com.juge.app.ui.AppSectionCard
import com.juge.app.ui.AppSectionHeader
import com.juge.app.ui.AppSectionLabel
import com.juge.app.ui.AppText
import com.juge.app.ui.SaveOutcome
import com.juge.app.ui.theme.AppTextRole
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.surfaceSubtle
import kotlin.math.roundToInt

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
    val hasImage = !selectedStyle.backgroundImagePath.isNullOrEmpty()
    // 两个步进器共用：先更新内存态（预览立即跟手），再按原有防抖链路落库
    fun applyStyle(newStyle: WidgetStyle) {
        onStyleStateChange(newStyle)
        onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
    }
    AppSectionCard(modifier = Modifier.fillMaxWidth()) {
        AppSectionHeader(title = "物理形状与背景材质")

        // 外框圆角（适用于卡片类形状）。
        // 无论形状是否可调圆角都常驻渲染，避免切换形状时控件移除导致列表高度突变跳动（“页面自动上滑”）：
        // 不可调时右侧显示说明，行高与可调时一致。
        val canAdjustCorner = canAdjustCardCorner(selectedStyle.shape)
        AppNumberStepperRow(
            label = "外框圆角大小",
            value = selectedStyle.cornerRadiusDp,
            valueRange = 0f..30f,
            step = 1f,
            format = { "${it.roundToInt()} dp" },
            snap = { it.roundToInt().toFloat() },
            enabled = canAdjustCorner,
            disabledHint = "此形状无需调整",
            onValueChange = { applyStyle(selectedStyle.copy(cornerRadiusDp = it)) },
        )

        // 背景不透明度：按 5% 一档，既够细也不会从 0 点到 100 点上百次
        AppNumberStepperRow(
            label = "背景不透明度",
            value = selectedStyle.backgroundOpacity,
            valueRange = 0f..1f,
            step = 0.05f,
            format = { "${(it * 100f).roundToInt()}%" },
            snap = { (it * 100f).roundToInt() / 100f },
            onValueChange = { applyStyle(selectedStyle.copy(backgroundOpacity = it)) },
        )

        // 2026-10 改版：此处不再画 1dp 分隔线，分组感由 AppSectionCard 内层 Column 的
        // spacedBy(controlGap) 间距承担（明度层次取代线条）。

        // 上传本地背景图
        AppSectionLabel("自定义背景图")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.itemGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!hasImage) {
                AppSecondaryButton(
                    text = "+ 选择本地图片",
                    onClick = {
                        if (!isActivated) {
                            Toast.makeText(context, "👑 上传自定义背景图为 PRO 专属功能，请先激活！", Toast.LENGTH_SHORT).show()
                        } else {
                            onPickImage()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    contentColor = accentBlue
                )
            } else {
                AppSecondaryButton(
                    text = "更换图片",
                    onClick = { onPickImage() },
                    modifier = Modifier.weight(1f)
                )
                AppDangerButton(
                    text = "清除背景图",
                    onClick = {
                        // 只把路径从样式里摘掉，交给 onStyleChange 的落库流程处理文件：
                        // 1) 保存可能被付费墙拦下（激活被账号同步撤销后必然发生），
                        //    先删文件再被拦会让 DB/组件的样式指向一个已消失的文件；
                        // 2) 落库成功后由 persist 按引用计数决定删不删（别处可能还用着）
                        val newStyle = selectedStyle.copy(backgroundImagePath = null)
                        onStyleStateChange(newStyle)
                        onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 只有存在背景图时才显示填充模式；高度变化由 AppSectionCard 的 animateContentSize 平滑处理
        if (hasImage) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.itemGap)) {
                AppSectionLabel("图片填充模式")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.iconGap)
                ) {
                    ImageScaleMode.values().forEach { mode ->
                        val modeDesc = when (mode) {
                            ImageScaleMode.STRETCH -> "拉伸"
                            ImageScaleMode.CENTER_CROP -> "裁剪"
                            ImageScaleMode.CENTER_FIT -> "完整"
                            ImageScaleMode.CENTER_CROP_TOP -> "铺满"
                            ImageScaleMode.TILE -> "平铺"
                        }
                        AppFilterChip(
                            selected = selectedStyle.bgImageScaleMode == mode,
                            onClick = {
                                val newStyle = selectedStyle.copy(bgImageScaleMode = mode)
                                onStyleStateChange(newStyle)
                                onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, newStyle)
                            },
                            label = modeDesc,
                            textStyle = AppType.caption,
                            containerColor = surfaceSubtle
                        )
                    }
                }
            }
        }
    }
}

/**
 * 「外框圆角大小」步进器是否可用。
 *
 * 两个理由会让步进器失去意义：
 * 1. **渲染层强制直角**（`ShapeTraits.SQUARE_CORNER_SHAPES`）：整幅插画或素材自带的框角
 *    被圆角裁切会切掉主体，渲染时不读用户的圆角值——按钮能点但画面上没有任何变化；
 * 2. 形状本身没有可调的外框（椭圆/撕纸/书架各自的外轮廓另有来源）。
 *
 * 名单**必须覆盖第 1 类**，`ShapeCornerAdjustTest` 会逐形状核对这一点：
 * 这份名单历史上漏登记过三次（竹林熊猫、萌宠乐园、可爱四小只），
 * 表现都是「按钮能点、画面无反应」。
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
        shape != WidgetShape.CUTE_FOUR_KIDS &&
        // 素材里的白色正文框是直角的，强制 0 圆角避免切掉白框底角
        shape != WidgetShape.BOY_ROOM &&
        // 素材里的浅灰卡面与蓝色描边自带圆角，管线再裁会切到描边
        shape != WidgetShape.BLUE_WHALE
