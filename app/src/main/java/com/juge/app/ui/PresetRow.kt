package com.juge.app.ui

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.juge.app.WidgetCanvasRenderer
import com.juge.app.data.WidgetShape
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.theme.AppMotion
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.borderBlue
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.selectBlue
import com.juge.app.ui.theme.surfaceSubtle
import com.juge.app.ui.theme.textGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通用风格预设横滑行：「个性定制」页的三行预设共用同一份缩略图渲染。
 *
 * 各行的容器、选中规则与点击行为本就不同，因此这些差异以参数形式显式表达；
 * 真正重复的部分（150×80 缩略图渲染与缓存、免费角标、名称排版）只保留这一份实现。
 *
 * @param onPresetClick 点击回调，只拿到原始 preset；是「仅预览」还是「套用并落库」
 *   （含会员拦截）由调用方决定，统一走 `previewOrApplyPreset`
 * @param showSelectionBorder 是否画选中描边（个性定制页三行统一开启：选中项 3dp 蓝色描边）
 * @param showThumbnailBackground 缩略图底色（先铺一层浅灰，避免无图时露黑）
 * @param imageContentDescription 缩略图的无障碍描述；两处文案不同，故由调用方给定
 * @param nameColor 名称配色规则（两处的"是否高亮"判据不同）
 */
@Composable
fun PresetRow(
    presets: List<Pair<String, WidgetStyle>>,
    title: String? = null,
    onPresetClick: (presetName: String, preset: WidgetStyle) -> Unit,
    modifier: Modifier = Modifier,
    isSelected: (WidgetStyle) -> Boolean = { false },
    showSelectionBorder: Boolean = true,
    showThumbnailBackground: Boolean = false,
    contentScale: ContentScale = ContentScale.Crop,
    imageContentDescription: (index: Int, presetName: String) -> String = { _, presetName -> presetName },
    titleColor: Color = textGray,
    titleBottomSpacing: Dp = 10.dp,
    nameFontWeight: FontWeight? = FontWeight.Normal,
    itemSpacing: Dp = AppSpacing.itemGap,
    nameTopPadding: Dp = 5.dp,
    nameColor: (isPro: Boolean, isSelected: Boolean) -> Color = { isPro, _ ->
        if (isPro) textGray else accentBlue
    }
) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        if (title != null) {
            Text(title, style = AppType.sectionTitle, color = titleColor)
            if (titleBottomSpacing > 0.dp) {
                Spacer(modifier = Modifier.height(titleBottomSpacing))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            presets.forEachIndexed { index, (presetName, preset) ->
                val isProPreset = WidgetStyle.isProPreset(preset)
                val selected = isSelected(preset)
                // 横向分割卡缩略图更矮：按 60dp 出图，显示盒仍是 80dp，保证一行内高度一致
                val thumbHeightDp = if (preset.shape == WidgetShape.SPLIT_CARD_HORIZONTAL) 60 else 80
                val presetBitmap by produceState<Bitmap?>(
                    // 缓存已在则同帧就有图：滚动回来看不到空白帧
                    initialValue = WidgetCanvasRenderer.cachedThumbnail(150, thumbHeightDp, preset),
                    preset, presetName
                ) {
                    value = withContext(Dispatchers.Default) {
                        try {
                            WidgetCanvasRenderer.renderThumbnail(
                                context = context,
                                widthDp = 150,
                                heightDp = thumbHeightDp,
                                // 缩略图内不写风格名：名称以独立文本显示在缩略图下方
                                content = "",
                                style = preset
                            )
                        } catch (t: Throwable) {
                            Bitmap.createBitmap(150, 80, Bitmap.Config.ARGB_8888)
                        }
                    }
                }

                Column(
                    modifier = Modifier.width(150.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val borderWidth by animateDpAsState(
                        targetValue = if (showSelectionBorder && selected) 3.dp else 1.dp,
                        animationSpec = AppMotion.standard(),
                        label = "presetBorderWidth"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (showSelectionBorder && selected) accentBlue else borderBlue,
                        animationSpec = AppMotion.color,
                        label = "presetBorderColor"
                    )
                    Box(
                        modifier = Modifier
                            .width(150.dp)
                            .height(80.dp)
                            .clip(AppShape.thumbnail)
                            .then(
                                if (showThumbnailBackground) {
                                    Modifier.background(surfaceSubtle)
                                } else {
                                    Modifier
                                }
                            )
                            .border(
                                width = borderWidth,
                                color = borderColor,
                                shape = AppShape.thumbnail
                            )
                            .clickable { onPresetClick(presetName, preset) }
                    ) {
                        if (presetBitmap != null) {
                            Image(
                                bitmap = presetBitmap!!.asImageBitmap(),
                                contentDescription = imageContentDescription(index, presetName),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = contentScale
                            )
                        }

                        // 免费风格角标：当前只有「纯色圆角」一款免费，单独标出来避免与分类标题的"会员专属"混淆
                        if (!isProPreset) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp)
                                    .background(selectBlue, AppShape.badge)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("免费", style = AppType.badge, color = Color.White)
                            }
                        }
                    }
                    Text(
                        text = presetName,
                        style = AppType.body.copy(fontWeight = nameFontWeight ?: FontWeight.Normal),
                        color = nameColor(isProPreset, selected),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = nameTopPadding)
                    )
                }
            }
        }
    }
}
