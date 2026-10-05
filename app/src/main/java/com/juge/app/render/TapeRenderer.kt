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
internal object TapeRenderer {

    const val TAPE_LINE_ALPHA = 45

    // 绘制手账胶带折线撕裂边缘 Path
    fun drawTape(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        angleDegrees: Float,
        color: Int,
        densityScale: Float
    ) {
        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.rotate(angleDegrees)

        val halfW = width / 2f
        val halfH = height / 2f

        val path = Path()
        // 从左上角开始绘制。左侧是撕裂毛边。
        path.moveTo(-halfW, -halfH)
        
        // 左毛边：y从 -halfH 到 halfH。分 6 个波折段
        val segments = 6
        val step = height / segments
        for (i in 1..segments) {
            val currY = -halfH + i * step
            val shiftX = if (i % 2 == 1) 3f * densityScale else 0f
            path.lineTo(-halfW + shiftX, currY)
        }

        // 下平边
        path.lineTo(halfW, halfH)

        // 右毛边：y从 halfH 回到 -halfH。分 6 个波折段
        for (i in segments downTo 0) {
            val currY = -halfH + i * step
            val shiftX = if (i % 2 == 1) -3f * densityScale else 0f
            path.lineTo(halfW + shiftX, currY)
        }

        // 上平边已通过 close 闭合
        path.close()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }
        canvas.drawPath(path, paint)

        // 绘制三条微弱的半透明白色光泽条纹纹理，增加纸胶带的真实感
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.color = Color.WHITE
            alpha = TAPE_LINE_ALPHA
            strokeWidth = 1.5f * densityScale
        }
        canvas.drawLine(-halfW + 12f * densityScale, -halfH + 2f * densityScale, -halfW + 22f * densityScale, halfH - 2f * densityScale, linePaint)
        canvas.drawLine(-halfW + 22f * densityScale, -halfH + 2f * densityScale, -halfW + 32f * densityScale, halfH - 2f * densityScale, linePaint)
        canvas.drawLine(halfW - 32f * densityScale, -halfH + 2f * densityScale, halfW - 22f * densityScale, halfH - 2f * densityScale, linePaint)

        canvas.restore()
    }

}
