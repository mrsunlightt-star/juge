package com.juge.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.juge.app.WidgetCanvasRenderer
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.AppText
import com.juge.app.ui.theme.AppTextRole
import com.juge.app.ui.theme.AppMotion
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.rememberAppHaptics
import com.juge.app.ui.theme.borderBlue
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.selectBlue
import com.juge.app.ui.theme.textGray
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 缩略图卡片的统一外框：旧版 150×80 的 1.2 倍（用户定档：再大就显得整行笨重）。
 *
 * 同一行里混着 4×2 与 4×4 风格（例如经典行的小霸王游戏机、萌宠行的猫趴），
 * 如果让每张图按自身比例决定宽高，行高会被最大的方形卡撑住，前面的横卡被顶出
 * 一大块空白。因此所有条目共用这个盒子，图在盒内等比居中。
 */
internal val PresetThumbnailBox = IntSize(180, 96)

/**
 * 按风格的最佳显示尺寸算出**实际渲染画布**，保证内容比例不变、并尽量填满
 * [PresetThumbnailBox]：4×2（250×110）→ 180×79，4×4（250×250）→ 96×96。
 *
 * 旧版所有缩略图都塞进 150×80 的横版画布：4×4 的方形组件被纵向压到 44%，
 * 在行里看起来就是一张扁卡。现在渲染画布与组件真实比例一致，方形不再被压扁。
 */
internal fun presetThumbnailSize(style: WidgetStyle): IntSize {
    val best = style.bestDisplaySize
    val aspect = best.widthDp.toFloat() / best.heightDp
    val boxAspect = PresetThumbnailBox.width.toFloat() / PresetThumbnailBox.height
    return if (aspect >= boxAspect) {
        // 比盒子更宽：贴满宽度，上下留白
        IntSize(
            PresetThumbnailBox.width,
            (PresetThumbnailBox.width / aspect).roundToInt().coerceAtLeast(1)
        )
    } else {
        // 比盒子更方：贴满高度，左右留白
        IntSize(
            (PresetThumbnailBox.height * aspect).roundToInt().coerceAtLeast(1),
            PresetThumbnailBox.height
        )
    }
}

/**
 * 通用风格预设横滑行：「个性定制」页的三行预设共用同一份缩略图渲染。
 *
 * 各行的容器、选中规则与点击行为本就不同，因此这些差异以参数形式显式表达；
 * 真正重复的部分（按最佳显示尺寸等比缩放的缩略图渲染与缓存、免费角标、名称排版）
 * 只保留这一份实现。
 *
 * @param onPresetClick 点击回调，只拿到原始 preset；是「仅预览」还是「套用并落库」
 *   （含会员拦截）由调用方决定，统一走 `previewOrApplyPreset`
 * @param showSelectionBorder 是否画选中描边（个性定制页三行统一开启：选中项 3dp 蓝色描边）
 * @param imageContentDescription 缩略图的无障碍描述；两处文案不同，故由调用方给定
 * @param nameColor 名称配色规则（两处的"是否高亮"判据不同）
 * @param onSwipeBack 行已滚到最左端、右滑无更多风格可露出时，继续右滑交给外层翻页
 */
