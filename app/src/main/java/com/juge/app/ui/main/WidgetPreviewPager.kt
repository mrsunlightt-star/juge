package com.juge.app.ui.main

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.juge.app.R
import com.juge.app.ReminderWidgetProvider
import com.juge.app.WidgetCanvasRenderer
import com.juge.app.data.WidgetConfig
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.PreviewMetrics
import com.juge.app.ui.theme.AppMotion
import com.juge.app.ui.theme.rememberAppHaptics
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
    widgetPages: List<WidgetPage>,
    widgetConfigs: List<WidgetConfig>,
    selectedWidgetId: Int,
    currentStyle: WidgetStyle,
    textContentState: String,
    previewBoxHeightDp: Int,
    previewHeightForPage: (Int, WidgetStyle) -> Int,
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
            // 不能写 appWidgetIds[page]：pageCount 由调用方实时读取状态，而本 lambda 捕获的是
            // 上一次组合时的数组快照。桌面新增组件后（1 → N），Pager 会先按新 pageCount 去
            // 组合第 N-1 页，此时快照仍只有 1 个元素 → ArrayIndexOutOfBoundsException。
            // 已用探针测试复现（Index 2 out of bounds for length 1）：增长与滚动同帧发生必崩。
            val pageWidgetId = appWidgetIds.getOrNull(page) ?: -1
            // 绑定关系、组件级样式、配置正文全部来自 [buildWidgetPages] 在 IO 线程拍好的快照。
            // 这里以前是现算的：getBoundConfigId → synchronized + 读 prefs，
            // getWidgetStyle → 再读一次 prefs + WidgetStyle.fromJsonString（29 字段 JSON）。
            // 每重组一次、每滑一页就是 N 次锁 + N 次 prefs 读 + N 次 JSON 解析，全在主线程上。
            // 快照还没到时（首帧）用兜底项，不再回头去读 prefs。
            val snapshot = widgetPages.getOrNull(page)
            val pageConfigId = snapshot?.configId ?: -1L
            val pageStyle = when {
                pageWidgetId == selectedWidgetId -> currentStyle
                snapshot != null -> snapshot.style
                else -> WidgetStyle.fromJsonString(widgetConfigs.firstOrNull()?.styleJson)
            }
            val pageContent = if (pageWidgetId == selectedWidgetId) {
                textContentState
            } else {
                snapshot?.content ?: DEFAULT_PAGE_CONTENT
            }

            // 复用与预览盒一致的规格计算，避免渲染高度与盒高度不一致造成裁切/空洞
            val previewHeightDp = previewHeightForPage(page, pageStyle)
            // 比例始终取风格自己的最佳显示尺寸，但出图像素尺寸取 ContentScale.Fit 后的
            // 实际显示 dp 尺寸。否则宽版 250dp 位图会被预览页放大到约 288dp，
            // Canvas 中同样字号的文字也会跟着放大，最终比桌面组件大约 15%。
            val displaySize = pageStyle.bestDisplaySize
            val haptics = rememberAppHaptics()
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeightDp.dp)
                    .clickable(
                        onClickLabel = "编辑样式",
                        role = Role.Button,
                    ) {
                        haptics.tap()
                        onEditClick()
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                val fittedSize = PreviewMetrics.fittedPreviewSizeDp(
                    availableWidthDp = maxWidth.value,
                    availableHeightDp = maxHeight.value,
                    sourceWidthDp = displaySize.widthDp,
                    sourceHeightDp = displaySize.heightDp,
                )
                val renderWidthDp = fittedSize.widthDp
                val renderHeightDp = fittedSize.heightDp
                // 预览位图分两步出：先磁盘成品图（冷启动首帧即有内容，不必空等约 0.8s），
                // 再按当前样式重画一张覆盖它。
                //
                // 两步都必须在 IO/Default 上做——这里以前是
                //   remember(...) { WidgetCanvasRenderer.cachedPreview(...) }
                // 直接把 `BitmapFactory.decodeFile` 放在组合函数体里：读盘 + PNG 解压
                // 全在**主线程的组合阶段**完成（几 MB 的位图在低端机上要几十毫秒），
                // 每次样式变化、每次 pager 滚动到新页都要付一次，表现为掉帧与白屏。
                val pageBitmap by produceState<Bitmap?>(
                    initialValue = null,
                    pageConfigId, pageContent, pageStyle, renderWidthDp, renderHeightDp
                ) {
                    // 1) 磁盘缓存（可能 miss）
                    val fromDisk = withContext(Dispatchers.IO) {
                        WidgetCanvasRenderer.cachedPreview(
                            context = context,
                            configId = pageConfigId,
                            widthDp = renderWidthDp,
                            heightDp = renderHeightDp,
                            style = pageStyle
                        )
                    }
                    if (fromDisk != null) {
                        value = fromDisk
                        // 磁盘图已经在屏上：让它先显示出来，别为了等重渲染而空白
                        kotlinx.coroutines.yield()
                    }
                    // 2) 按当前内容与样式重渲染（会顺带落盘）
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

                // 只在「离散的样式身份」变化时做渐隐过渡；正文、颜色和数值调整直接更新，
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
                // 初值给 null：磁盘缓存图现在是异步来的（见上面 produceState 的说明），
                // 同步拿它就意味着把解码放回组合线程。空一帧的代价远小于主线程卡住。
                var currentSlot by remember(pageWidgetId) {
                    mutableStateOf(PreviewSlot(previewTransitionKey, null))
                }
                var previousSlot by remember(pageWidgetId) {
                    mutableStateOf(PreviewSlot(previewTransitionKey, null))
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
                    painter = painterResource(R.drawable.ic_symbol_edit_square),
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
