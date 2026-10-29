package com.juge.app.ui.library

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.juge.app.ui.AppDivider
import com.juge.app.ui.AppText
import com.juge.app.ui.theme.AppMotion
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppShadows
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppTextRole
import com.juge.app.ui.theme.borderBlue
import com.juge.app.ui.theme.cardBg
import com.juge.app.ui.theme.rememberAppHaptics
import com.juge.app.ui.theme.surfaceSubtle
import kotlin.math.roundToInt

/** 上下文菜单的一项：图标 + 文字；破坏性操作（删除）图标与文字都用 [com.juge.app.ui.theme.dangerRed]。 */
internal data class ContextMenuItem(
    @DrawableRes val iconRes: Int,
    val label: String,
    val iconTint: Color,
    /** null = 用文字角色的默认墨色（如「编辑」）；删除等破坏性项传 dangerRed */
    val labelColor: Color? = null,
    val onClick: () -> Unit
)

/**
 * iOS 式长按上下文菜单浮层：全页压暗 + 指尖处弹出的白卡菜单。
 *
 * 刻意**不用 Dialog**：菜单弹出时那根手指通常还按在卡片上，继续拖动要能立刻
 * 取消菜单、进入拖拽排序——Dialog 是独立窗口，收不到这根手指的后续事件，
 * 只有同窗口的浮层才接得住这套连续手势。
 *
 * 遮罩只压暗不模糊：毛玻璃（Haze）跨不进同窗口浮层的层级，压暗 + dialog 档
 * 弥散阴影已足够把视线聚焦到菜单上（弹窗们用 FLAG_BLUR_BEHIND，是另一个窗口才有的能力）。
 */
