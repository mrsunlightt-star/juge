package com.juge.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.Path
import android.graphics.RectF
import java.util.Locale
import java.util.Random
import timber.log.Timber
import com.juge.app.WidgetCanvasRenderer.render

/**
 * 自 WidgetCanvasRenderer 拆分而来。成员由调用点 import，调用写法不变。
 */
internal object PaperRenderer {

    const val TEXTURE_SEED = 2026L

    // ===== 纹理录制缓存 =====
    //
    // 纹理的随机种子是固定常量（TEXTURE_SEED = 2026），意味着**同一组
    // (尺寸, 纹理类型, densityScale) 画出来的东西逐像素相同**。但此前每次绘制都从零
    // 重跑 100~1000 条纤维（每条 new 一个 Path）+ 100~800 个噪点圆；GRAIN 更是
    // 500~3000 个圆。而桌面组件缩放时 onAppWidgetOptionsChanged 会连续触发多次重画，
    // 正好落在最痛的地方。
    //
    // 这里用 android.graphics.Picture 把这一串绘制指令录下来复用：
    // **不用 Bitmap 预栅格化，是因为 Bitmap 在非整数坐标贴图会引入重采样，
    // 逐像素结果会和原来不同**（本项目的渲染指纹基线就是用来守这一点的）。
    // Picture 重放的是同一串 canvas 调用，坐标变换与原先完全一致，
    // 省掉的正好是每次重建上千个 Path / 上千次 drawCircle 的开销。
    //
    // 缓存按 (纹理类型, 宽, 高, densityScale) 寻址，实际条目量是个位数
    // （2 种纹理 × 4 种组件尺寸），上限只是防御性安全阀。
    private const val TEXTURE_CACHE_MAX_ENTRIES = 8
    private val textureCache = LinkedHashMap<String, Picture>(16, 0.75f, true)
    private val textureCacheLock = Any()

    private fun cachedTexture(
        textureType: String,
        width: Int,
        height: Int,
        densityScale: Float,
        paintInto: (Canvas) -> Unit
    ): Picture? {
        if (width <= 0 || height <= 0) return null
        val key = "$textureType|$width|$height|${String.format(Locale.US, "%.2f", densityScale)}"
        synchronized(textureCacheLock) {
            textureCache[key]?.let { return it }
        }
        val picture = try {
            val picture = Picture()
            // 注意：Canvas 没有接收 Picture 的构造函数，录制用的 Canvas
            // 必须由 beginRecording 返回，否则会走到 Canvas(Bitmap) 那个重载去。
            paintInto(picture.beginRecording(width, height))
            picture.endRecording()
            picture
        } catch (t: Throwable) {
            // 纹理只是装饰：录不下来就跳过，不能让一层纸纹把整帧渲染拖垮
            Timber.w(t, "PaperRenderer: 纹理录制失败，跳过本帧纹理")
            return null
        }
        synchronized(textureCacheLock) {
            if (textureCache.size >= TEXTURE_CACHE_MAX_ENTRIES) textureCache.clear()
            textureCache[key] = picture
        }
        return picture
    }

    /** 仅供单元测试：清空纹理缓存，验证「同一组参数只录制一次」 */
    @androidx.annotation.VisibleForTesting
    internal fun clearTextureCacheForTest() {
        synchronized(textureCacheLock) { textureCache.clear() }
    }

    /** 仅供单元测试：当前纹理缓存条目数 */
    @androidx.annotation.VisibleForTesting
    internal fun textureCacheSizeForTest(): Int = synchronized(textureCacheLock) { textureCache.size }

    // 绘制拟物纸张颗粒/纤维纹理
    fun drawPaperTexture(canvas: Canvas, rectF: RectF, textureType: String, densityScale: Float) {
        if (textureType != "PAPER" && textureType != "GRAIN") return

        // 录制时把原点归一到 (0,0)、按 rectF 的尺寸录制；绘制时再平移到 rectF 的位置。
        // 平移量与原先直接用 rectF.left/top 作起点完全等价，包含小数位的情形。
        val width = Math.ceil(rectF.width().toDouble()).toInt()
        val height = Math.ceil(rectF.height().toDouble()).toInt()
        val picture = cachedTexture(textureType, width, height, densityScale) { textureCanvas ->
            drawPaperTextureInto(
                textureCanvas,
                RectF(0f, 0f, rectF.width(), rectF.height()),
                textureType,
                densityScale
            )
        }
        if (picture != null) {
            val save = canvas.save()
            canvas.translate(rectF.left, rectF.top)
            canvas.drawPicture(picture)
            canvas.restoreToCount(save)
        }
    }

    private fun drawPaperTextureInto(canvas: Canvas, rectF: RectF, textureType: String, densityScale: Float) {
        val random = java.util.Random(TEXTURE_SEED)
        val width = rectF.width()
        val height = rectF.height()
        val left = rectF.left
        val top = rectF.top

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (textureType == "PAPER") {
            // 短细纤维线条与少量点噪点
            val fiberCount = (width * height / 300f).toInt().coerceIn(100, 1000)
            paint.style = Paint.Style.STROKE
            for (i in 0 until fiberCount) {
                val startX = left + random.nextFloat() * width
                val startY = top + random.nextFloat() * height
                val length = (4f + random.nextFloat() * 11f) * densityScale
                val angle = random.nextFloat() * Math.PI * 2
                val endX = startX + (Math.cos(angle) * length).toFloat()
                val endY = startY + (Math.sin(angle) * length).toFloat()

                val isDark = random.nextBoolean()
                paint.color = if (isDark) Color.BLACK else Color.WHITE
                paint.alpha = if (isDark) random.nextInt(8) + 2 else random.nextInt(12) + 4
                paint.strokeWidth = (0.5f + random.nextFloat() * 0.8f) * densityScale

                val ctrlX = (startX + endX) / 2f + (random.nextFloat() - 0.5f) * 3f * densityScale
                val ctrlY = (startY + endY) / 2f + (random.nextFloat() - 0.5f) * 3f * densityScale
                val path = Path().apply {
                    moveTo(startX, startY)
                    quadTo(ctrlX, ctrlY, endX, endY)
                }
                canvas.drawPath(path, paint)
            }

            paint.style = Paint.Style.FILL
            val noiseCount = (width * height / 200f).toInt().coerceIn(100, 800)
            for (i in 0 until noiseCount) {
                val px = left + random.nextFloat() * width
                val py = top + random.nextFloat() * height
                val r = (0.5f + random.nextFloat() * 1.0f) * densityScale

                val isDark = random.nextBoolean()
                paint.color = if (isDark) Color.BLACK else Color.WHITE
                paint.alpha = if (isDark) random.nextInt(10) + 2 else random.nextInt(15) + 5
                canvas.drawCircle(px, py, r, paint)
            }
        } else if (textureType == "GRAIN") {
            // 高密度的细微噪点
            paint.style = Paint.Style.FILL
            val noiseCount = (width * height / 50f).toInt().coerceIn(500, 3000)
            for (i in 0 until noiseCount) {
                val px = left + random.nextFloat() * width
                val py = top + random.nextFloat() * height
                val r = (0.3f + random.nextFloat() * 0.7f) * densityScale

                val isDark = random.nextBoolean()
                paint.color = if (isDark) Color.BLACK else Color.WHITE
                paint.alpha = if (isDark) random.nextInt(12) + 3 else random.nextInt(18) + 6
                canvas.drawCircle(px, py, r, paint)
            }
        }
    }

}
