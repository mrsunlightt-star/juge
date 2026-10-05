package com.juge.app

import android.content.Context
import android.graphics.Bitmap
import com.juge.app.data.WidgetStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 渲染指纹基线：结构重构（拆文件、挪函数、收敛分派）的可验证前提。
 *
 * 判据不是「能画出来」，而是「和重构前画得一样」。纯搬移不该改变任何像素，
 * 一旦某个风格的网格色明显偏移，说明重构改到了行为——这正是最容易发生、
 * 又最难靠肉眼发现的回归。
 *
 * 运行：
 *   ./gradlew :app:testDebugUnitTest --tests "com.juge.app.WidgetRenderBaselineTest"
 * 基线：
 *   app/src/test/resources/widget-render-baseline.tsv
 *
 * 基线不存在时会按当前实现生成并失败一次，人工确认渲染结果正常后重跑即可。
 * 需要重新生成（例如确实要改绘制逻辑）时，删除该文件后重跑。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetRenderBaselineTest {

    private val baselineFile = File("src/test/resources/widget-render-baseline.tsv")

    @Test
    fun `全部风格的渲染指纹与基线一致`() {
        val actual = renderAll()

        if (!baselineFile.exists()) {
            writeBaseline(actual)
            fail(
                "基线不存在，已按当前实现生成：${baselineFile.absolutePath}\n" +
                    "请人工确认这批渲染结果正常（可对照 build/widget-previews/ 下的 PNG），然后重新运行本测试。"
            )
        }

        val expected = readBaseline()
        val problems = ArrayList<String>()

        val missing = expected.keys - actual.keys
        val extra = actual.keys - expected.keys
        if (missing.isNotEmpty()) problems += "基线里有、本次未渲染：${missing.take(5)}"
        if (extra.isNotEmpty()) problems += "本次多出、基线未覆盖：${extra.take(5)}"
        for ((key, before) in expected) {
            val after = actual[key] ?: continue
            RenderFingerprint.describeMismatch(before, after).forEach { problems += "$key → $it" }
        }

        if (problems.isNotEmpty()) {
            fail(
                "渲染指纹与基线不一致（共 ${problems.size} 处，最多列出 20 处）：\n" +
                    problems.take(20).joinToString("\n") +
                    "\n若这是有意的绘制改动，请人工确认后删除 ${baselineFile.path} 重新生成。"
            )
        }
        assertEquals("基线条目数应与实际渲染数一致", expected.size, actual.size)
    }

    private fun renderAll(): LinkedHashMap<String, RenderPrint> {
        val context = RuntimeEnvironment.getApplication()
        val result = LinkedHashMap<String, RenderPrint>()
        val styles = RenderCases.allStyles()
        assertTrue("预设列表不应为空", styles.isNotEmpty())

        for ((sizeName, dims) in RenderCases.SIZES) {
            val (widthDp, heightDp) = dims
            for ((name, style) in styles) {
                result["$sizeName|默认|$name"] =
                    render(context, widthDp, heightDp, RenderCases.SAMPLE_TEXT, style)
                // 长文本只补 4x2：排版分支与默认文本不同，两个尺寸都跑性价比低
                if (sizeName == "4x2") {
                    result["$sizeName|长文|$name"] =
                        render(context, widthDp, heightDp, RenderCases.LONG_TEXT, style)
                }
            }
        }
        return result
    }

    private fun render(
        context: Context,
        widthDp: Int,
        heightDp: Int,
        text: String,
        style: WidgetStyle,
    ): RenderPrint {
        val bitmap = WidgetCanvasRenderer.render(
            context = context,
            widthDp = widthDp,
            heightDp = heightDp,
            content = text,
            style = style,
        )
        val print = try {
            RenderFingerprint.of(bitmap)
        } finally {
            bitmap.recycle()
        }
        return print
    }

    private fun writeBaseline(prints: Map<String, RenderPrint>) {
        baselineFile.parentFile?.mkdirs()
        val text = buildString {
            appendLine("# 句阁组件渲染指纹基线 —— 由 WidgetRenderBaselineTest 维护")
            appendLine("# 结构重构前后像素必须一致；删除本文件后重跑可重新生成（须人工确认）。")
            appendLine("# 字段: key \\t WxH \\t 不透明占比 \\t 32个网格平均色(RRGGBB) \\t sha256[:16]")
            for ((key, print) in prints) {
                append(key).append('\t')
                append("${print.width}x${print.height}").append('\t')
                append("%.4f".format(print.opaqueRatio)).append('\t')
                append(print.cells.joinToString(",")).append('\t')
                append(print.hash).append('\n')
            }
        }
        baselineFile.writeText(text)
    }

    private fun readBaseline(): Map<String, RenderPrint> {
        val result = LinkedHashMap<String, RenderPrint>()
        baselineFile.forEachLine { line ->
            if (line.isBlank() || line.startsWith("#")) return@forEachLine
            val parts = line.split('\t')
            if (parts.size != 5) return@forEachLine
            val dims = parts[1].split('x')
            result[parts[0]] = RenderPrint(
                width = dims[0].toInt(),
                height = dims[1].toInt(),
                opaqueRatio = parts[2].toDouble(),
                cells = parts[3].split(','),
                hash = parts[4],
            )
        }
        return result
    }
}
