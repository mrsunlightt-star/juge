package com.juge.app

import com.juge.app.render.WidgetRenderKernel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 预设插画的解码尺寸回归测试。
 *
 * 锁定的是「缓存里放什么」：`res/drawable` 素材必须先关掉密度放大、再缩到目标绘制框，
 * 缓存条目应与目标像素同量级（4×4 方形约 1125²≈5MB），而不是 480dpi 下被放大出来的
 * 6144²≈144MB。字节数变化说明密度缩放或采样策略被改回去了。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PresetImageDecodeSizeTest {

    private fun decode(name: String, width: Int, height: Int): android.graphics.Bitmap {
        WidgetRenderKernel.presetImageCache.evictAll()
        return WidgetRenderKernel.getPresetImage(
            RuntimeEnvironment.getApplication(), name, width, height
        ) ?: error("$name 解码失败")
    }

    @Test
    fun `2048 方图按目标尺寸解码而不是按密度放大`() {
        val bitmap = decode("youth_sculpture", 1125, 1125)
        assertEquals(1125, bitmap.width)
        assertEquals(1125, bitmap.height)
        assertEquals(1125 * 1125 * 4, bitmap.byteCount)
    }

    @Test
    fun `宽图保持宽高比并完整覆盖目标框`() {
        val bitmap = decode("keep_smiling", 1125, 495)
        assertTrue("宽不应小于目标：${bitmap.width}", bitmap.width >= 1125)
        assertTrue("高不应小于目标：${bitmap.height}", bitmap.height >= 495)
        assertEquals(2.0f, bitmap.width.toFloat() / bitmap.height, 0.01f)
    }

    @Test
    fun `尺寸相同再次取图命中缓存`() {
        WidgetRenderKernel.presetImageCache.evictAll()
        val context = RuntimeEnvironment.getApplication()
        val first = WidgetRenderKernel.getPresetImage(context, "panda_bamboo_square", 1125, 1125)
        val second = WidgetRenderKernel.getPresetImage(context, "panda_bamboo_square", 1125, 1125)
        assertSame("同尺寸应直接命中缓存，不应重新解码", first, second)
    }
}
