package com.juge.app

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.juge.app.data.AppBackgroundPreset
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.SaveOutcome
import com.juge.app.ui.adjust.AdjustTabContent
import com.juge.app.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 尺寸提示只属于最终「保存并应用到桌面小组件」动作。
 *
 * 回归背景：提示曾挂在通用落库函数里，用户在 4×2 桌面组件上点一款 4×4 风格时
 * 就会立刻弹窗，打断连续比较不同风格；现在应先完整预览，最终应用时才提醒。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetSizeHintTimingTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `切换四乘四风格不弹窗 最终应用桌面才提示`() {
        var selectedStyle by mutableStateOf(WidgetStyle.DEFAULT)

        compose.setContent {
            MyApplicationTheme {
                AdjustTabContent(
                    isActivated = true,
                    appWidgetIds = intArrayOf(920),
                    selectedWidgetId = 920,
                    selectedReminderId = 1L,
                    selectedStyle = selectedStyle,
                    selectedContent = "",
                    selectedAppBackground = AppBackgroundPreset.entries.first(),
                    onAppBackgroundSelect = {},
                    onStyleStateChange = { selectedStyle = it },
                    onContentStateChange = {},
                    onSelectPreset = { _, _, style -> selectedStyle = style },
                    onStyleChange = { _, _, _, _ -> SaveOutcome.SAVED },
                    onApplyToDesktop = { _, _, _, _ -> SaveOutcome.SAVED },
                    onOpenDoc = {},
                    onSwipeBack = {},
                    pagerState = rememberPagerState(pageCount = { 2 })
                )
            }
        }
        compose.waitForIdle()

        // 模拟用户只在 App 内切到 4×4 风格：此处不应出现尺寸弹窗。
        compose.runOnIdle {
            selectedStyle = WidgetStyle.PRESETS.first { it.presetId == "p_youth_sculpture" }
        }
        compose.waitForIdle()
        compose.onAllNodesWithText("建议调整桌面组件尺寸").assertCountEquals(0)

        // 最终点击保存并应用后才提示；测试里的 920 没有桌面 options，按 4×2 兜底。
        compose.onNodeWithTag("adjust_list")
            .performScrollToNode(hasText("保存并应用到桌面小组件"))
        compose.onNodeWithText("保存并应用到桌面小组件").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("建议调整桌面组件尺寸").assertIsDisplayed()
    }
}
