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
internal object SplitCardRenderer {

    const val SPLIT_CARD_RATIO = 0.48f

    const val SPLIT_CARD_HORIZONTAL_RATIO = 0.333f

    // 图文明信片：背景插画所在的区域。下半（左右分割时为右半）留给代码绘制的文字显示区，
    // 底色铺图与图片绘制共用同一份矩形，避免两处比例各写一遍后失配。
    fun splitImageRect(shape: WidgetShape, outerRect: RectF): RectF {
        return if (shape == WidgetShape.SPLIT_CARD_HORIZONTAL) {
            RectF(
                outerRect.left,
                outerRect.top,
                outerRect.left + outerRect.width() * SPLIT_CARD_HORIZONTAL_RATIO,
                outerRect.bottom
            )
        } else {
            RectF(
                outerRect.left,
                outerRect.top,
                outerRect.right,
                outerRect.top + outerRect.height() * SPLIT_CARD_RATIO
            )
        }
    }

}
