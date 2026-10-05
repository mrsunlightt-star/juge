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
import com.juge.app.render.WidgetRenderKernel.centerFitRect

/**
 * 自 WidgetCanvasRenderer 拆分而来。成员由调用点 import，调用写法不变。
 */
internal object SuborRenderer {

    // 小霸王游戏机：subor_console.png 的宽高比，以及机身屏幕（文本区）在素材中的相对位置，
    // 均由出图/合成时测得。素材换成新图后需同步更新这几个比例。
    // 素材四周预留了透明留白，避免 4×2 这类偏宽组件里机身/手柄贴住组件上下边缘
    const val SUBOR_IMAGE_ASPECT = 1.6043f

    const val SUBOR_SCREEN_LEFT_RATIO = 0.2893f

    const val SUBOR_SCREEN_RIGHT_RATIO = 0.7173f

    const val SUBOR_SCREEN_TOP_RATIO = 0.1176f

    const val SUBOR_SCREEN_BOTTOM_RATIO = 0.5882f

    const val SUBOR_SCREEN_TEXT_PAD_X_DP = 6f

    const val SUBOR_SCREEN_TEXT_PAD_Y_DP = 4f

    // 通电显像管的荧光屏配色：屏幕永远是绿色荧光（模拟已开机），与用户可调的字色无关
    val SUBOR_SCREEN_CORE_COLOR = 0xFF46A857.toInt() // 中心亮绿

    val SUBOR_SCREEN_MID_COLOR = 0xFF1C6B2C.toInt()

    val SUBOR_SCREEN_EDGE_COLOR = 0xFF04140A.toInt() // 边缘近黑，形成曲面暗角

    const val SUBOR_GLOW_RADIUS_DP = 7f

    const val SUBOR_SCREEN_FILL_ALPHA = 236

    const val SUBOR_SCANLINE_SPACING_DP = 2.6f

    const val SUBOR_SCANLINE_ALPHA = 44

    const val SUBOR_GLASS_SHEEN_ALPHA = 26

    // 小霸王游戏机：素材里"显像管玻璃"（屏幕）在组件位图中的矩形。
    // 素材按 CENTER_FIT 等比完整显示，屏幕位置随组件宽高比变化，必须按同一套比例换算。
    fun suborScreenRect(outerRect: RectF): RectF {
        val model = centerFitRect(outerRect, SUBOR_IMAGE_ASPECT)
        return RectF(
            model.left + model.width() * SUBOR_SCREEN_LEFT_RATIO,
            model.top + model.height() * SUBOR_SCREEN_TOP_RATIO,
            model.left + model.width() * SUBOR_SCREEN_RIGHT_RATIO,
            model.top + model.height() * SUBOR_SCREEN_BOTTOM_RATIO
        )
    }

    // 屏幕是圆角玻璃：按小圆角裁剪，避免绿色荧光溢出到机身红色边框上
    fun clipSuborScreen(canvas: Canvas, screen: RectF) {
        val radius = Math.min(screen.width(), screen.height()) * 0.06f
        val clipPath = Path().apply { addRoundRect(screen, radius, radius, Path.Direction.CW) }
        canvas.clipPath(clipPath)
    }

    /**
     * 通电显像管：在屏幕玻璃区域内叠一层绿色荧光底 + 中心亮斑。
     * 素材本身是"未通电"的深灰玻璃，这一步让它变成开机的绿屏。
     */
    fun drawSuborScreenGlow(canvas: Canvas, screen: RectF, densityScale: Float) {
        if (screen.width() <= 0f || screen.height() <= 0f) return
        canvas.save()
        clipSuborScreen(canvas, screen)

        val cx = screen.centerX()
        val cy = screen.centerY()
        val radius = Math.max(screen.width(), screen.height()) * 0.62f

        val screenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(SUBOR_SCREEN_CORE_COLOR, SUBOR_SCREEN_MID_COLOR, SUBOR_SCREEN_EDGE_COLOR),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            alpha = SUBOR_SCREEN_FILL_ALPHA
        }
        canvas.drawRect(screen, screenPaint)

        // 中心亮斑：模拟电子束在屏幕中央聚集形成的高光
        val hotSpotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, radius * 0.5f,
                intArrayOf(0x55CCFFC2, 0x00CCFFC2),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(screen, hotSpotPaint)

        canvas.restore()
    }

    /**
     * 显像管观感层：扫描线 + 曲面暗角 + 玻璃反光，全部压在文字之上。
     * 这是"文字真的显示在屏幕上"的关键——扫描线横穿字形，视觉上把文字与屏幕焊在一起。
     */
    fun drawSuborCrtOverlay(canvas: Canvas, screen: RectF, densityScale: Float) {
        if (screen.width() <= 0f || screen.height() <= 0f) return
        canvas.save()
        clipSuborScreen(canvas, screen)

        // 1. 扫描线：等距细横线，模拟显像管逐行扫描
        val spacing = (SUBOR_SCANLINE_SPACING_DP * densityScale).coerceAtLeast(1.5f)
        val scanlinePaint = Paint().apply {
            color = Color.BLACK
            alpha = SUBOR_SCANLINE_ALPHA
            strokeWidth = (spacing * 0.42f).coerceAtLeast(0.8f)
        }
        var y = screen.top
        while (y <= screen.bottom) {
            canvas.drawLine(screen.left, y, screen.right, y, scanlinePaint)
            y += spacing
        }

        // 2. 曲面暗角：中心透明、四周压暗，强化球面显像管的纵深感
        val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                screen.centerX(), screen.centerY(),
                Math.max(screen.width(), screen.height()) * 0.74f,
                intArrayOf(0x00000000, 0x00000000, 0x8F000000.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(screen, vignettePaint)

        // 3. 玻璃反光：左上角斜向柔光，恢复玻璃罩的质感
        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                screen.left, screen.top,
                screen.left + screen.width() * 0.6f, screen.bottom,
                intArrayOf(0xFFFFFFFF.toInt(), 0x00FFFFFF),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            alpha = SUBOR_GLASS_SHEEN_ALPHA
        }
        canvas.drawRect(screen, sheenPaint)

        canvas.restore()
    }

}
