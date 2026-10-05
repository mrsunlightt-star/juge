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
internal object WeatherBoxRenderer {

    // ==================== 天气盒子（WEATHER_BOX） ====================
    // 白色盒体正面挖一个内凹方腔，腔底铺 weather_box_cavity 素材（蓝天微缩城市）。
    // 关键：**腔体始终保持素材的宽高比**，只在盒体里水平居中、上下按比例分配。
    // 若让腔体去适应组件的宽高比（像 4×2 那样摊成 3.5:1），素材就填不满腔体，
    // 两侧会露出盒面空白、把"内凹"读成两条孤立暗带。锁死比例后素材永远恰好填满腔口，
    // 素材自带的四周暗角正好落在腔口边缘，凹感自然且 4×2 / 4×4 都不会变形。
    // 素材比例由 weather_box_cavity.webp 实测（1414×676），换图后需同步。
    const val WEATHER_BOX_CAVITY_ASPECT = 1414f / 676f

    // 腔体在盒体里的垂直位置与高度上限：顶部留 3.1%，腔高不超过盒高的 66%，
    // 剩下的留给正文，保证 4×4 这类高组件也放得下一行字
    const val WEATHER_BOX_CAVITY_TOP_RATIO = 0.031f

    // 腔高占盒高的比例。素材是横幅（2.09:1）：组件越扁，腔体越容易被宽度卡住、
    // 两侧留白越多，所以给到 0.66 让 4×2 下腔体接近顶满宽度；
    // 而正文区至少要留得下一行 18sp（约 30dp），否则文字会被挤出盒体下沿。
    const val WEATHER_BOX_CAVITY_HEIGHT_RATIO = 0.66f

    const val WEATHER_BOX_CAVITY_MAX_HEIGHT_RATIO = 0.66f

    // 腔体左右最少留边（占盒宽），与设计稿的 2.7% 同量级
    const val WEATHER_BOX_CAVITY_SIDE_RATIO = 0.027f

    // 腔口圆角：内凹开口的圆角略小于外框，弱化"贴纸感"
    const val WEATHER_BOX_CAVITY_RADIUS_DP = 10f

    // 内壁遮光带厚度（腔口向内渐暗的一条），按密度放大。
    // 素材边缘已自带一圈暗角，这里只补很浅的一层，主要是为了让上沿的暗更连贯
    const val WEATHER_BOX_INNER_WALL_DP = 5f

    const val WEATHER_BOX_INNER_SHADOW_ALPHA = 90

    // 腔体下沿高光：光从腔口下沿反弹进来的一条亮边，是"凹"的主要立体线索
    const val WEATHER_BOX_RIM_LIGHT_DP = 2.5f

    val WEATHER_BOX_INNER_SHADOW = 0xFF5A6478.toInt()

    val WEATHER_BOX_RIM_LIGHT = 0xFFFFFFFF.toInt()

    // 正文区（腔体下方留白）内边距
    const val WEATHER_BOX_TEXT_PAD_X_DP = 16f

    const val WEATHER_BOX_TEXT_PAD_Y_DP = 8f

    // 正文区最小净高：一行 18sp 正文（含行距）约需此高度。
    // 腔体高度按它反推，确保扁组件（4×2）下文字不会被挤出盒体下沿
    const val WEATHER_BOX_TEXT_MIN_HEIGHT_DP = 26f

