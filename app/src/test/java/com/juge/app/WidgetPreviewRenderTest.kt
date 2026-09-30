package com.juge.app

import android.graphics.Bitmap
import com.juge.app.data.ImageScaleMode
import com.juge.app.data.TrialManager
import com.juge.app.data.WidgetShape
import com.juge.app.data.WidgetStyle
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
        val trialManager = TrialManager.getInstance(context)
        val outputDir = File("build/widget-previews").absoluteFile

        val styles = buildList {
            WidgetStyle.PRESETS.forEachIndexed { index, style ->
                add((style.presetId ?: "preset_$index") to style)
            }
            WidgetStyle.ILLUSTRATION_PRESETS.forEach { (resName, displayName) ->
                add("插图_$displayName" to illustrationStyle(resName))
            }
        }

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
                    style = style,
                    trialManager = trialManager,
                    isPreview = true
                )
                val file = File(dir, "${sanitize(name)}.png")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                rendered++
            }
        }

        println("已生成 $rendered 张预览图 -> $outputDir")
        assertTrue("未生成任何预览图", rendered > 0)
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