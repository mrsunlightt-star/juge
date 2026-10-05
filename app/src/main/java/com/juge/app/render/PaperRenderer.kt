package com.juge.app.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.juge.app.data.ImageScaleMode
import com.juge.app.data.WidgetFont
import com.juge.app.data.WidgetShape
import com.juge.app.data.WidgetStyle
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.Random
import timber.log.Timber
import com.juge.app.WidgetCanvasRenderer.render

/**
 * 自 WidgetCanvasRenderer 拆分而来。成员由调用点 import，调用写法不变。
 */
internal object PaperRenderer {

    const val TEXTURE_SEED = 2026L

    const val STICKER_PAPER_GRAIN_TILE = 128 // 纸纹贴片边长（px）

    /**
     * 贴纸夜景：文本框的纸片轮廓。
     *
     * 圆角滑条（cornerRadiusDp）作用于文本框四角：>0 时四角走圆弧，半径 = 剪纸口的收角幅度 + 滑条值，
     * 这样滑条从 0 到 30 全程都在生效；=0 时保持直角，只留四角那一刀斜切。
     */
    fun paperCutBoxPath(rect: RectF, snip: Float, cornerRadius: Float): Path {
        val minSide = minOf(rect.width(), rect.height())
        return Path().apply {
            if (cornerRadius > 0f) {
                val r = (snip + cornerRadius).coerceAtMost(minSide * 0.5f)
                addRoundRect(rect, r, r, Path.Direction.CW)
            } else {
                val s = snip.coerceAtMost(minSide * 0.25f)
                moveTo(rect.left + s, rect.top)
                lineTo(rect.right - s, rect.top)
                lineTo(rect.right, rect.top + s)
                lineTo(rect.right, rect.bottom - s)
                lineTo(rect.right - s, rect.bottom)
                lineTo(rect.left + s, rect.bottom)
                lineTo(rect.left, rect.bottom - s)
                lineTo(rect.left, rect.top + s)
                close()
            }
        }
    }

    // 纸纹贴片只生成一次，之后复用
    var paperGrainCache: Bitmap? = null

    /**
     * 贴纸夜景：纸纹贴片。
     * 细密噪点 + 短纤维，按 REPEAT 平铺，纸面才不是一块干净的单色。
     */
    fun paperGrainBitmap(): Bitmap {
        paperGrainCache?.let { if (!it.isRecycled) return it }
        val size = STICKER_PAPER_GRAIN_TILE
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val rnd = Random(20261001L)
        val pixels = IntArray(size * size)
        for (i in pixels.indices) {
            val roll = rnd.nextInt(100)
            pixels[i] = when {
                roll < 30 -> (10 + rnd.nextInt(28)) shl 24 // 暗点
                roll < 46 -> ((8 + rnd.nextInt(20)) shl 24) or 0xFFFFFF // 亮点
                else -> 0
            }
        }
        bmp.setPixels(pixels, 0, size, 0, 0, size, size)

        // 短纤维：纸浆的走向，比纯噪点更像纸
        val fiberCanvas = Canvas(bmp)
        val fiber = Paint().apply {
            strokeWidth = 1f
            strokeCap = Paint.Cap.ROUND
        }
        repeat(46) {
            val light = rnd.nextBoolean()
            fiber.color = ((6 + rnd.nextInt(16)) shl 24) or if (light) 0xFFFFFF else 0x000000
            val x = rnd.nextInt(size).toFloat()
            val y = rnd.nextInt(size).toFloat()
            val len = 5f + rnd.nextInt(16)
            val drift = (rnd.nextFloat() - 0.5f) * 0.5f * len
            fiberCanvas.drawLine(x, y, x + len, y + drift, fiber)
        }
        paperGrainCache = bmp
        return bmp
    }

    /** 贴纸夜景：铺在文本框上的纸纹画笔。贴片按原尺寸平铺，颗粒才够细，不会变成噪点。 */
    fun paperGrainPaint(alpha: Int): Paint {
        val shader = BitmapShader(paperGrainBitmap(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            this.alpha = alpha
        }
    }

    // 绘制拟物纸张颗粒/纤维纹理
    fun drawPaperTexture(canvas: Canvas, rectF: RectF, textureType: String, densityScale: Float) {
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
