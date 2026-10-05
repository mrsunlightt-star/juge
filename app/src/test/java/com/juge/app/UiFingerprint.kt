package com.juge.app

import android.graphics.Bitmap
import java.security.MessageDigest

/** 单屏 UI 的指纹：截图像素 + 可见语义节点摘要。 */
internal data class UiPrint(
    val width: Int,
    val height: Int,
    val nodeCount: Int,
    val pixelHash: String,
    val digestHash: String,
)

/**
 * UI 基线指纹：对象是「整屏截图 + Compose 语义树」，思路与 [RenderFingerprint] 一致。
 *
 * 拆 MainActivity 属于纯搬移，既不该动像素，也不该动界面上的文本、控件与它们的位置；
 * 把它压成指纹后，视觉回归与结构回归都会立刻失败。
 *
 * 截图是**全图像素哈希**（和渲染指纹一样位级比对）。语义树则先摘成「可见节点摘要」：
 * 只保留完全落在屏幕内、且带用户可见属性（文本、无障碍描述、角色、开关状态）的节点，
 * 并按位置排序。原因是不这么做的语义树根本没法当基线——懒加载列表会把视口外的条目
 * 提前组合出来，具体多组合几个每次运行都不一样，而它们既看不见也不影响像素。
 */
internal object UiFingerprint {

    private val NODE_LINE =
        Regex("""Node #\d+ at \(l=([-\d.]+), t=([-\d.]+), r=([-\d.]+), b=([-\d.]+)\)px""")

    private val PROPERTY_LINE = Regex(
        """^\s*[|\s]*([A-Za-z][A-Za-z0-9]*) = '(.*)'\s*$""",
        RegexOption.DOT_MATCHES_ALL
    )

    /** 属性行的开头（值还未必结束）：`Text = '` 这种 */
    private val PROPERTY_START = Regex("""^\s*[|\s]*[A-Za-z][A-Za-z0-9]* = '""")

    /** 摘要保留的属性：能反映「用户看到什么、能点什么」 */
    private val KEPT_PROPERTIES =
        setOf("Text", "EditableText", "ContentDescription", "Role", "ToggleableState", "IsDialog")

    fun of(bitmap: Bitmap, rawTree: String): UiPrint {
        val digest = visibleDigest(rawTree, bitmap.width, bitmap.height)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return UiPrint(
            width = bitmap.width,
            height = bitmap.height,
            nodeCount = digest.lineSequence().count { it.isNotBlank() && !it.startsWith("#") },
            pixelHash = digest(pixels),
            digestHash = digest(digest.toByteArray(Charsets.UTF_8)),
        )
    }

    /**
     * 把 `printToString()` 的语义树摘成稳定的可见节点摘要：
     * 每行一个「位置 | 属性…」，按行排序，与遍历顺序、视口外条目无关。
     *
     * 属性值本身可能带换行（协议正文、多段文案的 `Text` 就常是），所以先把
     * 这类「跨行属性」折成一条逻辑行再解析——否则它们既匹配不上属性行，又会把
     * 标签当成新节点，结果是这类文案整段从基线里消失（改错了也不报警）。
     */
    fun visibleDigest(rawTree: String, width: Int, height: Int): String {
        val entries = ArrayList<String>()
        var bounds: IntArray? = null
        var properties = ArrayList<String>()

        fun flush() {
            val rect = bounds
            bounds = null
            val props = properties
            properties = ArrayList()
            if (rect == null || props.isEmpty()) return
            // 只有完全落在屏幕内、且真的有面积的节点才是「看得到的」
            if (rect[0] < 0 || rect[1] < 0 || rect[2] > width || rect[3] > height) return
            if (rect[2] <= rect[0] || rect[3] <= rect[1]) return
            entries += rect.joinToString(",") + " | " + props.joinToString(" | ")
        }

        logicalLines(rawTree).forEach { line ->
            val node = NODE_LINE.find(line)
            if (node != null) {
                flush()
                bounds = IntArray(4) { node.groupValues[it + 1].toDouble().toInt() }
                return@forEach
            }
            val property = PROPERTY_LINE.find(line) ?: return@forEach
            if (property.groupValues[1] in KEPT_PROPERTIES) {
                // 换行压成 \n：摘要是「一行一个节点」，跨行值不能把行数搅乱
                val value = property.groupValues[2].replace("\n", "\\n")
                properties += "${property.groupValues[1]}='$value'"
            }
        }
        flush()

        entries.sort()
        return buildString {
            for (entry in entries) append(entry).append('\n')
        }
    }

    /**
     * 把语义树折成逻辑行：属性值跨行时合并成一行，其余行原样保留。
     * 值以 `'` 收尾即视为结束——`printToString()` 的格式就是这样。
     */
    private fun logicalLines(rawTree: String): List<String> {
        val out = ArrayList<String>()
        var buffer: StringBuilder? = null
        for (line in rawTree.lineSequence()) {
            val pending = buffer
            if (pending != null) {
                pending.append('\n').append(line.trim())
                if (line.trimEnd().endsWith("'")) {
                    out += pending.toString()
                    buffer = null
                }
                continue
            }
            if (PROPERTY_START.containsMatchIn(line) && !line.trimEnd().endsWith("'")) {
                buffer = StringBuilder(line.trimEnd())
                continue
            }
            out += line
        }
        buffer?.let { out += it.toString() }
        return out
    }

    private fun digest(pixels: IntArray): String {
        val bytes = ByteArray(pixels.size * 4)
        var i = 0
        for (p in pixels) {
            bytes[i++] = (p ushr 24).toByte()
            bytes[i++] = (p ushr 16).toByte()
            bytes[i++] = (p ushr 8).toByte()
            bytes[i++] = p.toByte()
        }
        return sha256(bytes)
    }

    private fun digest(bytes: ByteArray): String = sha256(bytes)

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
