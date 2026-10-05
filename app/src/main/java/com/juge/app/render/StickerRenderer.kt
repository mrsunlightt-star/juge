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
internal object StickerRenderer {

    // 贴纸夜景：人物与路灯直接站在文本框这张"纸"的上沿，花枝垂在文本框下沿。
    // 各比例均相对组件位图，4×2 与 4×4 共用同一套，只按高度缩放。
    const val STICKER_ART_ASPECT = 1293f / 1200f // 贴纸素材(人物+路灯)宽高比

    const val STICKER_ART_HEIGHT_RATIO = 0.60f // 贴纸高度占组件高度

    const val STICKER_ART_SINK_RATIO = 0.015f // 贴纸底部下沉量：白边压进纸里，看起来才像站在纸上

    const val STICKER_GROUND_RATIO = 0.60f // 落地线 = 文本框上沿 = 贴纸脚底

    const val STICKER_TEXT_TOP_RATIO = 0.60f

    const val STICKER_TEXT_BOTTOM_RATIO = 0.90f

    const val STICKER_TEXT_LEFT_RATIO = 0.05f

    const val STICKER_TEXT_RIGHT_RATIO = 0.95f

    const val STICKER_TEXT_PAD_X_DP = 12f

    const val STICKER_TEXT_PAD_Y_DP = 8f

    // 灯光落点的参考线：光柱与人物受光都收在落地线上，人才是"站在光里"
    const val STICKER_BEAM_BOTTOM_RATIO = 0.60f

    // 路灯灯罩在贴纸素材里的水平位置（用于把光斑打在灯的正下方）
    const val STICKER_LAMP_CX_RATIO = 0.917f

    const val STICKER_COUPLE_CX_RATIO = 0.23f // 人物在贴纸素材里的水平位置（接触阴影用）

    const val STICKER_LAMP_HEAD_CY_RATIO = 0.105f // 灯罩中心在贴纸素材里的垂直位置

    // 灯光：从灯罩斜向下铺开一束光柱，落在人物与路灯之间，营造"在路灯下跳舞"的氛围
    const val STICKER_BEAM_FAR_LEFT_RATIO = -0.86f // 光束远端左边界（自灯心起算，相对贴纸宽度）

    const val STICKER_BEAM_FAR_RIGHT_RATIO = -0.30f // 光束远端右边界：整束光瞄准人物

    const val STICKER_BEAM_BLUR_DP = 6f // 光柱边缘柔化

    // 衰减要缓：人物离灯罩约 3/4 个光柱长，衰减太陡光还没走到人身上就没了
    const val STICKER_BEAM_FALLOFF = 1.7f // 径向渐变半径 = 光柱长 × 该系数

    const val STICKER_LIT_FALLOFF = 2.4f // 人身上的光衰减更缓，两个人受光才均匀

    val STICKER_BEAM_STOPS = floatArrayOf(0f, 0.28f, 0.55f, 0.8f, 0.93f, 1f)

    const val STICKER_BEAM_RGB = 0xE0BE7E // 灯光主色（暖白偏琥珀）

    val STICKER_BEAM_RAMP = intArrayOf(0x33, 0x2B, 0x1A, 0x0B, 0x04, 0x00)

    // 落在人物身上的受光：同一束光再叠一层，只保留人物像素，人是"被灯照着"而不是站在光旁边
    val STICKER_LIT_RAMP = intArrayOf(0x3E, 0x38, 0x2C, 0x1E, 0x0E, 0x00)

    const val STICKER_HALO_ALPHA = 0x3C // 灯罩外圈的暖光晕

    const val STICKER_HALO_RADIUS_RATIO = 0.22f

    val STICKER_HALO_COLOR = 0xFFE7B0.toInt()

    // 文本框：纸张剪纸。四角剪口 + 纸纹 + 白描边，避免一片纯色的"素净"
    const val STICKER_TEXT_EDGE_DP = 2.5f

    const val STICKER_TEXT_SNIP_DP = 7f // 四角剪掉的小口

    // 垂在文本框下沿的一束玫瑰：藤蔓从左沿贴着下沿拖出，玫瑰花冠大小依次递减
    const val STICKER_ROSE_DP = 10.5f // 主玫瑰花冠半径

    const val STICKER_ROSE_OUTLINE_DP = 1.1f

    val STICKER_ROSE_COLOR = 0xC2566B.toInt()

    val STICKER_ROSE_PETAL = 0xE38C9C.toInt()

    val STICKER_ROSE_CORE = 0x93394C.toInt()

    val STICKER_LEAF_COLOR = 0x4C7A55.toInt()

    val STICKER_VINE_COLOR = 0x3F6146.toInt()

