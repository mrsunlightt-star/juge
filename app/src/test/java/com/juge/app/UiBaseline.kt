package com.juge.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.printToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.io.FileOutputStream

/**
 * UI 基线：逐场景比对「整屏截图 + Compose 语义树」。
 *
 * 拆 Activity 的纯搬移很难靠肉眼发现回归（少画一个角标、页签顺序变了、某段文案没了），
 * 所以两样都必须与基线完全一致。基线不存在时按当前实现生成并失败一次，
 * 人工确认 build/ui-shots/ 下的截图正常后重跑即可；确实要改 UI 时删除基线重新生成。
 *
 * @param name 基线文件名（不含扩展名），同时作为语义树目录名
 * @param owner 维护该基线的测试类名，只用于失败提示
 */
internal class UiBaseline(private val name: String, private val owner: String) {

    /** 一个场景的实测结果：指纹 + 可见语义摘要（摘要落盘供人工 diff） */
    data class Scene(val print: UiPrint, val digest: String)

    private val indexFile = File("src/test/resources/ui-baseline/$name.tsv")
    private val digestDir = File("src/test/resources/ui-baseline/$name")

    fun verify(scenes: Map<String, Scene>) {
        if (!indexFile.exists()) {
            write(scenes)
            fail(
                "UI 基线不存在，已按当前实现生成：${indexFile.path}\n" +
                    "请先人工确认 build/ui-shots/*.png 里的界面正常，再重新运行本测试。"
            )
        }

        val expected = readIndex()
        val problems = ArrayList<String>()
        (expected.keys - scenes.keys).forEach { problems += "基线里有、本次未采集：$it" }
        (scenes.keys - expected.keys).forEach { problems += "本次多出、基线未覆盖：$it" }

        for ((scene, before) in expected) {
            val after = scenes[scene] ?: continue
            if (before.width != after.print.width || before.height != after.print.height) {
                problems += "$scene：截图尺寸 ${before.width}x${before.height} → ${after.print.width}x${after.print.height}"
                continue
            }
            if (before.pixelHash != after.print.pixelHash.take(HASH_LENGTH)) {
                problems += "$scene：截图像素变化（${before.pixelHash} → ${after.print.pixelHash.take(HASH_LENGTH)}）"
            }
            if (before.digestHash != after.print.digestHash.take(HASH_LENGTH)) {
                problems += "$scene：可见语义变化（节点数 ${before.nodeCount} → ${after.print.nodeCount}）"
                problems += describeDigestDiff(File(digestDir, "$scene.digest.txt"), after.digest)
            }
        }

        if (problems.isNotEmpty()) {
            fail(
                "$owner 的 UI 与基线不一致：\n" +
                    problems.joinToString("\n") +
                    "\n本次截图与语义摘要已导出到 build/ui-shots/，可逐个场景对比：" +
                    "\n  diff ${digestDir.path}/<场景>.digest.txt build/ui-shots/<场景>.digest.txt" +
                    "\n确认改动符合预期后，删除 ${indexFile.path} 与 ${digestDir.path}/ 重新生成。"
            )
        }
        assertEquals("基线条目数应与实际采集数一致", expected.size, scenes.size)
    }

    private fun describeDigestDiff(expectedFile: File, actualDigest: String): List<String> {
        if (!expectedFile.exists()) return listOf("  基线语义摘要缺失：${expectedFile.path}")
        // 摘要以换行结尾，末尾会多出一个空行，不算差异
        val expectedLines = expectedFile.readLines().dropLastWhile { it.isBlank() }
        val actualLines = actualDigest.lines().dropLastWhile { it.isBlank() }
        val out = ArrayList<String>()
        for (i in 0 until maxOf(expectedLines.size, actualLines.size)) {
            val before = expectedLines.getOrNull(i) ?: "<无>"
            val after = actualLines.getOrNull(i) ?: "<无>"
            if (before == after) continue
            out += "  第${i + 1}行 - ${before.trim().take(150)}"
            out += "  第${i + 1}行 + ${after.trim().take(150)}"
            if (out.size >= 12) {
                out += "  …（差异过多，完整对比见 build/ui-shots/）"
                break
            }
        }
        return out
    }

    private fun readIndex(): LinkedHashMap<String, UiPrint> {
        val result = LinkedHashMap<String, UiPrint>()
        indexFile.forEachLine { line ->
            if (line.isBlank() || line.startsWith("#")) return@forEachLine
            val parts = line.split('\t')
            if (parts.size < 5) return@forEachLine
            val dims = parts[1].split('x')
            result[parts[0]] = UiPrint(
                width = dims[0].toInt(),
                height = dims[1].toInt(),
                nodeCount = parts[2].toInt(),
                pixelHash = parts[3],
                digestHash = parts[4],
            )
        }
        return result
    }

    private fun write(scenes: Map<String, Scene>) {
        indexFile.parentFile?.mkdirs()
        digestDir.mkdirs()
        indexFile.writeText(
            buildString {
                appendLine("# 句阁 UI 基线 —— 由 $owner 维护")
                appendLine("# 拆 Activity 前后，截图像素与可见语义都应完全一致；删除本文件后重跑可重新生成（须人工确认）。")
                appendLine("# 字段: 场景 \\t WxH \\t 可见语义节点数 \\t sha256[:$HASH_LENGTH](截图) \\t sha256[:$HASH_LENGTH](语义摘要)")
                for ((scene, value) in scenes) {
                    append(scene).append('\t')
                        .append("${value.print.width}x${value.print.height}").append('\t')
                        .append(value.print.nodeCount).append('\t')
                        .append(value.print.pixelHash.take(HASH_LENGTH)).append('\t')
                        .append(value.print.digestHash.take(HASH_LENGTH))
                        .append('\n')
                }
            }
        )
        for ((scene, value) in scenes) {
            File(digestDir, "$scene.digest.txt").writeText(value.digest)
        }
    }

