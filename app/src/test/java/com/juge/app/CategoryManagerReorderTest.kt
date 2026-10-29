package com.juge.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
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
 * 金句分类管理中心：长按拖拽排序。
 *
 * 这是管理弹窗新增的手势（与「跃然纸上」页分类 chip 共用 ReorderDragState），
 * 这里直接注入「按下 → 等过长按阈值 → 下移一格 → 抬起」，断言提交的顺序已交换。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoryManagerReorderTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `长按拖拽分类后提交新顺序`() {
        val categories = listOf(
            Category(id = 1L, name = "甲"),
            Category(id = 2L, name = "乙"),
            Category(id = 3L, name = "丙"),
        )
        var submitted: List<Long>? = null
        compose.setContent {
            MyApplicationTheme {
                CategoryManagerDialog(
                    show = true,
                    categories = categories,
                    onRenameRequest = {},
                    onDeleteRequest = {},
                    onAddCategory = {},
                    onReorderCategories = { submitted = it },
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()

        // 行高由 48dp 溢出菜单按钮 + 24dp 内边距决定（约 72dp）：
        // 把第一行往下拖过第二行中心（>72dp）才会触发交换
        compose.onNodeWithText("甲").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, 100.dp.toPx()))
            up()
        }
        compose.waitForIdle()

        assertEquals("应提交交换后的分类顺序", listOf(2L, 1L, 3L), submitted)
    }
}
