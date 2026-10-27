package com.juge.app.ui.adjust

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.juge.app.WidgetCanvasRenderer
import com.juge.app.data.WidgetShape
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.AppText
import com.juge.app.ui.PresetThumbnailBox
import com.juge.app.ui.SaveOutcome
import com.juge.app.ui.horizontalControlUsage
import com.juge.app.ui.presetThumbnailSize
import com.juge.app.ui.theme.AppTextRole
import com.juge.app.ui.theme.AppMotion
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.borderBlue
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.selectBlue
import com.juge.app.ui.theme.surfaceSubtle
import com.juge.app.ui.theme.textGray
import com.juge.app.ui.theme.textSecondary
import com.juge.app.ui.theme.rememberAppHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 明信片风格行：首位整幅贴图、代码绘制的大卡、按真实渲染的场景卡、插画素材。
 *
 * 四类缩略图共用 [presetThumbnailSize]：宽度统一、高度按各自的最佳显示尺寸等比，
 * 4×4 的明信片得到正方形缩略图，不会再被压成扁卡。名称以独立文本显示在缩略图下方。
 */
@Composable
internal fun PostcardPresetsRow(
    selectedStyle: WidgetStyle,
    selectedContent: String,
    isActivated: Boolean,
    selectedWidgetId: Int,
    selectedReminderId: Long,
    onSelectPreset: (Int, Long, WidgetStyle) -> Unit,
    onStyleStateChange: (WidgetStyle) -> Unit,
    onStyleChange: (Int, Long, String, WidgetStyle) -> SaveOutcome,
    title: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    Column(modifier = modifier) {
        // 行标题与经典/萌宠行同款（13sp / Bold / #64748B，见 PresetRow 的 title 分支）。
        //
        // ⚠️ 行标题**不带**「· 4×4」那类尺寸后缀：每款风格的最佳显示尺寸本就各不相同
        // （见 WidgetStyle.bestDisplaySize，预览区按它出图），挂一个尺寸在行上只会误导。
        // 历史文案原是「明信片风格 · 会员专属 · 4×4 / 4×3」——
        // 从 2e9f34b 拆分 AdjustTabContent 起整行标题就丢了，行标题因为搬进了
        // PresetRow(title=) 才活下来（见 StylePresetCard）。
        if (title != null) {
            AppText(title, role = AppTextRole.sectionTitleNeutral)
            Spacer(modifier = Modifier.height(AppSpacing.itemGap))
        }
        Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 行滚动了就上报“用掉了这次横向位移”，页面手势层据此让位
                    .horizontalControlUsage(scrollState)
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.itemGap),
                // 所有条目共用同一外框，名称自然落在同一条基线上
                verticalAlignment = Alignment.CenterVertically
            ) {
            // 首位：整幅贴图预设（按产品要求排在明信片行第一个）。
            // 缩略图按组件真实渲染——整幅铺贴 + 正文安全区的观感，裁原图看不出来。
            WidgetStyle.POSTCARD_LEADING_PRESETS.forEach { (presetName, preset) ->
                RenderedPresetItem(
                    presetName = presetName,
                    preset = preset,
                    selectedStyle = selectedStyle,
                    isSelected = selectedStyle.presetId == preset.presetId,
                    isActivated = isActivated,
                    onStyleStateChange = onStyleStateChange,
                    onApply = { onSelectPreset(selectedWidgetId, selectedReminderId, it) }
                )
            }

            // 代码绘制的大卡（蓝色画报、书香书架）排在插图素材之前：
            // 组件内容在渲染时按画布自适应重排，避免一行内宽窄不一
            WidgetStyle.POSTCARD_CODE_PRESETS.forEach { (presetName, preset) ->
                val isCodeSelected = selectedStyle.shape == preset.shape &&
                    selectedStyle.presetImageResName == null &&
                    selectedStyle.backgroundImagePath.isNullOrEmpty()
                RenderedPresetItem(
                    presetName = presetName,
                    preset = preset,
                    selectedStyle = selectedStyle,
                    isSelected = isCodeSelected,
                    isActivated = isActivated,
                    onStyleStateChange = onStyleStateChange,
                    onApply = { onSelectPreset(selectedWidgetId, selectedReminderId, it) }
                )
            }

            // 明信片行的场景卡 / 抠图明信片：缩略图按组件真实渲染——
            // 若像插图那样裁原图，几张会长得一模一样、分不出方案。
            WidgetStyle.POSTCARD_RENDERED_PRESETS.forEach { (presetName, preset) ->
                RenderedPresetItem(
                    presetName = presetName,
                    preset = preset,
                    selectedStyle = selectedStyle,
                    isSelected = selectedStyle.presetId == preset.presetId,
                    isActivated = isActivated,
                    onStyleStateChange = onStyleStateChange,
                    onApply = { onSelectPreset(selectedWidgetId, selectedReminderId, it) }
                )
            }

            IllustrationPresetItems(
                selectedStyle = selectedStyle,
                selectedContent = selectedContent,
                isActivated = isActivated,
                selectedWidgetId = selectedWidgetId,
                selectedReminderId = selectedReminderId,
                onStyleStateChange = onStyleStateChange,
                onStyleChange = onStyleChange
            )
        }
    }
}

