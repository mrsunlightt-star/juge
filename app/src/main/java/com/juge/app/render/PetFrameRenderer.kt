package com.juge.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.juge.app.data.WidgetShape
import kotlin.math.min

/**
 * 萌宠花框：素材负责外框、角色与装饰，正文落在中央窗口。
 *
 * 春天到了、熊猫宝宝自带浅色卡面；太空小狗和 Q 萌海盗团的中央是真透明窗口，
 * 因此在正文下方补一层半透明浅色承托。中华田园犬的透明窗口保持完全透壁纸。
 */
internal object PetFrameRenderer {

    private const val PANEL_ALPHA = 0.94f

    // 三张方版素材入库时已裁掉透明外圈，并按原比例居中补成 2048×2048；
    // 两张横版素材保持原生 2176×1088。下列比例均按各自画布实测，避免文字落偏。
    fun textRect(shape: WidgetShape, outerRect: RectF): RectF = when (shape) {
        WidgetShape.SPRING_ARRIVAL ->
            proportionalRect(outerRect, 0.1744f, 0.2339f, 0.8481f, 0.8739f)
        WidgetShape.SPACE_DOG ->
            proportionalRect(outerRect, 0.1747f, 0.2868f, 0.7716f, 0.8251f)
        WidgetShape.Q_PIRATE_CREW ->
            proportionalRect(outerRect, 0.156f, 0.292f, 0.776f, 0.860f)
        // 2176×1088 横版素材：边框内沿约 x 7.4%~92.6% / y 27.8%~88.5%，
        // 再向内留一点，避免正文贴住骨头、爪印或竹节。
        WidgetShape.CHINESE_RURAL_DOG ->
            proportionalRect(outerRect, 0.09f, 0.30f, 0.91f, 0.86f)
        WidgetShape.PANDA_BABY ->
            proportionalRect(outerRect, 0.10f, 0.30f, 0.90f, 0.86f)
        else -> error("PetFrameRenderer 只处理萌宠花框形状：$shape")
    }

    fun drawTextBacking(scene: RenderScene) {
        val panel = textBackingRect(scene.style.shape, scene.outerRect) ?: return

        val canvas = scene.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when (scene.style.shape) {
                WidgetShape.SPACE_DOG -> Color.parseColor("#FFFCF3")
                else -> Color.parseColor("#FFF8F1")
            }
            alpha = (scene.alpha * PANEL_ALPHA).toInt().coerceIn(0, 255)
        }
        val cornerRadius = min(panel.width(), panel.height()) * 0.075f
        canvas.save()
        canvas.clipPath(scene.outerPath)
        canvas.drawRoundRect(panel, cornerRadius, cornerRadius, paint)
        canvas.restore()
    }

    /**
     * 透明窗口的浅色承托层。承托层先画、素材后压，因此可以贴近画框内沿，
     * 伸进窗口的角色、绳索和旗杆仍由素材覆盖，不会被浅色面板挡住。
     */
    fun textBackingRect(shape: WidgetShape, outerRect: RectF): RectF? = when (shape) {
        WidgetShape.SPACE_DOG ->
            proportionalRect(outerRect, 0.1513f, 0.2458f, 0.795f, 0.8719f)
        WidgetShape.Q_PIRATE_CREW ->
            proportionalRect(outerRect, 0.111f, 0.255f, 0.821f, 0.900f)
        else -> null
    }

    private fun proportionalRect(
        outerRect: RectF,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): RectF = RectF(
        outerRect.left + outerRect.width() * left,
        outerRect.top + outerRect.height() * top,
        outerRect.left + outerRect.width() * right,
        outerRect.top + outerRect.height() * bottom,
    )
}