    private companion object {
        const val HASH_LENGTH = 16
    }
}

/**
 * 采集一个场景：把界面画到 Bitmap（真实截图），同时抓一份语义树。
 * 截图与语义摘要固定导出到 build/ui-shots/，无论成功失败都能人工查看、逐个场景 diff。
 */
internal object UiSnapshot {

    /** 稳定性判定：轮询间隔、连续稳定多久算稳定、至少要等多久（给后台渲染留时间）、最多等多久 */
    private const val SETTLE_INTERVAL_MS = 100L
    private const val SETTLE_QUIET_MS = 300L
    private const val SETTLE_MIN_ATTEMPTS = 10
    private const val SETTLE_MAX_ATTEMPTS = 100


    /**
     * 取真实界面的语义树。
     *
     * Compose Dialog 在 Robolectric 下会额外注册一个 0x0 的空 Compose 根，`onRoot()`
     * 会因此报「找到 2 个根」。这里按面积取真实界面那个，并要求非空根唯一——
     * 一旦哪天真的多出一个界面，测试要失败而不是悄悄比错对象。
     */
    fun rootTree(compose: ComposeContentTestRule): String {
        val roots = compose.onAllNodes(isRoot())
        val nodes = roots.fetchSemanticsNodes()
        val nonEmpty = nodes.indices.filter { nodes[it].size.width * nodes[it].size.height > 0 }
        assertTrue("应恰好有一个非空的 Compose 根，实际 ${nonEmpty.size} 个", nonEmpty.size == 1)
        return roots[nonEmpty.first()].printToString()
    }

    /**
     * 反复截图直到画面连续两次完全一致。
     *
     * 风格缩略图是 `produceState` + `Dispatchers.Default` 异步渲染的，快慢取决于后台线程，
     * 截在第一帧还是渲染完成后完全看运气（实测同一份代码会截出「有缩略图」和「空白」两种图）。
     * 这里先等够 [SETTLE_MIN_ATTEMPTS] 再去要求「连续 [SETTLE_QUIET_MS] 不变」，
     * 把不确定性挡在基线之外；真的画错了则会稳定在新的错误画面上，照样失败。
     */
    private fun settle(root: View, compose: ComposeContentTestRule): Bitmap {
        var previous: IntArray? = null
        var stableSince = 0L
        repeat(SETTLE_MAX_ATTEMPTS) { attempt ->
            compose.waitForIdle()
            val bitmap = draw(root)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            if (previous != null && previous.contentEquals(pixels)) {
                if (attempt >= SETTLE_MIN_ATTEMPTS &&
                    System.currentTimeMillis() - stableSince >= SETTLE_QUIET_MS
                ) {
                    return bitmap
                }
            } else {
                stableSince = System.currentTimeMillis()
                previous = pixels
            }
            bitmap.recycle()
            Thread.sleep(SETTLE_INTERVAL_MS)
            if (attempt == SETTLE_MAX_ATTEMPTS - 1) {
                fail("界面在 ${SETTLE_MAX_ATTEMPTS * SETTLE_INTERVAL_MS / 1000} 秒内始终在变化，无法建立基线")
            }
        }
        error("unreachable")
    }

    private fun draw(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(
            view.width.coerceAtLeast(1),
            view.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        view.draw(Canvas(bitmap))
        return bitmap
    }

    /** 取要截图的视图，并保证它真的被测量/布局过 */
    fun snapshotRoot(activity: Activity, compose: ComposeContentTestRule): View {
        // 隐私弹窗等 Compose Dialog 画在独立窗口里，
        // Activity 自己的 content 是空的（透明底），截它只会得到一张全透明的图。
        val dialogContent = ShadowDialog.getLatestDialog()
            ?.takeIf { it.isShowing }
            ?.window?.decorView
            ?.findViewById<ViewGroup>(android.R.id.content)
        val content = if (dialogContent != null && dialogContent.width > 0) dialogContent
        else activity.findViewById<ViewGroup>(android.R.id.content)

        if (content.width > 0 && content.height > 0) return content

        // Robolectric 只在「窗口首次可见」时跑一次布局遍历。MainActivity 的 setContent 发生在
        // onCreate，赶得上那一次；若某个界面晚一步 setContent，此后不会再有遍历，
        // 视图尺寸永远是 0——截出来是 1x1，懒加载列表也只组合出不确定的一小段。
        // 这里按屏幕尺寸补一次测量/布局，让各种情况拿到同一套确定的结果。
        val metrics = activity.resources.displayMetrics
        val decor = content.rootView
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
        )
        decor.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
        compose.waitForIdle()
        return content
    }

    fun capture(scene: String, activity: Activity, compose: ComposeContentTestRule): UiBaseline.Scene {
        val root = snapshotRoot(activity, compose)
        val bitmap = settle(root, compose)
        val rawTree = rootTree(compose)
        val outDir = File("build/ui-shots").apply { mkdirs() }
        FileOutputStream(File(outDir, "$scene.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val digest = UiFingerprint.visibleDigest(rawTree, bitmap.width, bitmap.height)
        File(outDir, "$scene.digest.txt").writeText(digest)
        return UiBaseline.Scene(UiFingerprint.of(bitmap, rawTree), digest)
    }
}
