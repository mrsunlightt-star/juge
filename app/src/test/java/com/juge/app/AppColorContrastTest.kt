package com.juge.app

import androidx.compose.ui.graphics.Color
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.accentLightBlue
import com.juge.app.ui.theme.cardBg
import com.juge.app.ui.theme.dangerRed
import com.juge.app.ui.theme.darkBg
import com.juge.app.ui.theme.premiumGold
import com.juge.app.ui.theme.premiumGoldDeep
import com.juge.app.ui.theme.premiumGoldEnd
import com.juge.app.ui.theme.premiumGoldInk
import com.juge.app.ui.theme.selectBlue
import com.juge.app.ui.theme.successGreen
import com.juge.app.ui.theme.surfaceSubtle
import com.juge.app.ui.theme.textPrimary
import com.juge.app.ui.theme.textSecondary
import com.juge.app.ui.theme.textTertiary
import com.juge.app.ui.theme.warningBg
import com.juge.app.ui.theme.warningInk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * 颜色令牌的对比度回归。
 *
 * 界面里所有文字最终都落在这几组前景/背景上；颜色一改，用 WCAG 相对亮度公式
 * 直接卡住：正文 4.5:1、图标/状态描边 3:1。比肉眼和截图基线更早失败。
 *
 * 选中态浅蓝 [selectBlue] 作为背景填充保留；其上的文字/图标必须使用深色
 * [textPrimary]，并由单独断言验证前景与填充的对比度。
 */
class AppColorContrastTest {

    private fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)

    private fun contrast(fg: Color, bg: Color): Double {
        val a = luminance(fg)
        val b = luminance(bg)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun assertContrast(name: String, fg: Color, bg: Color, min: Double) {
        val actual = contrast(fg, bg)
        assertTrue(
            "$name 对比度 ${String.format("%.2f", actual)}:1 低于 $min:1",
            actual >= min
        )
    }

    @Test
    fun `正文与链接满足 WCAG AA 4点5比1`() {
        assertContrast("textPrimary on cardBg", textPrimary, cardBg, 4.5)
        assertContrast("textPrimary on selectBlue", textPrimary, selectBlue, 4.5)
        assertContrast("textSecondary on cardBg", textSecondary, cardBg, 4.5)
        assertContrast("textSecondary on darkBg", textSecondary, darkBg, 4.5)
        assertContrast("textSecondary on surfaceSubtle", textSecondary, surfaceSubtle, 4.5)
        assertContrast("textTertiary on cardBg", textTertiary, cardBg, 4.5)
        assertContrast("accentLightBlue on cardBg", accentLightBlue, cardBg, 4.5)
        assertContrast("accentLightBlue on darkBg", accentLightBlue, darkBg, 4.5)
        assertContrast("premiumGoldInk on premiumGold", premiumGoldInk, premiumGold, 4.5)
        assertContrast("premiumGoldInk on premiumGoldEnd", premiumGoldInk, premiumGoldEnd, 4.5)
        assertContrast("dangerRed on cardBg", dangerRed, cardBg, 4.5)
        assertContrast("白字 on dangerRed", Color.White, dangerRed, 4.5)
        assertContrast("successGreen on cardBg", successGreen, cardBg, 4.5)
        assertContrast("warningInk on warningBg", warningInk, warningBg, 4.5)
    }

    @Test
    fun `图标与状态描边满足 3比1`() {
        assertContrast("accentBlue 状态描边 on cardBg", accentBlue, cardBg, 3.0)
        assertContrast("premiumGoldDeep 图标 on cardBg", premiumGoldDeep, cardBg, 3.0)
        assertContrast("dangerRed 图标 on cardBg", dangerRed, cardBg, 3.0)
        assertContrast("textTertiary 图标 on cardBg", textTertiary, cardBg, 3.0)
    }

    /**
     * selectBlue 是用户指定保留的明亮「选中态浅蓝」。
     * 填充色锁定为 #42B8EC；前景使用 textPrimary 深墨色，并在上面的 AA 测试中验证。
     */
    @Test
    fun `选中态浅蓝保持用户指定色值`() {
        assertEquals(Color(0xFF42B8EC), selectBlue)
    }
}
