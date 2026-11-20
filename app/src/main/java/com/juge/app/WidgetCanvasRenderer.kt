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

    internal data class RenderDimensions(val width: Int, val height: Int, val densityScale: Float, val scale: Float)

    internal fun widgetPixelBudget(screenWidthPx: Int, screenHeightPx: Int): Long {
        val screenPixels = screenWidthPx.coerceAtLeast(1).toLong() * screenHeightPx.coerceAtLeast(1)
        return (screenPixels.toDouble() * 1.5 * 0.95).toLong()
            .coerceIn(1L, (Int.MAX_VALUE.toLong() - 4096L) / 4L)
    }

    internal fun calculateWidgetRenderDimensions(
        widthDp: Int,
        heightDp: Int,
        density: Float,
        screenWidthPx: Int,
        screenHeightPx: Int
    ): RenderDimensions {
        val scale = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        val densityScale = (scale * RENDER_DENSITY_SCALE).coerceAtMost(Float.MAX_VALUE)
        val width = (widthDp * densityScale).toInt().coerceAtLeast(MIN_BITMAP_SIZE)
        val height = (heightDp * densityScale).toInt().coerceAtLeast(MIN_BITMAP_SIZE)
        val maxPixels = widgetPixelBudget(screenWidthPx, screenHeightPx)
        val pixels = width.toLong() * height
        if (pixels <= maxPixels) return RenderDimensions(width, height, densityScale, scale)
        val factor = minOf(
            kotlin.math.sqrt(maxPixels.toDouble() / pixels),
            maxPixels.toDouble() / width,
            maxPixels.toDouble() / height
        )
        return RenderDimensions(
            (width * factor).toInt().coerceAtLeast(1),
            (height * factor).toInt().coerceAtLeast(1),
            (densityScale * factor).toFloat().coerceAtLeast(Float.MIN_VALUE),
            (scale * factor).toFloat().coerceAtLeast(Float.MIN_VALUE)
        )
    }

    fun renderForWidget(
        context: Context,
        widthDp: Int,
        heightDp: Int,
        content: String,
        style: WidgetStyle
    ): Bitmap {
        val metrics = context.resources.displayMetrics
        val dimensions = calculateWidgetRenderDimensions(
            widthDp, heightDp, metrics.density, metrics.widthPixels, metrics.heightPixels
        )
        return renderBitmap(context, widthDp, heightDp, content, style, dimensions)
    }

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
    ): Bitmap = renderBitmap(context, widthDp, heightDp, content, style)

    private fun renderBitmap(
        context: Context,
        widthDp: Int,
        heightDp: Int,
        content: String,
        style: WidgetStyle,
        dimensions: RenderDimensions? = null
    ): Bitmap {
        // 将 dp 尺寸转为像素，增加 RENDER_DENSITY_SCALE 倍分辨率防止桌面模糊
        val scale = dimensions?.scale ?: context.resources.displayMetrics.density
        val densityScale = dimensions?.densityScale ?: (scale * RENDER_DENSITY_SCALE)
        val targetWidth = dimensions?.width ?: (widthDp * densityScale).toInt().coerceAtLeast(MIN_BITMAP_SIZE)
        val targetHeight = dimensions?.height ?: (heightDp * densityScale).toInt().coerceAtLeast(MIN_BITMAP_SIZE)

        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

        // 任何一个绘制阶段失败都必须把这张位图还回去：一张 4×4 组件位图约 5~12MB，
        // 漏掉一次就是一次可观测的内存泄漏；而渲染链跑在 `widget-render` 这个
        // daemon 线程上（见 ReminderWidgetProvider.renderExecutor），
        // 该线程没有 UncaughtExceptionHandler，OOM 会直接带走整个进程。
        try {
            // 1. 形状裁切路径：随 RenderScene 一起算定
            val scene = RenderScene(context, content, style, scale, densityScale, targetWidth, targetHeight, bitmap)
            // 2. 整卡投影与底色
            WidgetRenderPipeline.drawCardBackground(scene)
            // 3. 背景图（自定义路径 / 内置预设插画）
            WidgetRenderPipeline.drawBackgroundImage(scene)
            // 4. 家族装饰（明信片文字区 / 抠图明信片 / 便签 / 书架 / 盒子 / 画框）
            WidgetRenderPipeline.drawFamilyChrome(scene)
            // 5. 纸张纹理与卡片描边
            WidgetRenderPipeline.drawTextureAndBorder(scene)
            // 6. 正文
            CardTextRenderer.drawContent(scene)
            // 7. 压在正文之上的叠加层（CRT / 手账胶带）
            WidgetRenderPipeline.drawTopOverlays(scene)
        } catch (t: Throwable) {
            // 半成品不能留在桌面：先回收再抛给调用方。
            // 调用方 ReminderWidgetProvider 的 onUpdate / onAppWidgetOptionsChanged /
            // dispatchAsync / updateAppWidgetAsync / triggerUpdateAllWidgets 现在统一
            // 按 Throwable 兜底（含 OOM 这类 Error），不会再落到 daemon 线程的默认处理器上；
            // 渲染失败时桌面维持上一帧，不会显示半张卡。
            runCatching { bitmap.recycle() }
            throw t
        }

        return bitmap
    }

    // 风格缩略图缓存：UI 里「经典/萌宠/明信片/插画」四行一共 20+ 张缩略图，
    // 每张都是一次完整的 Canvas 渲染（含解码素材、画纹理）。
    // 没有这层缓存时，每次打开面板所有缩略图一起在 Dispatchers.Default 上从零重画，
    // 表现为「先空白 1~2 秒再逐个补齐」。
    //
    // 缩略图统一外框 180×96，实际画布按 4×2 / 4×4 比例约 180×79 或 96×96，
    // 26 张合计约 2MB，远小于下面的容量上限，
    // 因此**实际使用中不会触发 LruCache 的淘汰**。这不是巧合而是刻意设计：
    // 缩略图行位于 LazyColumn 内，滑出视口会销毁 composition、回来时 produceState 重跑，
    // 只要缓存还在就是秒命中，不会再等 1~2 秒。
    // 之所以可以把缓存里的 Bitmap **原图**直接交给调用方（无需 copy），靠的是另一件事：
    // LruCache 淘汰条目时**只把它从 map 里摘掉，不会 recycle 位图**（本类没有覆写
    // `entryRemoved`，默认实现就是空操作）。于是"缓存里还有"与"位图还活着"是两件事——
    // 调用方手里的引用不会因为淘汰而变成已回收对象。
    // 早先这里写着「关闭了 LruCache 的条目回收（entryRemoved=false）」，那是错的：
    // 根本没有 entryRemoved=false 这种开关，LruCache 也照样会按 sizeOf 淘汰。
    // 结论没错，论证错了——现在按真实行为写。
    // 上限只是安全阀：实际条目 ≈ 预设数 × 3 种缩略图尺寸 × 约 50~115KB，远达不到这里。
    // 真到 32MB 说明 key 设计出了问题（例如把样式哈希混进了 key），届时淘汰反而是保护。
    private val thumbnailCache = object : android.util.LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // ===== 缩略图磁盘二级缓存 =====
    //
    // 内存缓存随进程死亡而清空，而「桌面组件 → 主界面」几乎总是冷进程进入
    // （App 退到后台后进程很快被系统回收）。实测 30 张缩略图冷渲染合计约 12s
    // CPU，Dispatchers.Default 8 线程并行也要 1.5~2.2s，表现为面板打开后缩略图
    // 空白 1~2 秒才逐个补齐。磁盘缓存（cacheDir/widget_thumbs/，每张外框
    // 180×96 以内、PNG 约 15~80KB，全部合计约 2MB）跨进程存活，冷启动退化为毫秒级 PNG 解码。
    //
    // **改动预设的视觉定义（颜色/纹理/素材）或本文件的绘制逻辑后必须
    // +1 THUMB_DISK_VERSION**：磁盘文件按「版本号 + presetId + 尺寸」寻址，
    // 版本不变就会继续沿用旧图。不把样式 JSON 哈希进 key，是因为 org.json 的
    // key 顺序跨进程不稳定，哈希不可靠。
    // v20：极速 / 极速机车裁掉透明外圈并放大正文区，清掉旧缩略图缓存
    private const val THUMB_DISK_VERSION = 20
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
     * 让「桌面 → 主界面」打开的第一帧就带图（否则首帧后还要等一次
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

    /**
     * 系统内存吃紧时主动让位。
     *
     * 之前全仓没有 `onTrimMemory` / `onLowMemory` 的实现：两块 LruCache
     * （本类的 32MB 缩略图 + WidgetRenderKernel 的 24MB 预设插画）只会等系统
     * 直接杀进程，而杀进程前那一下正是桌面重画组件、最需要内存的时刻。
     *
     * 清空是安全的：LruCache 淘汰条目时不会 recycle 位图（本类没有覆写
     * `entryRemoved`），已经交出引用的调用方手里那张仍然完好；
     * 磁盘二级缓存还在，下次进界面按毫秒级 PNG 解码补回来。
     *
     * @param level `ComponentCallbacks2` 的 trim 级别
     */
    fun trimMemory(level: Int) {
        when {
            // 后台进程 / 界面不可见：内存随时可能被收回，先放手
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                synchronized(thumbnailCache) { thumbnailCache.evictAll() }
                com.juge.app.render.WidgetRenderKernel.presetImageCache.evictAll()
                Timber.i("WidgetCanvasRenderer: 系统内存压力 %d，已清空缩略图与预设插画缓存", level)
            }
            // 前台但内存偏低（TRIM_MEMORY_RUNNING_LOW / CRITICAL）：只丢不常用的那一半，
            // 避免用户正滑着列表时缓存被整体清空导致重新渲染
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                synchronized(thumbnailCache) { thumbnailCache.trimToSize(thumbnailCache.maxSize() / 2) }
                com.juge.app.render.WidgetRenderKernel.presetImageCache.trimToSize(
                    com.juge.app.render.WidgetRenderKernel.presetImageCache.maxSize() / 2
                )
                Timber.i("WidgetCanvasRenderer: 系统内存偏低 %d，缓存已收缩到一半", level)
            }
        }
    }

    // 仅供单元测试：统计真实 Canvas 渲染次数，用于区分「磁盘命中」与「重渲染」
    @androidx.annotation.VisibleForTesting
    internal var thumbnailRenderCountForTest: Int = 0

    /**
     * 取一张已渲染好的风格缩略图。
     *
     * **直接返回缓存里的原图，不做 copy()**：
     * 缩略图外框 180×96，单张约 50~115KB，26 张合计约 2MB；每次命中都 copy 一份的话，
     * 列表滚动时会不断分配新位图、旧的被 GC 回收，表现为"滑走再滑回来又要等 1~2 秒"。
     * 之所以敢直接给原图：LruCache 淘汰条目时不会 recycle 位图（本类没有覆写
     * `entryRemoved`），被淘汰的位图仍然完好，调用方手里的引用始终可用；
     * 而稳态下 26 张 ≈ 2MB，离 32MB 上限很远，几乎不会被淘汰。
     * 代价是这约 2MB 常驻，换来滚动零重渲染。
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

    // ===== 预览位图磁盘缓存 =====
    //
    // 与缩略图同源的问题，只是对象换成了主界面上那张**完整尺寸**的组件预览：
    // 实测冷启动时界面框架约 0.8s 出现，而预览图要到约 1.6s 才画出来，
    // 中间这 0.8 秒预览区是空白的，用户感知就是"没真正进到界面里"。
    //
    // 磁盘缓存跨进程存活，冷启动时直接读出上次的成品图，首帧即有内容；
    // 后台仍会按当前样式重新渲染并覆盖，因此不会把过期画面留在屏幕上。
    //
    // key 由「配置 + 尺寸 + 样式指纹」组成（刻意不含内容，原因见 previewKey）：
    // 样式或尺寸一变就自然 miss 并重渲染。
    //
    // ⚠️ 但**样式指纹只覆盖样式字段**：改绘制代码、换素材本身都不会改变它，旧图会被一直命中，
    // 界面看起来就是"改了没生效"（今天就踩到了：WATER 衔接改了接缝，App 内预览仍显示旧图）。
    // 所以文件名前缀必须带版本号，**改动绘制逻辑 / 预设视觉定义 / 素材后 +1** ——
    // 与缩略图缓存那条 THUMB_DISK_VERSION 是同一个规矩。
    // v20：极速 / 极速机车裁掉透明外圈并放大正文区，清掉旧预览缓存
    private const val PREVIEW_DISK_VERSION = 20
    private const val PREVIEW_DISK_DIR = "widget_previews"
    private const val PREVIEW_DISK_MAX_FILES = 16

    // key 刻意**不含内容**：内容来自数据库、首帧还是空的，把内容算进 key 会让冷启动必然
    // miss（上次缓存的是"有文字"的图，这次首帧算出的 key 是"空文字"），缓存等于白做。
    // 只认「配置 + 尺寸 + 样式」后，首帧就能命中上次那张**带文字**的成品图，
    // 随后按最新内容重渲染并覆盖同一 key，画面自然过渡到最新状态。
    private fun previewKey(configId: Long, widthDp: Int, heightDp: Int, style: WidgetStyle): String {
        val styleHash = style.cacheFingerprint().hashCode().toUInt().toString(16)
        return "v$PREVIEW_DISK_VERSION-c${configId}_${widthDp}x${heightDp}_$styleHash"
    }

    private fun previewDiskFile(context: Context, key: String): File =
        File(File(context.applicationContext.cacheDir, PREVIEW_DISK_DIR), "$key.png")

    /**
     * 只查磁盘缓存、不触发渲染：命中时返回已解码的预览位图，未命中返回 null。
     *
     * 注意这是**同步**解码（磁盘读 + PNG 解码），不要在组合线程上直接调用。
     * 当前调用方 `WidgetPreviewPager` 把整个调用包在 `produceState` 的
     * `withContext(Dispatchers.IO)` 里，拿到结果后再在 `Dispatchers.Default` 上重渲染，
     * 翻页不会再卡主线程。
     */
    fun cachedPreview(
        context: Context,
        configId: Long,
        widthDp: Int,
        heightDp: Int,
        style: WidgetStyle
    ): Bitmap? {
        val file = previewDiskFile(context, previewKey(configId, widthDp, heightDp, style))
        if (!file.isFile) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)?.also {
                // 触碰修改时间，供 trimPreviewCache 按 LRU 淘汰
                file.setLastModified(System.currentTimeMillis())
            }
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * 渲染一张预览位图并落盘。绘制逻辑与 [render] 完全一致，只多了一步缓存写入；
     * 落盘失败只影响下次冷启动的速度，不影响本次显示。
     */
    fun renderPreview(
        context: Context,
        configId: Long,
        widthDp: Int,
        heightDp: Int,
        content: String,
        style: WidgetStyle
    ): Bitmap {
        val bitmap = render(context, widthDp, heightDp, content, style)
        try {
            val file = previewDiskFile(context, previewKey(configId, widthDp, heightDp, style))
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(tmp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (!tmp.renameTo(file)) tmp.delete()
            trimPreviewCache(context)
        } catch (t: Throwable) {
            Timber.w(t, "savePreviewToDisk failed")
        }
        return bitmap
    }

    /**
     * 预览缓存只保留最近使用的 [PREVIEW_DISK_MAX_FILES] 张，避免长期堆积；
     * 顺带清理残存的 .png.tmp 中间文件——写盘进程被杀（后台限制/OOM kill）时
     * rename 尚未发生，tmp 文件会留下来，而这个目录没有 prewarm 那样统一的清理点。
     */
    private fun trimPreviewCache(context: Context) {
        val dir = File(context.applicationContext.cacheDir, PREVIEW_DISK_DIR)
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".png.tmp") &&
                file.lastModified() < now - STALE_PREVIEW_TMP_MS
            ) {
                file.delete()
            }
        }
        val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".png") } ?: return
        if (files.size <= PREVIEW_DISK_MAX_FILES) return
        files.sortedByDescending { it.lastModified() }
            .drop(PREVIEW_DISK_MAX_FILES)
            .forEach { it.delete() }
    }

    /** tmp 文件 30 分钟后视为写盘中断的残留（正常 compress + rename 远用不了这么久） */
    private const val STALE_PREVIEW_TMP_MS = 30 * 60 * 1000L
}