    // 贴纸夜景：落地线的高度（= 文本框上沿 = 贴纸脚底所在位置）
    fun stickerGroundY(outerRect: RectF): Float =
        outerRect.top + outerRect.height() * STICKER_GROUND_RATIO

    // 贴纸夜景：文本框矩形——上沿就是人物与路灯的落地线
    fun stickerTextBoxRect(outerRect: RectF): RectF = RectF(
        outerRect.left + outerRect.width() * STICKER_TEXT_LEFT_RATIO,
        outerRect.top + outerRect.height() * STICKER_TEXT_TOP_RATIO,
        outerRect.left + outerRect.width() * STICKER_TEXT_RIGHT_RATIO,
        outerRect.top + outerRect.height() * STICKER_TEXT_BOTTOM_RATIO
    )

    // 贴纸夜景：贴纸（人物+路灯）的绘制矩形——按高度等比缩放、脚底压进路面上沿、水平居中
    fun stickerArtRect(outerRect: RectF): RectF {
        val feet = stickerGroundY(outerRect) + outerRect.height() * STICKER_ART_SINK_RATIO
        val h = outerRect.height() * STICKER_ART_HEIGHT_RATIO
        val w = h * STICKER_ART_ASPECT
        val cx = outerRect.centerX()
        return RectF(cx - w / 2f, feet - h, cx + w / 2f, feet)
    }

