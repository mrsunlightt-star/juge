package com.juge.app

import android.graphics.Bitmap
import com.juge.app.data.ImageScaleMode
import com.juge.app.data.WidgetShape
import com.juge.app.data.WidgetStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 在 JVM 上把所有组件风格渲染成 PNG，用于不连接真机/模拟器时查看渲染效果。
 *
 * 运行：
 *   ./gradlew :app:testDebugUnitTest --tests "com.juge.app.WidgetPreviewRenderTest"
 * 产物：
 *   app/build/widget-previews/{4x2,4x4}/<风格>.png
 *
 * 采用 Robolectric 的 NATIVE 图形模式，走真实 Skia 渲染，因此字体、圆角、
 * 阴影、贴图等效果与真机一致；density 用 xxhdpi 以还原真机的组件宽高比。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetPreviewRenderTest {

    private val sampleText = "生活不止眼前的苟且，还有诗和远方的田野。"

    private val sizes = listOf(
        "4x2" to (250 to 110),
        "4x4" to (250 to 250)
    )

    @Test
    fun renderAllWidgetStylesToPng() {
        val context = RuntimeEnvironment.getApplication()
        val outputDir = File("build/widget-previews").absoluteFile

        val styles = buildList {
            WidgetStyle.PRESETS.forEachIndexed { index, style ->
                add((style.presetId ?: "preset_$index") to style)
            }
            WidgetStyle.ILLUSTRATION_PRESETS.forEach { (resName, displayName) ->
                add("插图_$displayName" to illustrationStyle(resName))
            }
        }
        assertTrue("预设列表不应为空", styles.isNotEmpty())

        var rendered = 0
        for ((sizeName, dims) in sizes) {
            val (widthDp, heightDp) = dims
            val dir = File(outputDir, sizeName).apply { mkdirs() }

            for ((name, style) in styles) {
                val bitmap = WidgetCanvasRenderer.render(
                    context = context,
                    widthDp = widthDp,
                    heightDp = heightDp,
                    content = sampleText,
                    style = style
                )
                try {
                    // 断言真的渲染出了内容，而不是一张空白或全透明的图
                    assertTrue("$sizeName/$name 渲染尺寸异常", bitmap.width > 0 && bitmap.height > 0)
                    assertTrue("$sizeName/$name 渲染结果全透明，疑似渲染失败", hasOpaquePixel(bitmap))

                    val file = File(dir, "${sanitize(name)}.png")
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    assertTrue("$sizeName/$name 未写出 PNG", file.exists() && file.length() > 0)
                } finally {
                    bitmap.recycle()
                }
                rendered++
            }
        }

        println("已生成 $rendered 张预览图 -> $outputDir")
        assertEquals("预览图数量应为 预设数 × 尺寸数", styles.size * sizes.size, rendered)
    }

    /** 网格采样若干像素，只要存在一个非全透明像素即认为渲染有内容 */
    private fun hasOpaquePixel(bitmap: Bitmap): Boolean {
        val stepX = (bitmap.width / 20).coerceAtLeast(1)
        val stepY = (bitmap.height / 20).coerceAtLeast(1)
        var x = 0
        while (x < bitmap.width) {
            var y = 0
            while (y < bitmap.height) {
                if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) != 0) return true
                y += stepY
            }
            x += stepX
        }
        return false
    }

    // 插图类风格与首页一致：套用对应预设（形状/缩放模式随预设），背景图按资源名加载
    private fun illustrationStyle(resName: String): WidgetStyle {
        val matched = WidgetStyle.PRESETS.find { it.presetImageResName == resName }
        return matched?.copy(backgroundImagePath = null)
            ?: WidgetStyle(
                shape = WidgetShape.SPLIT_CARD,
                presetImageResName = resName,
                backgroundImagePath = null,
                bgImageScaleMode = ImageScaleMode.CENTER_CROP
            )
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^\\p{L}\\p{N}_-]"), "_")
}