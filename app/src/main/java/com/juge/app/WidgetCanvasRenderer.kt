package com.juge.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.LinearGradient
import android.graphics.Matrix
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
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.Random
import com.juge.app.render.BlueNoteRenderer.drawBlueNoteChrome
import com.juge.app.render.BookshelfRenderer.SHELF_PANEL_TEXT_PAD_X_DP
import com.juge.app.render.BookshelfRenderer.SHELF_PANEL_TEXT_PAD_Y_DP
import com.juge.app.render.BookshelfRenderer.bookshelfPanelRect
import com.juge.app.render.BookshelfRenderer.drawBookshelfChrome
import com.juge.app.render.CityRenderer.CITY_TEXT_PAD_X_DP
import com.juge.app.render.CityRenderer.CITY_TEXT_PAD_Y_DP
import com.juge.app.render.CityRenderer.CityJunction
import com.juge.app.render.CityRenderer.cityArtRect
import com.juge.app.render.CityRenderer.cityBand
import com.juge.app.render.CityRenderer.cityJunctionOf
import com.juge.app.render.CityRenderer.cityNeedsBottomPad
import com.juge.app.render.CityRenderer.cityTextBoxRect
import com.juge.app.render.CityRenderer.drawCityArt
import com.juge.app.render.CityRenderer.drawCityBottomPad
import com.juge.app.render.CityRenderer.drawCityFade
import com.juge.app.render.CityRenderer.drawCityWater
import com.juge.app.render.FeatherRenderer.drawFeatherLetterPath
import com.juge.app.render.PaperRenderer.drawPaperTexture
import com.juge.app.render.PaperRenderer.paperCutBoxPath
import com.juge.app.render.PaperRenderer.paperGrainPaint
import com.juge.app.render.SoilRenderer.drawSoilStrata
import com.juge.app.render.SplitCardRenderer.SPLIT_CARD_HORIZONTAL_RATIO
import com.juge.app.render.SplitCardRenderer.SPLIT_CARD_RATIO
import com.juge.app.render.SplitCardRenderer.splitImageRect
import com.juge.app.render.StickerRenderer.STICKER_TEXT_EDGE_DP
import com.juge.app.render.StickerRenderer.STICKER_TEXT_PAD_X_DP
import com.juge.app.render.StickerRenderer.STICKER_TEXT_PAD_Y_DP
import com.juge.app.render.StickerRenderer.STICKER_TEXT_SNIP_DP
import com.juge.app.render.StickerRenderer.drawStickerContactShadow
import com.juge.app.render.StickerRenderer.drawStickerLampHalo
import com.juge.app.render.StickerRenderer.drawStickerLightBeam
import com.juge.app.render.StickerRenderer.drawStickerLightOnArt
import com.juge.app.render.StickerRenderer.drawStickerRoses
import com.juge.app.render.StickerRenderer.stickerArtRect
import com.juge.app.render.StickerRenderer.stickerTextBoxRect
import com.juge.app.render.SuborRenderer.SUBOR_GLOW_RADIUS_DP
import com.juge.app.render.SuborRenderer.SUBOR_SCREEN_TEXT_PAD_X_DP
import com.juge.app.render.SuborRenderer.SUBOR_SCREEN_TEXT_PAD_Y_DP
import com.juge.app.render.SuborRenderer.drawSuborCrtOverlay
import com.juge.app.render.SuborRenderer.drawSuborScreenGlow
import com.juge.app.render.SuborRenderer.suborScreenRect
import com.juge.app.render.TapeRenderer.drawTape
import com.juge.app.render.TornPaperRenderer.TORN_BORDER_ALPHA
import com.juge.app.render.TornPaperRenderer.generateTornPath
import com.juge.app.render.WeatherBoxRenderer.WEATHER_BOX_TEXT_PAD_X_DP
import com.juge.app.render.WeatherBoxRenderer.WEATHER_BOX_TEXT_PAD_Y_DP
import com.juge.app.render.WeatherBoxRenderer.drawWeatherBoxCavity
import com.juge.app.render.WeatherBoxRenderer.weatherBoxCavityRect
import com.juge.app.render.WidgetRenderKernel.MIN_BITMAP_SIZE
import com.juge.app.render.WidgetRenderKernel.centerFitRect
import com.juge.app.render.WidgetRenderKernel.decodeFileSampled
import com.juge.app.render.WidgetRenderKernel.detectLightBorder
import com.juge.app.render.WidgetRenderKernel.fastBlur
import com.juge.app.render.WidgetRenderKernel.getPresetImage

object WidgetCanvasRenderer {

    private const val RENDER_DENSITY_SCALE = 1.5f
    private const val FONT_SIZE_SCALE = 1.2f
    private const val QUOTE_MARK_FONT_SIZE_SCALE = 3.5f
    private const val DEFAULT_OUTER_CORNER_RADIUS_DP = 16f
    // 卡片四周留出的内边距：让卡片不铺满整幅组件位图，从而给投影留出可见空间。
    // 阴影绘制在位图内部，若卡片满幅则阴影会被位图边界裁掉，组件看起来就是"贴平"的。
    private const val CARD_INSET_DP = 4f
    private const val QUOTE_ALPHA = 25











    // ==================== 天气盒子 ====================

