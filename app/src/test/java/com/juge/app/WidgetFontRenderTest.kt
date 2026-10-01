package com.juge.app

import android.graphics.Bitmap
import android.graphics.Color
import com.juge.app.data.WidgetFont
import com.juge.app.data.WidgetShape
import com.juge.app.data.WidgetStyle
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 内置字体可用性回归测试。
 *
 * 字体经过子集化裁剪（见 tools/fonts/subset_fonts.py）后仍是标准 TTF/OTF，
 * 但一旦裁剪损坏或资源名对不上，Android 会静默回退到系统字体——
 * 用户只会看到「风格变了」，不会报错。这里逐个渲染并比对，把这种静默失败钉死。
 *
 * 判据：同一段文字用内置字体渲染，结果必须与系统默认字体不同；
 * 完全相同说明该字体实际未被应用（加载失败或走了回退）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetFontRenderTest {

    private val sample = "永和九年岁在癸丑，暮春之初"

    @Test
    fun `每款内置字体都能加载并真实生效`() {
        val context = RuntimeEnvironment.getApplication()
        val baseline = render(context, WidgetFont.DEFAULT)

        val bundled = WidgetFont.entries.filter { it.fontPath != null }
        assertTrue("应有内置字体可供校验", bundled.isNotEmpty())

        for (font in bundled) {
            val bitmap = render(context, font)
            try {
                assertTrue("${font.displayName} 渲染结果全透明", hasOpaquePixel(bitmap))
                assertTrue(
                    "${font.displayName} 与系统默认渲染完全一致，字体可能未被应用",
                    differsFrom(bitmap, baseline),
                )
            } finally {
                bitmap.recycle()
            }
        }
        baseline.recycle()
    }

    private fun render(context: android.content.Context, font: WidgetFont): Bitmap =
        WidgetCanvasRenderer.render(
            context = context,
            widthDp = 250,
            heightDp = 110,
            content = sample,
            style = WidgetStyle(
                shape = WidgetShape.RECTANGLE,
                font = font,
                fontSizeSp = 34f,
                fontColor = Color.BLACK,
                backgroundColor = Color.WHITE,
            ),
        )

    private fun hasOpaquePixel(bitmap: Bitmap): Boolean {
        val stepX = (bitmap.width / 20).coerceAtLeast(1)
        val stepY = (bitmap.height / 20).coerceAtLeast(1)
        var x = 0
        while (x < bitmap.width) {
            var y = 0
            while (y < bitmap.height) {
                if (Color.alpha(bitmap.getPixel(x, y)) != 0) return true
                y += stepY
            }
            x += stepX
        }
        return false
    }

    private fun differsFrom(a: Bitmap, b: Bitmap): Boolean {
        if (a.width != b.width || a.height != b.height) return true
        val stepX = (a.width / 40).coerceAtLeast(1)
        val stepY = (a.height / 40).coerceAtLeast(1)
        var x = 0
        while (x < a.width) {
            var y = 0
            while (y < a.height) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) return true
                y += stepY
            }
            x += stepX
        }
        return false
    }
}