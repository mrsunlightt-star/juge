package com.juge.app.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.juge.app.data.Category
import com.juge.app.data.Reminder
import com.juge.app.ui.AppSectionCard
import com.juge.app.ui.AppText
import com.juge.app.ui.legal.LegalFooter
import com.juge.app.ui.theme.AppTextRole
import com.juge.app.ui.theme.AppMotion
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.rememberAppHaptics
import com.juge.app.ui.theme.selectBlue
import com.juge.app.ui.theme.textTertiary

/**
 * 金句列表：逐条卡片 + 空态 + 页脚。
 *
 * 交互口径（iOS 备忘录同款）：
 * - 单击 = 应用金句到组件；
 * - 长按 = 指尖弹出「编辑 / 删除」菜单（浮层由页面持有，[onMenuRequest] 只上报请求与锚点）；
 * - 长按后继续拖动 = 菜单取消、卡片抬起进入排序，松手弹性归位并把新顺序交给 [onReorder] 落库。
 * 编辑/删除不再放在卡面上：两个 40dp 图标按钮挤占卡面高度，且 iOS 惯例就是收进长按菜单。
 */
@Composable
internal fun ReminderList(
    reminders: List<Reminder>,
    categories: List<Category>,
    isLoaded: Boolean,
    onReminderClick: (Reminder) -> Unit,
    onEditRequest: (Reminder) -> Unit,
    onDeleteRequest: (Reminder) -> Unit,
    onMenuRequest: (reminder: Reminder, anchorInRoot: Offset) -> Unit,
    onReorder: (List<Reminder>) -> Unit,
    onDragStart: () -> Unit,
    onOpenDoc: (String) -> Unit
) {
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val dragState = remember { ReorderDragState<Reminder> { it.id } }
    // 只给初次出现的前几条或后来新增到顶部的条目播放一次进场动画；普通滚动不反复动画。
    val animatedReminderIds = remember { mutableSetOf<Long>() }

    // 提交后的顺序先在本地顶住，等 onReorder 落库回读的新列表（顺序一致）到达后再放行，
    // 否则松手到状态回传之间列表会按旧顺序闪跳一下
    var pendingOrder by remember { mutableStateOf<List<Reminder>?>(null) }
    LaunchedEffect(reminders) {
        val pending = pendingOrder ?: return@LaunchedEffect
        if (reminders.map { it.id } == pending.map { it.id }) pendingOrder = null
    }
    val displayList = dragState.items ?: pendingOrder ?: reminders

    DragAutoScrollEffect(state = dragState, listState = listState)

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.itemGap)
    ) {
        if (isLoaded && displayList.isEmpty()) {
            item(key = "empty_state") {
                EmptyReminderState(hasCategories = categories.isNotEmpty())
            }
        } else {
            itemsIndexed(displayList, key = { _, reminder -> reminder.id }) { index, reminder ->
                val catName = categories.find { it.id == reminder.categoryId }?.name ?: "普通"

                val shouldAnimate = index < 4 && reminder.id !in animatedReminderIds
                if (shouldAnimate) SideEffect { animatedReminderIds.add(reminder.id) }
                val visibleState = remember(reminder.id) {
                    MutableTransitionState(initialState = !shouldAnimate)
                }.apply { targetState = true }
                val isDragging = dragState.dragKey == reminder.id
                // 拖拽项不加 animateItem：它的视觉位置由 translation 手动钉住，
                // 再叠加让位动画会双重位移；其余卡片靠 animateItem 平滑让位
                AnimatedVisibility(
                    visibleState = visibleState,
                    enter = slideInVertically(
                        animationSpec = AppMotion.entrance()
                    ) { it / 2 } + fadeIn(
                        animationSpec = AppMotion.entrance()
                    ),
                    modifier = if (isDragging) Modifier.zIndex(1f) else Modifier.animateItem(
                        placementSpec = AppMotion.entrance()
                    ),
                ) {
                    // 手指位置：长按回调给的是卡片内坐标，加卡片 root 坐标得到菜单锚点
                    var cardRootPos by remember { mutableStateOf(Offset.Zero) }
                    var pressOffset by remember { mutableStateOf(Offset.Zero) }
                    AppSectionCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { cardRootPos = it.positionInRoot() }
                            // mergeDescendants：TalkBack 把整卡当单个元素朗读（旧 .clickable
                            // 自带合并，换成裸 pointerInput 后要补回来），自定义操作直达编辑/删除
                            .semantics(mergeDescendants = true) {
                                customActions = listOf(
                                    CustomAccessibilityAction("编辑金句") {
                                        onEditRequest(reminder)
                                        true
                                    },
                                    CustomAccessibilityAction("删除金句") {
                                        onDeleteRequest(reminder)
                                        true
                                    },
                                )
                            }
                            .graphicsLayer {
                                translationY = if (isDragging) dragState.translationPx else 0f
                                val lifted = isDragging
                                scaleX = if (lifted) 1.03f else 1f
                                scaleY = if (lifted) 1.03f else 1f
                                // 抬起加一层实影：graphicsLayer 的 shadowElevation 只在
                                // 有数值时画，落回 0 时与卡片自身的弥散阴影并存无冲突
                                shadowElevation = if (lifted) 16.dp.toPx() else 0f
                            }
                            .longPressMenuDragGestures(
                                key = reminder.id,
                                onTap = {
                                    haptics.tap()
                                    onReminderClick(reminder)
                                },
                                onLongPress = { local ->
                                    pressOffset = local
                                    haptics.tap()
                                    onMenuRequest(reminder, cardRootPos + local)
                                },
                                onDragStart = {
                                    onDragStart()
                                    haptics.tap()
                                    dragState.start(
                                        items = displayList,
                                        key = reminder.id,
                                        grabOffsetPx = pressOffset.y,
                                    )
                                },
                                onDrag = { delta ->
                                    dragState.addTranslation(delta.y)
                                    // 一次大位移可能跨过多项，反复交换直到不再越界
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
                            ),
                        contentPadding = PaddingValues(
                            horizontal = AppSpacing.cardPadding,
                            vertical = 12.dp
                        ),
                        animateSize = false,
                    ) {
                        Surface(
                            shape = AppShape.badge,
                            color = selectBlue
                        ) {
                            AppText(
                                text = catName,
                                role = AppTextRole.badgeOnFill,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        AppText(
                            text = reminder.content,
                            role = AppTextRole.bodyLarge
                        )
                    }
                }
            }
        }
        item {
            // 页脚与最后一张卡片之间留出空间：首屏不露出页脚，滚到底时再完整出现
            Spacer(modifier = Modifier.height(80.dp))
        }
        item {
            // 页脚放在按钮上方：滚到底时《字体许可》不会被浮动按钮遮住
            LegalFooter(onOpenDoc)
        }
        item {
            // 底部留白让页脚滚到「录入新句」按钮上方
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

/** 空文库：给下一步动作，而不是留一片空白。 */
@Composable
private fun EmptyReminderState(hasCategories: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 56.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            tint = textTertiary,
            modifier = Modifier.size(34.dp)
        )
        Spacer(modifier = Modifier.height(AppSpacing.itemGap))
        AppText(
            text = if (hasCategories) "这里还没有金句" else "还没有分类目录",
            role = AppTextRole.bodyStrong
        )
        Spacer(modifier = Modifier.height(AppSpacing.tight))
        AppText(
            text = if (hasCategories) {
                "点击右下角「录入新句」，把喜欢的句子收进来。"
            } else {
                "先到「分类管理」建一个目录，再录入第一句。"
            },
            role = AppTextRole.bodySecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}
