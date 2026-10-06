package com.juge.app.ui.adjust

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

/**
 * 明信片风格行：代码绘制的大卡、按真实渲染的微缩场景、插画素材。
 * 三类缩略图统一 150×80，名称以独立文本显示在缩略图下方。
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
    onStyleChange: (Int, Long, String, WidgetStyle) -> SaveOutcome
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 代码绘制的大卡（书香书架）排在插图素材之前：
        // 缩略图统一按 150×80 渲染显示（与经典/萌宠/插画组一致），
        // 组件内容在渲染时按画布自适应重排，避免一行内宽窄不一
        val codePreviewHeightDp = 80
        val codePreviewWidthDp = 150
        WidgetStyle.POSTCARD_CODE_PRESETS.forEach { (presetName, preset) ->
            val isCodeSelected = selectedStyle.shape == preset.shape &&
                selectedStyle.presetImageResName == null &&
                selectedStyle.backgroundImagePath.isNullOrEmpty()
            val codeBitmap by produceState<Bitmap?>(
                // 缓存已在则同帧就有图：滚动回来看不到空白帧
                initialValue = WidgetCanvasRenderer.cachedThumbnail(WidgetStyle.POSTCARD_CODE_RENDER_WIDTH_DP, WidgetStyle.POSTCARD_CODE_RENDER_HEIGHT_DP, preset),
                preset, presetName
            ) {
                value = withContext(Dispatchers.Default) {
                    try {
                        WidgetCanvasRenderer.renderThumbnail(
                            context = context,
                            widthDp = codePreviewWidthDp,
                            heightDp = codePreviewHeightDp,
                            // 缩略图内不写风格名，名称显示在下方标签
                            content = "",
                            style = preset
                        )
                    } catch (t: Throwable) {
                        Bitmap.createBitmap(codePreviewWidthDp, codePreviewHeightDp, Bitmap.Config.ARGB_8888)
                    }
                }
            }

            Column(
                modifier = Modifier.width(codePreviewWidthDp.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Box(
                modifier = Modifier
                    .width(codePreviewWidthDp.dp)
                    .height(codePreviewHeightDp.dp)
                    .clickable {
                        val newPresetStyle = preset.copy(
                            backgroundOpacity = selectedStyle.backgroundOpacity,
                            cornerRadiusDp = selectedStyle.cornerRadiusDp
                        )
                        previewOrApplyPreset(newPresetStyle, isActivated, onStyleStateChange) {
                            onSelectPreset(selectedWidgetId, selectedReminderId, it)
                        }
                    }
            ) {
                if (codeBitmap != null) {
                    Image(
                        bitmap = codeBitmap!!.asImageBitmap(),
                        contentDescription = presetName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (isCodeSelected) 3.dp else 1.dp,
                                color = if (isCodeSelected) selectBlue else Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
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

        // 明信片行的微缩城市 / 立体场景：缩略图按组件真实渲染——
        // 若像插图那样裁原图，几张会长得一模一样、分不出方案。
        WidgetStyle.POSTCARD_RENDERED_PRESETS.forEach { (presetName, preset) ->
            val isRenderedSelected = selectedStyle.presetId == preset.presetId
            // 缩略图统一按 150×80 渲染：天气盒子（4×4）等方版风格
            // 在缩略图中与明信片同宽，组件内容按画布自适应重排
            val renderedW = codePreviewWidthDp
            val renderedH = codePreviewHeightDp
            val renderedBitmap by produceState<Bitmap?>(
                // 缓存已在则同帧就有图：滚动回来看不到空白帧
                initialValue = WidgetCanvasRenderer.cachedThumbnail(renderedW, renderedH, preset),
                preset, presetName
            ) {
                value = withContext(Dispatchers.Default) {
                    try {
                        WidgetCanvasRenderer.renderThumbnail(
                                                    context = context,
                                                    widthDp = renderedW,
                                                    heightDp = renderedH,
                                                    content = "",
                                                    style = preset
                                                )
                    } catch (t: Throwable) {
                        Bitmap.createBitmap(renderedW, renderedH, Bitmap.Config.ARGB_8888)
                    }
                }
            }

            Column(
                modifier = Modifier.width(codePreviewWidthDp.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Box(
                modifier = Modifier
                    .width(codePreviewWidthDp.dp)
                    .height(codePreviewHeightDp.dp)
                    .clickable {
                        val newPresetStyle = preset.copy(
                            backgroundOpacity = selectedStyle.backgroundOpacity,
                            cornerRadiusDp = selectedStyle.cornerRadiusDp
                        )
                        previewOrApplyPreset(newPresetStyle, isActivated, onStyleStateChange) {
                            onSelectPreset(selectedWidgetId, selectedReminderId, it)
                        }
                    }
            ) {
                if (renderedBitmap != null) {
                    Image(
                        bitmap = renderedBitmap!!.asImageBitmap(),
                        contentDescription = presetName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (isRenderedSelected) 3.dp else 1.dp,
                                color = if (isRenderedSelected) selectBlue else Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
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
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
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
