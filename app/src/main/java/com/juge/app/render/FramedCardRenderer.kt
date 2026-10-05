package com.juge.app.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.juge.app.data.WidgetShape
import com.juge.app.render.WidgetRenderKernel.getPresetImage

/**
 * 画框卡片族（雪落宫墙 / 深海鲸歌 / 夏天的海 / 夏日荷花）。同一族整卡设计：
 * 卡纸由「背景颜色」绘制（含纸张颗粒），木框照片按真实比例摆上方，点缀层（梅枝 / 鲸影 /
 * 浪花 / 荷影）贴右下角。两个图层都是带羽化卡纸边的抠图（羽化边与卡纸同色），
 * 任意组件宽高比下都不露接缝。几何比例各自从设计稿实测。
 *
 * 新增一个画框风格 = 新增一条 [FramedCardSpec]，不必改渲染管线。
 *
 * 自 WidgetCanvasRenderer 拆分而来。成员由调用点 import，调用写法不变。
 */
internal object FramedCardRenderer {

    class FramedCardSpec(
        val frameAsset: String,
        val accentAsset: String,
        val frameAspect: Float,      // 相框补丁宽高比（含羽化边）
        val frameWFrac: Float,       // 相框补丁宽 / 卡宽
        val woodTopFrac: Float,      // 木框上沿 / 卡高（定位基准）
        val woodInset: Float,        // 木框上沿在补丁内的纵向占比
        val accentAspect: Float,     // 点缀层宽高比
        val accentWFrac: Float,      // 点缀层宽 / 卡宽（右下贴边）
        val frameShadow: Int         // 相框投影色（#AARRGGBB）
    )

    // 雪落宫墙：米色卡纸 + 木框雪景宫墙 + 梅枝（设计稿 1570×1122）
    private val WINTER_PALACE_SPEC = FramedCardSpec(
        frameAsset = "winter_frame", accentAsset = "winter_branch",
        frameAspect = 1280f / 648f, frameWFrac = 0.9324f,
        woodTopFrac = 0.0392f, woodInset = 0.03504f,
        accentAspect = 945f / 306f, accentWFrac = 0.6019f,
        frameShadow = 0x4D2E241C
    )

    // 深海鲸歌：白蓝卡纸 + 木框深海日光海面 + 鲸影（设计稿 1509×1126）
    private val DEEP_SEA_SPEC = FramedCardSpec(
        frameAsset = "deepsea_frame", accentAsset = "deepsea_whale",
        frameAspect = 1280f / 664f, frameWFrac = 0.9417f,
        woodTopFrac = 0.0293f, woodInset = 0.03523f,
        accentAspect = 915f / 405f, accentWFrac = 0.6064f,
        frameShadow = 0x452A3648
    )

    // 夏天的海：浅灰蓝卡纸 + 木框林间海径 + 浪花绿叶（设计稿 1508×1162）
    private val SUMMER_SEA_SPEC = FramedCardSpec(
        frameAsset = "summersea_frame", accentAsset = "summersea_accent",
        frameAspect = 1280f / 677f, frameWFrac = 0.9463f,
        woodTopFrac = 0.0508f, woodInset = 0.03444f,
        accentAspect = 875f / 396f, accentWFrac = 0.5802f,
        frameShadow = 0x43291F15
    )

    // 夏日荷花：淡紫卡纸 + 木框荷塘 + 荷影（设计稿 1512×1161）
    private val SUMMER_LOTUS_SPEC = FramedCardSpec(
        frameAsset = "lotus_frame", accentAsset = "lotus_accent",
        frameAspect = 1280f / 674f, frameWFrac = 0.9444f,
        woodTopFrac = 0.0500f, woodInset = 0.03453f,
        accentAspect = 938f / 398f, accentWFrac = 0.6204f,
        frameShadow = 0x42282838
    )

    private const val FRAMED_CARD_TEXT_MIN_HEIGHT_DP = 24f   // 正文最小净高（扁组件保底一行）

    /** 画框卡片的家族成员 → 比例参数。非画框形状不应调用。 */
    fun specFor(shape: WidgetShape): FramedCardSpec = when (shape) {
        WidgetShape.WINTER_PALACE -> WINTER_PALACE_SPEC
        WidgetShape.DEEP_SEA -> DEEP_SEA_SPEC
        WidgetShape.SUMMER_SEA -> SUMMER_SEA_SPEC
        else -> SUMMER_LOTUS_SPEC
    }

