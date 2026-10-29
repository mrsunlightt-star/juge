package com.juge.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.juge.app.R
import com.juge.app.data.Category
import com.juge.app.ui.AppDangerButton
import com.juge.app.ui.AppDialog
import com.juge.app.ui.AppDialogHeader
import com.juge.app.ui.AppDialogSurface
import com.juge.app.ui.AppDialogTitle
import com.juge.app.ui.AppDropdownMenu
import com.juge.app.ui.AppFormGroup
import com.juge.app.ui.AppFormRow
import com.juge.app.ui.AppMenuItem
import com.juge.app.ui.AppPrimaryButton
import com.juge.app.ui.AppText
import com.juge.app.ui.AppTextButton
import com.juge.app.ui.AppTextField
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppTextRole
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.appFontWeight
import com.juge.app.ui.theme.dangerRed
import com.juge.app.ui.theme.rememberAppHaptics
import com.juge.app.ui.theme.textGray
import com.juge.app.ui.theme.textPrimary

/**
 * 金句分类管理中心：重命名、删除、长按拖拽排序、inline 新建。
 *
 * 排序与「跃然纸上」页的分类 chip 共用 [ReorderDragState]：长按分类行后上下拖动，
 * 相邻行用 `animateItem` 让位；松手把新顺序交给 [onReorderCategories] 落库。
 * 这样在管理弹窗里就能调整展示顺序，不必退回列表页操作。
 *
 * 字号/字重：整屏正文比常规弹窗大一档（16sp / Medium），标题保持原样——
 * 分类管理是需要逐行阅读和逐行操作的设置页，小字读起来吃力（用户反馈）。
 */
