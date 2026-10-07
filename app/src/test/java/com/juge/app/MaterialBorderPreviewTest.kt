package com.juge.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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
 * 材质边框族（毛绒边框 / 素描线卡 / 绿藤缠绕）的人工审图台。
 *
 * 这三款风格的边是沿卡片轮廓**现画**的（`BorderMaterialRenderer`），所以有两个只有看图
 * 才能判断的问题：材质在**扁组件（4×2）与方组件（4×4）**上疏密是否一致、
 * 在**圆角被拧到两端**时藤条与绒边会不会散架。本测试把三款风格 × 两种尺寸拼成对比图，
 * 另出一张圆角极端值（0 / 30）的对照，省得每次都要装到真机上去拧滑条。
 *
 * 运行：
 *   ./gradlew :app:testDebugUnitTest --tests "com.juge.app.MaterialBorderPreviewTest" --offline
 * 产物：
 *   app/build/material-border/<风格>_对比.png        4×2 + 4×4 拼版
 *   app/build/material-border/<风格>_圆角对比.png     圆角 0 / 14 / 30 对照
 *   app/build/material-border/<风格>_<尺寸>.png       全分辨率单图
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MaterialBorderPreviewTest {

    private val styles: List<Pair<String, WidgetStyle>> = listOf(
        "毛绒边框" to preset("p_plush_card"),
        "素描线卡" to preset("p_sketch_card"),
        "绿藤缠绕" to preset("p_vine_card"),
    )

    @Test
    fun renderMaterialBorderStyles() {
        val context = RuntimeEnvironment.getApplication()
        val outputDir = File("build/material-border").absoluteFile
        outputDir.mkdirs()

        for ((name, style) in styles) {
            val rendered = LinkedHashMap<String, Bitmap>()
            for ((sizeName, dims) in RenderCases.SIZES) {
                val bitmap = render(context, dims, RenderCases.SAMPLE_TEXT, style)
                assertTrue("$name/$sizeName 渲染结果全透明，疑似渲染失败", hasOpaquePixel(bitmap))
                rendered[sizeName] = bitmap
                writePng(File(outputDir, "${RenderCases.sanitize(name)}_$sizeName.png"), bitmap)
            }
            writePng(
                File(outputDir, "${RenderCases.sanitize(name)}_对比.png"),
                compose(rendered) { sizeName -> sizeName }
            )

            // 圆角是这两款风格暴露给用户的滑条：材质沿轮廓现画，必须跟得上圆角的变化
            val corner = LinkedHashMap<String, Bitmap>()
            for (radius in listOf(0f, style.cornerRadiusDp, 30f)) {
                val key = "圆角 ${radius.toInt()}"
                val bitmap = render(context, RenderCases.SIZES.last().second, RenderCases.SAMPLE_TEXT, style.copy(cornerRadiusDp = radius))
                corner[key] = bitmap
            }
            writePng(
                File(outputDir, "${RenderCases.sanitize(name)}_圆角对比.png"),
                compose(corner) { it }
            )

            (rendered.values + corner.values).forEach { it.recycle() }
        }

        println("材质边框风格图 -> $outputDir")
    }

    private fun preset(presetId: String): WidgetStyle =
        WidgetStyle.PRESETS.firstOrNull { it.presetId == presetId }
            ?: error("预设不存在：$presetId（材质边框族的三款风格必须都在 PRESETS 里）")

    private fun render(
        context: android.content.Context,
        dims: Pair<Int, Int>,
        text: String,
        style: WidgetStyle
    ): Bitmap = WidgetCanvasRenderer.render(
        context = context,
        widthDp = dims.first,
        heightDp = dims.second,
        content = text,
        style = style
    )

    /**
     * 拼版：每张图一行，缩到 0.5 倍。原图 4×4 单张就有 1125px，不缩一屏看不全；
     * 要看细节再开全分辨率的单图。
     */
    private fun compose(bitmaps: Map<String, Bitmap>, label: (String) -> String): Bitmap {
        val scale = 0.5f
        val pad = 16
        val labelH = 26
        val rowGap = 10

        val cellW = bitmaps.values.maxOf { (it.width * scale).toInt() }
        val cellH = bitmaps.values.maxOf { (it.height * scale).toInt() }
        val width = pad * 2 + cellW
        val height = pad * 2 + (cellH + labelH + rowGap) * bitmaps.size - rowGap
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        // 中性浅灰底：三款风格的卡片四周都是透明的，白底看不出材质往卡外洇出的那一圈
        canvas.drawColor(Color.parseColor("#EDEDED"))

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#333333")
            textSize = 16f
        }
        val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        bitmaps.entries.forEachIndexed { index, (key, bitmap) ->
            val rowTop = pad + (cellH + labelH + rowGap) * index
            canvas.drawText(label(key), pad.toFloat(), (rowTop + 18).toFloat(), textPaint)
            val y = rowTop + labelH
            val dst = Rect(pad, y, pad + (bitmap.width * scale).toInt(), y + (bitmap.height * scale).toInt())
            canvas.drawBitmap(bitmap, null, dst, bitmapPaint)
        }
        return out
    }

    private fun writePng(file: File, bitmap: Bitmap) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("${file.name} 未写出", file.exists() && file.length() > 0)
    }

    /** 网格采样若干像素，只要存在一个非全透明像素即认为渲染有内容 */
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
}
