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
internal object WidgetRenderKernel {

    const val MIN_BITMAP_SIZE = 300

    // 检测近白相框阈值：RGB 均大于该值视为“留白像素”
    const val LIGHT_BORDER_THRESHOLD = 228

    // 检测位图四周是否带有统一近白留白相框，若是则返回裁剪到内容区(去掉相框)的矩形。
    // 明信片正方形插画常自带白色描边，直接用会在大横幅两侧露出白边，这里沿中轴线向内收缩定位。
    // 通过一条水平中线探测左右边界、一条垂直中线探测上下边界，避免需要整图逐像素扫描。
    fun detectLightBorder(bmp: Bitmap): android.graphics.Rect {
        val w = bmp.width
        val h = bmp.height
        val full = android.graphics.Rect(0, 0, w, h)
        if (w < 2 || h < 2) return full

        fun isLight(x: Int, y: Int): Boolean {
            val c = bmp.getPixel(x, y)
            return (c ushr 24) == 0xff &&
                android.graphics.Color.red(c) > LIGHT_BORDER_THRESHOLD &&
                android.graphics.Color.green(c) > LIGHT_BORDER_THRESHOLD &&
                android.graphics.Color.blue(c) > LIGHT_BORDER_THRESHOLD
        }
        if (!isLight(0, h / 2) && !isLight(w - 1, h / 2)) return full

        val midY = h / 2
        var left = 0
        while (left < w && isLight(left, midY)) left++
        var right = w - 1
        while (right > left && isLight(right, midY)) right--

        val midX = w / 2
        var top = 0
        while (top < h && isLight(midX, top)) top++
        var bottom = h - 1
        while (bottom > top && isLight(midX, bottom)) bottom--

        // 防御：若检测出的“相框”过大（说明整条中线接近同色，不是真正相框），回退为原图
        if (left > 0.30f * w || top > 0.30f * h) return full

        return android.graphics.Rect(left, top, right + 1, bottom + 1)
    }

    // 绘制背景图片缩放模式
    /**
     * CENTER_FIT 模式下整幅图等比完整显示后落在画布上的目标矩形（超出的方向留透明）。
     * 与 drawBgImage 的 CENTER_FIT 分支保持同一套算法，供需要贴合素材内固定位置（如机身屏幕）的形状复用。
     */
    fun centerFitRect(rectF: RectF, imageAspect: Float): RectF {
        val targetRatio = rectF.width() / rectF.height()
        return if (imageAspect > targetRatio) {
            // 图更宽：以宽为准，上下留透明
            val dstH = rectF.width() / imageAspect
            val top = rectF.centerY() - dstH / 2f
            RectF(rectF.left, top, rectF.right, top + dstH)
        } else {
            // 图更高：以高为准，左右留透明
            val dstW = rectF.height() * imageAspect
            val left = rectF.centerX() - dstW / 2f
            RectF(left, rectF.top, left + dstW, rectF.bottom)
        }
    }

    fun fastBlur(sentBitmap: Bitmap, radius: Int): Bitmap {
        val bitmap = sentBitmap.copy(sentBitmap.config ?: Bitmap.Config.ARGB_8888, true)
        if (radius < 1) {
            return bitmap
        }

        val w = bitmap.width
        val h = bitmap.height

        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(Math.max(w, h))
        val vmax = IntArray(Math.max(w, h))

        val dv = IntArray(256 * div)
        for (i in 0 until 256 * div) {
            dv[i] = i / div
        }

        yi = 0
        yw = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        for (y in 0 until h) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            for (i in -radius..radius) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))]
                sir = stack[i + radius]
                sir[0] = (p shr 16) and 0xff
                sir[1] = (p shr 8) and 0xff
                sir[2] = p and 0xff
                rbs = r1 - Math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
            }
            stackpointer = radius

            for (x in 0 until w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm)
                }
                p = pix[yw + vmin[x]]

                sir[0] = (p shr 16) and 0xff
                sir[1] = (p shr 8) and 0xff
                sir[2] = p and 0xff

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
            }
            yw += w
        }
        for (x in 0 until w) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            yp = -radius * w
            for (i in -radius..radius) {
                yi = Math.max(0, yp) + x
                sir = stack[i + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - Math.abs(i)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (i < hm) {
                    yp += w
                }
            }
            yi = x
            stackpointer = radius
            for (y in 0 until h) {
                val alphaPixel = pix[yi] and -0x1000000
                pix[yi] = alphaPixel or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (x == 0) {
                    vmax[y] = Math.min(y + r1, hm) * w
                }
                p = x + vmax[y]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi += w
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }

    // 预设插画缓存：桌面组件每次刷新都会渲染，避免重复全尺寸解码。
    // key 必须带上目标尺寸，否则首次按小尺寸解码的位图会被 4×4 大组件复用，
    // 导致桌面显示被放大的模糊图。
    // 24MB：整卡插画素材单张全分辨率就有 3~8.5MB（如春天与小狗 1812×1012），
    // 桌面上同时存在 4×2/4×3/4×4 三种尺寸时三张都要驻留，16MB 会把最不常用的那张挤掉、
    // 每次刷新重新解码一遍 8MB 位图。
    val presetImageCache = object : android.util.LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    @Synchronized
    fun getPresetImage(context: Context, resName: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        val cacheKey = "${resName}_${targetWidth}x${targetHeight}"
        presetImageCache.get(cacheKey)?.let { if (!it.isRecycled) return it }
        val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
        if (resId == 0) return null
        val bmp = decodeResourceSampled(context.resources, resId, targetWidth, targetHeight)
        if (bmp == null) {
            // 矢量或非位图资源降级为 Drawable 渲染：必须按目标尺寸栅格化，
            // 否则按 intrinsic 尺寸（或兜底 300×120）生成的小图放大到组件尺寸会发虚
            val drawable = context.resources.getDrawable(resId, null) ?: return null
            val drawW = targetWidth.coerceAtLeast(MIN_BITMAP_SIZE)
            val drawH = targetHeight.coerceAtLeast(MIN_BITMAP_SIZE)
            val tmpBmp = Bitmap.createBitmap(drawW, drawH, Bitmap.Config.ARGB_8888)
            val tmpCanvas = Canvas(tmpBmp)
            drawable.setBounds(0, 0, drawW, drawH)
            drawable.draw(tmpCanvas)
            presetImageCache.put(cacheKey, tmpBmp)
            return tmpBmp
        }
        presetImageCache.put(cacheKey, bmp)
        return bmp
    }

    @Synchronized
    fun decodeFileSampled(path: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calcInSampleSize(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
        }
        return BitmapFactory.decodeFile(path, opts)
    }

    fun decodeResourceSampled(res: android.content.res.Resources, resId: Int, targetWidth: Int, targetHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(res, resId, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calcInSampleSize(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
            // 透明 PNG（如信纸抠图）必须强制 ARGB_8888，否则采样后透明区会变成 RGB_565 导致的黑色
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeResource(res, resId, opts)
    }

    fun calcInSampleSize(outWidth: Int, outHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var sampleSize = 1
        var w = outWidth
        var h = outHeight
        while (w / 2 >= reqWidth && h / 2 >= reqHeight) {
            w /= 2
            h /= 2
            sampleSize *= 2
        }
        return sampleSize
    }

}
