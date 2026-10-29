package com.juge.app

import android.graphics.Bitmap
import com.juge.app.data.WidgetStyle
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「垂直位置：顶部对齐」的渲染回归。
 *
 * 用同一份样式/文案分别渲染 CENTER（旧行为）与 TOP，比较正文墨迹的纵向包围盒：
 * - TOP 的首行必须明显高于 CENTER 的首行；
 * - TOP 的正文必须从文本安全区顶部开始，而不是仍悬浮在卡片中部。
 *
 * 不比对整图像素（不同字体的抗锯齿会让逐像素比较很脆），只看「文字到底画在哪」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VerticalAlignRenderTest {

    private val context = RuntimeEnvironment.getApplication()

    /** 正文墨迹的纵向包围盒（top, bottom）；字体色是深墨 #333333，背景是浅底 */
    private fun textVerticalBounds(bitmap: Bitmap): IntRange {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var top = Int.MAX_VALUE
        var bottom = Int.MIN_VALUE
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val c = pixels[y * bitmap.width + x]
                // 透明像素的 RGB 也是 0，会被误判成墨色：先要求完全不透明（圆角外的空白）
                val a = (c ushr 24) and 0xFF
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (a == 255 && r < 120 && g < 120 && b < 120) {
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (top > bottom) return IntRange.EMPTY
        return top..bottom
    }

    private fun render(style: WidgetStyle): Bitmap = WidgetCanvasRenderer.render(
        context = context,
        widthDp = 250,
        heightDp = 110,
        content = "生活不止眼前的苟且，还有诗和远方的田野。",
        style = style,
    )

    @Test
    fun `顶部对齐让正文从文本区顶部开始`() {
        val centered = render(WidgetStyle.DEFAULT.copy(verticalAlign = "CENTER"))
        val topAligned = render(WidgetStyle.DEFAULT.copy(verticalAlign = "TOP"))

        val centerBounds = textVerticalBounds(centered)
        val topBounds = textVerticalBounds(topAligned)
        assertTrue("CENTER 应画得出正文", !centerBounds.isEmpty())
        assertTrue("TOP 应画得出正文", !topBounds.isEmpty())

        assertTrue(
            "顶部对齐的首行应明显高于垂直居中：top=${topBounds.first} center=${centerBounds.first}",
            topBounds.first < centerBounds.first,
        )
        assertTrue(
            "顶部对齐的正文应落在卡片上半部：top=${topBounds.first} height=${topAligned.height}",
            topBounds.first < topAligned.height / 2,
        )
        // 两种模式都不得把正文画出卡片下沿
        assertTrue("CENTER 正文不应越界", centerBounds.last < centered.height)
        assertTrue("TOP 正文不应越界", topBounds.last < topAligned.height)

        centered.recycle()
        topAligned.recycle()
    }
}
