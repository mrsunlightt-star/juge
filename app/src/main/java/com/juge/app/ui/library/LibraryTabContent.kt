package com.juge.app.ui.library

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.juge.app.R
import com.juge.app.data.Category
import com.juge.app.data.Reminder
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppShadows
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.dangerRed
import com.juge.app.ui.theme.rememberAppHaptics
import com.juge.app.ui.theme.selectBlue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

private enum class CategoryDialogOrigin { AddReminder, EditReminder, Manager }

private suspend fun saveLibraryEntry(block: suspend () -> Unit): String? = try {
    block()
    null
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Timber.e(error, "Library save failed")
    "保存失败，请重试，输入内容已保留"
}

// --- 子Tab：我的文库内容 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryTabContent(
    categories: List<Category>,
    reminders: List<Reminder>,
    isLoaded: Boolean,
    selectedCategory: Category?,
    editingReminder: Reminder?,
    onEditReminderRequest: (Reminder?) -> Unit,
    onCategorySelect: (Category?) -> Unit,
    onAddCategory: suspend (String) -> Category,
    onDeleteCategory: (Long) -> Unit,
    onRenameCategory: suspend (Long, String) -> Unit,
    /** 金句拖拽排序落库：参数为全局新顺序的 id 序列 */
    onReorderReminders: (List<Long>) -> Unit,
    /** 分类拖拽排序落库：参数为新顺序的 id 序列 */
    onReorderCategories: (List<Long>) -> Unit,
    onAddReminder: suspend (String, Long) -> Unit,
    onEditReminderSave: suspend (Long, String, Long) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    onReminderClick: (Reminder) -> Unit,
    onOpenDoc: (String) -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    var categoryOrigin by remember { mutableStateOf(CategoryDialogOrigin.Manager) }
    var categorySaving by remember { mutableStateOf(false) }
    var categorySaveError by remember { mutableStateOf<String?>(null) }
    var reminderSaving by remember { mutableStateOf(false) }
    var reminderSaveError by remember { mutableStateOf<String?>(null) }
    var editSaving by remember { mutableStateOf(false) }
    var editSaveError by remember { mutableStateOf<String?>(null) }
    var renameSaving by remember { mutableStateOf(false) }
    var renameSaveError by remember { mutableStateOf<String?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddReminderDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var newReminderContent by remember { mutableStateOf("") }
    var selectedCatForReminder by remember { mutableStateOf<Category?>(categories.firstOrNull()) }

    // 纯文本及分类修改对话框的控制状态
    var editContentField by remember { mutableStateOf("") }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }
    var deletingReminder by remember { mutableStateOf<Reminder?>(null) }
    var editCategorySelection by remember { mutableStateOf<Category?>(null) }

    // 分类管理中心对话框控制状态
    var showCategoryManagerDialog by remember { mutableStateOf(false) }
    var renamingCategory by remember { mutableStateOf<Category?>(null) }
    var renameCategoryNewName by remember { mutableStateOf("") }

    // 长按上下文菜单：金句卡片与分类 chip 共用一个浮层（编辑/删除、重命名/删除）
    var quoteMenuFor by remember { mutableStateOf<Reminder?>(null) }
    var categoryMenuFor by remember { mutableStateOf<Category?>(null) }
    var menuAnchor by remember { mutableStateOf(Offset.Zero) }
    val closeMenu = {
        quoteMenuFor = null
        categoryMenuFor = null
    }

    LaunchedEffect(editingReminder?.id) {
        editSaveError = null
        if (editingReminder != null) {
            editContentField = editingReminder.content
            editCategorySelection = categories.find { it.id == editingReminder.categoryId }
        }
    }

    fun saveCategory() {
        if (categorySaving || newCategoryName.isBlank() || categoryNameError(newCategoryName) != null) return
        categorySaving = true
        categorySaveError = null
        scope.launch {
            try {
                categorySaveError = saveLibraryEntry {
                    val category = onAddCategory(newCategoryName)
                    when (categoryOrigin) {
                        CategoryDialogOrigin.AddReminder -> selectedCatForReminder = category
                        CategoryDialogOrigin.EditReminder -> editCategorySelection = category
                        CategoryDialogOrigin.Manager -> Unit
                    }
                    newCategoryName = ""
                    showAddCategoryDialog = false
                }
            } finally {
                categorySaving = false
            }
        }
    }

    val filteredReminders = if (selectedCategory == null) {
        reminders
    } else {
        reminders.filter { it.categoryId == selectedCategory.id }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CategoryTabRow(
                categories = categories,
                selectedCategory = selectedCategory,
                onCategorySelect = onCategorySelect,
                onRenameRequest = { cat ->
                    renamingCategory = cat
                    renameCategoryNewName = cat.name
                },
                onDeleteRequest = { deletingCategory = it },
                onMenuRequest = { cat, anchor ->
                    categoryMenuFor = cat
                    menuAnchor = anchor
                },
                onReorder = { ordered -> onReorderCategories(ordered.map { it.id }) },
                onDragStart = { closeMenu() },
                onOpenManager = { showCategoryManagerDialog = true }
            )

            Spacer(modifier = Modifier.height(AppSpacing.itemGap))

            ReminderList(
                reminders = filteredReminders,
                categories = categories,
                isLoaded = isLoaded,
                onReminderClick = onReminderClick,
                onEditRequest = onEditReminderRequest,
                onDeleteRequest = { deletingReminder = it },
                onMenuRequest = { reminder, anchor ->
                    quoteMenuFor = reminder
                    menuAnchor = anchor
                },
                onReorder = { reordered ->
                    // 目录内排序只换该目录成员在全局序列里的相对位置，其他分类原位不动
                    val reorderedIds = reordered.map { it.id }.toSet()
                    val merged = reminders.toMutableList()
                    var next = 0
                    for (i in merged.indices) {
                        if (merged[i].id in reorderedIds) merged[i] = reordered[next++]
                    }
                    onReorderReminders(merged.map { it.id })
                },
                onDragStart = { closeMenu() },
                onOpenDoc = onOpenDoc
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .height(50.dp)
                .shadow(
                    elevation = AppShadows.dialog,
                    shape = AppShape.pill,
                    clip = false,
                    ambientColor = AppShadows.ambientShadow,
                    spotColor = AppShadows.spotShadow,
                )
                .clip(AppShape.pill)
                .background(selectBlue)
                .clickable(role = Role.Button) {
                    haptics.click()
                    if (categories.isEmpty()) {
                        Toast.makeText(context, "请先创建一个分类目录", Toast.LENGTH_SHORT).show()
                    } else {
                        selectedCatForReminder = categories.first()
                        showAddReminderDialog = true
                    }
                }
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("录入新句", style = AppType.button, color = Color.White)
            }
        }

        // 长按上下文菜单浮层：盖住整页（含 FAB），指尖弹出。菜单项的跳转全部
        // 复用既有弹窗链路：编辑走 EditReminderDialog，删除走各自的二次确认弹窗。
        val contextMenuItems = when {
            quoteMenuFor != null -> listOf(
                ContextMenuItem(R.drawable.ic_symbol_edit_square, "编辑金句", accentBlue) {
                    val target = quoteMenuFor
                    closeMenu()
                    if (target != null) onEditReminderRequest(target)
                },
                ContextMenuItem(R.drawable.ic_symbol_delete, "删除金句", dangerRed, dangerRed) {
                    val target = quoteMenuFor
                    closeMenu()
                    if (target != null) deletingReminder = target
                },
            )
            categoryMenuFor != null -> listOf(
                ContextMenuItem(R.drawable.ic_symbol_edit_square, "重命名", accentBlue) {
                    val target = categoryMenuFor
                    closeMenu()
                    if (target != null) {
                        renamingCategory = target
                        renameCategoryNewName = target.name
                    }
                },
                ContextMenuItem(R.drawable.ic_symbol_delete, "删除", dangerRed, dangerRed) {
                    val target = categoryMenuFor
                    closeMenu()
                    if (target != null) deletingCategory = target
                },
            )
            else -> null
        }
        if (contextMenuItems != null) {
            ContextMenuOverlay(
                anchorInRoot = menuAnchor,
                items = contextMenuItems,
                onDismiss = { closeMenu() }
            )
        }
    }

    // --- 全部弹窗：状态留在这里，弹窗实现见 LibraryEntryDialogs / CategoryManagerDialogs ---

    // 1. 新建分类弹窗
    AddCategoryDialog(
        show = showAddCategoryDialog,
        name = newCategoryName,
        onNameChange = {
            newCategoryName = it
            categorySaveError = null
        },
        onConfirm = { saveCategory() },
        onDismiss = { showAddCategoryDialog = false },
        isSaving = categorySaving,
        errorMessage = categorySaveError
    )

    // 2. 新增句阁记录弹窗
    AddReminderDialog(
        show = showAddReminderDialog && !showAddCategoryDialog,
        content = newReminderContent,
        onContentChange = {
            newReminderContent = it
            reminderSaveError = null
        },
        categories = categories,
        selectedCategory = selectedCatForReminder,
        onSelectCategory = { selectedCatForReminder = it },
        onRequestNewCategory = {
            categoryOrigin = CategoryDialogOrigin.AddReminder
            categorySaveError = null
            showAddCategoryDialog = true
        },
        onConfirm = {
            val category = selectedCatForReminder
            if (!reminderSaving && category != null) {
                reminderSaving = true
                reminderSaveError = null
                scope.launch {
                    try {
                        reminderSaveError = saveLibraryEntry {
                            onAddReminder(newReminderContent, category.id)
                            newReminderContent = ""
                            showAddReminderDialog = false
                        }
                    } finally {
                        reminderSaving = false
                    }
                }
            }
        },
        onDismiss = { showAddReminderDialog = false },
        isSaving = reminderSaving,
        errorMessage = reminderSaveError
    )

    // 3. 点击金句卡片后：弹出的纯粹文字与分类编辑弹窗
    EditReminderDialog(
        show = editingReminder != null && !showAddCategoryDialog,
        content = editContentField,
        onContentChange = {
            editContentField = it
            editSaveError = null
        },
        categories = categories,
        selectedCategory = editCategorySelection,
        onSelectCategory = { editCategorySelection = it },
        onRequestNewCategory = {
            categoryOrigin = CategoryDialogOrigin.EditReminder
            categorySaveError = null
            showAddCategoryDialog = true
        },
        onSave = {
            val reminder = editingReminder
            val category = editCategorySelection
            if (!editSaving && reminder != null && category != null) {
                editSaving = true
                editSaveError = null
                scope.launch {
                    try {
                        editSaveError = saveLibraryEntry {
                            onEditReminderSave(reminder.id, editContentField, category.id)
                            onEditReminderRequest(null)
                        }
                    } finally {
                        editSaving = false
                    }
                }
            }
        },
        onDismiss = { onEditReminderRequest(null) },
        isSaving = editSaving,
        errorMessage = editSaveError
    )

    // 5. 分类管理中心弹窗（排序已移到文库页长按拖拽，这里只管重命名/删除/新建）
    CategoryManagerDialog(
        show = showCategoryManagerDialog && !showAddCategoryDialog && renamingCategory == null,
        categories = categories,
        onRenameRequest = { cat ->
            renamingCategory = cat
            renameCategoryNewName = cat.name
            renameSaveError = null
        },
        onDeleteRequest = { deletingCategory = it },
        onAddCategory = { name ->
            newCategoryName = name
            categoryOrigin = CategoryDialogOrigin.Manager
            categorySaveError = null
            showAddCategoryDialog = true
            saveCategory()
        },
        onDismiss = { showCategoryManagerDialog = false }
    )

    // 6. 重命名分类子 Dialog
    AddCategoryDialog(
        show = renamingCategory != null,
        name = renameCategoryNewName,
        onNameChange = {
            renameCategoryNewName = it
            renameSaveError = null
        },
        onConfirm = {
            val category = renamingCategory
            if (!renameSaving && category != null) {
                renameSaving = true
                renameSaveError = null
                scope.launch {
                    try {
                        renameSaveError = saveLibraryEntry {
                            onRenameCategory(category.id, renameCategoryNewName)
                            renamingCategory = null
                        }
                    } finally {
                        renameSaving = false
                    }
                }
            }
        },
        onDismiss = {
            renamingCategory = null
            renameSaveError = null
        },
        isSaving = renameSaving,
        errorMessage = renameSaveError,
        title = "修改分类目录名称",
        confirmText = "保存"
    )

    // 4. 二次确认删除分类目录弹窗 (防误触高级 Dialog)；退出动画期间对象已被置
    // null，组件内部用末次快照继续渲染（见 DeleteCategoryDialog）
    val delCat = deletingCategory
    DeleteCategoryDialog(
        show = delCat != null,
        category = delCat,
        onConfirm = {
            if (delCat != null) onDeleteCategory(delCat.id)
            deletingCategory = null
        },
        onDismiss = { deletingCategory = null }
    )

    // 7. 二次确认删除金句：与分类删除保持一致，避免小热区误触直接删库
    val delReminder = deletingReminder
    DeleteReminderDialog(
        show = delReminder != null,
        reminder = delReminder,
        onConfirm = {
            if (delReminder != null) onDeleteReminder(delReminder.id)
            deletingReminder = null
        },
        onDismiss = { deletingReminder = null }
    )
}
