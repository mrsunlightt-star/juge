package com.juge.app.ui

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juge.app.WidgetCanvasRenderer
import com.juge.app.data.WidgetShape
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.theme.selectBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通用风格预设横滑行：「个性定制」页与桌面快捷面板共用同一份缩略图渲染。
 *
 * 两处的容器、选中规则与点击行为本就不同，因此这些差异以参数形式显式表达；
 * 真正重复的部分（150×80 缩略图渲染与缓存、免费角标、名称排版）只保留这一份实现。
 *
 * @param isLocked 是否锁住（PRO 专属且未激活）；由调用方决定锁定时是预览还是提示
 * @param onPresetClick 点击回调，拿到原始 preset 与是否锁定，由调用方决定保存/预览/提示
 * @param showSelectionBorder 是否画选中描边（个性定制页不做选中高亮，只有免费角标与名称配色）
 * @param showThumbnailBackground 缩略图底色（快捷面板会先铺一层浅灰，避免无图时露黑）
 * @param imageContentDescription 缩略图的无障碍描述；两处文案不同，故由调用方给定
 * @param nameColor 名称配色规则（两处的"是否高亮"判据不同）
 */
@Composable
fun PresetRow(
    presets: List<Pair<String, WidgetStyle>>,
    title: String? = null,
    onPresetClick: (presetName: String, preset: WidgetStyle, isLocked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isLocked: (WidgetStyle) -> Boolean = { false },
    isSelected: (WidgetStyle) -> Boolean = { false },
    showSelectionBorder: Boolean = true,
    showThumbnailBackground: Boolean = false,
    contentScale: ContentScale = ContentScale.Crop,
    imageContentDescription: (index: Int, presetName: String) -> String = { _, presetName -> presetName },
    titleColor: Color = Color(0xFF64748B),
    titleBottomSpacing: Dp = 10.dp,
    nameFontWeight: FontWeight? = FontWeight.Normal,
    itemSpacing: Dp = 10.dp,
    nameTopPadding: Dp = 5.dp,
    nameColor: (isPro: Boolean, isSelected: Boolean) -> Color = { isPro, _ ->
        if (isPro) Color(0xFF64748B) else selectBlue
    }
) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        if (title != null) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = titleColor)
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
                val locked = isLocked(preset)
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
                    Box(
                        modifier = Modifier
                            .width(150.dp)
                            .height(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (showThumbnailBackground) {
                                    Modifier.background(Color(0xFFF1F5F9))
                                } else {
                                    Modifier
                                }
                            )
                            .border(
                                width = if (showSelectionBorder && selected) 3.dp else 1.dp,
                                color = if (showSelectionBorder && selected) selectBlue else Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onPresetClick(presetName, preset, locked) }
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
                                    .background(selectBlue, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("免费", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                    Text(
                        text = presetName,
                        fontSize = 13.sp,
                        fontWeight = nameFontWeight,
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
