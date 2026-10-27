package com.juge.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.juge.app.ui.AppNumberStepperRow
import com.juge.app.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * 「数值行 + 步进器」的行为边界。
 *
 * 「个性定制」页已经不再用横向滑条，这组用例钉住替代控件本身：
 * 点一下走一步、端点禁用、拖动不误触、不可调整时显示说明。
 * 长按连发依赖 Compose 测试时钟，单独在真机上验（见改造记录）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppNumberStepperTest {

    @get:Rule
    val compose = createComposeRule()

    private var value by mutableFloatStateOf(30f)

    private fun setRow(initial: Float = 30f, enabled: Boolean = true) {
        value = initial
        compose.setContent {
            MyApplicationTheme {
                AppNumberStepperRow(
                    label = "字体大小",
                    value = value,
                    valueRange = 12f..48f,
                    step = 1f,
                    format = { "${it.roundToInt()} sp" },
                    snap = { it.roundToInt().toFloat() },
                    enabled = enabled,
                    disabledHint = if (enabled) null else "此形状无需调整",
                    onValueChange = { value = it },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `点一下走一步`() {
        setRow()
        compose.onNodeWithContentDescription("增大字体大小").performClick()
        compose.onNodeWithText("31 sp").assertIsDisplayed()
        compose.onNodeWithContentDescription("减小字体大小").performClick()
        compose.onNodeWithText("30 sp").assertIsDisplayed()
    }

    @Test
    fun `到达上限后加号禁用`() {
        setRow(initial = 48f)
        compose.onNodeWithContentDescription("增大字体大小").assertIsNotEnabled()
        compose.onNodeWithContentDescription("减小字体大小").performClick()
        compose.onNodeWithText("47 sp").assertIsDisplayed()
    }

    @Test
    fun `拖动按钮不误触步进`() {
        setRow()
        compose.onNodeWithContentDescription("增大字体大小").performTouchInput {
            down(center)
            // 手指滑出按钮边界：clickable 会取消点击，不能误走一步
            moveTo(Offset(center.x + 200f, center.y))
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithText("30 sp").assertIsDisplayed()
    }

    @Test
    fun `不可调整时显示说明且不渲染按钮`() {
        setRow(enabled = false)
        compose.onNodeWithText("此形状无需调整").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("增大字体大小").assertCountEquals(0)
    }
}
