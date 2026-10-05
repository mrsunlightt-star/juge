package com.juge.app.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.juge.app.ReminderWidgetProvider
import com.juge.app.data.Category
import com.juge.app.data.DbHelper
import com.juge.app.data.Reminder
import com.juge.app.data.WidgetConfig
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.SaveOutcome
import com.juge.app.ui.library.LibraryTabContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「跃然纸上」页签内容：把书库的增删改查回调接到 [DbHelper]，再交给 [LibraryTabContent]。
 *
 * 从 MainAppScreen 原样搬出（第 3/3 步拆 MainActivity）。状态仍归 MainAppScreen 所有，
 * 这里只做「写库 → 回读 → 把新列表交回调用方」，不持有任何 state。
 */
@Composable
fun LibraryPane(
    dbHelper: DbHelper,
    scope: CoroutineScope,
    categories: List<Category>,
    reminders: List<Reminder>,
    selectedCategory: Category?,
    editingReminder: Reminder?,
    onEditReminderRequest: (Reminder?) -> Unit,
    onCategorySelect: (Category?) -> Unit,
    onCategoriesChanged: (List<Category>) -> Unit,
    onRemindersChanged: (List<Reminder>) -> Unit,
    onWidgetConfigsChanged: (List<WidgetConfig>) -> Unit,
    selectedWidgetId: Int,
    selectedConfigId: Long,
    currentStyle: WidgetStyle,
    onContentChanged: (String) -> Unit,
    onStyleChange: (Int, Long, String, WidgetStyle) -> SaveOutcome,
    onOpenDoc: (String) -> Unit
) {
    val context = LocalContext.current
    LibraryTabContent(
        categories = categories,
        reminders = reminders,
        selectedCategory = selectedCategory,
        editingReminder = editingReminder,
        onEditReminderRequest = onEditReminderRequest,
        onCategorySelect = onCategorySelect,
        onAddCategory = { name ->
            scope.launch(Dispatchers.IO) {
                dbHelper.insertCategory(name)
                val cats = dbHelper.getAllCategories()
                withContext(Dispatchers.Main) {
                    onCategoriesChanged(cats)
                }
            }
        },
        onDeleteCategory = { id ->
            scope.launch(Dispatchers.IO) {
                dbHelper.deleteCategory(id)
                val cats = dbHelper.getAllCategories()
                val rems = dbHelper.getAllReminders()
                withContext(Dispatchers.Main) {
                    onCategoriesChanged(cats)
                    onRemindersChanged(rems)
                }
            }
        },
        onRenameCategory = { id, name ->
            scope.launch(Dispatchers.IO) {
                dbHelper.renameCategory(id, name)
                val cats = dbHelper.getAllCategories()
                withContext(Dispatchers.Main) {
                    onCategoriesChanged(cats)
                }
            }
        },
        onSwapCategoryOrder = { cat1Id, cat1Order, cat2Id, cat2Order ->
            scope.launch(Dispatchers.IO) {
                dbHelper.swapCategoryOrder(cat1Id, cat1Order, cat2Id, cat2Order)
                val cats = dbHelper.getAllCategories()
                withContext(Dispatchers.Main) {
                    onCategoriesChanged(cats)
                }
            }
        },
        onAddReminder = { content, catId ->
            scope.launch(Dispatchers.IO) {
                dbHelper.insertReminder(content, catId)
                val rems = dbHelper.getAllReminders()
                withContext(Dispatchers.Main) {
                    onRemindersChanged(rems)
                }
            }
        },
        onEditReminderSave = { id, content, catId ->
            scope.launch(Dispatchers.IO) {
                val rem = reminders.find { it.id == id }
                if (rem != null) {
                    dbHelper.updateReminder(id, content, catId, rem.isFavorite, rem.styleJson)
                    val rems = dbHelper.getAllReminders()
                    withContext(Dispatchers.Main) {
                        onRemindersChanged(rems)
                    }
                }
            }
        },
        onDeleteReminder = { id ->
            scope.launch(Dispatchers.IO) {
                dbHelper.deleteReminder(id)
                val rems = dbHelper.getAllReminders()
                withContext(Dispatchers.Main) {
                    onRemindersChanged(rems)
                }
            }
        },
        onReminderClick = { reminder ->
            onContentChanged(reminder.content)
            if (selectedWidgetId != -1) {
                // 绑定可能新建 widget_config 并写入新绑定关系，
                // 必须在绑定完成后重新读取配置 ID，否则下方保存会命中旧的 -1 配置
                val widgetId = selectedWidgetId
                scope.launch(Dispatchers.IO) {
                    ReminderWidgetProvider.bindReminderToWidget(context, widgetId, reminder.id)
                    val newConfigId = ReminderWidgetProvider.getBoundConfigId(context, widgetId)
                    val newConfig = dbHelper.getWidgetConfigById(newConfigId)
                    if (newConfig != null) {
                        dbHelper.updateWidgetConfig(
                            newConfig.copy(
                                content = reminder.content,
                                styleJson = currentStyle.toJsonString()
                            )
                        )
                    }
                    val configs = dbHelper.getAllWidgetConfigs()
                    withContext(Dispatchers.Main) {
                        onWidgetConfigsChanged(configs)
                        onContentChanged(reminder.content)
                    }
                    ReminderWidgetProvider.triggerUpdateAllWidgets(context)
                }
            } else {
                onStyleChange(selectedWidgetId, selectedConfigId, reminder.content, currentStyle)
            }
        },
        onOpenDoc = onOpenDoc
    )
}
