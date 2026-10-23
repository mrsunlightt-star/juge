package com.juge.app.ui.main

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.juge.app.ReminderWidgetProvider
import com.juge.app.WidgetCanvasRenderer
import com.juge.app.data.WidgetConfig
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.theme.AppMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 预览过渡的展示槽位：一个「样式身份 key + 该 key 当前应展示的位图」。
 * details 见 [WidgetPreviewPager] 内的槽位模型注释。
 */
private data class PreviewSlot(
    val key: String,
    val bitmap: Bitmap?,
)

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

            // 只在「离散的样式身份」变化时做渐隐过渡；正文、颜色和滑条连续调整直接更新，
            // 避免拖动时每一帧都 Crossfade，导致预览看起来跟手性差或残影。
            // 过渡目标（キー）只含离散身份，不包含 pageBitmap 本身。
            val previewTransitionKey = listOf(
                pageWidgetId,
                pageStyle.presetId,
                pageStyle.shape,
                pageStyle.backgroundImagePath,
                pageStyle.bgImageScaleMode,
            ).joinToString("|")

            // 展示槽位模型：
            //  - current 槽 = 当前 Crossfade 目标 key + 它对应的位图；
            //  - previous 槽 = 切换瞬间把 current 的旧值整体搬进去，供离场层继续展示。
            // 新渲染只有两种落地方式：
            //  1. key 未变（编辑文字/拖颜色）：current 槽原位换图——目标 key 不变，**不触发动画**；
            //  2. key 变了：旧槽搬进 previous、新图进 current，Crossfade 才从旧图淡入**已渲染好的**新图。
            // 此前实现是 key 一变就让两层都指着旧图干等新渲染（约 0.8s），渐变形同虚设、
            // 新图落地发生在动画结束后，视觉上是一次硬切。
            var currentSlot by remember(pageWidgetId) {
                mutableStateOf(PreviewSlot(previewTransitionKey, cachedPreview))
            }
            var previousSlot by remember(pageWidgetId) {
                mutableStateOf(PreviewSlot(previewTransitionKey, cachedPreview))
            }

            LaunchedEffect(pageBitmap, previewTransitionKey) {
                if (pageBitmap == null) return@LaunchedEffect
                if (previewTransitionKey == currentSlot.key) {
                    currentSlot = currentSlot.copy(bitmap = pageBitmap)
                } else {
                    previousSlot = currentSlot
                    currentSlot = PreviewSlot(previewTransitionKey, pageBitmap)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeightDp.dp)
                    .clickable(
                        onClickLabel = "编辑样式",
                        role = Role.Button,
                    ) {
                        onEditClick()
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                // 目标用 key 而不是 slot 对象：连续编辑换图时 key 不变，
                // Crossfade 不会因为位图实例更新而重启动画
                Crossfade(
                    targetState = currentSlot.key,
                    animationSpec = AppMotion.standard(),
                    modifier = Modifier.fillMaxSize()
                ) { slotKey ->
                    val bmp = if (slotKey == currentSlot.key) currentSlot.bitmap else previousSlot.bitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "组件预览，点击进入个性定制",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                }
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
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
