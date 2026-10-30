package com.juge.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.SaveOutcome
import com.juge.app.ui.adjust.ColorTextureCard
import com.juge.app.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「色彩与质感艺术」色板排布的回归用例。
 *
 * 线上问题（2026-10-30 反馈）：该模块的色板曾是「单行 + horizontalScroll」，
 * 用户添加颜色后多出来的色块被裁在可视区外，模块里看不到，只能靠横滑（实际上也滑不动）——
 * 与「一行放不下就自动开下一行」的既定设计不符。
 *
 * 这里钉住修复后的行为：颜色超过一行的容量时，卡片按行增高（换行），而不是横向截断。
 * 宽度算得很死：卡片 300dp - 左右 16dp 内边距 = 268dp 内容区，右侧「+」占 48dp 热区 + 12dp 行距，
 * 色板可用 208dp → 每行 4 个 48dp 色块。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColorPresetWrapTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `色板多于一行时自动换行_卡片按行增高`() {
        // 5 个内置色：一行放 4 个 + 第二行 1 个 → 每段 2 行
        var fontColors by mutableStateOf(presetColors(font = true, count = 5))
        var backgroundColors by mutableStateOf(presetColors(font = false, count = 5))
        compose.setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .width(300.dp)
                        .testTag("card")
                ) {
                    ColorTextureCard(
                        selectedStyle = WidgetStyle(),
                        selectedContent = "",
                        selectedWidgetId = 1,
                        selectedReminderId = -1L,
                        fontColorPresets = fontColors,
                        backgroundColorPresets = backgroundColors,
                        onStyleStateChange = {},
                        onStyleChange = { _, _, _, _ -> SaveOutcome.SAVED },
                        onRequestAddFontColor = {},
                        onRequestAddBackgroundColor = {},
                        onRequestDeleteFontColor = {},
                        onRequestDeleteBackgroundColor = {},
                        onBackgroundColorBlocked = {}
                    )
                }
            }
        }
        compose.waitForIdle()
        val twoRowsPerSection = heightOfCard()

        // 13 个色：每行 4 个 → 4 行（4×3 + 1）；两段各多 2 行
        compose.runOnIdle {
            fontColors = presetColors(font = true, count = 13)
            backgroundColors = presetColors(font = false, count = 13)
        }
        compose.waitForIdle()
        val fourRowsPerSection = heightOfCard()

        val rowPx = with(compose.density) { 48.dp.roundToPx() }
        // 修复前这里只差 0（多余色块被横向裁掉）；修复后正好多出 4 行色板
        assertEquals(
            "色板未按行换行：卡片高度只增加了 ${fourRowsPerSection - twoRowsPerSection}px，" +
                "预期 ${4 * rowPx}px（4 行）",
            4 * rowPx,
            fourRowsPerSection - twoRowsPerSection
        )
    }

    private fun heightOfCard(): Int =
        compose.onNodeWithTag("card").fetchSemanticsNode().size.height

    /** 造 [count] 个不同的色值；字体色与背景色取不同色相，避免与内置色撞值造成重复 */
    private fun presetColors(font: Boolean, count: Int): List<Int> =
        List(count) { index ->
            val base = if (font) 0x40 else 0x80
            (0xFF000000.toInt()) or ((base + index * 8) shl 16) or (index * 5 shl 8) or (index * 3)
        }
}
