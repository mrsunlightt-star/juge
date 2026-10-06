package com.juge.app

import android.graphics.Bitmap
import android.graphics.Color
import com.juge.app.data.WidgetShape
import com.juge.app.data.WidgetStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 明信片插图预设的样式换算回归测试。
 *
 * 背景：插图预设原先从**当前样式** copy 形状与素材名，其余字段（底色、字体、字体色…）
 * 全部继承上一个风格。用户在颜色面板选过「深墨」之类的深色后，再点「搏击俱乐部」，
 * 组件下半的文字显示区就变成近黑色，与素材缩略图（按预设原样渲染，白色文字区）完全对不上。
 *
 * 这里锁两件事：文字显示区底色一律取预设自身声明（明信片类都是白色），
 * 以及只有素材本身设计成别的颜色时（天空蓝）才不是白的。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IllustrationPresetStyleTest {

    /** 上一个风格是「深墨」底色 —— 就是复现黑底时用户的处境 */
    private val darkPrevious = WidgetStyle(backgroundColor = Color.parseColor("#1F2436"))

    private fun applyIllustration(resName: String, previous: WidgetStyle = darkPrevious): WidgetStyle =
        WidgetStyle.illustrationStyle(resName, previous).copy(
            backgroundOpacity = previous.backgroundOpacity,
            cornerRadiusDp = previous.cornerRadiusDp
        )

    @Test
    fun `套用明信片插图不继承上一个风格的底色`() {
        for (resName in listOf("boji_julebu", "breaking_bad", "v_for_vendetta", "aile_zhi_cheng")) {
            val style = applyIllustration(resName)
            assertEquals("$resName 的文字显示区应为预设声明的白色", Color.WHITE, style.backgroundColor)
            assertEquals(WidgetShape.SPLIT_CARD, style.shape)
            assertEquals(resName, style.presetImageResName)
        }
    }

    @Test
    fun `素材本身设计成别的颜色时保留自己的底色`() {
        // 天空蓝：预设里底色就是蓝色，不该被改成白色
        val style = applyIllustration("rectangle_1")
        assertEquals(Color.parseColor("#1E6DD0"), style.backgroundColor)
        assertEquals(WidgetShape.RECTANGLE, style.shape)
    }

    @Test
    fun `用户当前的不透明度与圆角仍然沿用`() {
        val previous = darkPrevious.copy(backgroundOpacity = 0.5f, cornerRadiusDp = 24f)
        val style = applyIllustration("boji_julebu", previous)
        assertEquals(0.5f, style.backgroundOpacity)
        assertEquals(24f, style.cornerRadiusDp)
    }

    @Test
    fun `所有插图素材都能找到预设定义`() {
        for ((resName, displayName) in WidgetStyle.ILLUSTRATION_PRESETS) {
            val style = applyIllustration(resName)
            assertEquals("$displayName 未取到自己的预设", resName, style.presetImageResName)
            // 找不到预设时只能退回「保留当前样式」，presetId 会留空 —— 这条断言就是防这件事
            assertNotNull("$displayName 没找到对应的预设定义，退回成了继承当前样式", style.presetId)
        }
    }

    /** 渲染层面的回归：深色风格之后套用搏击俱乐部，下半文字区必须是白的 */
    @Test
    fun `渲染出的文字显示区不是深色`() {
        val context = RuntimeEnvironment.getApplication()
        val bitmap = WidgetCanvasRenderer.render(
            context = context,
            widthDp = 250,
            heightDp = 110,
            content = "弱小和无知不是生存的障碍，傲慢才是。",
            style = applyIllustration("boji_julebu")
        )
        try {
            // 顺手留一张 PNG 供人工比对（与 WidgetPreviewRenderTest 的产物同性质，不参与断言）
            val dir = File("build/illustration-preset-previews").absoluteFile.apply { mkdirs() }
            File(dir, "搏击俱乐部_250x110.png").outputStream()
                .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

            val panel = dominantColor(bitmap, yFrom = 0.56f, yTo = 0.96f)
            assertEquals("文字显示区底色应为白色", Color.WHITE, panel)
            assertTrue("文字显示区不应是深色", brightness(panel) > 0.5f)
        } finally {
            bitmap.recycle()
        }
    }

    /** 文字显示区（下半）里出现次数最多的颜色 = 面板底色，避开文字的笔画像素 */
    private fun dominantColor(bitmap: Bitmap, yFrom: Float, yTo: Float): Int {
        val counts = HashMap<Int, Int>()
        var y = (bitmap.height * yFrom).toInt()
        while (y < (bitmap.height * yTo).toInt()) {
            var x = 4
            while (x < bitmap.width - 4) {
                val c = bitmap.getPixel(x, y)
                counts[c] = (counts[c] ?: 0) + 1
                x += 3
            }
            y += 3
        }
        return counts.maxByOrNull { it.value }!!.key
    }

    private fun brightness(color: Int): Float =
        (Color.red(color) * 0.299f + Color.green(color) * 0.587f + Color.blue(color) * 0.114f) / 255f
}
