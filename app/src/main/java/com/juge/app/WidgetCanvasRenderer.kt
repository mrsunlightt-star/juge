package com.juge.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.juge.app.data.WidgetStyle
import com.juge.app.render.CardTextRenderer
import com.juge.app.render.RenderScene
import com.juge.app.render.WidgetRenderPipeline
import com.juge.app.render.WidgetRenderKernel.MIN_BITMAP_SIZE
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

object WidgetCanvasRenderer {

    private const val RENDER_DENSITY_SCALE = 1.5f

    /**
     * 渲染一张组件位图。
     *
     * 这里只负责尺寸换算与阶段调度，具体实现分散在三个地方：
     * 绘制阶段见 [WidgetRenderPipeline]，正文排版见 [CardTextRenderer]，
     * 形状之间的差异（是否内缩 / 强制直角 / 是否走整卡铺图…）见 [RenderScene.traits]。
     *
     * 注意：返回的 Bitmap 由调用方负责回收。
     */
    fun render(
        context: Context,
        widthDp: Int,
        heightDp: Int,
        content: String,
        style: WidgetStyle
    ): Bitmap {
        // 将 dp 尺寸转为像素，增加 RENDER_DENSITY_SCALE 倍分辨率防止桌面模糊
        val scale = context.resources.displayMetrics.density
        val densityScale = scale * RENDER_DENSITY_SCALE
        val targetWidth = (widthDp * densityScale).toInt().coerceAtLeast(MIN_BITMAP_SIZE)
        val targetHeight = (heightDp * densityScale).toInt().coerceAtLeast(MIN_BITMAP_SIZE)

        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

        // 1. 形状裁切路径：随 RenderScene 一起算定
        val scene = RenderScene(context, content, style, scale, densityScale, targetWidth, targetHeight, bitmap)
        // 2. 整卡投影与底色
        WidgetRenderPipeline.drawCardBackground(scene)
        // 3. 背景图（自定义路径 / 内置预设插画）
        WidgetRenderPipeline.drawBackgroundImage(scene)
        // 4. 家族装饰（明信片文字区 / 贴纸 / 城市 / 便签 / 书架 / 盒子 / 画框）
        WidgetRenderPipeline.drawFamilyChrome(scene)
        // 5. 纸张纹理与卡片描边
        WidgetRenderPipeline.drawTextureAndBorder(scene)
        // 6. 正文
        CardTextRenderer.drawContent(scene)
        // 7. 压在正文之上的叠加层（CRT / 手账胶带）
        WidgetRenderPipeline.drawTopOverlays(scene)

        return bitmap
    }

    // 风格缩略图缓存：UI 里「经典/萌宠/明信片/插画」四行一共 20+ 张缩略图，
    // 每张都是一次完整的 Canvas 渲染（含解码素材、画纹理）。
    // 没有这层缓存时，每次打开面板所有缩略图一起在 Dispatchers.Default 上从零重画，
    // 表现为「先空白 1~2 秒再逐个补齐」。
    //
    // 缩略图总量只有约 1.2MB（150×80×4B × 约 26 张），远小于下面的容量上限，
    // 因此**永远不会触发 LruCache 的淘汰**。这不是巧合而是刻意设计：
    // 缩略图行位于 LazyColumn 内，滑出视口会销毁 composition、回来时 produceState 重跑，
    // 只要缓存还在就是秒命中，不会再等 1~2 秒。
    // 正因为条目不会被淘汰，才可以把缓存里的 Bitmap **原图**直接交给调用方（无需 copy），
    // 避免滚动时反复分配副本被 GC 回收。
    // 上限只是安全阀：实际条目 ≈ 预设数 × 3 种缩略图尺寸 × 约 48KB，远达不到这里。
    // 真到 32MB 说明 key 设计出了问题（例如把样式哈希混进了 key），届时淘汰反而是保护。
    private val thumbnailCache = object : android.util.LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // ===== 缩略图磁盘二级缓存 =====
    //
    // 内存缓存随进程死亡而清空，而「桌面组件 → 快捷面板」几乎总是冷进程进入
    // （面板关闭后 App 进程很快被系统回收）。实测 30 张缩略图冷渲染合计约 12s
    // CPU，Dispatchers.Default 8 线程并行也要 1.5~2.2s，表现为面板打开后缩略图
    // 空白 1~2 秒才逐个补齐。磁盘缓存（cacheDir/widget_thumbs/，每张 150×80
    // PNG 约 10~30KB、全部合计 <1MB）跨进程存活，冷启动退化为毫秒级 PNG 解码。
    //
    // **改动预设的视觉定义（颜色/纹理/素材）或本文件的绘制逻辑后必须
    // +1 THUMB_DISK_VERSION**：磁盘文件按「版本号 + presetId + 尺寸」寻址，
    // 版本不变就会继续沿用旧图。不把样式 JSON 哈希进 key，是因为 org.json 的
    // key 顺序跨进程不稳定，哈希不可靠。
    private const val THUMB_DISK_VERSION = 2
    private const val THUMB_DISK_DIR = "widget_thumbs"

