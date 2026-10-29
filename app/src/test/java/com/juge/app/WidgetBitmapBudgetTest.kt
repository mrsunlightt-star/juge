package com.juge.app

import android.app.Application
import com.juge.app.data.WidgetStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetBitmapBudgetTest {
    @Test
    fun `桌面全屏渲染等比缩小并保留RemoteViews预算余量`() {
        val dimensions = WidgetCanvasRenderer.calculateWidgetRenderDimensions(360, 640, 3f, 1080, 1920)
        val budget = WidgetCanvasRenderer.widgetPixelBudget(1080, 1920)
        val pixels = dimensions.width.toLong() * dimensions.height
        assertTrue(pixels <= budget)
        assertTrue(pixels > budget * 0.99)
        assertTrue(pixels * 4L < 1080L * 1920L * 6L)
        assertEquals(360.0 / 640.0, dimensions.width.toDouble() / dimensions.height, 0.001)
        assertEquals(dimensions.width / 360f, dimensions.densityScale, 1f / 360f)
        assertEquals(dimensions.height / 640f, dimensions.densityScale, 1f / 640f)
        assertEquals(dimensions.densityScale / 1.5f, dimensions.scale, 0.00001f)
        assertTrue(dimensions.densityScale < 4.5f)
    }

    @Test
    fun `原有小尺寸保留相同像素尺寸与densityScale`() {
        for (density in listOf(1f, 1.5f, 2.625f, 3f, 4f)) {
            for ((width, height) in listOf(250 to 110, 250 to 250, 12 to 12)) {
                val dimensions = WidgetCanvasRenderer.calculateWidgetRenderDimensions(width, height, density, 1440, 2560)
                assertEquals((width * density * 1.5f).toInt().coerceAtLeast(300), dimensions.width)
                assertEquals((height * density * 1.5f).toInt().coerceAtLeast(300), dimensions.height)
                assertEquals(density * 1.5f, dimensions.densityScale, 0f)
            }
        }
    }

    @Test
    fun `小尺寸桌面位图与原有预览逐像素一致`() {
        val context = RuntimeEnvironment.getApplication()
        for ((width, height) in listOf(250 to 110, 250 to 250, 12 to 12)) {
            val preview = WidgetCanvasRenderer.render(context, width, height, "small widget", WidgetStyle.DEFAULT)
            val desktop = WidgetCanvasRenderer.renderForWidget(context, width, height, "small widget", WidgetStyle.DEFAULT)
            try {
                assertEquals(preview.width, desktop.width)
                assertEquals(preview.height, desktop.height)
                assertTrue(preview.sameAs(desktop))
            } finally {
                preview.recycle()
                desktop.recycle()
            }
        }
    }

    @Test
    fun `像素限制只作用于桌面入口且真实位图内存不超预算`() {
        val context = RuntimeEnvironment.getApplication()
        val metrics = context.resources.displayMetrics
        val preview = WidgetCanvasRenderer.render(context, 360, 640, "preview", WidgetStyle.DEFAULT)
        val desktop = WidgetCanvasRenderer.renderForWidget(context, 360, 640, "preview", WidgetStyle.DEFAULT)
        try {
            assertEquals((360 * (metrics.density * 1.5f)).toInt(), preview.width)
            assertEquals((640 * (metrics.density * 1.5f)).toInt(), preview.height)
            val budget = WidgetCanvasRenderer.widgetPixelBudget(metrics.widthPixels, metrics.heightPixels)
            assertTrue(desktop.allocationByteCount.toLong() <= budget * 4L)
            assertTrue(preview.allocationByteCount > desktop.allocationByteCount)
        } finally {
            preview.recycle()
            desktop.recycle()
        }
    }

    @Test
    fun `异常尺寸和极端数值也不会溢出像素预算`() {
        val sizes = listOf(-1, 0, 1, 300, 10000, Int.MAX_VALUE)
        val densities = listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.MIN_VALUE, 0f, -1f, 1f, 3f, Float.MAX_VALUE)
        for (width in sizes) {
            for (height in sizes) {
                for (density in densities) {
                    for ((screenWidth, screenHeight) in listOf(0 to 0, 1 to 1, 1080 to 1920, Int.MAX_VALUE to Int.MAX_VALUE)) {
                        val dimensions = WidgetCanvasRenderer.calculateWidgetRenderDimensions(width, height, density, screenWidth, screenHeight)
                        val budget = WidgetCanvasRenderer.widgetPixelBudget(screenWidth, screenHeight)
                        assertTrue(dimensions.width > 0 && dimensions.height > 0)
                        assertTrue(dimensions.width.toLong() * dimensions.height <= budget)
                        assertTrue(dimensions.densityScale.isFinite() && dimensions.densityScale > 0f)
                    }
                }
            }
        }
    }
}