/**
 * 按组件真实渲染的预设条目：缩略图由 [WidgetCanvasRenderer.renderThumbnail] 现画，
 * 因此形状、铺图方式与正文安全区的差异都能在缩略图上看出来。
 *
 * 三组预设（首位 / 代码绘制 / 立体场景）的差别只在「什么算选中」，故由调用方给 [isSelected]。
 */
@Composable
private fun RenderedPresetItem(
    presetName: String,
    preset: WidgetStyle,
    selectedStyle: WidgetStyle,
    isSelected: Boolean,
    isActivated: Boolean,
    onStyleStateChange: (WidgetStyle) -> Unit,
    onApply: (WidgetStyle) -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    // 缩略图按这款风格自己的最佳显示尺寸取比例（4×2 矮宽、4×4 正方形）
    val thumbSize = presetThumbnailSize(preset)
    val bitmap by produceState<Bitmap?>(
        // 缓存已在则同帧就有图：滚动回来看不到空白帧。
        // 缓存 key 含尺寸，这里必须与下面 renderThumbnail 传的尺寸一致，否则永远 miss、必然闪一帧
        initialValue = WidgetCanvasRenderer.cachedThumbnail(
            thumbSize.width, thumbSize.height, preset
        ),
        preset, presetName
    ) {
        value = withContext(Dispatchers.Default) {
            try {
                WidgetCanvasRenderer.renderThumbnail(
                    context = context,
                    widthDp = thumbSize.width,
                    heightDp = thumbSize.height,
                    // 缩略图内不写风格名，名称显示在下方标签
                    content = "",
                    style = preset
                )
            } catch (t: Throwable) {
                Bitmap.createBitmap(thumbSize.width, thumbSize.height, Bitmap.Config.ARGB_8888)
            }
        }
    }

    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 1.dp,
        animationSpec = AppMotion.standard(),
        label = "postcardBorderWidth"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accentBlue else borderBlue,
        animationSpec = AppMotion.color,
        label = "postcardBorderColor"
    )
    Column(
        modifier = Modifier.width(PresetThumbnailBox.width.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(PresetThumbnailBox.width.dp)
                .height(PresetThumbnailBox.height.dp)
                // 盒内等比显示后，横卡上下/方卡左右会有留白：留白保持透明，透出页面近白底色
                // （2026-10-24 按用户要求去掉浅灰衬底——衬底让缩略图像蒙了一层灰）
                .clip(AppShape.thumbnail)
                .clickable {
                    haptics.tap()
                    val newPresetStyle = preset.copy(
                        backgroundOpacity = selectedStyle.backgroundOpacity,
                        cornerRadiusDp = selectedStyle.cornerRadiusDp
                    )
                    previewOrApplyPreset(newPresetStyle, isActivated, onStyleStateChange) { onApply(it) }
                }
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = presetName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(AppShape.thumbnail)
                        .border(
                            width = borderWidth,
                            color = borderColor,
                            shape = AppShape.thumbnail
                        ),
                    contentScale = ContentScale.Fit
                )
            }

            PresetFreeBadge(preset)
        }
        AppText(
            text = presetName,
            role = AppTextRole.bodySecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** 免费风格角标（与 PresetRow 同款）：明信片行里也有免费款了，得标出来 */
@Composable
private fun BoxScope.PresetFreeBadge(preset: WidgetStyle) {
    if (WidgetStyle.isProPreset(preset)) return
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(4.dp)
            .background(selectBlue, AppShape.badge)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        AppText("免费", role = AppTextRole.badgeOnFill)
    }
}