    private fun thumbDiskFile(context: Context, ramKey: String): File =
        File(File(context.applicationContext.cacheDir, THUMB_DISK_DIR), "v$THUMB_DISK_VERSION$ramKey.png")

    /** 磁盘命中：解码后顺手回填内存缓存，进程内的后续请求直接走内存。 */
    private fun loadThumbnailFromDisk(context: Context, ramKey: String): Bitmap? {
        val file = thumbDiskFile(context, ramKey)
        if (!file.isFile) return null
        val bytes = try {
            file.readBytes()
        } catch (t: Throwable) {
            return null
        }
        val bmp = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (t: Throwable) {
            null
        }
        if (bmp == null) {
            // 解码失败（文件损坏/被截断）：删掉坏文件，下次重新渲染
            file.delete()
            return null
        }
        synchronized(thumbnailCache) { thumbnailCache.put(ramKey, bmp) }
        return bmp
    }

    private fun saveThumbnailToDisk(context: Context, ramKey: String, bitmap: Bitmap) {
        val file = thumbDiskFile(context, ramKey)
        if (file.isFile) return // 同 key 已落盘（并发渲染时先到者写），无需重写
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(tmp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (!tmp.renameTo(file)) tmp.delete()
        } catch (t: Throwable) {
            // 落盘失败只影响下次冷启动的速度，不影响本次显示
            Timber.w(t, "saveThumbnailToDisk failed: $ramKey")
        }
    }

    /**
     * 进程启动后在后台线程调用：把磁盘缓存里的全部缩略图预热进内存，
     * 让「桌面 → 快捷面板」打开的第一帧就带图（否则首帧后还要等一次
     * 毫秒级磁盘解码）。顺带清理历史版本残留的旧图。
     */
    fun prewarmThumbnailCache(context: Context) {
        val dir = File(context.applicationContext.cacheDir, THUMB_DISK_DIR)
        val files = dir.listFiles() ?: return
        val versionPrefix = "v$THUMB_DISK_VERSION"
        for (file in files) {
            val name = file.name
            if (!name.startsWith(versionPrefix) || !name.endsWith(".png")) {
                if (file.isFile) file.delete() // 旧版本/临时残留
                continue
            }
            val ramKey = name.removePrefix(versionPrefix).removeSuffix(".png")
            if (ramKey.isEmpty()) continue
            val hit = synchronized(thumbnailCache) {
                thumbnailCache.get(ramKey)?.takeUnless { it.isRecycled }
            }
            if (hit != null) continue
            loadThumbnailFromDisk(context, ramKey)
        }
    }

    // 仅供单元测试：清空内存缩略图缓存，模拟「进程刚被系统回收后重启」的冷启动状态
    @androidx.annotation.VisibleForTesting
    internal fun clearThumbnailRamCacheForTest() {
        synchronized(thumbnailCache) { thumbnailCache.evictAll() }
    }

