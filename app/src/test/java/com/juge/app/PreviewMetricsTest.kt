package com.juge.app

import com.juge.app.data.WidgetDisplaySize
import com.juge.app.data.WidgetShape
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.PreviewMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 预览高度映射的回归测试。
 *
 * 之前正是这类尺寸映射出过问题：4×2 的组件按 4×4 的高度留白，预览被拉成近方形。
 * 这里把「行数 → 高度」和上下限都钉死；`previewHeightForStyle` 那两条再钉住
 * 产品规则「预览高度只认风格自己的最佳显示尺寸」。
 */
class PreviewMetricsTest {

    @Test
    fun `问不到行数时按默认 4x2 兜底`() {
        assertEquals(
            PreviewMetrics.previewHeightDp(WidgetShape.RECTANGLE, null),
            PreviewMetrics.previewHeightDp(WidgetShape.RECTANGLE, 2),
        )
    }

    @Test
    fun `行数越多预览越高且单调`() {
        val h2 = PreviewMetrics.previewHeightDp(WidgetShape.RECTANGLE, 2)
        val h3 = PreviewMetrics.previewHeightDp(WidgetShape.RECTANGLE, 3)
        val h4 = PreviewMetrics.previewHeightDp(WidgetShape.RECTANGLE, 4)

        assertTrue(h2 < h3 && h3 < h4)
    }

    @Test
    fun `行数被夹在 2 到 4 之间`() {
        val h2 = PreviewMetrics.previewHeightDp(WidgetShape.RECTANGLE, 2)
        assertEquals(h2, PreviewMetrics.previewHeightDp(WidgetShape.RECTANGLE, 0))
        assertEquals(h2, PreviewMetrics.previewHeightDp(WidgetShape.RECTANGLE, -5))
        assertEquals(
            PreviewMetrics.previewHeightDp(WidgetShape.RECTANGLE, 4),
            PreviewMetrics.previewHeightDp(WidgetShape.RECTANGLE, 9),
        )
    }

    @Test
    fun `高度不低于下限 180dp`() {
        // 2 行 = 60 + 100 = 160，必须被抬到 180
        assertEquals(PreviewMetrics.MIN_PREVIEW_HEIGHT_DP, PreviewMetrics.previewHeightDp(WidgetShape.RECTANGLE, 2))
    }

    @Test
    fun `横向分割卡不随行数变化`() {
        val a = PreviewMetrics.previewHeightDp(WidgetShape.SPLIT_CARD_HORIZONTAL, 2)
        val b = PreviewMetrics.previewHeightDp(WidgetShape.SPLIT_CARD_HORIZONTAL, 4)

        assertEquals(a, b)
    }

    @Test
    fun `显示盒高度夹在 244 到 268 之间`() {
        assertEquals(PreviewMetrics.PREVIEW_BOX_BASE_DP, PreviewMetrics.previewBoxHeightDp(180))
        assertEquals(PreviewMetrics.PREVIEW_BOX_MAX_DP, PreviewMetrics.previewBoxHeightDp(400))
        // 260 + 8 = 268，正好在上限
        assertEquals(268, PreviewMetrics.previewBoxHeightDp(260))
    }

    @Test
    fun `4x4 的显示盒比 4x2 高`() {
        val small = PreviewMetrics.previewBoxHeightDp(PreviewMetrics.previewHeightDp(WidgetShape.RECTANGLE, 2))
        val big = PreviewMetrics.previewBoxHeightDp(PreviewMetrics.previewHeightDp(WidgetShape.RECTANGLE, 4))

        assertTrue(big > small)
    }

    @Test
    fun `预览高度按风格自己的最佳显示尺寸`() {
        // 产品规则（2026-10-08）：预览只认风格的 bestDisplaySize，
        // 与组件从哪个入口添加、在桌面上拉成多大都无关
        val wide = WidgetStyle(bestDisplaySize = WidgetDisplaySize.WIDE_4X2)
        val square = WidgetStyle(bestDisplaySize = WidgetDisplaySize.SQUARE_4X4)

        assertEquals(180, PreviewMetrics.previewHeightForStyle(wide))
        assertEquals(260, PreviewMetrics.previewHeightForStyle(square))
    }

    @Test
    fun `换形状不改高度、换尺寸才改`() {
        val square = WidgetStyle(shape = WidgetShape.RECTANGLE, bestDisplaySize = WidgetDisplaySize.SQUARE_4X4)

        assertEquals(
            PreviewMetrics.previewHeightForStyle(square),
            PreviewMetrics.previewHeightForStyle(square.copy(shape = WidgetShape.BOOKSHELF)),
        )
        assertTrue(
            PreviewMetrics.previewHeightForStyle(square) >
                PreviewMetrics.previewHeightForStyle(square.copy(bestDisplaySize = WidgetDisplaySize.WIDE_4X2)),
        )
    }
}
