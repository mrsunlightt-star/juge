package com.juge.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import com.juge.app.data.Category
import com.juge.app.ui.AppFilterChip
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.rememberAppHaptics
import com.juge.app.ui.theme.textGray

/**
 * 文库顶部分类筛选条：全部 / 各分类 / 分类管理入口。
 *
 * 分类 chip 与金句卡片同一套手势（见 [longPressMenuDragGestures]）：
 * 长按弹「重命名 / 删除」菜单（请求上报给页面浮层，二次确认弹窗由页面持有），
 * 长按后拖动 = 排序（[onReorder] 提交新顺序）；单击仍是选中筛选。
 * 分类页与管理弹窗都支持长按拖拽排序（同一个 ReorderDragState 状态机）。
 */
@Composable
internal fun CategoryTabRow(
    categories: List<Category>,
    selectedCategory: Category?,
    onCategorySelect: (Category?) -> Unit,
    onRenameRequest: (Category) -> Unit,
    onDeleteRequest: (Category) -> Unit,
    onMenuRequest: (category: Category, anchorInRoot: Offset) -> Unit,
    onReorder: (List<Category>) -> Unit,
    onDragStart: () -> Unit,
    onOpenManager: () -> Unit
) {
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val dragState = remember { ReorderDragState<Category> { it.id } }

    var pendingOrder by remember { mutableStateOf<List<Category>?>(null) }
    LaunchedEffect(categories) {
        val pending = pendingOrder ?: return@LaunchedEffect
        if (categories.map { it.id } == pending.map { it.id }) pendingOrder = null
    }
    val displayList = dragState.items ?: pendingOrder ?: categories

    DragAutoScrollEffect(state = dragState, listState = listState)

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.screen),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.itemGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            AppFilterChip(
                selected = selectedCategory == null,
                onClick = { onCategorySelect(null) },
                label = "全部",
            )
        }
        itemsIndexed(displayList, key = { _, cat -> cat.id }) { _, cat ->
            val isDragging = dragState.dragKey == cat.id
            var chipRootPos by remember { mutableStateOf(Offset.Zero) }
            var pressOffset by remember { mutableStateOf(Offset.Zero) }
            Box(
                modifier = if (isDragging) Modifier.zIndex(1f) else Modifier.animateItem()
            ) {
                AppFilterChip(
                    selected = selectedCategory?.id == cat.id,
                    onClick = { onCategorySelect(cat) },
                    label = cat.name,
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = if (isDragging) dragState.translationPx else 0f
                            scaleX = if (isDragging) 1.05f else 1f
                            scaleY = if (isDragging) 1.05f else 1f
                        }
                        .onGloballyPositioned { chipRootPos = it.positionInRoot() }
                        .semantics {
                            // 长按菜单是视觉手势，TalkBack 走自定义操作直达重命名/删除
                            customActions = listOf(
                                CustomAccessibilityAction("重命名分类") {
                                    onRenameRequest(cat)
                                    true
                                },
                                CustomAccessibilityAction("删除分类") {
                                    onDeleteRequest(cat)
                                    true
                                },
                            )
                        }
                        .longPressMenuDragGestures(
                            key = cat.id,
                            // 单击仍归 AppFilterChip 自己的 onClick，这里不接管
                            onTap = {},
                            onLongPress = { local ->
                                pressOffset = local
                                haptics.tap()
                                onMenuRequest(cat, chipRootPos + local)
                            },
                            onDragStart = {
                                onDragStart()
                                haptics.tap()
                                dragState.start(
                                    items = displayList,
                                    key = cat.id,
                                    grabOffsetPx = pressOffset.x,
                                )
                            },
                            onDrag = { delta ->
                                dragState.addTranslation(delta.x)
                                while (dragState.swapIfNeeded(listState.layoutInfo)) {
                                    // 交换在 state 内部完成
                                }
                            },
                            onDragEnd = {
                                dragState.settle(scope) { newOrder ->
                                    if (newOrder != null) {
                                        haptics.confirm()
                                        pendingOrder = newOrder
                                        onReorder(newOrder)
                                    }
                                }
                            },
                            onDragCancel = { dragState.cancel() },
                        )
                )
            }
        }
        item {
            // 不覆写尺寸：IconButton 自带 48dp 触控热区
            IconButton(onClick = onOpenManager) {
                Icon(Icons.Default.Settings, contentDescription = "管理分类", tint = textGray)
            }
        }
    }
}
