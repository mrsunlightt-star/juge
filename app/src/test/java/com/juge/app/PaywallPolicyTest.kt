package com.juge.app

import com.juge.app.data.WidgetStyle
import com.juge.app.ui.requiresProForDesktop
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 付费拦截只发生在「保存并应用到桌面」：
 * 有桌面目标 + 未激活 + 付费风格/自定义背景图，三个条件同时成立。
 *
 * 编辑文字、改颜色、切风格等操作只预览，不弹框；没有桌面目标时也不拦。
 */
class PaywallPolicyTest {

    private val freeStyle = WidgetStyle.PRESETS.first { it.presetId == "p_pure_round" }
    private val proStyle = WidgetStyle.PRESETS.first { it.presetId == "p_boy_room" }
    private val widgetId = 42

    @Test
    fun `免费风格未激活也不拦截`() {
        assertFalse(requiresProForDesktop(freeStyle, isActivated = false, widgetId = widgetId))
    }

    @Test
    fun `付费风格未激活且要应用到桌面时拦截`() {
        assertTrue(requiresProForDesktop(proStyle, isActivated = false, widgetId = widgetId))
    }

    @Test
    fun `付费风格在已激活时不拦截`() {
        assertFalse(requiresProForDesktop(proStyle, isActivated = true, widgetId = widgetId))
    }

    @Test
    fun `没有桌面目标时不拦截`() {
        assertFalse(requiresProForDesktop(proStyle, isActivated = false, widgetId = -1))
    }

    @Test
    fun `自定义背景图同样只在应用桌面时拦截`() {
        val customBackground = freeStyle.copy(backgroundImagePath = "/tmp/bg.png")
        assertTrue(requiresProForDesktop(customBackground, isActivated = false, widgetId = widgetId))
        assertFalse(requiresProForDesktop(customBackground, isActivated = false, widgetId = -1))
    }
}
