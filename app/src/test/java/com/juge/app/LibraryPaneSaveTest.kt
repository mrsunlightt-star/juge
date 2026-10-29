package com.juge.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.juge.app.data.Category
import com.juge.app.data.DbHelper
import com.juge.app.data.Reminder
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.SaveOutcome
import com.juge.app.ui.main.LibraryPane
import com.juge.app.ui.theme.MyApplicationTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryPaneSaveTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var db: DbHelper
    private var categories by mutableStateOf(emptyList<Category>())
    private var reminders by mutableStateOf(emptyList<Reminder>())
    private var editing by mutableStateOf<Reminder?>(null)

    @Before
    fun setUp() {
        val instance = DbHelper::class.java.getDeclaredField("instance").apply { isAccessible = true }
        (instance.get(null) as? DbHelper)?.close()
        instance.set(null, null)
        db = DbHelper.getInstance(RuntimeEnvironment.getApplication())
        db.writableDatabase.delete("reminders", null, null)
        db.writableDatabase.delete("categories", null, null)
        db.insertCategory("测试目录")
        categories = db.getAllCategories()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun failedInsertResultKeepsDraftAndSuccessfulRetryCreatesOnlyOneReminder() {
        db.writableDatabase.execSQL(
            "CREATE TRIGGER reject_reminder BEFORE INSERT ON reminders BEGIN SELECT RAISE(IGNORE); END"
        )
        setPane()
        compose.onNodeWithText("录入新句").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("数据库失败时保留的草稿")
        compose.onNodeWithText("确认").performClick()
        awaitSaveError()
        compose.onNode(hasSetTextAction()).assertTextContains("数据库失败时保留的草稿")
        assertEquals(0, db.getAllReminders().size)
        db.writableDatabase.execSQL("DROP TRIGGER reject_reminder")
        compose.onNodeWithText("确认").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { reminders.size == 1 }
        assertEquals("数据库失败时保留的草稿", db.getAllReminders().single().content)
        compose.onNodeWithText("新增记录到句阁").assertDoesNotExist()
    }

    @Test
    fun failedUpdateKeepsEditingTargetAndDraftForRetry() {
        val id = db.insertReminder("原内容", categories.single().id)
        reminders = db.getAllReminders()
        editing = reminders.single()
        db.writableDatabase.execSQL(
            "CREATE TRIGGER reject_update BEFORE UPDATE ON reminders BEGIN SELECT RAISE(FAIL, 'write failed'); END"
        )
        setPane()
        compose.onNode(hasSetTextAction()).performTextReplacement("修改后的内容")
        compose.onNodeWithText("保存并更新").performClick()
        awaitSaveError()
        compose.onNode(hasSetTextAction()).assertTextContains("修改后的内容")
        compose.runOnIdle { assertEquals(id, editing?.id) }
        assertEquals("原内容", db.getReminderById(id)?.content)
        db.writableDatabase.execSQL("DROP TRIGGER reject_update")
        compose.onNodeWithText("保存并更新").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { editing == null }
        assertEquals("修改后的内容", db.getReminderById(id)?.content)
    }

    @Test
    fun newlyInsertedCategoryIsSelectedWhenReturningToReminderDraft() {
        setPane()
        compose.onNodeWithText("录入新句").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("属于新目录")
        compose.onNodeWithText("+ 新建目录").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("新目录")
        compose.onNodeWithText("确认").performClick()
        compose.waitUntil(5_000) { categories.any { it.name == "新目录" } }
        compose.onNodeWithText("新增记录到句阁").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("属于新目录")
        compose.onNodeWithText("确认").performClick()
        compose.waitUntil(5_000) { reminders.size == 1 }
        val saved = db.getAllReminders().single()
        assertEquals("属于新目录", saved.content)
        assertEquals(db.getAllCategories().single { it.name == "新目录" }.id, saved.categoryId)
    }

    private fun awaitSaveError() {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("保存失败，请重试，输入内容已保留")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("保存失败，请重试，输入内容已保留").assertIsDisplayed()
    }

    private fun setPane() {
        compose.setContent {
            MyApplicationTheme {
                LibraryPane(
                    dbHelper = db,
                    scope = rememberCoroutineScope(),
                    categories = categories,
                    reminders = reminders,
                    isLoaded = true,
                    selectedCategory = null,
                    editingReminder = editing,
                    onEditReminderRequest = { editing = it },
                    onCategorySelect = {},
                    onCategoriesChanged = { categories = it },
                    onRemindersChanged = { reminders = it },
                    onWidgetConfigsChanged = {},
                    selectedWidgetId = -1,
                    selectedConfigId = -1L,
                    currentStyle = WidgetStyle(),
                    onContentChanged = {},
                    onStyleChange = { _, _, _, _ -> SaveOutcome.SAVED },
                    onOpenDoc = {},
                )
            }
        }
    }
}
