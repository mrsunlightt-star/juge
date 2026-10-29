package com.juge.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.juge.app.ui.ColorPickerDialog
import com.juge.app.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 取色弹窗的「子页面统一规范」回归：
 * 它曾经是一颗自带窄窗口 / 无阴影 / 左右等分实心按钮的特例，与其它子页面不一致。
 * 现在统一走 AppDialogSurface + AppDialogHeader + 右下角「取消 / 主操作」，
 * 这里锁住标题与动作收口，避免以后又被改回特例。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColorPickerDialogTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `取色弹窗使用统一标题与右下角动作`() {
        var confirmed: Int? = null
        var dismissed = false
        compose.setContent {
            MyApplicationTheme {
                ColorPickerDialog(
                    show = true,
                    title = "添加字体颜色预设",
                    initialColor = 0xFF42B8EC.toInt(),
                    onDismiss = { dismissed = true },
                    onConfirm = { confirmed = it },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("添加字体颜色预设").assertExists()
        compose.onNodeWithText("颜色编码").assertExists()
        compose.onNodeWithText("取消").assertExists()
        compose.onNodeWithText("添加").performClick()
        compose.waitForIdle()
        assertEquals(0xFF42B8EC.toInt(), confirmed!!)

        compose.onNodeWithText("取消").performClick()
        compose.waitForIdle()
        assertTrue("取消应回调 onDismiss", dismissed)
    }
}