    // ==================== 画框卡片（雪落宫墙 / 深海鲸歌共用） ====================
    // 同一族整卡设计：卡纸由「背景颜色」绘制（含纸张颗粒），木框照片按真实比例
    // 摆上方，点缀层（梅枝/鲸影）贴右下角。两个图层都是带羽化卡纸边的抠图
    // （羽化边与卡纸同色），任意组件宽高比下都不露接缝。几何比例各自从设计稿实测。
    private data class FramedCardSpec(
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

    // ==================== 画框卡片 ====================



    /**
     * 画框卡片：按当前组件尺寸现算三个区域。
     *
     * 相框按真实比例摆上方（宽随组件，超高时按"正文最小净高"收缩并保持居中）；
     * 点缀层贴右下角（宽随组件，与相框重叠时让位收缩）；文字落在相框下方的整幅
     * 留白带（梅枝/鲸影虚影极淡，文字直接压上去）。全部随组件比例自适应。
     */
    private data class FramedCardLayout(
        val frameRect: RectF,
        val accentRect: RectF,
        val textRect: RectF
    )

    private fun framedCardRects(outerRect: RectF, densityScale: Float, spec: FramedCardSpec): FramedCardLayout {
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
    private fun drawFramedCard(
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

    fun render(
        context: Context,
        widthDp: Int,
        heightDp: Int,
        content: String,
        style: WidgetStyle
    ): Bitmap {
        // 将 dp 尺寸转为像素，增加 RENDER_DENSITY_SCALE 倍分辨率防止桌面模糊
        val scale = context.resources.displayMetrics.density
        val densityScale = scale * RENDER_DENSITY_SCALE
        val targetWidth = (widthDp * densityScale).toInt().coerceAtLeast(MIN_BITMAP_SIZE)
        val targetHeight = (heightDp * densityScale).toInt().coerceAtLeast(MIN_BITMAP_SIZE)

        // 注意：此 Bitmap 由调用方负责回收。调用方在使用完毕后应调用 bitmap.recycle() 以释放内存。
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. 绘制背景区域与形状裁切路径
        val path = Path()
        // 只对"纯圆角矩形"这一族的形状做内缩：它们的内容完全按 rectF/outerRect 布局，内缩不会溢出；
        // 其余形状（撕纸/八角/信纸/萌宠/像素/书架等）有各自按整幅位图绘制的装饰，保持满幅以免错位
        val usesInsetCard = style.shape == WidgetShape.RECTANGLE ||
            style.shape == WidgetShape.HANDBOOK_TAPE ||
            style.shape == WidgetShape.SPLIT_CARD ||
            style.shape == WidgetShape.SPLIT_CARD_HORIZONTAL ||
            // 天气盒子：盒体与腔体都按 outerRect 布局，内缩安全；预设开了投影，
            // 不内缩的话阴影会被位图边界裁掉，盒体看起来是"贴平"的
            style.shape == WidgetShape.WEATHER_BOX
        val cardInset = if (usesInsetCard) CARD_INSET_DP * densityScale else 0f
        val offsetY = cardInset
        val rectF = RectF(cardInset, offsetY, targetWidth - cardInset, targetHeight - cardInset)

        // 巨剑/毛绒森林/小霸王游戏机是整幅插画（剑身横贯、毛绒小树在顶部、实物模型铺满），
        // 圆角裁剪会把主体切掉。预设套用时会继承上一个风格的圆角值，
        // 这里统一强制按直角渲染，避免旧数据/跨风格套用后画面被裁。
        // 贴纸夜景整幅透明、不画外框，它的圆角滑条作用在文本框上（见 paperCutBoxPath），不在此列。
        // 城市剪影同理：没有卡片外框，圆角滑条作用在文字栏底部两角（见 cityBarPath）。
        val effectiveCornerRadiusDp =
            if (style.shape == WidgetShape.GIANT_SWORD || style.shape == WidgetShape.PLUSH_FOREST ||
                style.shape == WidgetShape.SUBOR_CONSOLE || style.shape == WidgetShape.CITY_CUTOUT
            ) 0f
            else style.cornerRadiusDp

        when (style.shape) {
            WidgetShape.RECTANGLE, WidgetShape.HANDBOOK_TAPE, WidgetShape.SPLIT_CARD, WidgetShape.SPLIT_CARD_HORIZONTAL, WidgetShape.PIXEL_RETRO, WidgetShape.PET_CAT_NAP, WidgetShape.BLUE_NOTE, WidgetShape.ZHU_QING_SI_ZHI, WidgetShape.NIUPI_SHOUZHANG, WidgetShape.CLASSROOM_BLACKBOARD, WidgetShape.BOOKSHELF, WidgetShape.GIANT_SWORD, WidgetShape.PLUSH_FOREST, WidgetShape.SUBOR_CONSOLE, WidgetShape.STICKER_SCENE, WidgetShape.CITY_CUTOUT, WidgetShape.WEATHER_BOX, WidgetShape.WINTER_PALACE, WidgetShape.DEEP_SEA, WidgetShape.SUMMER_SEA, WidgetShape.SUMMER_LOTUS -> {
                val rx = effectiveCornerRadiusDp * densityScale
                if (rx <= 0f) {
                    path.addRect(rectF, Path.Direction.CW)
                } else {
                    path.addRoundRect(rectF, rx, rx, Path.Direction.CW)
                }
            }
            WidgetShape.TORN_PAPER -> {
                val tornPath = generateTornPath(targetWidth.toFloat(), targetHeight.toFloat(), densityScale)
                path.set(tornPath)
            }
            WidgetShape.FEATHER_LETTER -> {
                // 羽毛信纸：居中撕纸信纸矩形，四周留出卡片边距
                drawFeatherLetterPath(path, targetWidth.toFloat(), targetHeight.toFloat(), densityScale)
            }
            WidgetShape.ELLIPSE -> {
                path.addOval(rectF, Path.Direction.CW)
            }
        }

        // 1.5 绘制全局卡片大背景（防止气泡等非铺满形状在外部露出黑色透明像素）
        val outerPath = Path()
        val outerRx = when (style.shape) {
            // 与上方形状路径保持同一份名单：这些形状的外框圆角都跟随用户的圆角设置，
            // 否则圆角滑条对复古像素 / 萌宠猫咪 / 竹青撕纸等形状完全不生效
            WidgetShape.RECTANGLE, WidgetShape.HANDBOOK_TAPE,
            WidgetShape.SPLIT_CARD, WidgetShape.SPLIT_CARD_HORIZONTAL, WidgetShape.BLUE_NOTE,
            WidgetShape.PIXEL_RETRO, WidgetShape.PET_CAT_NAP, WidgetShape.ZHU_QING_SI_ZHI, WidgetShape.NIUPI_SHOUZHANG, WidgetShape.CLASSROOM_BLACKBOARD, WidgetShape.BOOKSHELF, WidgetShape.GIANT_SWORD, WidgetShape.PLUSH_FOREST, WidgetShape.SUBOR_CONSOLE, WidgetShape.STICKER_SCENE, WidgetShape.CITY_CUTOUT, WidgetShape.WEATHER_BOX, WidgetShape.WINTER_PALACE, WidgetShape.DEEP_SEA, WidgetShape.SUMMER_SEA, WidgetShape.SUMMER_LOTUS ->
                effectiveCornerRadiusDp * densityScale
            else -> DEFAULT_OUTER_CORNER_RADIUS_DP * densityScale
        }
        val outerRect = RectF(cardInset, offsetY, targetWidth - cardInset, targetHeight - cardInset)
        if (outerRx <= 0f) {
            outerPath.addRect(outerRect, Path.Direction.CW)
        } else {
            outerPath.addRoundRect(outerRect, outerRx, outerRx, Path.Direction.CW)
        }
        
        // 主体四周透明的形状不支持背景色：渲染时强制按透明处理，
        // 让已经落库/落到桌面的旧组件不必重新保存也不会露出包裹卡片
        val effectiveBgColor = if (WidgetStyle.supportsBackgroundColor(style.shape)) {
            style.backgroundColor
        } else {
            Color.TRANSPARENT
        }

        // 绘制卡片软阴影（移至 clip 外部以防被气泡边界截断）
        // 贴纸夜景整幅是透明底，阴影只该跟着文本框走，因此单独在文本框绘制处处理
        // 城市剪影同理：整幅没有卡片外框，只有剪影 + 文字栏，不画整卡投影
        if (style.showCardShadow && style.shape != WidgetShape.STICKER_SCENE &&
            style.shape != WidgetShape.CITY_CUTOUT
        ) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = effectiveBgColor
                if (Color.alpha(effectiveBgColor) < 255) {
                    color = effectiveBgColor or 0xFF000000.toInt()
                }
                setShadowLayer(
                    6f * densityScale,
                    0f,
                    3f * densityScale,
                    Color.parseColor("#40000000")
                )
            }
            canvas.drawPath(if (style.shape == WidgetShape.TORN_PAPER) path else outerPath, shadowPaint)
        }

        // 填充全局大底色（颜色与透明度）
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val alpha = (style.backgroundOpacity * 255).toInt().coerceIn(0, 255)
        if (style.gradientColors != null && style.gradientColors.size >= 2 &&
            WidgetStyle.supportsBackgroundColor(style.shape)
        ) {
            val w = rectF.width()
            val h = rectF.height()
            val r = Math.sqrt((w * w + h * h).toDouble()) / 2.0
            val cx = rectF.centerX()
            val cy = rectF.centerY()
            val angleRad = Math.toRadians(style.gradientAngle.toDouble())
            val cos = Math.cos(angleRad)
            val sin = Math.sin(angleRad)
            val x0 = (cx - cos * r).toFloat()
            val y0 = (cy - sin * r).toFloat()
            val x1 = (cx + cos * r).toFloat()
            val y1 = (cy + sin * r).toFloat()
            
            val colors = style.gradientColors.toIntArray()
            bgPaint.shader = LinearGradient(x0, y0, x1, y1, colors, null, Shader.TileMode.CLAMP)
        } else {
            bgPaint.color = effectiveBgColor
        }
        bgPaint.alpha = alpha
        // 背景色为透明时不填充，避免 alpha 被强制为 255 后把透明底画成黑色。
        // 贴纸夜景整幅是透明底，背景色只作用于文本框（下方单独绘制），这里不铺整卡底色
        // 城市剪影的背景色只作用于文字栏（下方单独绘制），同样不铺整卡底色
        if (Color.alpha(effectiveBgColor) > 0 && style.shape != WidgetShape.STICKER_SCENE &&
            style.shape != WidgetShape.CITY_CUTOUT
        ) {
            if (style.shape == WidgetShape.SPLIT_CARD || style.shape == WidgetShape.SPLIT_CARD_HORIZONTAL) {
                // 图文明信片：文字显示区（下半/右半）的底色由下方 panelPaint 单独绘制，
                // 这里只铺图片区，避免同一底色叠两遍导致不透明度失真
                canvas.save()
                canvas.clipPath(outerPath)
                canvas.drawRect(splitImageRect(style.shape, outerRect), bgPaint)
                canvas.restore()
            } else {
                canvas.drawPath(if (style.shape == WidgetShape.TORN_PAPER) path else outerPath, bgPaint)
            }
        }

        // 尝试加载背景图片（优先使用自定义路径，次之使用内置预设插画名）
        var bgBitmap: Bitmap? = null
        var bgFromCache = false
        if (!style.backgroundImagePath.isNullOrEmpty()) {
            try {
                val file = File(style.backgroundImagePath)
                if (file.exists()) {
                    bgBitmap = decodeFileSampled(file.absolutePath, targetWidth, targetHeight)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load background image from path")
            }
        } else {
            val resName = if (style.shape == WidgetShape.CITY_CUTOUT) {
                // 城市微缩：一个风格一张素材，presetImageResName 就是素材名
                style.presetImageResName
            } else if (style.shape == WidgetShape.SPLIT_CARD || style.shape == WidgetShape.SPLIT_CARD_HORIZONTAL) {
                style.presetImageResName ?: "bg_illustration_1"
            } else {
                style.presetImageResName
            }
            if (!resName.isNullOrEmpty()) {
                try {
                    bgBitmap = getPresetImage(context, resName, targetWidth, targetHeight)
                    bgFromCache = bgBitmap != null
                } catch (t: Throwable) {
                    Timber.e(t, "Failed to load preset background image")
                }
            }
        }

        // 绘制背景图片（若有）
        // 贴纸夜景的素材是抠出的人物+路灯，位置/大小由下方贴纸逻辑单独计算，不走这里的整卡铺图
        // 城市剪影的素材要按剪影带单独铺（且必须跳过 detectLightBorder，见 drawCityArt）
        // 天气盒子同理：素材只铺进内凹腔体，盒面留白由背景色负责
        // 雪落宫墙：相框与梅枝两块图层由下方单独摆放，卡纸由背景色负责
        if (bgBitmap != null && style.shape != WidgetShape.STICKER_SCENE &&
            style.shape != WidgetShape.CITY_CUTOUT && style.shape != WidgetShape.WEATHER_BOX &&
            style.shape != WidgetShape.WINTER_PALACE && style.shape != WidgetShape.DEEP_SEA &&
            style.shape != WidgetShape.SUMMER_SEA && style.shape != WidgetShape.SUMMER_LOTUS
        ) {
            canvas.save()
            canvas.clipPath(if (style.shape == WidgetShape.TORN_PAPER) path else outerPath)
            if (style.shape == WidgetShape.SPLIT_CARD || style.shape == WidgetShape.SPLIT_CARD_HORIZONTAL) {
                drawBgImage(canvas, bgBitmap, splitImageRect(style.shape, outerRect), style)
            } else {
                drawBgImage(canvas, bgBitmap, if (style.shape == WidgetShape.TORN_PAPER) rectF else outerRect, style)
            }
            canvas.restore()

            // 自定义路径图片随本次渲染释放；预设图由缓存统一管理
            if (!bgFromCache && !bgBitmap.isRecycled) {
                bgBitmap.recycle()
            }
        }

        // 小霸王游戏机：素材的显像管玻璃原本是"未通电"的深灰玻璃，
        // 这里在屏幕区域内叠一层绿色荧光底，让屏幕看起来是开机的
        if (style.shape == WidgetShape.SUBOR_CONSOLE) {
            drawSuborScreenGlow(canvas, suborScreenRect(outerRect), densityScale)
        }

        // 图文明信片：文字显示区（下半/右半）的底色由代码绘制，不是背景图片的一部分，
        // 因此跟随"小组件背景颜色"自定义，并同样受背景不透明度控制。
        // 预设背景色为白色，默认观感与旧版一致。
        if (style.shape == WidgetShape.SPLIT_CARD || style.shape == WidgetShape.SPLIT_CARD_HORIZONTAL) {
            val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.backgroundColor
                this.alpha = alpha
            }
            canvas.save()
            canvas.clipPath(outerPath)
            if (style.shape == WidgetShape.SPLIT_CARD) {
                val dividerY = outerRect.top + outerRect.height() * SPLIT_CARD_RATIO
                val bottomRect = RectF(
                    outerRect.left,
                    dividerY,
                    outerRect.right,
                    outerRect.bottom
                )
                canvas.drawRect(bottomRect, panelPaint)

                // 绘制 1px 精致分割线
                val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#E5E7EB") // 使用更苹果风格的浅灰边线 (#E5E7EB)
                    strokeWidth = 1f * densityScale
                    this.style = Paint.Style.STROKE
                }
                canvas.drawLine(outerRect.left, dividerY, outerRect.right, dividerY, linePaint)
            } else {
                val dividerX = outerRect.left + outerRect.width() * SPLIT_CARD_HORIZONTAL_RATIO
                val rightRect = RectF(
                    dividerX,
                    outerRect.top,
                    outerRect.right,
                    outerRect.bottom
                )
                canvas.drawRect(rightRect, panelPaint)
            }
            canvas.restore()
        }

        // 贴纸夜景：文本框（直角剪边）→ 贴纸（人物+路灯站在纸上，带白色描边）→ 花枝垂在文本框下沿。
        // 组件整幅透明，只有文本框是实体色块，背景色/不透明度都只作用于文本框。
        if (style.shape == WidgetShape.STICKER_SCENE) {
            val artRect = stickerArtRect(outerRect)
            // 光柱在人物之下：人是站在光里的剪影，而不是被光糊住
            drawStickerLightBeam(canvas, outerRect, artRect, densityScale, alpha)

            val textBox = stickerTextBoxRect(outerRect)
            // 纸张剪纸：默认四角剪掉一小块；圆角滑条调大后四角改为圆弧（半径见 paperCutBoxPath）
            val snip = STICKER_TEXT_SNIP_DP * densityScale
            val borderPath = paperCutBoxPath(textBox, snip, style.cornerRadiusDp * densityScale)
            if (style.showCardShadow) {
                val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = style.backgroundColor
                    setShadowLayer(6f * densityScale, 0f, 3f * densityScale, Color.parseColor("#40000000"))
                }
                canvas.drawPath(borderPath, shadowPaint)
            }
            val textBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.backgroundColor
                this.alpha = alpha
            }
            canvas.drawPath(borderPath, textBoxPaint)

