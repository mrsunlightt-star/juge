package com.juge.app.ui.adjust

import android.graphics.Bitmap
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.juge.app.WidgetCanvasRenderer
import com.juge.app.data.WidgetShape
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.SaveOutcome
import com.juge.app.ui.theme.selectBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 行内每个条目的缩略图尺寸：三类缩略图统一长宽，一行内不会宽窄不一 */
private const val PREVIEW_WIDTH_DP = 150
private const val PREVIEW_HEIGHT_DP = 80

/**
 * 明信片风格行：首位整幅贴图、代码绘制的大卡、按真实渲染的微缩场景、插画素材。
 * 四类缩略图统一 150×80，名称以独立文本显示在缩略图下方。
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
    Column(modifier = modifier) {
        // 行标题与经典/萌宠行同款（13sp / Bold / #64748B，见 PresetRow 的 title 分支）。
        //
        // ⚠️ 行标题**不带**「· 4×4」那类尺寸后缀：每款风格的最佳显示尺寸本就各不相同
        // （见 WidgetStyle.bestDisplaySize，预览区按它出图），挂一个尺寸在行上只会误导。
        // 历史文案原是「明信片风格 · 会员专属 · 4×4 / 4×3」——
        // 从 2e9f34b 拆分 AdjustTabContent 起整行标题就丢了，行标题因为搬进了
        // PresetRow(title=) 才活下来（见 StylePresetCard）。
        if (title != null) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
            Spacer(modifier = Modifier.height(10.dp))
        }
        Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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

            // 明信片行的微缩城市 / 立体场景：缩略图按组件真实渲染——
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
    val bitmap by produceState<Bitmap?>(
        // 缓存已在则同帧就有图：滚动回来看不到空白帧。
        // 缓存 key 含尺寸，这里必须与下面 renderThumbnail 传的尺寸一致，否则永远 miss、必然闪一帧
        initialValue = WidgetCanvasRenderer.cachedThumbnail(PREVIEW_WIDTH_DP, PREVIEW_HEIGHT_DP, preset),
        preset, presetName
    ) {
        value = withContext(Dispatchers.Default) {
            try {
                WidgetCanvasRenderer.renderThumbnail(
                    context = context,
                    widthDp = PREVIEW_WIDTH_DP,
                    heightDp = PREVIEW_HEIGHT_DP,
                    // 缩略图内不写风格名，名称显示在下方标签
                    content = "",
                    style = preset
                )
            } catch (t: Throwable) {
                Bitmap.createBitmap(PREVIEW_WIDTH_DP, PREVIEW_HEIGHT_DP, Bitmap.Config.ARGB_8888)
            }
        }
    }

    Column(
        modifier = Modifier.width(PREVIEW_WIDTH_DP.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(PREVIEW_WIDTH_DP.dp)
                .height(PREVIEW_HEIGHT_DP.dp)
                .clickable {
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
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) selectBlue else Color(0xFFE2E8F0),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentScale = ContentScale.Fit
                )
            }

            PresetFreeBadge(preset)
        }
        Text(
            text = presetName,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp)
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
            .background(selectBlue, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text("免费", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
    WidgetStyle.ILLUSTRATION_PRESETS.forEachIndexed { _, (resName, desc) ->
         val matchingPreset = WidgetStyle.PRESETS.find { it.presetImageResName == resName }
         val targetShape = matchingPreset?.shape ?: WidgetShape.SPLIT_CARD
         val isSelected = selectedStyle.presetImageResName == resName && selectedStyle.shape == targetShape && selectedStyle.backgroundImagePath.isNullOrEmpty()
        val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)

        Column(
            modifier = Modifier.width(150.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF1F5F9))
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) selectBlue else Color(0xFFE2E8F0),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable {
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
                contentScale = ContentScale.Crop,
                error = androidx.compose.ui.graphics.painter.ColorPainter(Color.LightGray)
            )
        }
        Text(
            text = desc,
            fontSize = 13.sp,
            color = if (isSelected) selectBlue else Color(0xFF64748B),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp)
        )
        }
    }
}
