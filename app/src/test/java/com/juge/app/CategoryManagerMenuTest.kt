package com.juge.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.juge.app.data.Category
import com.juge.app.ui.library.CategoryManagerDialog
import com.juge.app.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 金句分类管理中心的 ⋮ 溢出菜单（AppDropdownMenu）。
 *
 * 这里只断言「菜单真的弹出、点得到、回调对得上」——外观（白卡/圆角/阴影）由
 * AppMenu.kt 统一绘制，落点几何见 [AppMenuPlacementTest]。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoryManagerMenuTest {

    @get:Rule
    val compose = createComposeRule()

    private val categories = listOf(
        Category(id = 1L, name = "甲"),
        Category(id = 2L, name = "乙"),
    )

    private fun setDialogContent(
        onRenameRequest: (Category) -> Unit = {},
        onDeleteRequest: (Category) -> Unit = {},
    ) {
        compose.setContent {
            MyApplicationTheme {
                CategoryManagerDialog(
                    show = true,
                    categories = categories,
                    onRenameRequest = onRenameRequest,
                    onDeleteRequest = onDeleteRequest,
                    onAddCategory = {},
                    onReorderCategories = {},
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `点 ⋮ 弹出菜单后可点重命名且回调对应分类`() {
        var renamed: Category? = null
        setDialogContent(onRenameRequest = { renamed = it })

        compose.onAllNodesWithContentDescription("更多分类操作")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("重命名").assertIsDisplayed()

        compose.onNodeWithText("重命名").performClick()
        compose.waitForIdle()
        assertEquals("重命名回调应带上该行分类", 1L, renamed?.id)
        compose.onNodeWithText("重命名").assertDoesNotExist()
    }

    @Test
    fun `菜单里的删除项可点且回调对应分类`() {
        var deleted: Category? = null
        setDialogContent(onDeleteRequest = { deleted = it })

        compose.onAllNodesWithContentDescription("更多分类操作")[1].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("删除").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(2L, deleted?.id)
        compose.onNodeWithText("删除").assertDoesNotExist()
    }

    @Test
    fun `未点 ⋮ 时菜单项不存在`() {
        setDialogContent()
        compose.onNodeWithText("重命名").assertDoesNotExist()
    }
}
