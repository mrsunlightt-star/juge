package com.juge.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.juge.app.data.AppPrefs
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「个性定制」页右滑返回「跃然纸上」的手势回归测试。
 *
 * 逐类面板各起一次手势：普通卡片、横向预设行、输入框、数值步进行、步进按钮。
 * 页面里的滑条已全部换成「数值行 + 步进器」，手势冲突的源头消失；这些用例防止
 * 以后又把横向拖动控件放回 pager，或步进器按钮挡住右滑。
 *
 * 屏幕特意设得很高，让整页设置都参与布局，测试不必先滚动——滚动本身会重建
 * LazyColumn 语义节点，手势用例里容易引入与手势无关的噪声。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h3000dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdjustSwipeBackTest {

    @get:Rule(order = 0)
    val acceptPrivacy = object : ExternalResource() {
        override fun before() {
            AppPrefs.setPrivacyAccepted(RuntimeEnvironment.getApplication())
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    /** 普通卡片区域右滑：必须翻回「跃然纸上」 */
    @Test
    fun `普通面板右滑返回跃然纸上`() {
        openAdjust()
        rightSwipeAt(centerOf("文本内容与字形定制"))
        assertOnLibrary()
    }

    /** 金句输入框区域右滑：必须翻回「跃然纸上」 */
    @Test
    fun `金句输入框右滑返回跃然纸上`() {
        openAdjust()
        val field = compose.onNode(hasSetTextAction()).fetchSemanticsNode()
        rightSwipeAt(field.boundsInRoot.center)
        assertOnLibrary()
    }

    /** 横向预设行（行已滚到最左端）右滑：必须翻回「跃然纸上」 */
    @Test
    fun `风格行右滑返回跃然纸上`() {
        openAdjust()
        rightSwipeAt(centerOf("经典风格"))
        assertOnLibrary()
    }

    /**
     * 色板区域右滑：必须翻回「跃然纸上」。
     *
     * 色板行已改为流式换行（不再横向滚动，见 ColorPresetFlow），这一层仍保留：
     * 色板是页面里最密集的点击区域，右滑起手落在色块上也要能翻页。
     */
    @Test
    fun `色板行右滑返回跃然纸上`() {
        openAdjust()
        val label = compose.onNodeWithText("字体颜色").fetchSemanticsNode().boundsInRoot
        rightSwipeAt(Offset(label.center.x, label.bottom + 60f))
        assertOnLibrary()
    }

    /** 文本面板的数值步进行右滑：必须翻回「跃然纸上」，且不能误触步进 */
    @Test
    fun `文本面板步进行右滑返回跃然纸上`() {
        openAdjust()
        val before = centerOf("字间距")
        rightSwipeAt(before)
        assertOnLibrary()
        // 回到定制页确认值没被这次滑动改掉（字间距默认 0.00 em）
        compose.onNodeWithText("个性定制").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("0.00 em").assertIsDisplayed()
    }

    /** 形状面板的数值步进行右滑：必须翻回「跃然纸上」 */
    @Test
    fun `形状面板步进行右滑返回跃然纸上`() {
        openAdjust()
        rightSwipeAt(centerOf("背景不透明度"))
        assertOnLibrary()
    }

    /** 从「+」按钮上起手、滑出按钮的右滑：按钮取消点击，页面照常翻回 */
    @Test
    fun `步进按钮上起手右滑也能返回跃然纸上`() {
        openAdjust()
        val plus = compose.onNodeWithContentDescription("增大字体大小")
            .fetchSemanticsNode().boundsInRoot.center
        rightSwipeAt(plus)
        assertOnLibrary()
    }

    private fun openAdjust() {
        compose.onNodeWithText("个性定制").performClick()
        compose.waitForIdle()
    }

    private fun centerOf(text: String): Offset {
        val node = compose.onNodeWithText(text, substring = true).fetchSemanticsNode()
        return node.boundsInRoot.center
    }

    private fun rightSwipeAt(start: Offset, distance: Float = 300f) {
        drag(start, distance / 6f)
    }

    /** 分多段移动，贴近真机事件节奏 */
    private fun drag(start: Offset, stepX: Float) {
        compose.onRoot().performTouchInput {
            down(start)
            var x = start.x
            repeat(6) {
                advanceEventTime(16)
                x += stepX
                moveTo(Offset(x, start.y))
            }
            up()
        }
        compose.waitForIdle()
    }

    private fun assertOnLibrary() {
        compose.onNodeWithText("全部").assertIsDisplayed()
    }
}