@Composable
internal fun CategoryManagerDialog(
    show: Boolean,
    categories: List<Category>,
    onRenameRequest: (Category) -> Unit,
    onDeleteRequest: (Category) -> Unit,
    onAddCategory: (String) -> Unit,
    onReorderCategories: (List<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        visible = show,
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AppDialogSurface(contentPadding = 20.dp) {
            // 管理页统一字级：16sp / Medium（AppTextRole.dialogBody，见设计规范 §6.1.4）。
            // 菜单、输入框、按钮都取同一个 style，不能只放大某一行。
            val labelRole = AppTextRole.dialogBody
            val bodyStyle = labelRole.style

            // 与取色 / 协议全文等子页面同一颗头部（标题 + 右侧 X），不再用「< 关闭」文字
            AppDialogHeader(title = "金句分类管理中心", onClose = onDismiss)

            // —— 分类列表：长按拖拽排序 ——
            val scope = rememberCoroutineScope()
            val listState = rememberLazyListState()
            val dragState = remember { ReorderDragState<Category> { it.id } }
            val haptics = rememberAppHaptics()
            // 提交后的顺序先在本地顶住，等落库回读的新列表（顺序一致）到达后再放行，
            // 否则松手到状态回传之间列表会按旧顺序闪跳一下
            var pendingOrder by remember { mutableStateOf<List<Category>?>(null) }
            LaunchedEffect(categories) {
                val pending = pendingOrder ?: return@LaunchedEffect
                if (categories.map { it.id } == pending.map { it.id }) pendingOrder = null
            }
            val displayList = dragState.items ?: pendingOrder ?: categories
            DragAutoScrollEffect(state = dragState, listState = listState)

            AppFormGroup {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                ) {
                    itemsIndexed(displayList, key = { _, cat -> cat.id }) { _, cat ->
                        val isDragging = dragState.dragKey == cat.id
                        var pressOffset by remember { mutableStateOf(Offset.Zero) }
                        Box(
                            modifier = if (isDragging) Modifier.zIndex(1f)
                            else Modifier.animateItem()
                        ) {
                            AppFormRow(
                                label = cat.name,
                                labelRole = labelRole,
                                modifier = Modifier
                                    .graphicsLayer {
                                        translationY = if (isDragging) dragState.translationPx else 0f
                                        val lifted = isDragging
                                        scaleX = if (lifted) 1.03f else 1f
                                        scaleY = if (lifted) 1.03f else 1f
                                    }
                                    .longPressMenuDragGestures(
                                        key = cat.id,
                                        // 行本身不需要单击；点按交给右侧溢出菜单
                                        onTap = null,
                                        onLongPress = { local ->
                                            pressOffset = local
                                            haptics.tap()
                                        },
                                        onDragStart = {
                                            haptics.tap()
                                            dragState.start(
                                                items = displayList,
                                                key = cat.id,
                                                grabOffsetPx = pressOffset.y,
                                            )
                                        },
                                        onDrag = { delta ->
                                            dragState.addTranslation(delta.y)
                                            while (dragState.swapIfNeeded(listState.layoutInfo)) {
                                                // 一次大位移可能跨过多项，反复交换直到不再越界
                                            }
                                        },
                                        onDragEnd = {
                                            dragState.settle(scope) { newOrder ->
                                                if (newOrder != null) {
                                                    haptics.confirm()
                                                    pendingOrder = newOrder
                                                    onReorderCategories(newOrder.map { it.id })
                                                }
                                            }
                                        },
                                        onDragCancel = { dragState.cancel() },
                                    ),
                                trailing = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // 重命名/删除收进溢出菜单：4 颗 28dp 按钮并排既挤又容易误触
                                        Box {
                                            var menuExpanded by remember { mutableStateOf(false) }
                                            IconButton(onClick = { menuExpanded = true }) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = "更多分类操作",
                                                    tint = textPrimary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            // 与长按上下文菜单同一颗卡片/同一套行（AppMenu.kt）。
                                            // 此前这里是 Material3 DropdownMenu：容器色取 surfaceContainer，
                                            // 而本项目 colorScheme 没接这个槽位，落回 M3 基线淡紫 #F3EDF7，
                                            // 配 4dp 小圆角、无 App 弥散阴影——在白色弹窗上像块外来贴纸。
                                            AppDropdownMenu(
                                                expanded = menuExpanded,
                                                items = listOf(
                                                    AppMenuItem(
                                                        iconRes = R.drawable.ic_symbol_edit_square,
                                                        label = "重命名",
                                                        iconTint = accentBlue,
                                                        labelRole = AppTextRole.dialogBody,
                                                    ) { onRenameRequest(cat) },
                                                    AppMenuItem(
                                                        iconRes = R.drawable.ic_symbol_delete,
                                                        label = "删除",
                                                        iconTint = dangerRed,
                                                        labelColor = dangerRed,
                                                        labelRole = AppTextRole.dialogBody,
                                                    ) { onDeleteRequest(cat) },
                                                ),
                                                onDismissRequest = { menuExpanded = false },
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 拖拽排序的发现性提示：长按是隐式手势，补一行说明
            AppText(
                text = "长按分类可上下拖动排序",
                role = AppTextRole.bodyTertiary,
            )

            // 底部 inline 新建分类输入区
            var inlineNewCatName by remember { mutableStateOf("") }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.itemGap)
            ) {
                AppTextField(
                    value = inlineNewCatName,
                    onValueChange = { inlineNewCatName = it },
                    placeholder = "新建分类名称",
                    modifier = Modifier.weight(1f),
                    textStyle = bodyStyle,
                    singleLine = true
                )
                AppPrimaryButton(
                    text = "添加",
                    textStyle = bodyStyle.copy(fontWeight = appFontWeight(FontWeight.SemiBold)),
                    onClick = {
                        if (inlineNewCatName.isNotBlank()) {
                            onAddCategory(inlineNewCatName)
                            inlineNewCatName = ""
                        }
                    },
                    enabled = inlineNewCatName.isNotBlank()
                )
            }
        }
    }
}

/** 二次确认删除分类目录（会连目录下的金句一起清空，故单独一个弹窗）。 */
@Composable
internal fun DeleteCategoryDialog(
    show: Boolean,
    category: Category?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // 同 DeleteReminderDialog：留住退出动画期间的最后一次非空值
    var lastCategory by remember { mutableStateOf(category) }
    if (category != null) lastCategory = category
    val bound = lastCategory ?: return

    AppDialog(
        visible = show,
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AppDialogSurface {
            AppDialogTitle("确定要删除分类目录吗？")
            Text(
                text = "确定要删除“${bound.name}”吗？这将同时清空该目录下的所有诗词金句，此操作不可恢复。",
                style = AppType.body,
                color = textGray
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTextButton(text = "取消", onClick = onDismiss)
                Spacer(modifier = Modifier.width(8.dp))
                AppDangerButton(text = "删除并清空", onClick = onConfirm)
            }
        }
    }
}
