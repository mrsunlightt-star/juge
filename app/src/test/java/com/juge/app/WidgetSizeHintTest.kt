package com.juge.app

import com.juge.app.data.WidgetStyle
import com.juge.app.ui.main.widgetSizeHintFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 「风格默认尺寸 vs 桌面组件实际尺寸」的提示判定。 */
class WidgetSizeHintTest {

    private val squareStyle = WidgetStyle.PRESETS.first { it.presetId == "p_after_school" } // 4×4
    private val wideStyle = WidgetStyle.PRESETS.first { it.presetId == "p_blue_whale" }   // 4×2

    @Test
    fun `4x2 组件应用 4x4 风格时提示拉成 4x4`() {
        val hint = widgetSizeHintFor(squareStyle, "4*2")
        assertEquals("4×2", hint?.current)
        assertEquals("4×4", hint?.target)
    }

    @Test
    fun `4x4 组件应用 4x4 风格不提示`() {
        assertNull(widgetSizeHintFor(squareStyle, "4*4"))
    }

    @Test
    fun `4x4 组件应用 4x2 风格时提示压成 4x2`() {
        val hint = widgetSizeHintFor(wideStyle, "4*4")
        assertEquals("4×4", hint?.current)
        assertEquals("4×2", hint?.target)
    }

    @Test
    fun `4x2 组件应用 4x2 风格不提示`() {
        assertNull(widgetSizeHintFor(wideStyle, "4*2"))
    }

    @Test
    fun `4x3 等中间尺寸按行数偏差提示`() {
        assertEquals("4×4", widgetSizeHintFor(squareStyle, "4*3")?.target)
        assertEquals("4×2", widgetSizeHintFor(wideStyle, "4*3")?.target)
    }

    @Test
    fun `异常尺寸字符串不提示`() {
        assertNull(widgetSizeHintFor(squareStyle, "?"))
    }
}
