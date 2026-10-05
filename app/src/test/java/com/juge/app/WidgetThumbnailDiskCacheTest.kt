package com.juge.app

import android.graphics.Bitmap
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
 * 缩略图磁盘二级缓存的回归测试。
 *
 * 背景：内存缩略图缓存随进程死亡清空，而「桌面组件 → 快捷面板」几乎总是
 * 冷进程进入。没有磁盘缓存时，30 张缩略图冷渲染合计约 12s CPU，表现为面板
 * 打开后缩略图空白 1~2 秒才逐个补齐（真机实测）。这里锁定四层行为：
 * 渲染后落盘、冷缓存从磁盘取回且不重渲染、磁盘缺失时重渲染并重新落盘、
 * 预热把磁盘图加载回内存。
 *
 * 注意：WidgetCanvasRenderer 是单例，内存缓存在同一 JVM 的多个测试方法间
 * 残留；每个方法开头必须 clearThumbnailRamCacheForTest()，否则上一个方法
 * 渲染过的 key 会命中内存、根本不走磁盘路径（当前方法的 cacheDir 是新的，
 * 磁盘上并没有那张图）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetThumbnailDiskCacheTest {

    private val preset = WidgetStyle.PRESETS.first { it.presetId != null }

    /** 缩略图按 dp 传入、渲染时按密度放大，实际像素尺寸以首次渲染结果为准。 */
    private fun diskFile(context: android.content.Context, key: String): File {
        val dir = File(context.cacheDir, "widget_thumbs")
        return dir.listFiles()!!.firstOrNull { it.name.endsWith("$key.png") && !it.name.endsWith(".tmp") }
            ?: error("缩略图未落盘: $key, 实际文件: ${dir.listFiles()?.map { it.name }}")
    }

    @Test
    fun renderWritesDiskCache() {
        val context = RuntimeEnvironment.getApplication()
        WidgetCanvasRenderer.clearThumbnailRamCacheForTest()
        val key = "${preset.presetId}_150x80"
        val rendersBefore = WidgetCanvasRenderer.thumbnailRenderCountForTest

        val bitmap = WidgetCanvasRenderer.renderThumbnail(context, 150, 80, "", preset)

        assertNotNull(bitmap)
        assertEquals(rendersBefore + 1, WidgetCanvasRenderer.thumbnailRenderCountForTest)
        assertTrue("落盘文件不应为空", diskFile(context, key).length() > 0)
    }

    @Test
    fun coldRamCacheServesThumbnailFromDiskWithoutRendering() {
        val context = RuntimeEnvironment.getApplication()
        WidgetCanvasRenderer.clearThumbnailRamCacheForTest()
        val key = "${preset.presetId}_150x80"
        val rendersBefore = WidgetCanvasRenderer.thumbnailRenderCountForTest

        val first = assertNotNull(WidgetCanvasRenderer.renderThumbnail(context, 150, 80, "", preset))
        assertEquals(rendersBefore + 1, WidgetCanvasRenderer.thumbnailRenderCountForTest)
        val bytesOnDisk = diskFile(context, key).readBytes()

        // 模拟进程重启：内存缓存清空、仅剩磁盘
        WidgetCanvasRenderer.clearThumbnailRamCacheForTest()
        val served = assertNotNull(WidgetCanvasRenderer.renderThumbnail(context, 150, 80, "", preset))

        // 关键断言：没有再次真实渲染——冷启动空白 1~2 秒的回退就发生在这里
        assertEquals(rendersBefore + 1, WidgetCanvasRenderer.thumbnailRenderCountForTest)
        assertEquals("磁盘取回的图与首次渲染尺寸一致", first.width, served.width)
        assertEquals(first.height, served.height)
        assertTrue("磁盘文件应保持原样", diskFile(context, key).readBytes().contentEquals(bytesOnDisk))
        // 磁盘取回后应回填内存缓存：进程内的后续请求直接走内存
        assertNotNull(WidgetCanvasRenderer.cachedThumbnail(150, 80, preset))
    }

    @Test
    fun missingDiskFileTriggersRerenderAndRewrite() {
        val context = RuntimeEnvironment.getApplication()
        WidgetCanvasRenderer.clearThumbnailRamCacheForTest()
        val key = "${preset.presetId}_150x80"
        val rendersBefore = WidgetCanvasRenderer.thumbnailRenderCountForTest

        assertNotNull(WidgetCanvasRenderer.renderThumbnail(context, 150, 80, "", preset))
        diskFile(context, key).delete()
        WidgetCanvasRenderer.clearThumbnailRamCacheForTest()

        val served = assertNotNull(WidgetCanvasRenderer.renderThumbnail(context, 150, 80, "", preset))

        assertEquals(rendersBefore + 2, WidgetCanvasRenderer.thumbnailRenderCountForTest)
        assertNotNull(served)
        assertTrue("重渲染后应重新落盘", diskFile(context, key).length() > 0)
    }

    @Test
    fun prewarmLoadsAllDiskThumbnailsIntoRam() {
        val context = RuntimeEnvironment.getApplication()
        WidgetCanvasRenderer.clearThumbnailRamCacheForTest()
        val rendersBefore = WidgetCanvasRenderer.thumbnailRenderCountForTest

        val first = assertNotNull(WidgetCanvasRenderer.renderThumbnail(context, 150, 80, "", preset))
        assertEquals(rendersBefore + 1, WidgetCanvasRenderer.thumbnailRenderCountForTest)
        WidgetCanvasRenderer.clearThumbnailRamCacheForTest()

        WidgetCanvasRenderer.prewarmThumbnailCache(context)

        val warmed = assertNotNull(WidgetCanvasRenderer.cachedThumbnail(150, 80, preset))
        assertEquals("预热加载的图与首次渲染尺寸一致", first.width, warmed.width)
        assertEquals(first.height, warmed.height)
        assertEquals("预热只解码、不重渲染", rendersBefore + 1, WidgetCanvasRenderer.thumbnailRenderCountForTest)
    }

    private fun <T> assertNotNull(value: T?): T {
        org.junit.Assert.assertNotNull(value)
        return value!!
    }
}
