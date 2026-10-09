package com.juge.app.ui.main

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.juge.app.ReminderWidgetProvider
import com.juge.app.WidgetCanvasRenderer
import com.juge.app.data.WidgetConfig
import com.juge.app.data.WidgetStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 上 1/3 预览区：横向 Pager，每页按**该页风格的最佳显示尺寸**渲染一张预览位图
 * （`WidgetStyle.bestDisplaySize`：4×4 款 250×250、4×2 款 250×110）。
 *
 * 从 MainAppScreen 原样搬出（第 3/3 步拆 MainActivity）：规格计算仍由调用方的
 * [previewHeightForPage] 提供（它同时决定外层显示盒高度），点击预览切到「个性定制」
 * 由 [onEditClick] 交回调用方。
 */
@Composable
fun WidgetPreviewPager(
    pagerState: PagerState,
    appWidgetIds: IntArray,
    widgetConfigs: List<WidgetConfig>,
    selectedWidgetId: Int,
    currentStyle: WidgetStyle,
    textContentState: String,
    previewBoxHeightDp: Int,
    previewHeightForPage: (Int) -> Int,
    onEditClick: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(previewBoxHeightDp.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(previewBoxHeightDp.dp)
                .padding(top = 0.dp),
            contentPadding = PaddingValues(horizontal = 36.dp),
            pageSpacing = 16.dp
        ) { page ->
            val pageWidgetId = if (appWidgetIds.isNotEmpty()) appWidgetIds[page] else -1
            val pageConfigId = if (pageWidgetId != -1) {
                ReminderWidgetProvider.getBoundConfigId(context, pageWidgetId)
            } else {
                -1L
            }
            val pageConfig = if (pageConfigId != -1L) {
                widgetConfigs.find { it.id == pageConfigId } ?: widgetConfigs.firstOrNull()
            } else {
                widgetConfigs.firstOrNull()
            }

            val pageStyle = if (pageWidgetId == selectedWidgetId) {
                currentStyle
            } else if (pageWidgetId != -1) {
                ReminderWidgetProvider.getWidgetStyle(context, pageWidgetId, pageConfig?.styleJson)
            } else {
                WidgetStyle.fromJsonString(pageConfig?.styleJson)
            }

            val pageContent = if (pageWidgetId == selectedWidgetId) {
                textContentState
            } else {
                pageConfig?.content ?: "静静地，坐一会。"
            }

            // 复用与预览盒一致的规格计算，避免渲染高度与盒高度不一致造成裁切/空洞
            val previewHeightDp = previewHeightForPage(page)
            // 预览按**风格自己的最佳显示尺寸**出图（4×4 款 250×250、4×2 款 250×110，见
            // WidgetStyle.bestDisplaySize），不跟桌面组件走：同一个风格无论从哪个入口添加、
            // 在桌面上被拉成多大，App 内预览的比例都固定——4:4 款始终方形、4:2 款始终长条
            // （产品规则，2026-10-08）。显示盒高度由 previewHeightForPage 按同一尺寸算出。
            // 也不能写死 360dp 之类的宽度：各类按比例分配的几何（图文卡分割线、
            // 天气盒子的腔体等）都按画布比例算，尺寸与声明尺寸脱节就会算错格子。
            val displaySize = pageStyle.bestDisplaySize
            val renderWidthDp = displaySize.widthDp
            val renderHeightDp = displaySize.heightDp
            // 先同步取一张磁盘缓存里的成品图作为初值（remember 保证每个 key 只读一次盘，
            // 写在下面 produceState 的 initialValue 里会在每次重组时重复解码）。
            // 冷启动时预览区因此首帧就有内容，不必空等约 0.8s；
            // 随后的渲染会用当前样式重新画一张并覆盖它。
            val cachedPreview = remember(pageConfigId, pageStyle, renderWidthDp, renderHeightDp) {
                WidgetCanvasRenderer.cachedPreview(
                    context = context,
                    configId = pageConfigId,
                    widthDp = renderWidthDp,
                    heightDp = renderHeightDp,
                    style = pageStyle
                )
            }
            val pageBitmap by produceState<Bitmap?>(
                initialValue = cachedPreview,
                pageContent, pageStyle, renderWidthDp, renderHeightDp
            ) {
                value = withContext(Dispatchers.Default) {
                    try {
                        WidgetCanvasRenderer.renderPreview(
                            context = context,
                            configId = pageConfigId,
                            widthDp = renderWidthDp,
                            heightDp = renderHeightDp,
                            content = pageContent,
                            style = pageStyle
                        )
                    } catch (t: Throwable) {
                        // 极端情况下（如 OOM）返回一个空白位图
                        Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeightDp.dp)
                    .clickable {
                        onEditClick()
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                // 用 Crossfade 让预览位图在切换风格时平滑过渡，避免颜色/形状突变造成"晃动"感
                androidx.compose.animation.Crossfade(
                    targetState = pageBitmap,
                    modifier = Modifier.fillMaxSize()
                ) { bmp ->
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Style Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                }
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit Style",
                    tint = Color(pageStyle.fontColor).copy(alpha = 0.6f),
                    modifier = Modifier
                        .padding(8.dp)
                        .size(16.dp)
                        .align(Alignment.BottomEnd)
                )
            }
        }
    }
}