@Composable
internal fun ContextMenuOverlay(
    /** 菜单锚点（触发长按的手指位置），root 坐标 */
    anchorInRoot: Offset,
    items: List<ContextMenuItem>,
    onDismiss: () -> Unit
) {
    // 浮层自身在 root 中的边界：把 root 坐标的锚点换算成浮层内坐标并钳制菜单落点
    var overlayBounds by remember { mutableStateOf(Rect.Zero) }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }

    // 出场：压暗先到、菜单带一点从指尖放大的弹性（iOS 同款手感）
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = AppMotion.entrance(),
        label = "contextMenuProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayBounds = it.boundsInRoot() }
            // 遮罩点击/长按都收掉菜单。触发长按的那根手指的后续事件仍归卡片检测器，
            // 这里只会收到浮层出现之后的新触摸。
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.16f * progress }
                .background(Color.Black)
        )
        if (overlayBounds != Rect.Zero && items.isNotEmpty()) {
            val haptics = rememberAppHaptics()
            val menuWidth = 176.dp
            // 菜单落点与缩放原点都在 Density 域内一次算清（浮层内 px 坐标）
            val placement = with(LocalDensity.current) {
                val anchorLocal = anchorInRoot - overlayBounds.topLeft
                val marginPx = 8.dp.toPx()
                val menuWidthPx = menuWidth.toPx()
                val x = (anchorLocal.x - menuWidthPx / 2f)
                    .coerceIn(marginPx, overlayBounds.width - menuWidthPx - marginPx)
                // 默认贴在指尖下方；下方放不下就翻到上方，同样钳制在浮层内
                val belowY = anchorLocal.y + 20.dp.toPx()
                val y = if (belowY + menuSize.height > overlayBounds.height - marginPx) {
                    anchorLocal.y - menuSize.height - 12.dp.toPx()
                } else {
                    belowY
                }.coerceAtLeast(marginPx)
                // 缩放原点朝向指尖：菜单从手指的方向长出来，而不是从中心
                val origin = TransformOrigin(
                    pivotFractionX = ((anchorLocal.x - x) / menuSize.width.coerceAtLeast(1))
                        .coerceIn(0f, 1f),
                    pivotFractionY = ((anchorLocal.y - y) / menuSize.height.coerceAtLeast(1))
                        .coerceIn(0f, 1f),
                )
                Triple(x, y, origin)
            }
            Column(
                modifier = Modifier
                    .offset {
                        IntOffset(placement.first.roundToInt(), placement.second.roundToInt())
                    }
                    .width(menuWidth)
                    .graphicsLayer {
                        alpha = progress
                        scaleX = 0.9f + 0.1f * progress
                        scaleY = 0.9f + 0.1f * progress
                        transformOrigin = placement.third
                    }
                    .shadow(
                        elevation = AppShadows.dialog,
                        shape = AppShape.menu,
                        clip = false,
                        ambientColor = AppShadows.ambientShadow,
                        spotColor = AppShadows.spotShadow,
                    )
                    .border(1.dp, borderBlue, AppShape.menu)
                    .background(cardBg, AppShape.menu)
                    .onGloballyPositioned { menuSize = it.size },
            ) {
                items.forEachIndexed { index, item ->
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(if (pressed) surfaceSubtle else Color.Transparent)
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                            ) {
                                haptics.click()
                                item.onClick()
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(item.iconRes),
                            contentDescription = null,
                            tint = item.iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.iconGap))
                        AppText(
                            text = item.label,
                            role = AppTextRole.body,
                            color = item.labelColor,
                        )
                    }
                    if (index < items.lastIndex) {
                        // iOS 菜单分隔线：与文字对齐的内缩细线
                        AppDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

/**
 * 长按菜单 + 长按拖拽的双用检测器，金句卡片与分类 chip 共用。
 *
 * 手工实现单一状态机（一个 pointerInput 观察一根手指的完整生命周期），
 * 不能拆成 detectTapGestures + detectDragGesturesAfterLongPress 并行：
 * 后者的 onDragStart 在**长按超时那一刻**就触发（不等移动），会把同时弹出的
 * 菜单瞬间关掉，两个回调打架。
 *
 * - 按下 → 越过 touch slop：滚动手势，整体放行给列表，本检测器退出；
 * - 按下 → 快速抬起：单击（[onTap]，可为 null 让 chip 自己的 onClick 接管）；
 * - 按下 → 停满长按阈值：[onLongPress] 弹菜单，之后按住不动菜单保持打开（iOS 同款）；
 * - 菜单开着 → 继续移动越过 slop：[onDragStart]（关菜单、进入拖拽），此后位移经
 *   [onDrag] 流出并消费掉（LazyColumn 滚动接不走）；
 * - 拖拽中抬起：[onDragEnd]；菜单开着原地抬起：菜单保持，事件消费掉防止触发 chip 单击。
 */
@Composable
internal fun Modifier.longPressMenuDragGestures(
    key: Any?,
    onTap: (() -> Unit)?,
    onLongPress: (localOffset: Offset) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (delta: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
): Modifier {
    val latestOnTap by rememberUpdatedState(onTap)
    val latestOnLongPress by rememberUpdatedState(onLongPress)
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDrag by rememberUpdatedState(onDrag)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)
    val latestOnDragCancel by rememberUpdatedState(onDragCancel)
    return pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pressPosition = down.position
            val slop = viewConfiguration.touchSlop

            // 阶段一：长按判定。越界移动 = 滚动，整体放行；抬起 = 单击。
            // 位移（ slop 内）不算打断：input 注入的微小抖动也走长按。
            val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var traveled = 0f
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: return@withTimeoutOrNull PressOutcome.Cancelled
                    if (change.changedToUpIgnoreConsumed()) {
                        if (!change.isConsumed) latestOnTap?.invoke()
                        return@withTimeoutOrNull PressOutcome.Tap
                    }
                    traveled += change.positionChange().getDistance()
                    if (traveled > slop) return@withTimeoutOrNull PressOutcome.Scroll
                }
                @Suppress("UNREACHABLE_CODE") PressOutcome.LongPress
            } ?: PressOutcome.LongPress
            when (outcome) {
                PressOutcome.Tap, PressOutcome.Scroll, PressOutcome.Cancelled ->
                    return@awaitEachGesture
                PressOutcome.LongPress -> Unit
            }

            // 长按成立：弹菜单。此后指针归本检测器，移动按拖拽处理。
            latestOnLongPress(pressPosition)

            // 阶段二：菜单开着——按住不动保持菜单，移动越过 slop 进入拖拽。
            var dragStarted = false
            var moveTotal = Offset.Zero
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null) return@awaitEachGesture
                    if (change.changedToUpIgnoreConsumed()) {
                        // ACTION_CANCEL 会生成已消费的抬起事件，应交给 finally 取消拖拽。
                        if (dragStarted && !change.isConsumed) {
                            dragStarted = false
                            latestOnDragEnd()
                        }
                        change.consume()
                        return@awaitEachGesture
                    }
                    val delta = change.positionChangeIgnoreConsumed()
                    if (!dragStarted) {
                        moveTotal += delta
                        if (moveTotal.getDistance() > slop) {
                            dragStarted = true
                            latestOnDragStart()
                        }
                    }
                    if (dragStarted) latestOnDrag(delta)
                    // 长按后事件全部消费：列表滚动、chip 单击都不再插手
                    change.consume()
                }
            } finally {
                if (dragStarted) latestOnDragCancel()
            }
        }
    }
}

private enum class PressOutcome { Tap, Scroll, LongPress, Cancelled }
