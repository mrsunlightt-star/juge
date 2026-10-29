package com.juge.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.juge.app.data.Category
import com.juge.app.data.Reminder
import com.juge.app.ui.library.AddCategoryDialog
import com.juge.app.ui.library.AddReminderDialog
import com.juge.app.ui.library.EditReminderDialog
import com.juge.app.ui.library.LibraryTabContent
import com.juge.app.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryEntryFlowTest {
    @get:Rule val compose = createComposeRule()

    private val original = Category(id = 1L, name = "原目录")
    private val reminder = Reminder(id = 2L, content = "原始金句", categoryId = original.id)
    private var categories by mutableStateOf(listOf(original))
    private var editing by mutableStateOf<Reminder?>(null)

    @Test
    fun addReminderRejectsOversizedPasteWithoutDiscardingIt() {
        var content by mutableStateOf("")
        var saves = 0
        compose.setContent {
            MyApplicationTheme {
                AddReminderDialog(
                    show = true,
                    content = content,
                    onContentChange = { content = it },
                    categories = categories,
                    selectedCategory = original,
                    onSelectCategory = {},
                    onRequestNewCategory = {},
                    onConfirm = { saves++ },
                    onDismiss = {},
                )
            }
        }
        assertReminderLimit("确认")
        assertEquals(1, saves)
        assertEquals("句".repeat(500), content)
    }

    @Test
    fun editReminderRejectsOversizedPasteWithoutDiscardingIt() {
        var content by mutableStateOf("")
        var saves = 0
        compose.setContent {
            MyApplicationTheme {
                EditReminderDialog(
                    show = true,
                    content = content,
                    onContentChange = { content = it },
                    categories = categories,
                    selectedCategory = original,
                    onSelectCategory = {},
                    onRequestNewCategory = {},
                    onSave = { saves++ },
                    onDismiss = {},
                )
            }
        }
        assertReminderLimit("保存并更新")
        assertEquals(1, saves)
        assertEquals("句".repeat(500), content)
    }

    @Test
    fun categoryRejectsOversizedPasteAndAcceptsExactly100Characters() {
        var name by mutableStateOf("")
        var saves = 0
        compose.setContent {
            MyApplicationTheme {
                AddCategoryDialog(
                    show = true,
                    name = name,
                    onNameChange = { name = it },
                    onConfirm = { saves++ },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("确认").assertIsNotEnabled()
        replaceInput("名".repeat(101))
        compose.onNodeWithText("分类名称最多100个字符").assertIsDisplayed()
        compose.onNodeWithText("确认").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).assertTextContains("名".repeat(101))
        replaceInput("名".repeat(100))
        compose.onNodeWithText("确认").assertIsEnabled().performClick()
        assertEquals(1, saves)
    }

    @Test
    fun cancellingNewCategoryReturnsToEditWithDraftAndEditingTarget() {
        editing = reminder
        var saved: Triple<Long, String, Long>? = null
        setLibrary(onEditSave = { id, content, category -> saved = Triple(id, content, category) })
        replaceInput("未保存的编辑草稿")
        compose.onNodeWithText("+ 新建目录").performClick()
        compose.onNodeWithText("新建分类目录").assertIsDisplayed()
        compose.runOnIdle { assertEquals(reminder, editing) }
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("编辑金句内容").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("未保存的编辑草稿")
        compose.onNodeWithText("保存并更新").performClick()
        compose.runOnIdle { assertEquals(Triple(reminder.id, "未保存的编辑草稿", original.id), saved) }
    }

    @Test
    fun asyncNewCategoryReturnsToEditAndSelectsReturnedIdNotLastCategory() {
        editing = reminder
        val created = Category(id = 8L, name = "新目录")
        val unrelated = Category(id = 9L, name = "并发目录")
        val completion = CompletableDeferred<Category>()
        var saved: Triple<Long, String, Long>? = null
        setLibrary(
            onAddCategory = { completion.await() },
            onEditSave = { id, content, category -> saved = Triple(id, content, category) },
        )
        replaceInput("继续编辑的草稿")
        compose.onNodeWithText("+ 新建目录").performClick()
        replaceInput(created.name)
        compose.onNodeWithText("确认").performClick()
        compose.onNodeWithText("保存中").assertIsNotEnabled()
        compose.onNodeWithText("取消").assertIsNotEnabled()
        compose.runOnIdle {
            assertNotNull(editing)
            categories = listOf(created, original, unrelated)
            editing = reminder.copy(content = "后台回读内容")
            completion.complete(created)
        }
        compose.onNodeWithText("编辑金句内容").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("继续编辑的草稿")
        compose.onNodeWithText("保存并更新").performClick()
        compose.runOnIdle { assertEquals(Triple(reminder.id, "继续编辑的草稿", created.id), saved) }
    }

    @Test
    fun addReminderReturnsFromCancelledCategoryWithItsDraft() {
        var saved: Pair<String, Long>? = null
        setLibrary(onAddReminder = { content, category -> saved = content to category })
        compose.onNodeWithText("录入新句").performClick()
        replaceInput("新增草稿")
        compose.onNodeWithText("+ 新建目录").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("新增记录到句阁").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("新增草稿")
        compose.onNodeWithText("确认").performClick()
        compose.runOnIdle { assertEquals("新增草稿" to original.id, saved) }
    }

    @Test
    fun addReminderSelectsAsyncCreatedCategoryAndRetainsDraft() {
        val created = Category(id = 7L, name = "新增目录")
        val completion = CompletableDeferred<Category>()
        var saved: Pair<String, Long>? = null
        setLibrary(
            onAddCategory = { completion.await() },
            onAddReminder = { content, category -> saved = content to category },
        )
        compose.onNodeWithText("录入新句").performClick()
        replaceInput("新增草稿")
        compose.onNodeWithText("+ 新建目录").performClick()
        replaceInput(created.name)
        compose.onNodeWithText("确认").performClick()
        compose.runOnIdle {
            categories = listOf(created, original)
            completion.complete(created)
        }
        compose.onNodeWithText("新增记录到句阁").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("新增草稿")
        compose.onNodeWithText("确认").performClick()
        compose.runOnIdle { assertEquals("新增草稿" to created.id, saved) }
    }

    @Test
    fun failedCategorySaveRetainsNameAndCanReturnToEdit() {
        editing = reminder
        setLibrary(onAddCategory = { error("write failed") })
        replaceInput("编辑草稿")
        compose.onNodeWithText("+ 新建目录").performClick()
        replaceInput("目录草稿")
        compose.onNodeWithText("确认").performClick()
        assertSaveError()
        compose.onNode(hasSetTextAction()).assertTextContains("目录草稿")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("编辑金句内容").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("编辑草稿")
        compose.runOnIdle { assertEquals(reminder, editing) }
    }

    @Test
    fun failedAddSaveKeepsDraftAndPreventsDuplicatePendingSubmissions() {
        val completion = CompletableDeferred<Unit>()
        var attempts = 0
        setLibrary(onAddReminder = { _, _ ->
            attempts++
            if (attempts == 1) completion.await()
        })
        compose.onNodeWithText("录入新句").performClick()
        replaceInput("保留新增草稿")
        compose.onNodeWithText("确认").performClick()
        compose.onNodeWithText("保存中").assertIsNotEnabled()
        compose.onNodeWithText("取消").assertIsNotEnabled()
        compose.runOnIdle { completion.completeExceptionally(IllegalStateException("write failed")) }
        assertSaveError()
        compose.onNode(hasSetTextAction()).assertTextContains("保留新增草稿")
        compose.runOnIdle { assertEquals(1, attempts) }
        compose.onNodeWithText("确认").assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("新增记录到句阁").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, attempts) }
    }

    @Test
    fun failedEditSaveKeepsTargetAndDraftUntilSuccessfulRetry() {
        editing = reminder
        var attempts = 0
        setLibrary(onEditSave = { _, _, _ ->
            attempts++
            if (attempts == 1) error("write failed")
        })
        replaceInput("保留编辑草稿")
        compose.onNodeWithText("保存并更新").performClick()
        assertSaveError()
        compose.onNode(hasSetTextAction()).assertTextContains("保留编辑草稿")
        compose.runOnIdle { assertEquals(reminder, editing) }
        compose.onNodeWithText("保存并更新").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(2, attempts)
            assertEquals(null, editing)
        }
    }

    @Test
    fun managerOversizedCategoryIsPreservedInValidatedDialog() {
        var attempts = 0
        setLibrary(onAddCategory = { name ->
            attempts++
            original.copy(id = 3L, name = name)
        })
        compose.onNodeWithContentDescription("管理分类").performClick()
        replaceInput("名".repeat(101))
        compose.onNodeWithText("添加").performClick()
        compose.onNodeWithText("新建分类目录").assertIsDisplayed()
        compose.onNodeWithText("分类名称最多100个字符").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("名".repeat(101))
        compose.onNodeWithText("确认").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, attempts) }
    }

    @Test
    fun renameCategoryValidatesLengthAndRetainsFailedDraft() {
        var attempts = 0
        setLibrary(onRenameCategory = { _, _ ->
            attempts++
            error("write failed")
        })
        compose.onNodeWithContentDescription("管理分类").performClick()
        compose.onNodeWithContentDescription("更多分类操作").performClick()
        compose.onNodeWithText("重命名").performClick()
        replaceInput("名".repeat(101))
        compose.onNodeWithText("分类名称最多100个字符").assertIsDisplayed()
        compose.onNodeWithText("保存").assertIsNotEnabled()
        replaceInput("名".repeat(100))
        compose.onNodeWithText("保存").assertIsEnabled().performClick()
        assertSaveError()
        compose.onNode(hasSetTextAction()).assertTextContains("名".repeat(100))
        compose.runOnIdle { assertEquals(1, attempts) }
    }

    private fun assertReminderLimit(button: String) {
        compose.onNodeWithText(button).assertIsNotEnabled()
        replaceInput("句".repeat(501))
        compose.onNodeWithText("金句内容最多500个字符").assertIsDisplayed()
        compose.onNodeWithText(button).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).assertTextContains("句".repeat(501))
        replaceInput("句".repeat(500))
        compose.onNodeWithText(button).assertIsEnabled().performClick()
    }

    private fun replaceInput(text: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(text)
    }

    private fun assertSaveError() {
        compose.onNodeWithText("保存失败，请重试，输入内容已保留").assertIsDisplayed()
    }

    private fun setLibrary(
        onAddCategory: suspend (String) -> Category = { name -> original.copy(id = 3L, name = name) },
        onAddReminder: suspend (String, Long) -> Unit = { _, _ -> },
        onEditSave: suspend (Long, String, Long) -> Unit = { _, _, _ -> },
        onRenameCategory: suspend (Long, String) -> Unit = { _, _ -> },
    ) {
        compose.setContent {
            MyApplicationTheme {
                LibraryTabContent(
                    categories = categories,
                    reminders = listOf(reminder),
                    isLoaded = true,
                    selectedCategory = null,
                    editingReminder = editing,
                    onEditReminderRequest = { editing = it },
                    onCategorySelect = {},
                    onAddCategory = onAddCategory,
                    onDeleteCategory = {},
                    onRenameCategory = onRenameCategory,
                    onReorderReminders = {},
                    onReorderCategories = {},
                    onAddReminder = onAddReminder,
                    onEditReminderSave = onEditSave,
                    onDeleteReminder = {},
                    onReminderClick = {},
                    onOpenDoc = {},
                )
            }
        }
    }
}
