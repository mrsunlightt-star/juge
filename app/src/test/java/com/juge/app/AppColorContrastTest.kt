package com.juge.app

import androidx.compose.ui.graphics.Color
import com.juge.app.ui.theme.AppPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * 颜色令牌的对比度回归。
 *
 * 界面里所有文字最终都落在这几组前景/背景上；颜色一改，用 WCAG 相对亮度公式
 * 直接卡住：正文 4.5:1、图标/状态描边 3:1。比肉眼和截图基线更早失败。
 * 日/夜两套 [AppPalette] 都是纯数据，这里直接断言实例，不经过组合。
 *
 * 选中态浅蓝 selectBlue 的白字搭配是用户明确选择的品牌例外：
 * 对比度约 2.26:1，低于普通文字的 4.5:1；单独锁定色值与预期对比度，不冒充 AA 合规。
 */
class AppColorContrastTest {

    private val palettes = listOf("日间" to AppPalette.Light, "夜间" to AppPalette.Dark)

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
        palettes.forEach { (label, p) ->
            assertContrast("$label textPrimary on cardBg", p.textPrimary, p.cardBg, 4.5)
            assertContrast("$label textSecondary on cardBg", p.textSecondary, p.cardBg, 4.5)
            assertContrast("$label textSecondary on darkBg", p.textSecondary, p.darkBg, 4.5)
            assertContrast("$label textSecondary on surfaceSubtle", p.textSecondary, p.surfaceSubtle, 4.5)
            assertContrast("$label textTertiary on cardBg", p.textTertiary, p.cardBg, 4.5)
            assertContrast("$label accentBlue on cardBg", p.accentBlue, p.cardBg, 4.5)
            assertContrast("$label accentBlue on darkBg", p.accentBlue, p.darkBg, 4.5)
            assertContrast("$label premiumGoldInk on premiumGold", p.premiumGoldInk, p.premiumGold, 4.5)
            assertContrast("$label premiumGoldInk on premiumGoldEnd", p.premiumGoldInk, p.premiumGoldEnd, 4.5)
            assertContrast("$label dangerRed on cardBg", p.dangerRed, p.cardBg, 4.5)
            assertContrast("$label successGreen on cardBg", p.successGreen, p.cardBg, 4.5)
            assertContrast("$label warningInk on warningBg", p.warningInk, p.warningBg, 4.5)
        }
        // 危险按钮的字色随模式换：日间红底白字，夜间红提亮为 red-400 后配深墨字
        // （见 AppDangerButton，与 Material 夜间 onPrimary=darkBg 同一约定）
        assertContrast("日间 白字 on dangerRed", Color.White, AppPalette.Light.dangerRed, 4.5)
        assertContrast("夜间 深墨字 on dangerRed", AppPalette.Dark.darkBg, AppPalette.Dark.dangerRed, 4.5)
    }

    @Test
    fun `图标与状态描边满足 3比1`() {
        palettes.forEach { (label, p) ->
            assertContrast("$label accentBlue 状态描边 on cardBg", p.accentBlue, p.cardBg, 3.0)
            assertContrast("$label accentFillStrong 填充 on cardBg", p.accentFillStrong, p.cardBg, 3.0)
            assertContrast("$label premiumGoldDeep 图标 on cardBg", p.premiumGoldDeep, p.cardBg, 3.0)
            assertContrast("$label dangerRed 图标 on cardBg", p.dangerRed, p.cardBg, 3.0)
            assertContrast("$label textTertiary 图标 on cardBg", p.textTertiary, p.cardBg, 3.0)
        }
    }

    /**
     * selectBlue 是用户指定保留的明亮浅蓝底白字搭配。
     * 对比度低于 AA 标准，作为产品偏好明确记录，不纳入常规 AA 断言。
     * 它是品牌填充色，日/夜同值。
     */
    @Test
    fun `选中态浅蓝保持用户指定色值`() {
        assertEquals(Color(0xFF42B8EC), AppPalette.Light.selectBlue)
        assertEquals(AppPalette.Light.selectBlue, AppPalette.Dark.selectBlue)
        assertEquals(2.26, contrast(Color.White, AppPalette.Light.selectBlue), 0.03)
    }

    /** 夜间色板锁底色：页面底与卡片底的明度关系（卡片必须比页面亮一档）是深色分层的根基 */
    @Test
    fun `夜间页面底比卡片底更深`() {
        assertTrue(
            "夜间 darkBg 亮度应低于 cardBg",
            luminance(AppPalette.Dark.darkBg) < luminance(AppPalette.Dark.cardBg)
        )
    }
}
