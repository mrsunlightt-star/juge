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
internal object TornPaperRenderer {

    const val TORN_SEED = 42L

    const val TORN_BORDER_ALPHA = 220

    // 随机生成撕纸效果 Path
    fun generateTornPath(width: Float, height: Float, densityScale: Float): Path {
        val path = Path()
        val random = java.util.Random(TORN_SEED)
        val maxJitter = 2.0f * densityScale
        val step = 4.0f * densityScale

        path.moveTo(0f, 0f)
        // 1. 上边缘：(0, 0) -> (width, 0)
        var x = 0f
        while (x < width) {
            x += step
            if (x > width) x = width
            val jitterY = (random.nextFloat() - 0.5f) * maxJitter
            path.lineTo(x, jitterY)
        }

        // 2. 右边缘：(width, 0) -> (width, height)
        var y = 0f
        while (y < height) {
            y += step
            if (y > height) y = height
            val jitterX = (random.nextFloat() - 0.5f) * maxJitter
            path.lineTo(width + jitterX, y)
        }

        // 3. 下边缘：(width, height) -> (0, height)
        while (x > 0f) {
            x -= step
            if (x < 0f) x = 0f
            val jitterY = (random.nextFloat() - 0.5f) * maxJitter
            path.lineTo(x, height + jitterY)
        }

        // 4. 左边缘：(0, height) -> (0, 0)
        while (y > 0f) {
            y -= step
            if (y < 0f) y = 0f
            val jitterX = (random.nextFloat() - 0.5f) * maxJitter
            path.lineTo(jitterX, y)
        }

        path.close()
        return path
    }

}