    /**
     * 内凹腔体在盒体里的矩形，**宽高比恒等于素材比例**。
     *
     * 高度从两个约束里取较小值：一个是按盒高分的上限（让腔体在 4×4 这类高组件上
     * 不会过大），另一个是**给正文预留出的净高度**——扁组件（4×2）按比例分完腔高后
     * 剩下的空间不足一行字，文字会被挤出盒体，所以这里先把正文需要的高度扣掉。
     * 宽度再由高度按素材比例反推，超出可用宽度（左右留边后）则以宽度为准回调高度。
     */
    fun weatherBoxCavityRect(outerRect: RectF, densityScale: Float): RectF {
        val availWidth = outerRect.width() * (1f - 2f * WEATHER_BOX_CAVITY_SIDE_RATIO)
        val top = outerRect.top + outerRect.height() * WEATHER_BOX_CAVITY_TOP_RATIO
        val ratioCap = outerRect.height() * WEATHER_BOX_CAVITY_MAX_HEIGHT_RATIO

        // 正文净高：一行 18sp 正文（含行距与上下内边距）所需的高度
        val textPadY = WEATHER_BOX_TEXT_PAD_Y_DP * densityScale
        val textMinHeight = WEATHER_BOX_TEXT_MIN_HEIGHT_DP * densityScale
        val textCap = outerRect.bottom - textPadY - textMinHeight - top

        var height = minOf(outerRect.height() * WEATHER_BOX_CAVITY_HEIGHT_RATIO, ratioCap, textCap)
        if (height <= 0f) {
            // 组件过矮（扣掉正文后放不下腔体）：腔体退让，保证正文仍有位置
            height = textCap.coerceAtLeast(outerRect.height() * 0.4f)
        }

        var width = height * WEATHER_BOX_CAVITY_ASPECT
        if (width > availWidth) {
            width = availWidth
            height = width / WEATHER_BOX_CAVITY_ASPECT
        }
        val cx = outerRect.centerX()
        return RectF(
            cx - width / 2f,
            top,
            cx + width / 2f,
            top + height
        )
    }

    /**
     * 画内凹腔体。腔体尺寸与素材比例一致，因此素材直接铺满整个腔口：
     * 素材四周自带的暗角正好压在腔口边缘，读起来就是腔壁遮光。
     * 这里再补两笔：上沿一条浅暗（让顶部转折更连贯）与下沿一条亮边
     * （光从腔口下沿反弹回来）——这条亮边是让平面矩形读成"凹"的关键。
     */
    fun drawWeatherBoxCavity(
        canvas: Canvas,
        cavity: RectF,
        bitmap: Bitmap?,
        style: WidgetStyle,
        densityScale: Float,
        alpha: Int
    ) {
        val radius = WEATHER_BOX_CAVITY_RADIUS_DP * densityScale
        val clip = Path().apply {
            addRoundRect(cavity, radius, radius, Path.Direction.CW)
        }

        canvas.save()
        canvas.clipPath(clip)

        if (bitmap != null && !bitmap.isRecycled) {
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                this.alpha = alpha
            }
            // 腔体比例 == 素材比例，故直接铺满腔口（dst 与 cavity 重合）
            canvas.drawBitmap(bitmap, null, cavity, paint)
        } else {
            // 素材缺失时铺一层天空蓝兜底，避免腔体变成一块空洞
            canvas.drawRect(
                cavity,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFA8C0E0.toInt()
                    this.alpha = alpha
                }
            )
        }

        // 上沿浅暗：衔接盒面与腔壁的转折
        val wall = WEATHER_BOX_INNER_WALL_DP * densityScale
        val shadeAlpha = (alpha * WEATHER_BOX_INNER_SHADOW_ALPHA / 255f).toInt()
        val shade = LinearGradient(
            cavity.left, cavity.top, cavity.left, cavity.top + wall,
            WEATHER_BOX_INNER_SHADOW, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(
            cavity.left, cavity.top, cavity.right, cavity.top + wall,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = shade; this.alpha = shadeAlpha }
        )

        // 腔口下沿高光：贴着腔体底边、向上淡出的一条亮边
        val rimH = WEATHER_BOX_RIM_LIGHT_DP * densityScale
        val rim = LinearGradient(
            cavity.bottom, 0f, cavity.bottom - rimH, 0f,
            WEATHER_BOX_RIM_LIGHT, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(
            cavity.left, cavity.bottom - rimH, cavity.right, cavity.bottom,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = rim
                this.alpha = (alpha * 0.65f).toInt()
            }
        )

        canvas.restore()
    }

}
