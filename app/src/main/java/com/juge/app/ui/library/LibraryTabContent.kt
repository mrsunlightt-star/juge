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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juge.app.data.Category
import com.juge.app.data.Reminder
import com.juge.app.ui.theme.selectBlue

// --- 子Tab：我的文库内容 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryTabContent(
    categories: List<Category>,
    reminders: List<Reminder>,
    selectedCategory: Category?,
    editingReminder: Reminder?,
    onEditReminderRequest: (Reminder?) -> Unit,
    onCategorySelect: (Category?) -> Unit,
    onAddCategory: (String) -> Unit,
    onDeleteCategory: (Long) -> Unit,
    onRenameCategory: (Long, String) -> Unit,
    onSwapCategoryOrder: (Long, Int, Long, Int) -> Unit,
    onAddReminder: (String, Long) -> Unit,
    onEditReminderSave: (Long, String, Long) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    onReminderClick: (Reminder) -> Unit,
    onOpenDoc: (String) -> Unit
) {
    val context = LocalContext.current
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddReminderDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var newReminderContent by remember { mutableStateOf("") }
    var selectedCatForReminder by remember { mutableStateOf<Category?>(categories.firstOrNull()) }

    // 纯文本及分类修改对话框的控制状态
    var editContentField by remember { mutableStateOf("") }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }
    var editCategorySelection by remember { mutableStateOf<Category?>(null) }

    // 分类管理中心对话框控制状态
    var showCategoryManagerDialog by remember { mutableStateOf(false) }
    var renamingCategory by remember { mutableStateOf<Category?>(null) }
    var renameCategoryNewName by remember { mutableStateOf("") }

    LaunchedEffect(editingReminder) {
        if (editingReminder != null) {
            editContentField = editingReminder.content
            editCategorySelection = categories.find { it.id == editingReminder.categoryId }
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
                onDeleteRequest = { deletingCategory = it },
                onOpenManager = { showCategoryManagerDialog = true }
            )

            Spacer(modifier = Modifier.height(10.dp))

            ReminderList(
                reminders = filteredReminders,
                categories = categories,
                onReminderClick = onReminderClick,
                onEditRequest = onEditReminderRequest,
                onDeleteReminder = onDeleteReminder,
                onOpenDoc = onOpenDoc
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .height(50.dp)
                .shadow(6.dp, RoundedCornerShape(25.dp))
                .clip(RoundedCornerShape(25.dp))
                .background(selectBlue)
                .clickable {
                    if (categories.isEmpty()) {
                        Toast.makeText(context, "请先创建一个分类目录", Toast.LENGTH_SHORT).show()
                    } else {
                        selectedCatForReminder = categories.first()
                        showAddReminderDialog = true
                    }
                }
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("录入新句", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            }
        }
    }

    // --- 全部弹窗：状态留在这里，弹窗实现见 LibraryEntryDialogs / CategoryManagerDialogs ---

    // 1. 新建分类弹窗
    if (showAddCategoryDialog) {
        AddCategoryDialog(
            name = newCategoryName,
            onNameChange = { newCategoryName = it },
            onConfirm = {
                onAddCategory(newCategoryName)
                newCategoryName = ""
                showAddCategoryDialog = false
            },
            onDismiss = { showAddCategoryDialog = false }
        )
    }

    // 2. 新增句阁记录弹窗
    if (showAddReminderDialog) {
        AddReminderDialog(
            content = newReminderContent,
            onContentChange = { newReminderContent = it },
            categories = categories,
            selectedCategory = selectedCatForReminder,
            onSelectCategory = { selectedCatForReminder = it },
            onRequestNewCategory = {
                showAddReminderDialog = false
                showAddCategoryDialog = true
            },
            onConfirm = {
                onAddReminder(newReminderContent, selectedCatForReminder!!.id)
                newReminderContent = ""
                showAddReminderDialog = false
            },
            onDismiss = { showAddReminderDialog = false }
        )
    }

    // 3. 点击金句卡片后：弹出的纯粹文字与分类编辑弹窗
    if (editingReminder != null) {
        EditReminderDialog(
            content = editContentField,
            onContentChange = { editContentField = it },
            categories = categories,
            selectedCategory = editCategorySelection,
            onSelectCategory = { editCategorySelection = it },
            onRequestNewCategory = {
                onEditReminderRequest(null) // 先关掉编辑框
                showAddCategoryDialog = true // 开启新建分类
            },
            onSave = {
                onEditReminderSave(editingReminder.id, editContentField, editCategorySelection!!.id)
            },
            onDismiss = { onEditReminderRequest(null) }
        )
    }

    // 5. 分类管理中心弹窗
    if (showCategoryManagerDialog) {
        CategoryManagerDialog(
            categories = categories,
            onSwapOrder = onSwapCategoryOrder,
            onRenameRequest = { cat ->
                renamingCategory = cat
                renameCategoryNewName = cat.name
            },
            onDeleteRequest = { deletingCategory = it },
            onAddCategory = onAddCategory,
            onDismiss = { showCategoryManagerDialog = false }
        )
    }

    // 6. 重命名分类子 Dialog
    if (renamingCategory != null) {
        RenameCategoryDialog(
            name = renameCategoryNewName,
            onNameChange = { renameCategoryNewName = it },
            onConfirm = {
                onRenameCategory(renamingCategory!!.id, renameCategoryNewName)
                renamingCategory = null
            },
            onDismiss = { renamingCategory = null }
        )
    }

    // 4. 二次确认删除分类目录弹窗 (防误触高级 Dialog)
    if (deletingCategory != null) {
        DeleteCategoryDialog(
            category = deletingCategory!!,
            onConfirm = {
                onDeleteCategory(deletingCategory!!.id)
                deletingCategory = null
            },
            onDismiss = { deletingCategory = null }
        )
    }
}
