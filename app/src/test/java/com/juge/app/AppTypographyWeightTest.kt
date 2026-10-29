package com.juge.app

import androidx.compose.ui.text.font.FontWeight
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.scaleFontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 暗色模式字重缩放（用户要求「暗色模式下字重整体 -40」）。
 *
 * 规则：FontWeight 数值 ×[AppType.DarkWeightScale]（0.6）后吸附到最近的 100 档，
 * 保证系统字体回退可预期；日间 scale = 1 时原样返回。
 * 这个测试锁住换算表，避免以后改角度时把「-40%」悄悄改成别的比例。
 */
class AppTypographyWeightTest {

    /** 系统字体实际会走到的全部字重档（FontWeight 是 value class，没有 entries） */
    private val weights = listOf(
        FontWeight.Thin,
        FontWeight.ExtraLight,
        FontWeight.Light,
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold,
        FontWeight.ExtraBold,
        FontWeight.Black,
    )

    @Test
    fun `日间不缩放`() {
        weights.forEach { weight ->
            assertEquals(weight, scaleFontWeight(weight, 1f))
        }
    }

    @Test
    fun `夜间字重整体减 40 个百分点`() {
        val scale = AppType.DarkWeightScale
        assertEquals(0.6f, scale, 0.0001f)

        // 900 × 0.6 = 540 → 吸附到 500
        assertEquals(FontWeight.Medium, scaleFontWeight(FontWeight.Black, scale))
        // 700 × 0.6 = 420 → 400
        assertEquals(FontWeight.Normal, scaleFontWeight(FontWeight.Bold, scale))
        // 600 × 0.6 = 360 → 400
        assertEquals(FontWeight.Normal, scaleFontWeight(FontWeight.SemiBold, scale))
        // 500 × 0.6 = 300 → 300
        assertEquals(FontWeight.Light, scaleFontWeight(FontWeight.Medium, scale))
        // 400 × 0.6 = 240 → 200
        assertEquals(FontWeight.ExtraLight, scaleFontWeight(FontWeight.Normal, scale))
        // 200 × 0.6 = 120 → 100
        assertEquals(FontWeight.Thin, scaleFontWeight(FontWeight.ExtraLight, scale))
    }

    @Test
    fun `缩放结果吸附到百位并钳制在 100 到 900`() {
        // 任意权重都落在 100 的整数倍上，且不越界
        listOf(1f, 0.33f, 0.6f, 0.9f, 2f).forEach { scale ->
            weights.forEach { weight ->
                val scaled = scaleFontWeight(weight, scale)
                assertEquals(
                    "字重 ${weight.weight} × $scale 未吸附到百位",
                    0,
                    scaled.weight % 100
                )
                assertTrue("字重 ${weight.weight} × $scale 越界", scaled.weight in 100..900)
            }
        }
    }
}
