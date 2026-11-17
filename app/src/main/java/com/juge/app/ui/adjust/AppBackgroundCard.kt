package com.juge.app.ui.adjust

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juge.app.data.AppBackgroundPreset
import com.juge.app.ui.AppSectionCard
import com.juge.app.ui.AppSectionHeader
import com.juge.app.ui.PresetThumbnailBox
import com.juge.app.ui.horizontalControlUsage
import com.juge.app.ui.thumbnailAlignment
import com.juge.app.ui.theme.AppMotion
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.appFontWeight
import com.juge.app.ui.theme.borderBlue
import com.juge.app.ui.theme.rememberAppHaptics
import com.juge.app.ui.theme.textGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App 预览区背景选择。
 *
 * 只提供图片替换，不暴露组件样式的背景色、文字或填充模式；缩略图尺寸、选中描边、
 * 名称排版全部复用「组件风格」行的规范。
 */
@Composable
internal fun AppBackgroundCard(
    selected: AppBackgroundPreset,
    onSelect: (AppBackgroundPreset) -> Unit,
) {
    AppSectionCard(modifier = Modifier.fillMaxWidth()) {
        AppSectionHeader(title = "App 背景图")
        AppBackgroundPresetRow(
            selected = selected,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun AppBackgroundPresetRow(
    selected: AppBackgroundPreset,
    onSelect: (AppBackgroundPreset) -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalControlUsage(scrollState)
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.itemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppBackgroundPreset.entries.forEach { preset ->
            val isSelected = preset == selected
            val thumbnail by produceState<Bitmap?>(initialValue = null, preset) {
                value = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeResource(
                        context.resources,
                        preset.imageRes,
                        BitmapFactory.Options().apply {
                            // 缩略图只需显示在 180×96dp 框内，按 1/2 采样即可；
                            // 避免两个完整 1152² 背景解码后常驻内存。
                            inSampleSize = 2
                        },
                    )
                }
            }

            Column(
                modifier = Modifier.width(PresetThumbnailBox.width.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val borderWidth by animateDpAsState(
                    targetValue = if (isSelected) 3.dp else 0.5.dp,
                    animationSpec = AppMotion.standard(),
                    label = "appBackgroundBorderWidth",
                )
                val borderColor by animateColorAsState(
                    targetValue = if (isSelected) accentBlue else borderBlue,
                    animationSpec = AppMotion.color,
                    label = "appBackgroundBorderColor",
                )
                Box(
                    modifier = Modifier
                        .width(PresetThumbnailBox.width.dp)
                        .height(PresetThumbnailBox.height.dp)
                        .clip(AppShape.thumbnail)
                        .border(
                            width = borderWidth,
                            color = borderColor,
                            shape = AppShape.thumbnail,
                        )
                        .semantics {
                            contentDescription = "${preset.displayName} App 背景图"
                        }
                        .clickable(role = Role.Button) {
                            haptics.tap()
                            onSelect(preset)
                        },
                ) {
                    thumbnail?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            alignment = preset.thumbnailAlignment,
                        )
                    }
                }
                Text(
                    text = preset.displayName,
                    style = AppType.body.copy(
                        fontWeight = appFontWeight(FontWeight.Normal),
                    ),
                    color = if (isSelected) accentBlue else textGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
