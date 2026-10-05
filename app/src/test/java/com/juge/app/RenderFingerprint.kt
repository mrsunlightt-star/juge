package com.juge.app

import android.graphics.Bitmap
import android.graphics.Color
import java.security.MessageDigest
import kotlin.math.abs

/**
 * 一张渲染结果的紧凑指纹。
 *
 * 用途：结构重构（拆文件、挪函数、收敛分派）前后必须渲染出同一张图。
 * 不直接存 PNG 做金标准，是因为 4x4 单张就有 1.4MB，且跨机器的 Skia
 * 细微差异会让像素级比对天天假报警。这里取两个对「真实变化」敏感、
 * 对「渲染实现的微小抖动」不敏感的指标：
 *
 *  - 8x4 网格平均色：某个装饰少画了、裁剪区域变了、颜色错了，必然带动若干网格偏移；
 *  - 不透明像素占比：整块元素消失或整体位移会立刻反映出来。
 *
 * 透明像素一律按白底合成后再平均，否则 alpha 的变化会被无视。
 */
internal data class RenderPrint(
    val width: Int,
    val height: Int,
    val opaqueRatio: Double,
    val cells: List<String>,
    val hash: String,
)

internal object RenderFingerprint {

    const val COLS = 8
    const val ROWS = 4

    /** 单个网格平均色的每通道容差（0-255） */
    const val CELL_TOLERANCE = 8

    /** 不透明像素占比容差 */
    const val OPAQUE_TOLERANCE = 0.01

    fun of(bitmap: Bitmap): RenderPrint {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var opaque = 0
        for (p in pixels) if (Color.alpha(p) != 0) opaque++

        val cells = ArrayList<String>(COLS * ROWS)
        for (row in 0 until ROWS) {
            val y0 = row * h / ROWS
            val y1 = ((row + 1) * h / ROWS).coerceAtLeast(y0 + 1)
            for (col in 0 until COLS) {
                val x0 = col * w / COLS
                val x1 = ((col + 1) * w / COLS).coerceAtLeast(x0 + 1)
                var r = 0L
                var g = 0L
                var b = 0L
                var n = 0L
                for (y in y0 until y1) {
                    val rowOffset = y * w
                    for (x in x0 until x1) {
                        val c = pixels[rowOffset + x]
                        val a = Color.alpha(c)
                        r += (Color.red(c) * a + 255 * (255 - a)) / 255
                        g += (Color.green(c) * a + 255 * (255 - a)) / 255
                        b += (Color.blue(c) * a + 255 * (255 - a)) / 255
                        n++
                    }
                }
                cells += hex((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
            }
        }
        return RenderPrint(w, h, opaque.toDouble() / pixels.size, cells, digest(pixels))
    }

    /**
     * 判据是**全图像素哈希**：纯搬移不该动任何一个像素，位级一致才算通过。
     *
     * 网格与占比只在失败时充当诊断（告诉你差在画面哪一块），不做放行依据——
     * 只比网格会漏掉小面积改动（实测只改圆角半径时网格平均色仍落在容差内）。
     * 若哈希变化但网格与占比都无异常，多半是 JDK / Skia 版本差异，
     * 确认后删除基线重新生成即可。
     */
    fun describeMismatch(expected: RenderPrint, actual: RenderPrint): List<String> {
        if (expected.width != actual.width || expected.height != actual.height) {
            return listOf("尺寸变化：${expected.width}x${expected.height} → ${actual.width}x${actual.height}")
        }
        if (expected.hash == actual.hash) return emptyList()

        val problems = ArrayList<String>()
        problems += "像素哈希：${expected.hash} → ${actual.hash}"
        if (abs(expected.opaqueRatio - actual.opaqueRatio) > OPAQUE_TOLERANCE) {
            problems += "不透明占比：%.4f → %.4f".format(expected.opaqueRatio, actual.opaqueRatio)
        }
        var changed = 0
        expected.cells.forEachIndexed { index, before ->
            val after = actual.cells[index]
            if (!withinTolerance(before, after)) {
                changed++
                if (changed <= 8) {
                    problems += "网格#${index}（第${index / COLS}行第${index % COLS}列）：$before → $after"
                }
            }
        }
        if (changed == 0) {
            problems += "网格与占比均在容差内：若确认是环境差异（JDK/Skia）而非逻辑改动，可删除基线重新生成"
        }
        return problems
    }

    private fun withinTolerance(before: String, after: String): Boolean {
        if (before.length != 6 || after.length != 6) return false
        for (i in 0 until 3) {
            val a = before.substring(i * 2, i * 2 + 2).toInt(16)
            val b = after.substring(i * 2, i * 2 + 2).toInt(16)
            if (abs(a - b) > CELL_TOLERANCE) return false
        }
        return true
    }

    private fun hex(r: Int, g: Int, b: Int): String = "%02x%02x%02x".format(r, g, b)

    /** 采样像素的 SHA-256 前 16 位：只写进基线便于人工排查，不参与判定 */
    private fun digest(pixels: IntArray): String {
        val bytes = ByteArray(pixels.size * 4)
        var i = 0
        for (p in pixels) {
            bytes[i++] = (p ushr 24).toByte()
            bytes[i++] = (p ushr 16).toByte()
            bytes[i++] = (p ushr 8).toByte()
            bytes[i++] = p.toByte()
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
            .take(16)
    }
}