@Composable
fun PresetRow(
    presets: List<Pair<String, WidgetStyle>>,
    title: String? = null,
    onPresetClick: (presetName: String, preset: WidgetStyle) -> Unit,
    modifier: Modifier = Modifier,
    isSelected: (WidgetStyle) -> Boolean = { false },
    showSelectionBorder: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
    imageContentDescription: (index: Int, presetName: String) -> String = { _, presetName -> presetName },
    titleColor: Color = textGray,
    titleBottomSpacing: Dp = AppSpacing.itemGap,
    nameFontWeight: FontWeight? = FontWeight.Normal,
    itemSpacing: Dp = AppSpacing.itemGap,
    nameTopPadding: Dp = 4.dp,
    nameColor: (isPro: Boolean, isSelected: Boolean) -> Color = { isPro, _ ->
        if (isPro) textGray else accentBlue
    },
    /** 行已滚到最左端、右滑无内容可再露出时，继续右滑交给外层翻回「跃然纸上」 */
    onSwipeBack: () -> Unit = {},
    /** 外层分段 pager：边界右滑的消费前提是它静止停在「个性定制」 */
    pagerState: PagerState
) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val scrollState = rememberScrollState()
    Column(modifier = modifier.pageBackAtRowStart(scrollState, pagerState, onSwipeBack)) {
        if (title != null) {
            AppText(title, role = AppTextRole.sectionTitleNeutral, color = titleColor)
            if (titleBottomSpacing > 0.dp) {
                Spacer(modifier = Modifier.height(titleBottomSpacing))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            // 统一盒子后各条目同高，名称自然对齐；居中与底部对齐等价，保留居中
            verticalAlignment = Alignment.CenterVertically
        ) {
            presets.forEachIndexed { index, (presetName, preset) ->
                val isProPreset = WidgetStyle.isProPreset(preset)
                val selected = isSelected(preset)
                // 宽高都按风格的最佳显示尺寸来：4×2 矮宽、4×4 为正方形，
                // 不再把方形组件硬塞进横版画布
                val thumbSize = presetThumbnailSize(preset)
                val presetBitmap by produceState<Bitmap?>(
                    // 缓存已在则同帧就有图：滚动回来看不到空白帧
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
                                // 缩略图内不写风格名：名称以独立文本显示在缩略图下方
                                content = "",
                                style = preset
                            )
                        } catch (t: Throwable) {
                            Bitmap.createBitmap(
                                thumbSize.width, thumbSize.height, Bitmap.Config.ARGB_8888
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.width(PresetThumbnailBox.width.dp),
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
                                onPresetClick(presetName, preset)
                            }
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
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                AppText("免费", role = AppTextRole.badgeOnFill)
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

/**
 * 行已到最左端后，继续右滑多少（dp）就交给外层翻页。
 *
 * 从**触摸滑动被识别之后**开始累计（系统 touch slop 约 8dp 不经过这里），
 * 取 6dp：整段手势约 14dp 就能翻页，比 pager 默认的半屏距离轻得多，
 * 同时仍要求一个明确的横向拖动。
 */
private const val ROW_EDGE_SWIPE_TO_PAGE_DP = 6

/**
 * 只在风格行**已经滚到最左端**（[scrollState] 为 0，右滑没有更多内容可露出）、
 * 且外层 pager **静止停在「个性定制」**时，把继续右滑就地消费掉，并累计右滑距离；
 * 松手时若累计超过阈值，翻回「跃然纸上」。
 *
 * 行本身仍可双向滚动：未到左端时右滑照常回卷。
 *
 * **为什么必须以 pager 静止为消费前提**：无条件消费（早期版本）会让 pager 收不到
 * 任何拖拽，只能靠 animateScrollToPage 动画移动；动画被下一次触摸打断后 pager 停在
 * 两页中间，而行上的手势仍被截走，永远无法归位（卡在两页中间）。无条件不消费也不行：
 * pager 会在手势中途把拖拽接管走，onSwipeBack 在拖动仍活跃时触发，
 * animateScrollToPage 抢不到滚动锁，翻页丢失。所以只在 pager 静止时消费——
 * 触发可靠；pager 一旦离开静止位（动画被打断、被拖到半路），消费立即放开，
 * 手势原生到达 pager，随时可拖动、可回弹，半路状态不可能卡死。
 */
@Composable
internal fun Modifier.pageBackAtRowStart(
    scrollState: ScrollState,
    pagerState: PagerState,
    onSwipeBack: () -> Unit
): Modifier {
    val thresholdPx = with(LocalDensity.current) { ROW_EDGE_SWIPE_TO_PAGE_DP.dp.toPx() }
    val latestOnSwipeBack by rememberUpdatedState(onSwipeBack)
    val connection = remember(scrollState, pagerState, thresholdPx) {
        // 手势内的累计量只给回调读，不参与组合
        var accumulated = 0f
        var triggered = false
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.x == 0f) {
                    return Offset.Zero
                }
                // 手指向左：行向前浏览，不拦截、清累计
                if (available.x < 0f) {
                    accumulated = 0f
                    triggered = false
                    return Offset.Zero
                }
                // pager 不在「个性定制」静止位：翻页动画进行中、被拖到半路、或已翻过去。
                // 一律放行，让手势沿链条到达 pager，跟手与 settle 全部原生。
                val pagerParkedOnAdjust = pagerState.currentPage == 1 &&
                    pagerState.currentPageOffsetFraction == 0f
                if (scrollState.value > 0 || !pagerParkedOnAdjust) {
                    accumulated = 0f
                    triggered = false
                    return Offset.Zero
                }
                if (!triggered) {
                    accumulated += available.x
                    if (accumulated >= thresholdPx) triggered = true
                }
                // 消费掉：pager 静止时不被这些增量拖走，翻页只由松手后的动画执行
                return Offset(available.x, 0f)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val shouldPageBack = triggered
                accumulated = 0f
                triggered = false
                if (shouldPageBack) {
                    // 松手后再启动翻页动画，避免被正在进行的拖动取消；
                    // 同时截下 fling 速度，pager 不再自行 settle，只走这一段翻页动画
                    latestOnSwipeBack()
                    return available.copy(y = 0f)
                }
                return Velocity.Zero
            }
        }
    }
    return nestedScroll(connection)
}