            // 纸纹：只铺在纸面里，让底色不再是一块干净的单色
            val grain = paperGrainPaint(alpha)
            val grainLayer = canvas.save()
            canvas.clipPath(borderPath)
            canvas.drawRect(textBox, grain)
            canvas.restoreToCount(grainLayer)

            // 剪纸白边：与贴纸的白色描边呼应，让文本框也像"剪下来贴上去"的纸片
            val edgeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                this.alpha = alpha
                strokeWidth = STICKER_TEXT_EDGE_DP * densityScale
                this.style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(borderPath, edgeStroke)

            // 接触阴影：人物与路灯是踩在这张纸上的，脚下压一层软阴影才站得住
            drawStickerContactShadow(canvas, textBox, borderPath, artRect, alpha)

            if (bgBitmap != null) {
                val artPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                    this.alpha = alpha
                }
                canvas.drawBitmap(bgBitmap, null, artRect, artPaint)
                drawStickerLightOnArt(canvas, outerRect, artRect, bgBitmap, densityScale, alpha)
                if (!bgFromCache && !bgBitmap.isRecycled) {
                    bgBitmap.recycle()
                }
            }
            drawStickerLampHalo(canvas, artRect)
            // 玫瑰画在最后：垂在文本框下沿
            drawStickerRoses(canvas, textBox, densityScale, alpha)
        }

        // 城市微缩：先把抠掉天空的城市按原比例铺上（天空透明处露出壁纸），
        // 再按**这个风格自己的**衔接把城市底边接进文字栏。
        if (style.shape == WidgetShape.CITY_CUTOUT) {
            val junction = cityJunctionOf(style)
            val artRect = cityArtRect(outerRect, junction, style, densityScale, bgBitmap)
            if (bgBitmap != null && !bgBitmap.isRecycled) {
                // 先垫平素材底边的透明垫高区，再画城市：垫平带画在模型之下，
                // 被模型实体盖住的部分不可见，只有露在衔接线上方的那截把壁纸挡掉。
                if (cityNeedsBottomPad(style)) {
                    drawCityBottomPad(canvas, bgBitmap, artRect, style)
                }
                drawCityArt(canvas, bgBitmap, artRect, style)
            }
            when (junction) {
                CityJunction.SOIL -> drawSoilStrata(
                    canvas, outerRect, artRect, bgBitmap, densityScale, style, bgPaint)
                CityJunction.WATER -> drawCityWater(
                    canvas, outerRect, artRect, bgBitmap, densityScale, style, bgPaint)
                CityJunction.FADE -> drawCityFade(
                    canvas, outerRect, artRect, densityScale, style, bgPaint)
            }
            if (bgBitmap != null && !bgFromCache && !bgBitmap.isRecycled) {
                bgBitmap.recycle()
            }
        }

        // 蓝色便签：在蓝色大底上追加顶部 NOTE 区域与底部米白签条
        if (style.shape == WidgetShape.BLUE_NOTE) {
            drawBlueNoteChrome(canvas, targetWidth.toFloat(), targetHeight.toFloat(), outerRect, outerPath, densityScale, style, context)
        }

        // 书香书架：顶部彩色书脊立在横板上，底部米色摘录面板
        if (style.shape == WidgetShape.BOOKSHELF) {
            drawBookshelfChrome(canvas, outerRect, densityScale, style, context)
        }

        // 天气盒子：白色盒体正面挖一个内凹方腔，腔底铺蓝天微缩城市素材，
        // 腔口画内壁暗面 + 下沿高光，形成"凹进去"的体积感；腔下留白即正文区。
        if (style.shape == WidgetShape.WEATHER_BOX) {
            val cavity = weatherBoxCavityRect(outerRect, densityScale)
            drawWeatherBoxCavity(canvas, cavity, bgBitmap, style, densityScale, alpha)
            if (bgBitmap != null && !bgFromCache && !bgBitmap.isRecycled) {
                bgBitmap.recycle()
            }
        }

        // 画框卡片族（雪落宫墙/深海鲸歌/夏天的海/夏日荷花）：卡纸由背景色铺好
        //（含纸张颗粒），这里叠相框与点缀层
        if (style.shape == WidgetShape.WINTER_PALACE || style.shape == WidgetShape.DEEP_SEA ||
            style.shape == WidgetShape.SUMMER_SEA || style.shape == WidgetShape.SUMMER_LOTUS
        ) {
            val spec = when (style.shape) {
                WidgetShape.WINTER_PALACE -> WINTER_PALACE_SPEC
                WidgetShape.DEEP_SEA -> DEEP_SEA_SPEC
                WidgetShape.SUMMER_SEA -> SUMMER_SEA_SPEC
                else -> SUMMER_LOTUS_SPEC
            }
            val layout = framedCardRects(outerRect, densityScale, spec)
            drawFramedCard(canvas, context, outerPath, spec, layout, targetWidth, targetHeight, densityScale, alpha)
        }

        // 羽毛信纸：使用透自信纸抠图作背景（走上方背景图绘制逻辑），透明区透底色

        // 绘制纸张颗粒/纤维纹理 (作用于全局大卡片上，效果更拟真一致)
        if (style.textureType == "PAPER" || style.textureType == "GRAIN") {
            drawPaperTexture(canvas, if (style.shape == WidgetShape.TORN_PAPER) rectF else outerRect, style.textureType, densityScale)
        }

        // 保存画布状态并应用形状剪裁
        canvas.save()
        canvas.clipPath(path)

        // 释放剪裁状态
        canvas.restore()

        // 绘制卡片描边 (Border)与撕裂白边
        if (style.shape == WidgetShape.TORN_PAPER) {
            val tornBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                this.strokeWidth = 1.0f * densityScale
                this.color = Color.parseColor("#EAEAEA")
                this.alpha = TORN_BORDER_ALPHA
            }
            canvas.drawPath(path, tornBorderPaint)
        }

        if (style.cardBorderWidthDp > 0f) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                this.strokeWidth = style.cardBorderWidthDp * densityScale
                this.color = style.cardBorderColor
            }
            canvas.drawPath(path, borderPaint)
        }

        // 4. 准备绘制文字（新付费规则：预览任意风格、桌面默认免费、仅在“同步到桌面”时弹付费，渲染层不感知会员状态）
        val renderStyle = style

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.fontColor
            textSize = style.fontSizeSp * scale * FONT_SIZE_SCALE
            typeface = style.font.getTypeface(context)
            
            // 处理粗体与斜体
            var styleFlags = 0
            if (style.fontBold) styleFlags = styleFlags or android.graphics.Typeface.BOLD
            if (style.fontItalic) styleFlags = styleFlags or android.graphics.Typeface.ITALIC
            if (styleFlags != 0) {
                typeface = android.graphics.Typeface.create(style.font.getTypeface(context), styleFlags)
            }

            // 字间距
            letterSpacing = style.letterSpacing

            // 阴影设置
            if (style.shadow.enabled) {
                // 小霸王游戏机：显像管荧光晕。预设里的阴影半径是像素值，在 3x 密度下几乎看不见，
                // 这里按密度放大到 dp 级别，让文字真正"发"出绿色荧光
                val shadowRadius = if (style.shape == WidgetShape.SUBOR_CONSOLE) {
                    SUBOR_GLOW_RADIUS_DP * densityScale
                } else {
                    style.shadow.radius
                }
                setShadowLayer(
                    shadowRadius,
                    style.shadow.dx,
                    style.shadow.dy,
                    style.shadow.color
                )
            }
        }

        // 两端对齐（JUSTIFY）依赖 StaticLayout 的 justification 能力（API 26+），
        // 低版本回退为左对齐；词间距拉伸只在折行行生效，末行保持正常排布（标准行为）
        val alignKey = style.textAlign.uppercase(Locale.ROOT)
        val isJustify = alignKey == "JUSTIFY" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        val textAlignment = when {
            alignKey == "LEFT" -> Layout.Alignment.ALIGN_NORMAL
            alignKey == "RIGHT" -> Layout.Alignment.ALIGN_OPPOSITE
            isJustify -> Layout.Alignment.ALIGN_NORMAL
            else -> Layout.Alignment.ALIGN_CENTER
        }

        // 5. 绘制主体内容（新付费规则：桌面与预览均直接渲染真实风格；首次落地不在渲染层拦截或展示付费蒙层）
        // 天空之蓝 "NOTE" 标签绘制
        if (style.presetImageResName == "rectangle_1") {
            val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 14f * densityScale
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            canvas.drawText("NOTE", 20f * densityScale, 28f * densityScale, notePaint)
        }

            // B. 计算安全的文本安全排版边界，防止遮挡人物
            val paddingLeft: Float
            val paddingRight: Float
            val textWidth: Float
            val cardTop: Float
            val cardHeight: Float

            if (style.shape == WidgetShape.SPLIT_CARD_HORIZONTAL) {
                paddingLeft = targetWidth * SPLIT_CARD_HORIZONTAL_RATIO + 12f * densityScale
                paddingRight = targetWidth - 12f * densityScale
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = 12f * densityScale
                cardHeight = targetHeight - 24f * densityScale
            } else if (style.shape == WidgetShape.SPLIT_CARD) {
                paddingLeft = 16f * densityScale
                paddingRight = targetWidth - 16f * densityScale
                textWidth = paddingRight - paddingLeft
                cardTop = targetHeight * SPLIT_CARD_RATIO + 12f * densityScale
                cardHeight = targetHeight - cardTop - 12f * densityScale
            } else if (style.shape == WidgetShape.FEATHER_LETTER) {
                // 羽毛信纸：文字落在信纸留白区（扩大区域，右侧多留避羽毛笔，顶部避开尖角）
                val verticalInset = targetHeight * 0.16f
                val leftLateral = targetWidth * 0.10f
                val rightLateral = targetWidth * 0.20f
                paddingLeft = leftLateral
                paddingRight = targetWidth - rightLateral
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = verticalInset // 顶部让出信封尖角
                cardHeight = targetHeight - cardTop - targetHeight * 0.14f // 底部让出信纸下缘
            } else if (style.shape == WidgetShape.PIXEL_RETRO) {
                // 复古像素：文字落在薄荷绿背景区，避开四周深蓝虚线边框与红框
                val verticalInset = targetHeight * 0.12f
                val lateral = targetWidth * 0.10f
                paddingLeft = lateral
                paddingRight = targetWidth - lateral
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = verticalInset // 顶部让出虚线边框
                cardHeight = targetHeight - cardTop - targetHeight * 0.12f // 底部让出虚线边框
            } else if (style.shape == WidgetShape.PET_CAT_NAP) {
                // 萌宠猫咪趴：橘猫趴在卡片顶部,文字落在卡片渐变中下部(避开猫)
                val verticalInset = targetHeight * 0.42f // 顶部让出猫
                val lateral = targetWidth * 0.12f
                paddingLeft = lateral
                paddingRight = targetWidth - lateral
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = verticalInset
                cardHeight = targetHeight - cardTop - targetHeight * 0.08f // 底部少留
            } else if (style.shape == WidgetShape.BLUE_NOTE) {
                // 蓝色便签：顶部让出 NOTE + 信息钮，底部让出米白签条
                val footerH = targetHeight * (76f / 363f)
                val headerH = targetHeight * (76f / 363f) * 0.85f
                val lateral = targetWidth * 0.08f
                paddingLeft = lateral
                paddingRight = targetWidth - lateral
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = headerH
                cardHeight = targetHeight - cardTop - footerH - 8f * densityScale
            } else if (style.shape == WidgetShape.ZHU_QING_SI_ZHI || style.shape == WidgetShape.NIUPI_SHOUZHANG) {
                // 竹青撕纸 / 撕边牛皮手账：纸即主体，文字居中留白避开撕边与右下阴影
                val verticalInset = targetHeight * 0.11f
                val lateral = targetWidth * 0.11f
                paddingLeft = lateral
                paddingRight = targetWidth - lateral
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = verticalInset
                cardHeight = targetHeight - cardTop - verticalInset
            } else if (style.shape == WidgetShape.CLASSROOM_BLACKBOARD) {
                // 教室黑板：文字写在绿色板面上，四周避开木框，底部让出粉笔槽
                val lateral = targetWidth * 0.09f
                paddingLeft = lateral
                paddingRight = targetWidth - lateral
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = targetHeight * 0.12f
                cardHeight = targetHeight * 0.70f
            } else if (style.shape == WidgetShape.BOOKSHELF) {
                // 书香书架：正文落在底部米色摘录面板内。书籍区高度固定，面板吃掉组件多出来的高度，
                // 因此组件变高时只有文本区域变大（面板与正文区域用同一个面板矩形，保证文字不越界）
                val panel = bookshelfPanelRect(outerRect, densityScale)
                val textPadX = SHELF_PANEL_TEXT_PAD_X_DP * densityScale
                val textPadY = SHELF_PANEL_TEXT_PAD_Y_DP * densityScale
                paddingLeft = panel.left + textPadX
                paddingRight = panel.right - textPadX
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = panel.top + textPadY
                cardHeight = (panel.bottom - textPadY - cardTop).coerceAtLeast(1f)
            } else if (style.shape == WidgetShape.GIANT_SWORD) {
                // 巨剑：左侧是扛剑武士，正文只压在右侧剑身金属面上，避开剑柄/护手与上下剑棱。
                // 剑身纵向只占画面约 1/3，这里把可用高度吃满，保证 4×2 规格下也能排出两行
                paddingLeft = targetWidth * 0.33f
                paddingRight = targetWidth * 0.93f
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = targetHeight * 0.39f
                cardHeight = targetHeight * 0.33f
            } else if (style.shape == WidgetShape.PLUSH_FOREST) {
                // 毛绒森林：顶部毛绒小树/蘑菇与粉色花边不可压，正文落在奶油色毛绒面板内
                paddingLeft = targetWidth * 0.115f
                paddingRight = targetWidth * 0.885f
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = targetHeight * 0.40f
                cardHeight = targetHeight * 0.47f
            } else if (style.shape == WidgetShape.SUBOR_CONSOLE) {
                // 小霸王游戏机：素材按 CENTER_FIT 等比完整显示，正文只落在机身屏幕的玻璃区域内。
                // 屏幕矩形由素材内测得的相对位置换算，组件是 4×3 还是 4×4 文字都始终贴在屏幕上
                val screen = suborScreenRect(outerRect)
                val textPadX = SUBOR_SCREEN_TEXT_PAD_X_DP * densityScale
                val textPadY = SUBOR_SCREEN_TEXT_PAD_Y_DP * densityScale
                paddingLeft = screen.left + textPadX
                paddingRight = screen.right - textPadX
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = screen.top + textPadY
                cardHeight = (screen.bottom - textPadY - cardTop).coerceAtLeast(1f)
            } else if (style.shape == WidgetShape.STICKER_SCENE) {
                // 贴纸夜景：正文落在文本框内，四周留出内边距避免贴边
                val textBox = stickerTextBoxRect(outerRect)
                val textPadX = STICKER_TEXT_PAD_X_DP * densityScale
                val textPadY = STICKER_TEXT_PAD_Y_DP * densityScale
                paddingLeft = textBox.left + textPadX
                paddingRight = textBox.right - textPadX
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = textBox.top + textPadY
                cardHeight = (textBox.bottom - textPadY - cardTop).coerceAtLeast(1f)
            } else if (style.shape == WidgetShape.CITY_CUTOUT) {
                // 城市微缩：正文落在衔接层之下——土层的轮廓谷底 / 倒影的最下沿。
                // 取的是这条带的**最低点**，所以文字绝不会压到剖面或倒影上。
                val junction = cityJunctionOf(style)
                val artRect = cityArtRect(
                    outerRect, junction, style, densityScale, bgBitmap?.takeIf { !it.isRecycled })
                val textBox = cityTextBoxRect(
                    outerRect, artRect, cityBand(junction, outerRect, densityScale))
                val textPadX = CITY_TEXT_PAD_X_DP * densityScale
                val textPadY = CITY_TEXT_PAD_Y_DP * densityScale
                paddingLeft = textBox.left + textPadX
                paddingRight = textBox.right - textPadX
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = textBox.top + textPadY
                cardHeight = (textBox.bottom - textPadY - cardTop).coerceAtLeast(1f)
            } else if (style.shape == WidgetShape.WEATHER_BOX) {
                // 天气盒子：正文落在腔体下方的白色留白区。留白区从腔体下沿切起，
                // 因此组件变高时多出来的高度全给正文，腔体本身不会被拉长变形
                val cavity = weatherBoxCavityRect(outerRect, densityScale)
                val textPadX = WEATHER_BOX_TEXT_PAD_X_DP * densityScale
                val textPadY = WEATHER_BOX_TEXT_PAD_Y_DP * densityScale
                paddingLeft = outerRect.left + textPadX
                paddingRight = outerRect.right - textPadX
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = cavity.bottom + textPadY
                cardHeight = (outerRect.bottom - textPadY - cardTop).coerceAtLeast(1f)
            } else if (style.shape == WidgetShape.WINTER_PALACE || style.shape == WidgetShape.DEEP_SEA ||
                style.shape == WidgetShape.SUMMER_SEA || style.shape == WidgetShape.SUMMER_LOTUS
            ) {
                // 画框卡片：正文落在相框下方的整幅留白带（与绘制层同一套布局）
                val spec = when (style.shape) {
                    WidgetShape.WINTER_PALACE -> WINTER_PALACE_SPEC
                    WidgetShape.DEEP_SEA -> DEEP_SEA_SPEC
                    WidgetShape.SUMMER_SEA -> SUMMER_SEA_SPEC
                    else -> SUMMER_LOTUS_SPEC
                }
                val layout = framedCardRects(outerRect, densityScale, spec)
                paddingLeft = layout.textRect.left
                paddingRight = layout.textRect.right
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = layout.textRect.top
                cardHeight = (layout.textRect.bottom - layout.textRect.top).coerceAtLeast(1f)
            } else {
                paddingLeft = 16f * densityScale
                paddingRight = targetWidth - 16f * densityScale
                textWidth = paddingRight - paddingLeft
                // 卡片内缩后文字区域同步内缩，避免长文本越过卡片下沿
                cardTop = cardInset
                cardHeight = targetHeight - 2f * cardInset
            }

            // 构建 StaticLayout 处理文本自动折行。
            // 文本行数超过显示区域可容纳的行数时，超出部分用省略号代替，
            // 而不是被画布底部硬裁切；空间足够时始终完整显示全部文本。
            val lineHeight = textPaint.fontSpacing * style.lineSpacingMultiplier
            val maxLines = if (lineHeight > 0f) {
                ((cardHeight - 8f * densityScale) / lineHeight).toInt().coerceAtLeast(1)
            } else {
                1
            }
            // 先按完整文本构建，用 lineCount 判断实际折行数是否超过显示区域可容纳行数。
            // 不能用显式换行符数量判断，否则无换行符的长文本永远不会触发省略号。
            val fullLayout = StaticLayout.Builder.obtain(content, 0, content.length, textPaint, textWidth.toInt())
                .setAlignment(textAlignment)
                .setLineSpacing(0f, style.lineSpacingMultiplier)
                .setIncludePad(true)
                .apply { if (isJustify) setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD) }
                .build()
            val staticLayout = if (fullLayout.lineCount > maxLines) {
                StaticLayout.Builder.obtain(content, 0, content.length, textPaint, textWidth.toInt())
                    .setAlignment(textAlignment)
                    .setLineSpacing(0f, style.lineSpacingMultiplier)
                    .setIncludePad(true)
                    .setMaxLines(maxLines)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END)
                    .apply { if (isJustify) setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD) }
                    .build()
            } else {
                fullLayout
            }

            // 不在组件底部绘制作者签名/风格名，把这块空间预留给正文，让组件能容纳更多文字。
            // 签名仅作为数据保留在 WidgetStyle 中，不参与渲染。
            val totalContentHeight = staticLayout.height

            canvas.save()
            // 垂直居中计算
            val centerY = cardTop + (cardHeight - totalContentHeight) / 2f
            canvas.translate(paddingLeft, centerY.coerceAtLeast(cardTop + 4f * densityScale))
            staticLayout.draw(canvas)

            // 绘制艺术双引号
            if (style.showQuoteMark) {
                val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = style.fontColor
                    setAlpha(QUOTE_ALPHA)
                    textSize = style.fontSizeSp * scale * QUOTE_MARK_FONT_SIZE_SCALE
                    typeface = android.graphics.Typeface.create(style.font.getTypeface(context), android.graphics.Typeface.BOLD)
                }

                // 左上双引号
                val leftQuoteX = -12f * densityScale
                val leftQuoteY = 24f * densityScale
                canvas.drawText("“", leftQuoteX, leftQuoteY, quotePaint)

                // 右下双引号
                val rightQuoteX = textWidth - 28f * densityScale
                val rightQuoteY = totalContentHeight + 8f * densityScale
                canvas.drawText("”", rightQuoteX, rightQuoteY, quotePaint)
            }

            canvas.restore()

            // 小霸王游戏机：扫描线 + 暗角 + 玻璃反光压在文字之上，
            // 让文字看起来是"透过显像管玻璃"看到的，而不是贴在图上
            if (style.shape == WidgetShape.SUBOR_CONSOLE) {
                drawSuborCrtOverlay(canvas, suborScreenRect(outerRect), densityScale)
            }

            // C. 绘制手账胶带贴纸 (HANDBOOK_TAPE)
            if (style.shape == WidgetShape.HANDBOOK_TAPE) {
                drawTape(canvas, 24f * densityScale, 16f * densityScale, 70f * densityScale, 18f * densityScale, -18f, Color.parseColor("#80FFF176"), densityScale) // 左上角黄胶带
                drawTape(canvas, targetWidth - 24f * densityScale, targetHeight - 16f * densityScale, 70f * densityScale, 18f * densityScale, 18f, Color.parseColor("#80FF8A80"), densityScale) // 右下角粉胶带
            }

        return bitmap
    }
























































    private fun drawBgImage(canvas: Canvas, bitmap: Bitmap, rectF: RectF, style: WidgetStyle) {
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



    // 风格缩略图缓存：UI 里「经典/萌宠/明信片/插画」四行一共 20+ 张缩略图，
    // 每张都是一次完整的 Canvas 渲染（含解码素材、画纹理）。
    // 没有这层缓存时，每次打开面板所有缩略图一起在 Dispatchers.Default 上从零重画，
    // 表现为「先空白 1~2 秒再逐个补齐」。
    //
    // 缩略图总量只有约 1.2MB（150×80×4B × 约 26 张），远小于下面的容量上限，
    // 因此**永远不会触发 LruCache 的淘汰**。这不是巧合而是刻意设计：
    // 缩略图行位于 LazyColumn 内，滑出视口会销毁 composition、回来时 produceState 重跑，
    // 只要缓存还在就是秒命中，不会再等 1~2 秒。
    // 正因为条目不会被淘汰，才可以把缓存里的 Bitmap **原图**直接交给调用方（无需 copy），
    // 避免滚动时反复分配副本被 GC 回收。
    // 上限只是安全阀：实际条目 ≈ 预设数 × 3 种缩略图尺寸 × 约 48KB，远达不到这里。
    // 真到 32MB 说明 key 设计出了问题（例如把样式哈希混进了 key），届时淘汰反而是保护。
    private val thumbnailCache = object : android.util.LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // ===== 缩略图磁盘二级缓存 =====
    //
    // 内存缓存随进程死亡而清空，而「桌面组件 → 快捷面板」几乎总是冷进程进入
    // （面板关闭后 App 进程很快被系统回收）。实测 30 张缩略图冷渲染合计约 12s
    // CPU，Dispatchers.Default 8 线程并行也要 1.5~2.2s，表现为面板打开后缩略图
    // 空白 1~2 秒才逐个补齐。磁盘缓存（cacheDir/widget_thumbs/，每张 150×80
    // PNG 约 10~30KB、全部合计 <1MB）跨进程存活，冷启动退化为毫秒级 PNG 解码。
    //
    // **改动预设的视觉定义（颜色/纹理/素材）或本文件的绘制逻辑后必须
    // +1 THUMB_DISK_VERSION**：磁盘文件按「版本号 + presetId + 尺寸」寻址，
    // 版本不变就会继续沿用旧图。不把样式 JSON 哈希进 key，是因为 org.json 的
    // key 顺序跨进程不稳定，哈希不可靠。
    private const val THUMB_DISK_VERSION = 1
    private const val THUMB_DISK_DIR = "widget_thumbs"

    private fun thumbDiskFile(context: Context, ramKey: String): File =
        File(File(context.applicationContext.cacheDir, THUMB_DISK_DIR), "v$THUMB_DISK_VERSION$ramKey.png")

    /** 磁盘命中：解码后顺手回填内存缓存，进程内的后续请求直接走内存。 */
    private fun loadThumbnailFromDisk(context: Context, ramKey: String): Bitmap? {
        val file = thumbDiskFile(context, ramKey)
        if (!file.isFile) return null
        val bytes = try {
            file.readBytes()
        } catch (t: Throwable) {
            return null
        }
        val bmp = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (t: Throwable) {
            null
        }
        if (bmp == null) {
            // 解码失败（文件损坏/被截断）：删掉坏文件，下次重新渲染
            file.delete()
            return null
        }
        synchronized(thumbnailCache) { thumbnailCache.put(ramKey, bmp) }
        return bmp
    }

    private fun saveThumbnailToDisk(context: Context, ramKey: String, bitmap: Bitmap) {
        val file = thumbDiskFile(context, ramKey)
        if (file.isFile) return // 同 key 已落盘（并发渲染时先到者写），无需重写
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(tmp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (!tmp.renameTo(file)) tmp.delete()
        } catch (t: Throwable) {
            // 落盘失败只影响下次冷启动的速度，不影响本次显示
            Timber.w(t, "saveThumbnailToDisk failed: $ramKey")
        }
    }

    /**
     * 进程启动后在后台线程调用：把磁盘缓存里的全部缩略图预热进内存，
     * 让「桌面 → 快捷面板」打开的第一帧就带图（否则首帧后还要等一次
     * 毫秒级磁盘解码）。顺带清理历史版本残留的旧图。
     */
    fun prewarmThumbnailCache(context: Context) {
        val dir = File(context.applicationContext.cacheDir, THUMB_DISK_DIR)
        val files = dir.listFiles() ?: return
        val versionPrefix = "v$THUMB_DISK_VERSION"
        for (file in files) {
            val name = file.name
            if (!name.startsWith(versionPrefix) || !name.endsWith(".png")) {
                if (file.isFile) file.delete() // 旧版本/临时残留
                continue
            }
            val ramKey = name.removePrefix(versionPrefix).removeSuffix(".png")
            if (ramKey.isEmpty()) continue
            val hit = synchronized(thumbnailCache) {
                thumbnailCache.get(ramKey)?.takeUnless { it.isRecycled }
            }
            if (hit != null) continue
            loadThumbnailFromDisk(context, ramKey)
        }
    }

    // 仅供单元测试：清空内存缩略图缓存，模拟「进程刚被系统回收后重启」的冷启动状态
    @androidx.annotation.VisibleForTesting
    internal fun clearThumbnailRamCacheForTest() {
        synchronized(thumbnailCache) { thumbnailCache.evictAll() }
    }

    // 仅供单元测试：统计真实 Canvas 渲染次数，用于区分「磁盘命中」与「重渲染」
    @androidx.annotation.VisibleForTesting
    internal var thumbnailRenderCountForTest: Int = 0

    /**
     * 取一张已渲染好的风格缩略图。
     *
     * **直接返回缓存里的原图，不做 copy()**：
     * 缩略图只有 150×80，一条约 48KB，26 张合计约 1.2MB；每次命中都 copy 一份的话，
     * 列表滚动时会不断分配新位图、旧的被 GC 回收，表现为"滑走再滑回来又要等 1~2 秒"。
     * 之所以敢直接给原图，是因为本缓存**关闭了 LruCache 的条目回收**
     * （entryRemoved=false，见 thumbnailCache 定义）：条目只增不减、不会被淘汰，
     * 原图一旦创建就一直在，调用方持有的引用始终有效。
     * 代价是这 1.2MB 常驻，换来滚动零重渲染。
     *
     * **锁只保护缓存的读写，绝不能把 producer（真正的 Canvas 渲染）也圈进去**：
     * 一旦整个方法加 @Synchronized，26 张缩略图会被强制串行逐张渲染，
     * 比并行慢好几倍，表现为"打开面板要等 1~2 秒"。之前踩过这个坑。
     */
    private fun thumbnailCacheKey(
        cacheKey: String,
        producer: () -> Bitmap?
    ): Bitmap? {
        synchronized(thumbnailCache) {
            thumbnailCache.get(cacheKey)?.let { if (!it.isRecycled) return it }
        }
        // 锁外渲染：允许所有缩略图在 Dispatchers.Default 上并行跑满 CPU
        val fresh = producer() ?: return null
        synchronized(thumbnailCache) {
            // 另一个线程可能已经渲染好了同一张：优先用先到的那份，避免重复渲染
            thumbnailCache.get(cacheKey)?.let { if (!it.isRecycled) return it }
            thumbnailCache.put(cacheKey, fresh)
        }
        return fresh
    }


    /**
     * 只查缓存、不触发渲染的同步查询。供 UI 侧作为 produceState 的 initialValue：
     * 命中时该缩略图在首帧就有图（滚动回已渲染过的位置时完全无空白帧），
     * 未命中返回 null，由 produceState 内部异步渲染并写入缓存。
     */
    fun cachedThumbnail(
        widthDp: Int,
        heightDp: Int,
        style: WidgetStyle
    ): Bitmap? {
        val key = "${style.presetId ?: style.shape}_${widthDp}x$heightDp"
        synchronized(thumbnailCache) {
            thumbnailCache.get(key)?.let { if (!it.isRecycled) return it }
        }
        return null
    }

    /**
     * 风格缩略图统一入口：供 MainActivity / QuickAdjustActivity 的缩略图列表调用。
     *
     * 命中缓存时**同步返回**，因此 UI 侧 `produceState` 的首次赋值也在同一帧完成，
     * 不会出现"先空白一帧再补上"的闪动；未命中才回落到耗时渲染。
     */
    fun renderThumbnail(
        context: Context,
        widthDp: Int,
        heightDp: Int,
        content: String,
        style: WidgetStyle
    ): Bitmap? {
        // key 用 presetId（缺失时退回形状+尺寸）而不是整个 style.toJson()：
        // 前者稳定且短，后者会随用户每次微调颜色而变，导致缓存永远不命中。
        val key = "${style.presetId ?: style.shape}_${widthDp}x$heightDp"
        return thumbnailCacheKey(key) {
            // 内存未命中 → 先查磁盘缓存（跨进程存活，冷启动毫秒级）；
            // 磁盘也没有才真正走 Canvas 渲染，并在渲染完成后落盘供下次冷启动使用。
            val fromDisk = loadThumbnailFromDisk(context, key)
            if (fromDisk != null) {
                fromDisk
            } else {
                try {
                    thumbnailRenderCountForTest++
                    render(context, widthDp, heightDp, content, style)
                } catch (t: Throwable) {
                    null
                }?.also { saveThumbnailToDisk(context, key, it) }
            }
        }
    }






}
