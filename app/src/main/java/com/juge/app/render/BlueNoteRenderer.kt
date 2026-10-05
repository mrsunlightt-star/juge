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
internal object BlueNoteRenderer {

    // 蓝色便签贴纸：在圆角蓝底上绘制顶部 NOTE 行、右上信息钮、底部米色签条与手写签名
    fun drawBlueNoteChrome(
        canvas: Canvas,
        width: Float,
        height: Float,
        outerRect: RectF,
        outerPath: Path,
        densityScale: Float,
        style: WidgetStyle,
        context: Context
    ) {
        canvas.save()
        canvas.clipPath(outerPath)

        val svgW = 578f
        val svgH = 363f
        val headerH = 76f
        val footerH = 76f
        val sx = width / svgW
        val sy = height / svgH
        val scaleText = (sx + sy) * 0.5f

        // 顶部 NOTE：左上 NOTE 大写，右上圆形信息钮（?）
        val noteSize = 22f * densityScale * (scaleText / (densityScale * 0.9f)).coerceIn(0.85f, 1.25f)
        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = noteSize.coerceIn(12f * densityScale, 26f * densityScale)
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val notePadX = 18f * sx
        val notePadY = 18f * sy + notePaint.textSize
        canvas.drawText("NOTE", outerRect.left + notePadX, outerRect.top + notePadY, notePaint)

        // 右上信息钮：白底圆 + 黄色 ? 造型，复刻 SVG 中 50×50 的圆形与外围刻度
        val badgeCx = outerRect.right - 28f * sx
        val badgeCy = outerRect.top + 30f * sy
        val badgeR = 18f * sx.coerceAtLeast(sy) * 0.9f
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.style = Paint.Style.FILL
        }
        canvas.drawCircle(badgeCx, badgeCy, badgeR, ringPaint)
        // 外圈细环
        val ringStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.style = Paint.Style.STROKE
            strokeWidth = 1.2f * densityScale
            alpha = 210
        }
        canvas.drawCircle(badgeCx, badgeCy, badgeR + 2f * densityScale * 0.4f, ringStroke)

        val qPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8D000")
            textSize = badgeR * 1.45f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val qFm = qPaint.fontMetrics
        val qY = badgeCy - (qFm.ascent + qFm.descent) / 2f
        canvas.drawText("?", badgeCx, qY, qPaint)

        // 外围小刻度（8 个小短线，复刻 SVG 的发散点）
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8D000")
            strokeWidth = 1.4f * densityScale
            strokeCap = Paint.Cap.ROUND
        }
        val tickR = badgeR + 7f * sx
        for (i in 0 until 8) {
            val a = Math.toRadians((i * 45.0) - 22.5)
            val x0 = (badgeCx + Math.cos(a) * (tickR - 3f * sx)).toFloat()
            val y0 = (badgeCy + Math.sin(a) * (tickR - 3f * sy)).toFloat()
            val x1 = (badgeCx + Math.cos(a) * (tickR + 2f * sx)).toFloat()
            val y1 = (badgeCy + Math.sin(a) * (tickR + 2f * sy)).toFloat()
            canvas.drawLine(x0, y0, x1, y1, tickPaint)
        }

        // 底部米色签条：高度 76/363，颜色 #F7F4F0，内写 i + 句阁、手写签名（沿用 style.authorSignature）
        val footerTop = outerRect.bottom - footerH * sy
        val footerHpx = footerH * sy
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F7F4F0")
            this.style = Paint.Style.FILL
        }
        // 只在底部条区域填充，保持顶部仍为 #43A8F0
        canvas.drawRect(RectF(outerRect.left, footerTop, outerRect.right, outerRect.bottom), footerPaint)

        // 底部条内：底部基线的分隔细线（微灰）
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E8E2DC")
            strokeWidth = 1f * densityScale
        }
        canvas.drawLine(outerRect.left, footerTop, outerRect.right, footerTop, linePaint)

        // 底部手写英文（对齐参考图：居中 "focus yourself"，毛笔楷书手写体）
        val footerText = "focus yourself"
        val sigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1A1A")
            textSize = (20f * sy).coerceIn(14f * densityScale, 26f * densityScale)
            textAlign = Paint.Align.CENTER
            typeface = try {
                val tf = android.graphics.Typeface.createFromAsset(context.assets, "fonts/MaShanZheng-Regular.ttf")
                android.graphics.Typeface.create(tf, android.graphics.Typeface.ITALIC)
            } catch (_: Exception) {
                android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.ITALIC)
            }
        }
        val centerX = (outerRect.left + outerRect.right) / 2f
        val fm = sigPaint.fontMetrics
        val centerY = footerTop + footerHpx / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(footerText, centerX, centerY, sigPaint)

        canvas.restore()
    }

}
