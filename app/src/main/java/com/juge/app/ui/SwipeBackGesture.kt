package com.juge.app.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 越过 touch slop 之后，还要再右滑多少（dp）才触发翻页。
 *
 * 与旧版风格行一致：整段手势约 slop + 6dp（约 14dp）就能翻页，比 pager 默认的
 * 半屏距离轻得多，同时仍要求一次明确的横向拖动。
 */
private const val SWIPE_BACK_DISTANCE_DP = 6

/**
 * 抽屉里「自己会吃掉横向拖动」的横向滚动行在本次手势里的参与打点。
 *
 * 页面观察者靠它区分两种右滑：
 * - 按下后行真的滚动了：位移已被行用掉，观察者让位；
 * - 按下后行没动（已经滚到最左端 / 行根本不需要滚动）：
 *   这次右滑对行没有任何作用，交给翻页。即使某些机型上行的 overscroll
 *   把剩余位移吃掉、pager 没跟手，也能照常翻页。
 *
 * 不持有任何 UI 状态，只递增计数；同一时刻只有一根手指在手势里，主线程读写即可。
 * 观察者在 DOWN 的 Initial 阶段先取基线、子控件随后打点，所以基线一定在打点之前。
 */
internal object SwipeBackControlUsage {
    /** 横向控件收到按下的次数 */
    var downCount: Long = 0
    /** 控件的值/滚动位置真正发生变化的次数 */
    var valueChangeCount: Long = 0
}

/**
 * 横向滚动行的“手势用量”上报：与 [SwipeBackControlUsage] 配套，挂在
 * `horizontalScroll(state)` 旁边即可，不需要行自己判断翻页。
 *
 * 行滚动了 → 记一次值变化（观察者让位）；行到头了、这次右滑没让它动 →
 * 不记，观察者可以把这次右滑当翻页。
 */
@Composable
internal fun Modifier.horizontalControlUsage(state: ScrollState): Modifier {
    val usageModifier = remember(state) {
        Modifier.pointerInput(state) {
            awaitPointerEventScope {
                var wasPressed = false
                var valueAtDragStart = state.value
                while (true) {
                    // Initial 阶段早于行的 scrollable 处理本次事件；
                    // 上一帧滚出来的新 value 在这里一定已经写回，能及时打点
                    val pressed = awaitPointerEvent(PointerEventPass.Initial)
                        .changes.any { it.pressed }
                    if (pressed) {
                        if (!wasPressed) {
                            SwipeBackControlUsage.downCount++
                            valueAtDragStart = state.value
                        } else if (state.value != valueAtDragStart) {
                            SwipeBackControlUsage.valueChangeCount++
                            valueAtDragStart = state.value
                        }
                    }
                    wasPressed = pressed
                }
            }
        }
    }
    return this.then(usageModifier)
}

/**
 * 页级「向右滑 → 返回上一页」手势层。**只观察，不消费**任何指针事件。
 *
 * 旧版把翻页逻辑挂在风格行上，导致只有那一行有 6dp 的灵敏度，其余区域（字体行、
 * 色板行、卡片、页签栏……）要么走 pager 的半屏阈值，要么根本收不到手势。这里改成
 * 页面级观察者，所有区域共用同一套判定：
 *
 * - 手势方向：横向、向右，越过 slop 后再走 6dp（起手带纵向抖动时不立即放弃，
 *   累计位移里横向占优即可）；
 * - 关键判据：这次拖动要么真的把 pager 往右推了至少 6dp（普通区域、滚到左端的横向行），
 *   要么按在了横向滚动行上、但行自己没有动（已到最左端 / 本来就不需要滚动）——
 *   后一种情况这次右滑对行无事可做，交给翻页。行“有没有动”由
 *   [SwipeBackControlUsage] 打点：挂了 `horizontalControlUsage` 的横向行自动上报。
 *   用绝对位移而不是比例，是为了让“行滚到一半后继续右滑”的同手势交接也能触发。
 *
 * 因为不消费事件，pager 的跟手、行的回卷、文本选择全部保持原样；
 * 我们只在松手瞬间决定要不要把“本来会弹回去”的拖动补成一次完整翻页。
 * 动画用 [animateToPageOnUserInput] 的手势优先级启动，能压过 pager 松手后的回弹。
 */
@Composable
internal fun Modifier.swipeBackToPage(
    pagerState: PagerState,
    onSwipeBack: () -> Unit,
    /**
     * true：只有这次拖动真的把 [pagerState] 往右推了才翻页（抽屉内容区——
     * 横向行自己滚动时 pager 不动，不能误翻）。
     * false：反过来，只有 pager **没动** 才翻页（顶部预览区——预览轮播还能翻时
     * 让轮播自己翻，单个组件或已在第一个预览时右滑返回）。
     */
    requirePagerMovement: Boolean = true
): Modifier = swipeBackObserver(
    pagerState = pagerState,
    requirePagerMovement = requirePagerMovement,
    onSwipeBack = onSwipeBack
)

/**
 * 抽屉顶栏（拖拽把手 + 分段页签）专用：它们不在 pager 内，右滑不会让 pager 跟手，
 * 所以这里不要求 pager 位移，只做方向 + 距离判定。
 *
 * 只包住把手和页签本身，不包 pager；因此不会和内容层的观察者重复。
 */
