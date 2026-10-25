package com.juge.app.ui.library

import androidx.compose.animation.core.animate
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.juge.app.ui.theme.AppMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 长按拖拽排序的共享状态机：金句竖列（LazyColumn）与分类横排（LazyRow）共用。
 *
 * 拖拽期间 [items] 持有本地新顺序，列表用它即时重排（相邻项让位交给
 * `Modifier.animateItem`）；每交换一次，[translationPx] 都按两槽实测 offset 差
 * 校正一次，拖拽项钉在指尖。松手走 [settle]：先弹性归位，再把新顺序交给调用方落库；
 * [cancel] 丢弃本地顺序，一切照旧。
 */
internal class ReorderDragState<T : Any>(
    private val keyOf: (T) -> Any
) {
    /** 正在拖拽的项 key；settle 归位动画期间仍非空，新手势不得抢占 */
    var dragKey: Any? by mutableStateOf(null)
        private set

    /** 拖拽项沿主轴的位移（px） */
    var translationPx by mutableFloatStateOf(0f)
        private set

    /** 手指按进卡片内的位置（px，主轴），自动滚动判断到边距离用 */
    var grabOffsetPx by mutableFloatStateOf(0f)
        private set

    /** 拖拽期间的本地顺序；null = 未在拖拽 */
    var items: List<T>? by mutableStateOf(null)
        private set

    private var settling = false

    val isActive: Boolean get() = dragKey != null

    fun start(items: List<T>, key: Any, grabOffsetPx: Float) {
        if (dragKey != null) return
        this.items = items.toList()
        dragKey = key
        translationPx = 0f
        this.grabOffsetPx = grabOffsetPx
    }

    fun addTranslation(deltaPx: Float) {
        translationPx += deltaPx
    }

    /**
     * 松手：拖拽项先弹回所在槽位（位移归零），再交出新顺序。
     * 没拖出任何交换（原地点了一下就松手）时新顺序与旧顺序一致，调用方照常提交无害。
     */
    fun settle(scope: CoroutineScope, onCommit: (List<T>?) -> Unit) {
        if (dragKey == null || settling) return
        settling = true
        scope.launch {
            animate(
                initialValue = translationPx,
                targetValue = 0f,
                animationSpec = AppMotion.standard(),
            ) { value, _ -> translationPx = value }
            val result = items
            clear()
            onCommit(result)
        }
    }

    fun cancel() {
        if (settling) return
        clear()
    }

    private fun clear() {
        items = null
        dragKey = null
        translationPx = 0f
        grabOffsetPx = 0f
        settling = false
    }

    /**
     * 拖拽项中心越过相邻项中心时交换一次，并校正 [translationPx]。
     * 返回是否发生了交换（调用方在外层 while 里反复调用，可一次跨越多格）。
     */
    fun swapIfNeeded(layoutInfo: LazyListLayoutInfo): Boolean {
        val list = items ?: return false
        val key = dragKey ?: return false
        val dragged = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return false
        val draggedIndex = list.indexOfFirst { keyOf(it) == key }
        if (draggedIndex < 0) return false
        val center = dragged.offset + dragged.size / 2f + translationPx
        val gap = layoutInfo.mainAxisItemSpacing.toFloat()

        val next = layoutInfo.visibleItemsInfo.firstOrNull { it.index == dragged.index + 1 }
        if (next != null && draggedIndex < list.lastIndex && center > next.offset + next.size / 2f) {
            items = list.toMutableList().apply { add(draggedIndex, removeAt(draggedIndex + 1)) }
            // 交换后拖拽项的 layout offset 下移 (next.size + gap)，位移反向补偿钉在指尖
            translationPx -= next.size + gap
            return true
        }
        val prev = layoutInfo.visibleItemsInfo.firstOrNull { it.index == dragged.index - 1 }
        if (prev != null && draggedIndex > 0 && center < prev.offset + prev.size / 2f) {
            items = list.toMutableList().apply { add(draggedIndex, removeAt(draggedIndex - 1)) }
            // 交换后拖拽项的 layout offset 上移 (prev.size + gap)
            translationPx += prev.size + gap
            return true
        }
        return false
    }
}

/**
 * 拖拽期间贴近列表上下边缘时匀速滚动；每帧同时做一次相邻交换检测，
 * 手指停在边缘不动、内容滚过时也能连续移位。
 */
@Composable
internal fun DragAutoScrollEffect(
    state: ReorderDragState<*>,
    listState: LazyListState,
) {
    val density = LocalDensity.current
    LaunchedEffect(state) {
        snapshotFlow { state.isActive }.collect { active ->
            if (!active) return@collect
            val edgePx = with(density) { 56.dp.toPx() }
            val stepPx = with(density) { 9.dp.toPx() }
            while (state.isActive && isActive) {
                withFrameNanos { }
                val info = listState.layoutInfo
                val dragged = state.dragKey?.let { key ->
                    info.visibleItemsInfo.firstOrNull { it.key == key }
                } ?: break
                // 指针在视口坐标系里的位置：拖拽项 offset + 按点位移 + 拖拽位移
                val pointer = dragged.offset + state.grabOffsetPx + state.translationPx
                val canScrollUp = listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > 0
                val last = info.visibleItemsInfo.lastOrNull()
                val canScrollDown = last != null &&
                    (last.index < info.totalItemsCount - 1 ||
                        last.offset + last.size > info.viewportEndOffset)
                when {
                    pointer < info.viewportStartOffset + edgePx && canScrollUp -> {
                        scrollByAndCompensate(listState, state, -stepPx)
                    }
                    pointer > info.viewportEndOffset - edgePx && canScrollDown -> {
                        scrollByAndCompensate(listState, state, stepPx)
                    }
                }
            }
        }
    }
}

private suspend fun scrollByAndCompensate(
    listState: LazyListState,
    state: ReorderDragState<*>,
    deltaPx: Float,
) {
    val key = state.dragKey ?: return
    val before = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.offset
        ?: return
    listState.dispatchRawDelta(deltaPx)
    val after = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.offset
        ?: return
    // 滚动改变拖拽项的 layout offset，位移补上同量差值，卡片钉在指尖
    state.addTranslation((before - after).toFloat())
    // 手指不动也可能已把相邻项滚过中心，补一次交换检测
    var swapped = true
    while (swapped) {
        swapped = state.swapIfNeeded(listState.layoutInfo)
    }
}