    // 仅供单元测试：统计真实 Canvas 渲染次数，用于区分「磁盘命中」与「重渲染」
    @androidx.annotation.VisibleForTesting
    internal var thumbnailRenderCountForTest: Int = 0

    /**
     * 取一张已渲染好的风格缩略图。
     *
     * **直接返回缓存里的原图，不做 copy()**：
     * 缩略图只有 150×80，一条约 48KB，26 张合计约 1.2MB；每次命中都 copy 一份的话，
     * 列表滚动时会不断分配新位图、旧的被 GC 回收，表现为"滑走再滑回来又要等 1~2 秒"。
     * 之所以敢直接给原图，是因为本缓存**关闭了 LruCache 的条目回收**
     * （entryRemoved=false，见 thumbnailCache 定义）：条目只增不减、不会被淘汰，
     * 原图一旦创建就一直在，调用方持有的引用始终有效。
     * 代价是这 1.2MB 常驻，换来滚动零重渲染。
     *
     * **锁只保护缓存的读写，绝不能把 producer（真正的 Canvas 渲染）也圈进去**：
     * 一旦整个方法加 @Synchronized，26 张缩略图会被强制串行逐张渲染，
     * 比并行慢好几倍，表现为"打开面板要等 1~2 秒"。之前踩过这个坑。
     */
    private fun thumbnailCacheKey(
        cacheKey: String,
        producer: () -> Bitmap?
    ): Bitmap? {
        synchronized(thumbnailCache) {
            thumbnailCache.get(cacheKey)?.let { if (!it.isRecycled) return it }
        }
        // 锁外渲染：允许所有缩略图在 Dispatchers.Default 上并行跑满 CPU
        val fresh = producer() ?: return null
        synchronized(thumbnailCache) {
            // 另一个线程可能已经渲染好了同一张：优先用先到的那份，避免重复渲染
            thumbnailCache.get(cacheKey)?.let { if (!it.isRecycled) return it }
            thumbnailCache.put(cacheKey, fresh)
        }
        return fresh
    }


    /**
     * 只查缓存、不触发渲染的同步查询。供 UI 侧作为 produceState 的 initialValue：
     * 命中时该缩略图在首帧就有图（滚动回已渲染过的位置时完全无空白帧），
     * 未命中返回 null，由 produceState 内部异步渲染并写入缓存。
     */
    fun cachedThumbnail(
        widthDp: Int,
        heightDp: Int,
        style: WidgetStyle
    ): Bitmap? {
        val key = "${style.presetId ?: style.shape}_${widthDp}x$heightDp"
        synchronized(thumbnailCache) {
            thumbnailCache.get(key)?.let { if (!it.isRecycled) return it }
        }
        return null
    }

    /**
     * 风格缩略图统一入口：供主界面的风格列表与小组件预览调用。
     *
     * 命中缓存时**同步返回**，因此 UI 侧 `produceState` 的首次赋值也在同一帧完成，
     * 不会出现"先空白一帧再补上"的闪动；未命中才回落到耗时渲染。
     */
    fun renderThumbnail(
        context: Context,
        widthDp: Int,
        heightDp: Int,
        content: String,
        style: WidgetStyle
    ): Bitmap? {
        // key 用 presetId（缺失时退回形状+尺寸）而不是整个 style.toJson()：
        // 前者稳定且短，后者会随用户每次微调颜色而变，导致缓存永远不命中。
        val key = "${style.presetId ?: style.shape}_${widthDp}x$heightDp"
        return thumbnailCacheKey(key) {
            // 内存未命中 → 先查磁盘缓存（跨进程存活，冷启动毫秒级）；
            // 磁盘也没有才真正走 Canvas 渲染，并在渲染完成后落盘供下次冷启动使用。
            val fromDisk = loadThumbnailFromDisk(context, key)
            if (fromDisk != null) {
                fromDisk
            } else {
                try {
                    thumbnailRenderCountForTest++
                    render(context, widthDp, heightDp, content, style)
                } catch (t: Throwable) {
                    null
                }?.also { saveThumbnailToDisk(context, key, it) }
            }
        }
    }
}