    /**
     * 贴纸夜景：人物与路灯踩在纸上的接触阴影。
     * 白描边贴纸直接压在纸面上容易"浮"起来，脚底压一层软阴影才站得住。
     * 阴影只画在纸片轮廓内，不会溢出纸外。
     */
    fun drawStickerContactShadow(canvas: Canvas, textBox: RectF, paperPath: Path, artRect: RectF, alpha: Int) {
        val feetY = artRect.bottom
        val coupleCx = artRect.left + artRect.width() * STICKER_COUPLE_CX_RATIO
        val rx = artRect.width() * 0.28f
        val ry = textBox.height() * 0.055f

        val layer = canvas.save()
        canvas.clipPath(paperPath)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                coupleCx, feetY, rx,
                intArrayOf(0x66000000, 0x00000000), null, Shader.TileMode.CLAMP
            )
            this.alpha = alpha
        }
        val ellipse = canvas.save()
        canvas.scale(1f, ry / rx, coupleCx, feetY)
        canvas.drawCircle(coupleCx, feetY, rx, paint)
        canvas.restoreToCount(ellipse)

        // 路灯底座下的一小块
        val lampCx = artRect.left + artRect.width() * STICKER_LAMP_CX_RATIO
        val lampR = rx * 0.18f
        val lampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                lampCx, feetY, lampR,
                intArrayOf(0x59000000, 0x00000000), null, Shader.TileMode.CLAMP
            )
            this.alpha = alpha
        }
        canvas.drawCircle(lampCx, feetY, lampR, lampPaint)

        canvas.restoreToCount(layer)
    }

    /**
     * 贴纸夜景：灯光渐变。
     * 以灯罩为圆心向外衰减，[ramp] 是各档不透明度，颜色统一用 [STICKER_BEAM_RGB]。
     */
    fun stickerBeamGradient(headX: Float, headY: Float, bottom: Float, falloff: Float, ramp: IntArray): RadialGradient {
        val colors = IntArray(ramp.size) { (ramp[it] shl 24) or STICKER_BEAM_RGB }
        return RadialGradient(
            headX, headY, (bottom - headY) * falloff,
            colors, STICKER_BEAM_STOPS, Shader.TileMode.CLAMP
        )
    }

    /** 贴纸夜景：以灯罩为顶点、斜向人物铺开的光锥。 */
    fun stickerBeamCone(outerRect: RectF, artRect: RectF): Path {
        val headX = artRect.left + artRect.width() * STICKER_LAMP_CX_RATIO
        val headY = artRect.top + artRect.height() * STICKER_LAMP_HEAD_CY_RATIO
        val bottom = outerRect.top + outerRect.height() * STICKER_BEAM_BOTTOM_RATIO
        val w = artRect.width()
        return Path().apply {
            moveTo(headX, headY)
            lineTo(headX + w * STICKER_BEAM_FAR_RIGHT_RATIO, bottom)
            lineTo(headX + w * STICKER_BEAM_FAR_LEFT_RATIO, bottom)
            close()
        }
    }

    /**
     * 贴纸夜景：空气里的光柱。
     * 用"以灯罩为圆心的径向渐变"做衰减——离灯越远越淡，再叠一层高斯模糊把锥形边缘化开，
     * 看起来是空气里的光而不是一块半透明色块。画在人物之下，人是站在光里。
     */
    fun drawStickerLightBeam(canvas: Canvas, outerRect: RectF, artRect: RectF, densityScale: Float, alpha: Int) {
        val headX = artRect.left + artRect.width() * STICKER_LAMP_CX_RATIO
        val headY = artRect.top + artRect.height() * STICKER_LAMP_HEAD_CY_RATIO
        val bottom = outerRect.top + outerRect.height() * STICKER_BEAM_BOTTOM_RATIO

        val cone = stickerBeamCone(outerRect, artRect)

        val beam = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isDither = true
            shader = stickerBeamGradient(headX, headY, bottom, STICKER_BEAM_FALLOFF, STICKER_BEAM_RAMP)
            maskFilter = BlurMaskFilter(STICKER_BEAM_BLUR_DP * densityScale, BlurMaskFilter.Blur.NORMAL)
            this.alpha = alpha
        }
        canvas.drawPath(cone, beam)
    }

    /**
     * 贴纸夜景：落在人物身上的受光。
     * 同一束光再画一次，但用贴纸自身的 alpha 当遮罩，只留在人物（和灯）的像素上——
     * 于是光是"照在"他们身上，而不是从他们身后透过去。
     */
    fun drawStickerLightOnArt(canvas: Canvas, outerRect: RectF, artRect: RectF, art: Bitmap, densityScale: Float, alpha: Int) {
        val headX = artRect.left + artRect.width() * STICKER_LAMP_CX_RATIO
        val headY = artRect.top + artRect.height() * STICKER_LAMP_HEAD_CY_RATIO
        val bottom = outerRect.top + outerRect.height() * STICKER_BEAM_BOTTOM_RATIO

        val layer = canvas.saveLayer(artRect, null)
        val lit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isDither = true
            shader = stickerBeamGradient(headX, headY, bottom, STICKER_LIT_FALLOFF, STICKER_LIT_RAMP)
            maskFilter = BlurMaskFilter(STICKER_BEAM_BLUR_DP * densityScale, BlurMaskFilter.Blur.NORMAL)
            this.alpha = alpha
        }
        canvas.drawPath(stickerBeamCone(outerRect, artRect), lit)

        val mask = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawBitmap(art, null, artRect, mask)
        canvas.restoreToCount(layer)
    }

    /** 贴纸夜景：灯罩外圈的暖光晕，压在最上层，灯才有"正在发光"的感觉。 */
    fun drawStickerLampHalo(canvas: Canvas, artRect: RectF) {
        val headX = artRect.left + artRect.width() * STICKER_LAMP_CX_RATIO
        val headY = artRect.top + artRect.height() * STICKER_LAMP_HEAD_CY_RATIO
        val radius = artRect.width() * STICKER_HALO_RADIUS_RATIO
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isDither = true
            shader = RadialGradient(
                headX, headY, radius,
                intArrayOf(
                    (STICKER_HALO_ALPHA shl 24) or STICKER_HALO_COLOR,
                    (0x2A shl 24) or STICKER_HALO_COLOR,
                    (0x14 shl 24) or STICKER_HALO_COLOR,
                    (0x08 shl 24) or STICKER_HALO_COLOR,
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.3f, 0.55f, 0.78f, 1f), Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(headX, headY, radius, halo)
    }

    /**
     * 贴纸夜景：垂在文本框下沿的一束玫瑰。
     * 藤蔓贴着下沿往右拖、向下鼓出，花冠依次变小、整束坠在纸的下边；
     * 每片花瓣/叶子都描一圈白边，和人物、路灯一样是"剪下来贴上去"的纸片。
     */
    fun drawStickerRoses(canvas: Canvas, textBox: RectF, densityScale: Float, alpha: Int) {
        // 花枝垂在文本框下方那点空当里：组件矮（4×2）时按高度收一收，花才不会掉出画面
        val r = minOf(STICKER_ROSE_DP * densityScale, textBox.height() * 0.14f)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.alpha = alpha
            style = Paint.Style.STROKE
            strokeWidth = STICKER_ROSE_OUTLINE_DP * densityScale
            strokeJoin = Paint.Join.ROUND
        }
        val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = STICKER_LEAF_COLOR
            this.alpha = alpha
        }
        val vinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = STICKER_VINE_COLOR
            this.alpha = alpha
            style = Paint.Style.STROKE
            strokeWidth = r * 0.16f
            strokeCap = Paint.Cap.ROUND
        }

        val x0 = textBox.left + r * 1.15f
        val y0 = textBox.bottom

        // 藤蔓：贴着下沿往右拖一段，向下鼓出，末端带一个小卷
        val vine = Path().apply {
            moveTo(x0 - r * 0.2f, y0 - r * 0.35f)
            cubicTo(x0 + r * 1.6f, y0 + r * 1.15f,
                x0 + r * 3.4f, y0 - r * 0.35f,
                x0 + r * 4.6f, y0 + r * 0.55f)
            cubicTo(x0 + r * 5.3f, y0 + r * 1.0f,
                x0 + r * 5.2f, y0 - r * 0.15f,
                x0 + r * 4.35f, y0 + r * 0.05f)
        }
        canvas.drawPath(vine, vinePaint)

        // 叶子：沿藤蔓两侧各插几片，花枝贴着纸沿生长
        drawRoseLeaf(canvas, x0 + r * 2.3f, y0 + r * 0.85f, x0 + r * 3.5f, y0 + r * 1.75f, r * 0.34f, leafPaint, outline)
        drawRoseLeaf(canvas, x0 + r * 3.2f, y0 + r * 0.05f, x0 + r * 4.4f, y0 - r * 0.45f, r * 0.30f, leafPaint, outline)
        drawRoseLeaf(canvas, x0 + r * 1.0f, y0 + r * 0.15f, x0 + r * 0.05f, y0 - r * 0.5f, r * 0.30f, leafPaint, outline)
        drawRoseLeaf(canvas, x0 + r * 4.9f, y0 + r * 1.05f, x0 + r * 5.7f, y0 + r * 1.75f, r * 0.24f, leafPaint, outline)

        // 玫瑰：主花在左，右边两朵渐小，整束坠在文本框下边
        drawRose(canvas, x0 + r * 0.35f, y0 + r * 0.72f, r, outline, alpha)
        drawRose(canvas, x0 + r * 2.05f, y0 + r * 1.15f, r * 0.72f, outline, alpha)
        drawRose(canvas, x0 + r * 3.75f, y0 + r * 0.72f, r * 0.55f, outline, alpha)
    }

    /** 一片玫瑰叶：两段二次曲线拼成的柳叶形。 */
    fun drawRoseLeaf(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, bulge: Float, fill: Paint, outline: Paint) {
        val dx = x1 - x0
        val dy = y1 - y0
        val len = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.001f)
        val nx = -dy / len * bulge
        val ny = dx / len * bulge
        val mx = (x0 + x1) / 2f
        val my = (y0 + y1) / 2f
        val leaf = Path().apply {
            moveTo(x0, y0)
            quadTo(mx + nx, my + ny, x1, y1)
            quadTo(mx - nx, my - ny, x0, y0)
            close()
        }
        canvas.drawPath(leaf, fill)
        canvas.drawPath(leaf, outline)
    }

    /**
     * 一朵玫瑰：波浪边的花体 + 从花心卷到花沿的螺旋。
     * 描边只走整朵花的剪影，花面上不留白线，避免把花瓣切成一块块。
     */
    fun drawRose(canvas: Canvas, cx: Float, cy: Float, r: Float, outline: Paint, alpha: Int) {
        val deep = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = STICKER_ROSE_CORE
            this.alpha = alpha
        }
        val light = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = STICKER_ROSE_PETAL
            this.alpha = alpha
        }

        // 剪影：圆花体 + 一圈波浪边，合成一个 Path，填色时自然取并集
        val body = Path().apply {
            addCircle(cx, cy, r * 0.86f, Path.Direction.CW)
            for (i in 0 until 6) {
                val a = Math.toRadians(i * 60.0 - 90.0)
                addCircle(
                    cx + (0.68f * r * Math.cos(a)).toFloat(),
                    cy + (0.68f * r * Math.sin(a)).toFloat(),
                    r * 0.36f, Path.Direction.CW
                )
            }
        }
        // 描边走整朵花的剪影：先铺一层白，再填花体色，白边只留在最外圈，
        // 花面上不会留下把花瓣切成一块块的白线
        val sticker = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.alpha = alpha
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = outline.strokeWidth * 2f
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(body, sticker)
        canvas.drawPath(body, light)

        // 花心螺旋：从花心一路卷到花沿，这是玫瑰最认得出的特征。
        // 半径按幂次收缩，花心卷得紧、外圈松得开，才不像机械等距的螺纹
        val rMax = r * 0.82f
        fun spiralOf(t0: Float, t1: Float): Path {
            val p = Path()
            val n = 56
            for (k in 0..n) {
                val t = t0 + (t1 - t0) * k / n
                val a = 2.2 * 2.0 * Math.PI * t
                val rr = rMax * Math.pow((1f - t).toDouble(), 1.35).toFloat()
                val x = cx + (rr * Math.cos(a)).toFloat()
                val y = cy + (rr * Math.sin(a)).toFloat()
                if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            return p
        }
        fun spiralPaint(width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = STICKER_ROSE_COLOR
            this.alpha = alpha
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(spiralOf(0f, 0.56f), spiralPaint(r * 0.15f))
        canvas.drawPath(spiralOf(0.53f, 1f), spiralPaint(r * 0.10f))
        canvas.drawCircle(cx, cy, r * 0.10f, deep)
    }

}