@Composable
internal fun Modifier.swipeBackChrome(onSwipeBack: () -> Unit): Modifier =
    swipeBackObserver(pagerState = null, requirePagerMovement = true, onSwipeBack = onSwipeBack)

@Composable
private fun Modifier.swipeBackObserver(
    pagerState: PagerState?,
    requirePagerMovement: Boolean,
    onSwipeBack: () -> Unit
): Modifier {
    val extraPx = with(LocalDensity.current) { SWIPE_BACK_DISTANCE_DP.dp.toPx() }
    val latestOnSwipeBack by rememberUpdatedState(onSwipeBack)
    val latestRequirePagerMovement by rememberUpdatedState(requirePagerMovement)
    return pointerInput(pagerState, extraPx) {
        awaitEachGesture {
            // 在 Initial 阶段取 DOWN：比横向滚动行等子控件先看到按下，
            // 这样“本次手势是否按在控件上”的基线一定落在子控件打点之前
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val controlDownBaseline = SwipeBackControlUsage.downCount
            val controlValueBaseline = SwipeBackControlUsage.valueChangeCount
            val startPosition = pagerState?.let {
                it.currentPage + it.currentPageOffsetFraction
            } ?: 0f
            val pagePx = pagerState?.let {
                (it.layoutInfo.pageSize + it.layoutInfo.pageSpacing).toFloat()
            } ?: 0f
            var deltaX = 0f
            var deltaY = 0f
            var armed = false
            var verticalWon = false
            var postSlopDx = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                // 只观察、不仲裁：横向行会消费位移，positionChange() 会被清零，
                // 必须读忽略消费的原始位移
                val delta = change.positionChangeIgnoreConsumed()
                deltaX += delta.x
                deltaY += delta.y
                if (!armed) {
                    // 方向判定不锁死：真机起手常带纵向抖动或斜向弧线，
                    // 只要累计位移里横向最终占优，就从那一刻开始量 6dp。
                    // 但纵向如果先越过 slop，说明这一手势已经被外层列表拿走，不再抢。
                    val slop = viewConfiguration.touchSlop
                    if (deltaX > slop && deltaX > abs(deltaY)) {
                        armed = true
                        postSlopDx = deltaX - slop
                    } else if (abs(deltaY) > slop) {
                        verticalWon = true
                    }
                } else {
                    postSlopDx += delta.x
                }
            }
            if (verticalWon || !armed || postSlopDx < extraPx) return@awaitEachGesture
            if (pagerState != null) {
                if (pagePx <= 0f) return@awaitEachGesture
                val currentPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction
                val pagerMovedPx = (startPosition - currentPosition) * pagePx
                val pagerMoved = pagerMovedPx >= extraPx
                if (latestRequirePagerMovement) {
                    // 内容区：pager 必须被推走（普通区域、滚到左端的横向行）。
                    // 例外是横向滚动行——它自己消费指针、pager 不会动：
                    // 行没滚动（已在最左端）说明这次右滑没有别的用途，交给翻页；
                    // 行滚动了就继续让行自己回卷，不让位。
                    if (!pagerMoved) {
                        val controlPressed =
                            SwipeBackControlUsage.downCount > controlDownBaseline
                        val controlChanged =
                            SwipeBackControlUsage.valueChangeCount > controlValueBaseline
                        if (!controlPressed || controlChanged) return@awaitEachGesture
                    }
                } else {
                    // 顶部预览区：预览轮播自己在翻时让给轮播
                    if (pagerMoved) return@awaitEachGesture
                }
            }
            latestOnSwipeBack()
        }
    }
}

/**
 * 以**手势优先级**把 pager 动画到 [targetPage]。
 *
 * 不能直接调 `animateScrollToPage`：松手那一刻 pager 自身的回弹事务还占着滚动锁，
 * 标准实现以 Default 优先级进入，动画刚启动就被打断——表现就是「右滑后页面弹回」。
 *
 * 这里以 UserInput 优先级接管同一把锁，并沿用 pager 默认的 spring 曲线：
 * 此时手指已抬起，没有需要跟随的真实拖拽，而**新的用户操作仍能以同等优先级打断它**——
 * 页签点击也走这里，所以「翻页动画播到一半点另一个页签」照样立即生效。
 *
 * 位移按「当前页 + 页内偏移」折算：与 [PagerState.animateScrollToPage] 在相邻页上一致；
 * 分段 pager 只有两页，本 App 的用途（右滑返回、页签点击、预览卡编辑）都是相邻页。
 */
internal suspend fun PagerState.animateToPageOnUserInput(targetPage: Int) {
    val pageStep = layoutInfo.pageSize + layoutInfo.pageSpacing
    if (pageStep <= 0) {
        // 首帧还没量出页宽：退回标准实现（它会等首次布局完成）
        animateScrollToPage(targetPage)
        return
    }
    val distance = (targetPage - (currentPage + currentPageOffsetFraction)) * pageStep
    if (distance == 0f) return
    scroll(MutatePriority.UserInput) {
        var scrolled = 0f
        animate(0f, distance, animationSpec = spring()) { value, _ ->
            scrolled += scrollBy(value - scrolled)
        }
    }
}