/** 插画素材缩略图（明信片行的最后一段）。 */
@Composable
private fun IllustrationPresetItems(
    selectedStyle: WidgetStyle,
    selectedContent: String,
    isActivated: Boolean,
    selectedWidgetId: Int,
    selectedReminderId: Long,
    onStyleStateChange: (WidgetStyle) -> Unit,
    onStyleChange: (Int, Long, String, WidgetStyle) -> SaveOutcome
) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    WidgetStyle.ILLUSTRATION_PRESETS.forEachIndexed { _, (resName, desc) ->
         val matchingPreset = WidgetStyle.PRESETS.find { it.presetImageResName == resName }
         val targetShape = matchingPreset?.shape ?: WidgetShape.SPLIT_CARD
         val isSelected = selectedStyle.presetImageResName == resName && selectedStyle.shape == targetShape && selectedStyle.backgroundImagePath.isNullOrEmpty()
        val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
        val borderWidth by animateDpAsState(
            targetValue = if (isSelected) 3.dp else 1.dp,
            animationSpec = AppMotion.standard(),
            label = "illustrationBorderWidth"
        )
        val borderColor by animateColorAsState(
            targetValue = if (isSelected) accentBlue else borderBlue,
            animationSpec = AppMotion.color,
            label = "illustrationBorderColor"
        )

        Column(
            modifier = Modifier.width(PresetThumbnailBox.width.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Box(
            modifier = Modifier
                .width(PresetThumbnailBox.width.dp)
                .height(PresetThumbnailBox.height.dp)
                .clip(AppShape.thumbnail)
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = AppShape.thumbnail
                )
                .clickable {
                     haptics.tap()
                     // 形状与配色都取自素材预设（不继承上一个风格的底色，见 illustrationStyle 注释），
                     // 圆角与不透明度沿用用户当前设置，与经典/萌宠/明信片行的预设行为一致
                     val newStyle = WidgetStyle.illustrationStyle(resName, selectedStyle).copy(
                         backgroundOpacity = selectedStyle.backgroundOpacity,
                         cornerRadiusDp = selectedStyle.cornerRadiusDp
                     )
                    previewOrApplyPreset(newStyle, isActivated, onStyleStateChange) {
                        onStyleChange(selectedWidgetId, selectedReminderId, selectedContent, it)
                    }
                }
        ) {
            // 图内只放画面；名称以独立文本显示在缩略图下方（与其他风格行统一）
            AsyncImage(
                model = resId,
                contentDescription = desc,
                modifier = Modifier.fillMaxSize(),
                // 盒内等比完整显示：4×4 素材不再为了填满 168×120 的盒子被裁掉上下
                contentScale = ContentScale.Fit,
                error = androidx.compose.ui.graphics.painter.ColorPainter(surfaceSubtle)
            )
        }
        AppText(
            text = desc,
            role = AppTextRole.body,
            color = if (isSelected) accentBlue else textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        }
    }
}