    class FramedCardLayout(
        val frameRect: RectF,
        val accentRect: RectF,
        val textRect: RectF
    )

    /**
     * 相框按真实比例摆上方（宽随组件，超高时按"正文最小净高"收缩并保持居中）；
     * 点缀层贴右下角（宽随组件，与相框重叠时让位收缩）；文字落在相框下方的整幅
     * 留白带（梅枝/鲸影虚影极淡，文字直接压上去）。全部随组件比例自适应。
     */
    fun framedCardRects(outerRect: RectF, densityScale: Float, spec: FramedCardSpec): FramedCardLayout {
        val w = outerRect.width()
        val h = outerRect.height()

        // —— 相框：宽随组件，超高时为正文让位收缩 ——
        var frameW = w * spec.frameWFrac
        var frameH = frameW / spec.frameAspect
        val frameTopBase = maxOf(4f * densityScale, h * spec.woodTopFrac - spec.woodInset * frameH)
        val maxFrameH = h - frameTopBase - FRAMED_CARD_TEXT_MIN_HEIGHT_DP * densityScale - 4f * densityScale
        if (frameH > maxFrameH) {
            frameH = maxFrameH.coerceAtLeast(10f * densityScale)
            frameW = frameH * spec.frameAspect
        }
        // 收缩后木框内缩量随补丁变小，顶沿要按最终尺寸重算
        val frameTop = maxOf(4f * densityScale, h * spec.woodTopFrac - spec.woodInset * frameH)
        val frameLeft = outerRect.left + (w - frameW) / 2f
        val frameRect = RectF(frameLeft, frameTop, frameLeft + frameW, frameTop + frameH)

        // —— 点缀层：贴右下角，与相框重叠时让位收缩 ——
        var accentW = w * spec.accentWFrac
        var accentH = accentW / spec.accentAspect
        val maxAccentH = h - frameRect.bottom - 2f * densityScale
        if (accentH > maxAccentH) {
            accentH = maxAccentH.coerceAtLeast(8f * densityScale)
            accentW = accentH * spec.accentAspect
        }
        val accentRect = RectF(outerRect.right - accentW, outerRect.bottom - accentH, outerRect.right, outerRect.bottom)

        // —— 文字：相框下方的整幅留白带。点缀层的浓墨部分只在最右下角，
        // 虚影极淡，文字压上去不影响可读（用户确认不再避让） ——
        val textLeft = outerRect.left + maxOf(14f * densityScale, w * 0.05f)
        val textRight = outerRect.right - maxOf(12f * densityScale, w * 0.035f)
        val textTop = frameRect.bottom + 3f * densityScale
        val textBottom = outerRect.bottom - maxOf(5f * densityScale, h * 0.025f)
        val textRect = RectF(
            textLeft,
            minOf(textTop, textBottom - 18f * densityScale),
            textRight,
            textBottom
        )
        return FramedCardLayout(frameRect, accentRect, textRect)
    }

    /** 画框卡片：卡纸已由背景色铺好，这里叠相框（带投影）与点缀层两块抠图。 */
    fun drawFramedCard(
        canvas: Canvas,
        context: Context,
        outerPath: Path,
        spec: FramedCardSpec,
        layout: FramedCardLayout,
        targetWidth: Int,
        targetHeight: Int,
        densityScale: Float,
        alpha: Int
    ) {
        val frame = getPresetImage(context, spec.frameAsset, targetWidth, targetHeight)
        val accent = getPresetImage(context, spec.accentAsset, targetWidth, targetHeight)

        val saveCount = canvas.save()
        canvas.clipPath(outerPath)

        // 相框投影：木框压在卡纸上的落影，向下柔散；补丁的羽化卡纸边会盖住投影内侧。
        // 画笔透明=只落投影不画形状，避免在羽化边下垫出异色
        if (frame != null && !frame.isRecycled) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.TRANSPARENT
                setShadowLayer(9f * densityScale, 0f, 5f * densityScale, spec.frameShadow)
            }
            canvas.drawRect(layout.frameRect, shadowPaint)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { this.alpha = alpha }
            canvas.drawBitmap(frame, null, layout.frameRect, paint)
        }

        if (accent != null && !accent.isRecycled) {
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { this.alpha = alpha }
            canvas.drawBitmap(accent, null, layout.accentRect, paint)
        }

        canvas.restoreToCount(saveCount)
    }
}
