package com.juge.app.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.juge.app.data.ImageScaleMode
import com.juge.app.data.WidgetStyle
import com.juge.app.render.WidgetRenderKernel.centerFitRect
import com.juge.app.render.WidgetRenderKernel.detectLightBorder
import com.juge.app.render.WidgetRenderKernel.fastBlur

/**
 * 自 WidgetCanvasRenderer 拆分而来。成员由调用点 import，调用写法不变。
 */
internal object BgImageRenderer {

    /** 按 [WidgetStyle.bgImageScaleMode] 把背景图铺进 [rectF]（含模糊、留白裁切、蒙版） */
    fun drawBgImage(canvas: Canvas, bitmap: Bitmap, rectF: RectF, style: WidgetStyle) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.alpha = (style.backgroundOpacity * 255).toInt().coerceIn(0, 255)

        val processedBitmap = if (style.bgBlurRadius > 0f) {
            val radius = style.bgBlurRadius.toInt().coerceIn(1, 25)
            // 先把工作图压到安全边长再模糊：fastBlur 需要多个与像素数等大的数组，
            // 高分辨率背景图直接模糊会造成内存峰值过高甚至 OOM；
            // 模糊本身是低频信息，缩小后模糊再绘制到卡片上视觉差异可忽略
            val maxSide = 800
            val blurScale = minOf(1f, maxSide.toFloat() / maxOf(bitmap.width, bitmap.height))
            if (blurScale < 1f) {
                val sw = (bitmap.width * blurScale).toInt().coerceAtLeast(1)
                val sh = (bitmap.height * blurScale).toInt().coerceAtLeast(1)
                val small = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
                val blurred = fastBlur(small, radius)
                small.recycle()
                blurred
            } else {
                fastBlur(bitmap, radius)
            }
        } else {
            bitmap
        }

        when (style.bgImageScaleMode) {
            ImageScaleMode.STRETCH -> {
                canvas.drawBitmap(processedBitmap, null, rectF, paint)
            }
            ImageScaleMode.CENTER_CROP -> {
                // 先检测并裁掉素材四周自带的近白留白相框（正方形明信片插画常见），
                // 再对剩余内容做铺满裁切，避免宽横幅两侧露出白边
                val content = detectLightBorder(processedBitmap)
                val cw = content.right - content.left
                val ch = content.bottom - content.top
                val targetRatio = rectF.width() / rectF.height()
                val srcRatio = cw.toFloat() / ch.toFloat()
                val srcRect = android.graphics.Rect()

                if (srcRatio > targetRatio) {
                    val srcWidth = (ch * targetRatio).toInt()
                    val left = content.left + (cw - srcWidth) / 2
                    srcRect.set(left, content.top, left + srcWidth, content.bottom)
                } else {
                    val srcHeight = (cw / targetRatio).toInt()
                    val top = content.top + (ch - srcHeight) / 2
                    srcRect.set(content.left, top, content.right, top + srcHeight)
                }
                canvas.drawBitmap(processedBitmap, srcRect, rectF, paint)
            }
            ImageScaleMode.CENTER_FIT -> {
                // 等比完整显示整幅图，超出的空白保留透明，避免主体被裁切或压扁
                val srcRect = android.graphics.Rect(0, 0, processedBitmap.width, processedBitmap.height)
                val dstRect = centerFitRect(
                    rectF,
                    processedBitmap.width.toFloat() / processedBitmap.height.toFloat()
                )
                canvas.drawBitmap(processedBitmap, srcRect, dstRect, paint)
            }
            ImageScaleMode.CENTER_CROP_TOP -> {
                // 等比铺满(覆盖)目标区域：先放大到两方向都 >= 目标，超出方向裁剪。
                // 与 CENTER_CROP 不同：垂直方向多余的高度从【底部】裁掉、锚点在顶部，
                // 让顶部主体(如趴在卡片上方的猫)完整保留，而 CENTER_FIT 会因组件过宽把整图缩得左右留白。
                val content = detectLightBorder(processedBitmap)
                val cw = content.right - content.left
                val ch = content.bottom - content.top
                val targetRatio = rectF.width() / rectF.height()
                val srcRatio = cw.toFloat() / ch.toFloat()
                val srcRect = android.graphics.Rect()

                if (srcRatio > targetRatio) {
                    // 源更宽：以宽为准铺满、上下选裁，但要保住顶部主体 → 从顶部取满高，不居中
                    val srcWidth = (ch * targetRatio).toInt()
                    val left = content.left + (cw - srcWidth) / 2
                    srcRect.set(left, content.top, left + srcWidth, content.bottom)
                } else {
                    // 源更高：以高为准铺满、左右居中裁 → 顶部锚定，底部超出的往下裁掉
                    val srcHeight = (cw / targetRatio).toInt()
                    srcRect.set(content.left, content.top, content.right, content.top + srcHeight)
                }
                canvas.drawBitmap(processedBitmap, srcRect, rectF, paint)
            }
            ImageScaleMode.TILE -> {
                val shader = BitmapShader(processedBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
                tilePaint.shader = shader
                tilePaint.alpha = (style.backgroundOpacity * 255).toInt().coerceIn(0, 255)
                canvas.drawRect(rectF, tilePaint)
            }
        }

        if (processedBitmap != bitmap) {
            processedBitmap.recycle()
        }

        if (style.bgScrimAlpha > 0f) {
            val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                alpha = (style.bgScrimAlpha * 255).toInt().coerceIn(0, 255)
            }
            canvas.drawRect(rectF, scrimPaint)
        }
    }
}
