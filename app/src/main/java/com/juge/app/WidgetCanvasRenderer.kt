package com.juge.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.juge.app.data.ImageScaleMode
import com.juge.app.data.TrialManager
import com.juge.app.data.WidgetFont
import com.juge.app.data.WidgetShape
import com.juge.app.data.WidgetStyle
import timber.log.Timber
import java.io.File
import java.util.Locale

object WidgetCanvasRenderer {

    private const val RENDER_DENSITY_SCALE = 1.5f
    private const val MIN_BITMAP_SIZE = 300
    private const val SPLIT_CARD_RATIO = 0.48f
    private const val SPLIT_CARD_HORIZONTAL_RATIO = 0.333f
    // 检测近白相框阈值：RGB 均大于该值视为“留白像素”
    private const val LIGHT_BORDER_THRESHOLD = 228
    private const val TORN_SEED = 42L
    private const val TEXTURE_SEED = 2026L
    private const val FONT_SIZE_SCALE = 1.2f
    private const val QUOTE_MARK_FONT_SIZE_SCALE = 3.5f
    private const val DEFAULT_OUTER_CORNER_RADIUS_DP = 16f
    // 卡片四周留出的内边距：让卡片不铺满整幅组件位图，从而给投影留出可见空间。
    // 阴影绘制在位图内部，若卡片满幅则阴影会被位图边界裁掉，组件看起来就是"贴平"的。
    private const val CARD_INSET_DP = 4f
    private const val QUOTE_ALPHA = 25
    private const val TORN_BORDER_ALPHA = 220
    private const val TAPE_LINE_ALPHA = 45

    // 书香书架纵向几何：书籍区（书脊 + 横板）高度固定为 dp，不随组件变高而拉伸，
    // 组件多出来的高度全部留给下方摘录面板，因此尺寸变大时只有文本区域变大。
    // 组件高度不足时优先压缩书籍区，保证面板至少能显示正文。
    private const val SHELF_BOOK_BAND_HEIGHT_DP = 90f // 组件顶部到横板上沿
    private const val SHELF_BOARD_HEIGHT_DP = 4f // 横板厚度
    private const val SHELF_BOOK_TOP_MARGIN_DP = 6f // 书脊顶部与组件顶部的留白
    private const val SHELF_PANEL_GAP_DP = 6f // 横板下沿到面板上沿
    private const val SHELF_PANEL_BOTTOM_MARGIN_DP = 6f // 面板下沿到组件底部
    private const val SHELF_PANEL_MIN_HEIGHT_DP = 54f // 面板最小高度
    private const val SHELF_PANEL_MIN_BAND_RATIO = 0.35f // 组件过矮时书籍区的最小占比
    private const val SHELF_PANEL_TEXT_PAD_X_DP = 14f // 面板内正文左右留白
    private const val SHELF_PANEL_TEXT_PAD_Y_DP = 6f // 面板内正文上下留白
    // 横向仍按比例：书架横向铺满组件宽度
    private const val SHELF_BOARD_LEFT_RATIO = 0.035f
    private const val SHELF_BOARD_RIGHT_RATIO = 0.965f
    private const val SHELF_BOOKS_LEFT_RATIO = 0.07f
    private const val SHELF_BOOKS_RIGHT_RATIO = 0.93f
    private const val SHELF_PANEL_LEFT_RATIO = 0.055f
    private const val SHELF_PANEL_RIGHT_RATIO = 0.945f
    // 书脊竖排书名的行距倍数：略大于字号即可，保持字符紧凑而不铺满整条书脊
    private const val BOOK_TITLE_LINE_STEP_RATIO = 1.06f

    // 小霸王游戏机：subor_console.png 的宽高比，以及机身屏幕（文本区）在素材中的相对位置，
    // 均由出图/合成时测得。素材换成新图后需同步更新这几个比例。
    // 素材四周预留了透明留白，避免 4×2 这类偏宽组件里机身/手柄贴住组件上下边缘
    private const val SUBOR_IMAGE_ASPECT = 1.6043f
    private const val SUBOR_SCREEN_LEFT_RATIO = 0.2893f
    private const val SUBOR_SCREEN_RIGHT_RATIO = 0.7173f
    private const val SUBOR_SCREEN_TOP_RATIO = 0.1176f
    private const val SUBOR_SCREEN_BOTTOM_RATIO = 0.5882f
    private const val SUBOR_SCREEN_TEXT_PAD_X_DP = 6f
    private const val SUBOR_SCREEN_TEXT_PAD_Y_DP = 4f
    // 通电显像管的荧光屏配色：屏幕永远是绿色荧光（模拟已开机），与用户可调的字色无关
    private val SUBOR_SCREEN_CORE_COLOR = 0xFF46A857.toInt() // 中心亮绿
    private val SUBOR_SCREEN_MID_COLOR = 0xFF1C6B2C.toInt()
    private val SUBOR_SCREEN_EDGE_COLOR = 0xFF04140A.toInt() // 边缘近黑，形成曲面暗角
    private const val SUBOR_GLOW_RADIUS_DP = 7f
    private const val SUBOR_SCREEN_FILL_ALPHA = 236
    private const val SUBOR_SCANLINE_SPACING_DP = 2.6f
    private const val SUBOR_SCANLINE_ALPHA = 44
    private const val SUBOR_GLASS_SHEEN_ALPHA = 26

    fun render(
        context: Context,
        widthDp: Int,
        heightDp: Int,
        content: String,
        style: WidgetStyle,
        trialManager: TrialManager,
        isPreview: Boolean = false
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
            style.shape == WidgetShape.SPLIT_CARD_HORIZONTAL
        val cardInset = if (usesInsetCard) CARD_INSET_DP * densityScale else 0f
        val offsetY = cardInset
        val rectF = RectF(cardInset, offsetY, targetWidth - cardInset, targetHeight - cardInset)

        // 巨剑/毛绒森林/小霸王游戏机是整幅插画（剑身横贯、毛绒小树在顶部、实物模型铺满），
        // 圆角裁剪会把主体切掉。预设套用时会继承上一个风格的圆角值，
        // 这里统一强制按直角渲染，避免旧数据/跨风格套用后画面被裁。
        val effectiveCornerRadiusDp =
            if (style.shape == WidgetShape.GIANT_SWORD || style.shape == WidgetShape.PLUSH_FOREST || style.shape == WidgetShape.SUBOR_CONSOLE) 0f
            else style.cornerRadiusDp

        when (style.shape) {
            WidgetShape.RECTANGLE, WidgetShape.HANDBOOK_TAPE, WidgetShape.SPLIT_CARD, WidgetShape.SPLIT_CARD_HORIZONTAL, WidgetShape.PIXEL_RETRO, WidgetShape.PET_CAT_NAP, WidgetShape.BLUE_NOTE, WidgetShape.ZHU_QING_SI_ZHI, WidgetShape.NIUPI_SHOUZHANG, WidgetShape.CLASSROOM_BLACKBOARD, WidgetShape.BOOKSHELF, WidgetShape.CAT_CARD, WidgetShape.GIANT_SWORD, WidgetShape.PLUSH_FOREST, WidgetShape.SUBOR_CONSOLE -> {
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
            WidgetShape.PIXEL_RETRO, WidgetShape.PET_CAT_NAP, WidgetShape.ZHU_QING_SI_ZHI, WidgetShape.NIUPI_SHOUZHANG, WidgetShape.CLASSROOM_BLACKBOARD, WidgetShape.BOOKSHELF, WidgetShape.CAT_CARD, WidgetShape.GIANT_SWORD, WidgetShape.PLUSH_FOREST, WidgetShape.SUBOR_CONSOLE ->
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
        if (style.showCardShadow) {
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
        // 背景色为透明时不填充，避免 alpha 被强制为 255 后把透明底画成黑色
        if (Color.alpha(effectiveBgColor) > 0) {
            canvas.drawPath(if (style.shape == WidgetShape.TORN_PAPER) path else outerPath, bgPaint)
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
            val resName = style.presetImageResName ?: if (style.shape == WidgetShape.SPLIT_CARD || style.shape == WidgetShape.SPLIT_CARD_HORIZONTAL) "bg_illustration_1" else null
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
        if (bgBitmap != null) {
            canvas.save()
            canvas.clipPath(if (style.shape == WidgetShape.TORN_PAPER) path else outerPath)
            if (style.shape == WidgetShape.SPLIT_CARD) {
                val imgRect = RectF(
                    outerRect.left,
                    outerRect.top,
                    outerRect.right,
                    outerRect.top + outerRect.height() * SPLIT_CARD_RATIO
                )
                drawBgImage(canvas, bgBitmap, imgRect, style)
            } else if (style.shape == WidgetShape.SPLIT_CARD_HORIZONTAL) {
                val imgRect = RectF(
                    outerRect.left,
                    outerRect.top,
                    outerRect.left + outerRect.width() * SPLIT_CARD_HORIZONTAL_RATIO,
                    outerRect.bottom
                )
                drawBgImage(canvas, bgBitmap, imgRect, style)
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

        // 如果是 SPLIT_CARD 形状，在 clip(outerPath) 作用下，将卡片下半部分（高度 52% 区域）填充为纯白色，并画一条分割细线
        if (style.shape == WidgetShape.SPLIT_CARD) {
            canvas.save()
            canvas.clipPath(outerPath)
            val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
            }
            val dividerY = outerRect.top + outerRect.height() * SPLIT_CARD_RATIO
            val bottomRect = RectF(
                outerRect.left,
                dividerY,
                outerRect.right,
                outerRect.bottom
            )
            canvas.drawRect(bottomRect, whitePaint)

            // 绘制 1px 精致分割线
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E5E7EB") // 使用更苹果风格的浅灰边线 (#E5E7EB)
                strokeWidth = 1f * densityScale
                this.style = Paint.Style.STROKE
            }
            canvas.drawLine(outerRect.left, dividerY, outerRect.right, dividerY, linePaint)
            
            canvas.restore()
        } else if (style.shape == WidgetShape.SPLIT_CARD_HORIZONTAL) {
            canvas.save()
            canvas.clipPath(outerPath)
            val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
            }
            val dividerX = outerRect.left + outerRect.width() * SPLIT_CARD_HORIZONTAL_RATIO
            val rightRect = RectF(
                dividerX,
                outerRect.top,
                outerRect.right,
                outerRect.bottom
            )
            canvas.drawRect(rightRect, whitePaint)
            canvas.restore()
        }

        // 蓝色便签：在蓝色大底上追加顶部 NOTE 区域与底部米白签条
        if (style.shape == WidgetShape.BLUE_NOTE) {
            drawBlueNoteChrome(canvas, targetWidth.toFloat(), targetHeight.toFloat(), outerRect, outerPath, densityScale, style, context)
        }

        // 书香书架：顶部彩色书脊立在横板上，底部米色摘录面板
        if (style.shape == WidgetShape.BOOKSHELF) {
            drawBookshelfChrome(canvas, outerRect, densityScale, style, context)
        }

        // 猫咪卡片：奶白卡上补一层浅粉内描边，并在顶部画出猫头头像
        if (style.shape == WidgetShape.CAT_CARD) {
            drawCatCardChrome(canvas, outerRect, outerPath, densityScale, style)
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

        // 4. 准备绘制文字（新付费规则：预览任意风格、桌面默认免费、仅在“同步到桌面”时弹付费，渲染层不再拦截或显示付费蒙层）
        val renderStyle = style
        @Suppress("UNUSED_VARIABLE")
        val isActivated = trialManager.isActivated()

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

        val textAlignment = when (style.textAlign.uppercase(Locale.ROOT)) {
            "LEFT" -> Layout.Alignment.ALIGN_NORMAL
            "RIGHT" -> Layout.Alignment.ALIGN_OPPOSITE
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
            } else if (style.shape == WidgetShape.CAT_CARD) {
                // 猫咪卡片：顶部让出猫头头像，正文落在头像下方的奶白留白区
                val lateral = targetWidth * 0.11f
                paddingLeft = lateral
                paddingRight = targetWidth - lateral
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = targetHeight * 0.50f
                cardHeight = targetHeight - cardTop - targetHeight * 0.10f
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
                .build()
            val staticLayout = if (fullLayout.lineCount > maxLines) {
                StaticLayout.Builder.obtain(content, 0, content.length, textPaint, textWidth.toInt())
                    .setAlignment(textAlignment)
                    .setLineSpacing(0f, style.lineSpacingMultiplier)
                    .setIncludePad(true)
                    .setMaxLines(maxLines)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END)
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

    // 小霸王游戏机：素材里"显像管玻璃"（屏幕）在组件位图中的矩形。
    // 素材按 CENTER_FIT 等比完整显示，屏幕位置随组件宽高比变化，必须按同一套比例换算。
    private fun suborScreenRect(outerRect: RectF): RectF {
        val model = centerFitRect(outerRect, SUBOR_IMAGE_ASPECT)
        return RectF(
            model.left + model.width() * SUBOR_SCREEN_LEFT_RATIO,
            model.top + model.height() * SUBOR_SCREEN_TOP_RATIO,
            model.left + model.width() * SUBOR_SCREEN_RIGHT_RATIO,
            model.top + model.height() * SUBOR_SCREEN_BOTTOM_RATIO
        )
    }

    // 屏幕是圆角玻璃：按小圆角裁剪，避免绿色荧光溢出到机身红色边框上
    private fun clipSuborScreen(canvas: Canvas, screen: RectF) {
        val radius = Math.min(screen.width(), screen.height()) * 0.06f
        val clipPath = Path().apply { addRoundRect(screen, radius, radius, Path.Direction.CW) }
        canvas.clipPath(clipPath)
    }

    /**
     * 通电显像管：在屏幕玻璃区域内叠一层绿色荧光底 + 中心亮斑。
     * 素材本身是"未通电"的深灰玻璃，这一步让它变成开机的绿屏。
     */
    private fun drawSuborScreenGlow(canvas: Canvas, screen: RectF, densityScale: Float) {
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
    private fun drawSuborCrtOverlay(canvas: Canvas, screen: RectF, densityScale: Float) {
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

    // 绘制手账胶带折线撕裂边缘 Path
    private fun drawTape(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        angleDegrees: Float,
        color: Int,
        densityScale: Float
    ) {
        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.rotate(angleDegrees)

        val halfW = width / 2f
        val halfH = height / 2f

        val path = Path()
        // 从左上角开始绘制。左侧是撕裂毛边。
        path.moveTo(-halfW, -halfH)
        
        // 左毛边：y从 -halfH 到 halfH。分 6 个波折段
        val segments = 6
        val step = height / segments
        for (i in 1..segments) {
            val currY = -halfH + i * step
            val shiftX = if (i % 2 == 1) 3f * densityScale else 0f
            path.lineTo(-halfW + shiftX, currY)
        }

        // 下平边
        path.lineTo(halfW, halfH)

        // 右毛边：y从 halfH 回到 -halfH。分 6 个波折段
        for (i in segments downTo 0) {
            val currY = -halfH + i * step
            val shiftX = if (i % 2 == 1) -3f * densityScale else 0f
            path.lineTo(halfW + shiftX, currY)
        }

        // 上平边已通过 close 闭合
        path.close()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }
        canvas.drawPath(path, paint)

        // 绘制三条微弱的半透明白色光泽条纹纹理，增加纸胶带的真实感
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.color = Color.WHITE
            alpha = TAPE_LINE_ALPHA
            strokeWidth = 1.5f * densityScale
        }
        canvas.drawLine(-halfW + 12f * densityScale, -halfH + 2f * densityScale, -halfW + 22f * densityScale, halfH - 2f * densityScale, linePaint)
        canvas.drawLine(-halfW + 22f * densityScale, -halfH + 2f * densityScale, -halfW + 32f * densityScale, halfH - 2f * densityScale, linePaint)
        canvas.drawLine(halfW - 32f * densityScale, -halfH + 2f * densityScale, halfW - 22f * densityScale, halfH - 2f * densityScale, linePaint)

        canvas.restore()
    }

    // 书架上的一本书：书名、书脊配色、相对最高书脊的高度比例
    private data class ShelfBook(
        val title: String,
        val color: Int,
        val heightFactor: Float,
        val highlighted: Boolean = false
    )

    private val SHELF_BOOKS: List<ShelfBook> = listOf(
        ShelfBook("活着", 0xFF3F3F3F.toInt(), 0.86f),
        ShelfBook("围城", 0xFF2B4A6B.toInt(), 0.77f),
        ShelfBook("百年孤独", 0xFF2F5D3A.toInt(), 0.92f),
        ShelfBook("小王子", 0xFFF0A81E.toInt(), 0.76f, highlighted = true),
        ShelfBook("人间失格", 0xFF8E2B2B.toInt(), 0.82f),
        ShelfBook("月亮与六便士", 0xFF8FB8D8.toInt(), 0.80f),
        ShelfBook("平凡的世界", 0xFFA83232.toInt(), 0.92f),
        ShelfBook("三体", 0xFF1F3D6E.toInt(), 0.87f),
        ShelfBook("解忧杂货店", 0xFFE0642E.toInt(), 0.80f),
        ShelfBook("追风筝的人", 0xFF2E9E6B.toInt(), 0.91f),
        ShelfBook("城南旧事", 0xFFF0A8C0.toInt(), 0.72f),
        ShelfBook("瓦尔登湖", 0xFF4E8C3A.toInt(), 0.92f),
        ShelfBook("红楼梦", 0xFF5B3E9E.toInt(), 0.93f)
    )

    // 书脊底色偏亮时改用深色书名，保证竖排书名始终清晰
    private fun isLightSpine(color: Int): Boolean {
        val luminance = 0.299f * Color.red(color) / 255f +
            0.587f * Color.green(color) / 255f +
            0.114f * Color.blue(color) / 255f
        return luminance > 0.62f
    }

    // 书香书架：书架本身就是组件，四周保持透明（不画底色/描边/投影、不做形状裁切），
    // 只有横板与底部米色摘录面板是可着色区域，两者都按位图比例绘制，随组件尺寸一起缩放
    private fun drawBookshelfChrome(
        canvas: Canvas,
        outerRect: RectF,
        densityScale: Float,
        style: WidgetStyle,
        context: Context
    ) {
        val fx = outerRect.left
        val fy = outerRect.top
        val fw = outerRect.width()
        val fh = outerRect.height()

        // 1. 书架横板：纵向位置固定，比书脊两侧略宽，下沿压一条深色细线做出板厚
        val shelfTop = bookshelfShelfTop(fy, fh, densityScale)
        val shelfHeight = bookshelfBoardHeight(densityScale)
        val shelfLeft = fx + fw * SHELF_BOARD_LEFT_RATIO
        val shelfRight = fx + fw * SHELF_BOARD_RIGHT_RATIO
        canvas.drawRect(shelfLeft, shelfTop, shelfRight, shelfTop + shelfHeight, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D9D4CB")
        })
        canvas.drawRect(shelfLeft, shelfTop + shelfHeight * 0.6f, shelfRight, shelfTop + shelfHeight, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C2BBB0")
        })

        // 2. 书脊：13 本书按不同高度比例铺满书架宽度，其中「小王子」作为重点书目带浅色描边。
        // 最高一本由固定的书籍区高度决定，因此组件变高时书脊尺寸保持不变
        val booksLeft = fx + fw * SHELF_BOOKS_LEFT_RATIO
        val booksRight = fx + fw * SHELF_BOOKS_RIGHT_RATIO
        val slot = (booksRight - booksLeft) / SHELF_BOOKS.size
        val bookWidth = slot * 0.94f
        val maxBookHeight = (shelfTop - fy - SHELF_BOOK_TOP_MARGIN_DP * densityScale)
            .coerceAtLeast(4f * densityScale)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = style.font.getTypeface(context)
            textAlign = Paint.Align.CENTER
        }

        SHELF_BOOKS.forEachIndexed { index, book ->
            val bookHeight = maxBookHeight * book.heightFactor
            val left = booksLeft + slot * index + (slot - bookWidth) / 2f
            val right = left + bookWidth
            val top = shelfTop - bookHeight
            val radius = bookWidth * 0.06f

            canvas.drawRoundRect(RectF(left, top, right, shelfTop), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = book.color
            })

            if (book.highlighted) {
                val inset = 1.8f * densityScale
                canvas.drawRoundRect(
                    RectF(left + inset, top + inset, right - inset, shelfTop - inset),
                    radius,
                    radius,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.style = Paint.Style.STROKE
                        strokeWidth = 1.6f * densityScale
                        color = Color.parseColor("#FFF3D0")
                    }
                )
            }

            // 3. 竖排书名：字符之间只留紧凑行距，自书脊顶部向下排布，
            // 而不是把字符按书脊高度等距铺满；书名过长时整体缩小以容纳在书脊内
            val title = book.title
            val topPadding = (bookHeight * 0.07f).coerceAtLeast(2f * densityScale)
            val availableHeight = (bookHeight - topPadding * 2f).coerceAtLeast(1f)
            val textSize = minOf(bookWidth * 0.60f, availableHeight / (title.length * BOOK_TITLE_LINE_STEP_RATIO))
                .coerceAtLeast(5f * densityScale)
            titlePaint.textSize = textSize
            titlePaint.color = if (isLightSpine(book.color)) Color.parseColor("#2B2B2B") else Color.WHITE
            val centerX = (left + right) / 2f
            val lineStep = textSize * BOOK_TITLE_LINE_STEP_RATIO
            var baseline = top + topPadding + lineStep * 0.5f + textSize * 0.36f
            for (i in title.indices) {
                canvas.drawText(title[i].toString(), centerX, baseline, titlePaint)
                baseline += lineStep
            }
        }

        // 4. 底部米色摘录面板：正文由通用排版逻辑绘制在这块面板内，两者共用同一个面板矩形，
        // 面板高度随组件高度增长，正文可显示的行数随之增加
        val panel = bookshelfPanelRect(outerRect, densityScale)
        val panelRadius = minOf(panel.width(), panel.height()) * 0.12f
        canvas.drawRoundRect(
            panel,
            panelRadius,
            panelRadius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F7F3EC") }
        )
    }

    // 书香书架横板上沿的纵向位置：书籍区高度固定为 SHELF_BOOK_BAND_HEIGHT_DP，
    // 组件不够高时压缩书籍区，优先保证摘录面板的最小高度
    private fun bookshelfShelfTop(fy: Float, fh: Float, densityScale: Float): Float {
        val reserved = (SHELF_PANEL_MIN_HEIGHT_DP + SHELF_PANEL_GAP_DP + SHELF_PANEL_BOTTOM_MARGIN_DP + SHELF_BOARD_HEIGHT_DP) * densityScale
        val maxBand = (fh - reserved).coerceAtLeast(fh * SHELF_PANEL_MIN_BAND_RATIO)
        return fy + (SHELF_BOOK_BAND_HEIGHT_DP * densityScale).coerceAtMost(maxBand)
    }

    private fun bookshelfBoardHeight(densityScale: Float): Float =
        (SHELF_BOARD_HEIGHT_DP * densityScale).coerceAtLeast(2f * densityScale)

    // 摘录面板矩形：上沿紧跟横板，下沿留出底部留白，中间全部属于文本显示区域
    private fun bookshelfPanelRect(outerRect: RectF, densityScale: Float): RectF {
        val fx = outerRect.left
        val fy = outerRect.top
        val fw = outerRect.width()
        val fh = outerRect.height()
        val panelTop = bookshelfShelfTop(fy, fh, densityScale) +
            bookshelfBoardHeight(densityScale) +
            SHELF_PANEL_GAP_DP * densityScale
        val panelBottom = (fy + fh - SHELF_PANEL_BOTTOM_MARGIN_DP * densityScale)
            .coerceAtLeast(panelTop + 1f)
        return RectF(
            fx + fw * SHELF_PANEL_LEFT_RATIO,
            panelTop,
            fx + fw * SHELF_PANEL_RIGHT_RATIO,
            panelBottom
        )
    }

    // 蓝色便签贴纸：在圆角蓝底上绘制顶部 NOTE 行、右上信息钮、底部米色签条与手写签名
    private fun drawBlueNoteChrome(
        canvas: Canvas,
        width: Float,
        height: Float,
        outerRect: RectF,
        outerPath: Path,
        densityScale: Float,
        style: WidgetStyle,
        context: Context
    ) {
        canvas.save()
        canvas.clipPath(outerPath)

        val svgW = 578f
        val svgH = 363f
        val headerH = 76f
        val footerH = 76f
        val sx = width / svgW
        val sy = height / svgH
        val scaleText = (sx + sy) * 0.5f

        // 顶部 NOTE：左上 NOTE 大写，右上圆形信息钮（?）
        val noteSize = 22f * densityScale * (scaleText / (densityScale * 0.9f)).coerceIn(0.85f, 1.25f)
        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = noteSize.coerceIn(12f * densityScale, 26f * densityScale)
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val notePadX = 18f * sx
        val notePadY = 18f * sy + notePaint.textSize
        canvas.drawText("NOTE", outerRect.left + notePadX, outerRect.top + notePadY, notePaint)

        // 右上信息钮：白底圆 + 黄色 ? 造型，复刻 SVG 中 50×50 的圆形与外围刻度
        val badgeCx = outerRect.right - 28f * sx
        val badgeCy = outerRect.top + 30f * sy
        val badgeR = 18f * sx.coerceAtLeast(sy) * 0.9f
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.style = Paint.Style.FILL
        }
        canvas.drawCircle(badgeCx, badgeCy, badgeR, ringPaint)
        // 外圈细环
        val ringStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.style = Paint.Style.STROKE
            strokeWidth = 1.2f * densityScale
            alpha = 210
        }
        canvas.drawCircle(badgeCx, badgeCy, badgeR + 2f * densityScale * 0.4f, ringStroke)

        val qPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8D000")
            textSize = badgeR * 1.45f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val qFm = qPaint.fontMetrics
        val qY = badgeCy - (qFm.ascent + qFm.descent) / 2f
        canvas.drawText("?", badgeCx, qY, qPaint)

        // 外围小刻度（8 个小短线，复刻 SVG 的发散点）
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8D000")
            strokeWidth = 1.4f * densityScale
            strokeCap = Paint.Cap.ROUND
        }
        val tickR = badgeR + 7f * sx
        for (i in 0 until 8) {
            val a = Math.toRadians((i * 45.0) - 22.5)
            val x0 = (badgeCx + Math.cos(a) * (tickR - 3f * sx)).toFloat()
            val y0 = (badgeCy + Math.sin(a) * (tickR - 3f * sy)).toFloat()
            val x1 = (badgeCx + Math.cos(a) * (tickR + 2f * sx)).toFloat()
            val y1 = (badgeCy + Math.sin(a) * (tickR + 2f * sy)).toFloat()
            canvas.drawLine(x0, y0, x1, y1, tickPaint)
        }

        // 底部米色签条：高度 76/363，颜色 #F7F4F0，内写 i + 句阁、手写签名（沿用 style.authorSignature）
        val footerTop = outerRect.bottom - footerH * sy
        val footerHpx = footerH * sy
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F7F4F0")
            this.style = Paint.Style.FILL
        }
        // 只在底部条区域填充，保持顶部仍为 #43A8F0
        canvas.drawRect(RectF(outerRect.left, footerTop, outerRect.right, outerRect.bottom), footerPaint)

        // 底部条内：底部基线的分隔细线（微灰）
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E8E2DC")
            strokeWidth = 1f * densityScale
        }
        canvas.drawLine(outerRect.left, footerTop, outerRect.right, footerTop, linePaint)

        // 底部手写英文（对齐参考图：居中 "focus yourself"，毛笔楷书手写体）
        val footerText = "focus yourself"
        val sigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1A1A")
            textSize = (20f * sy).coerceIn(14f * densityScale, 26f * densityScale)
            textAlign = Paint.Align.CENTER
            typeface = try {
                val tf = android.graphics.Typeface.createFromAsset(context.assets, "fonts/MaShanZheng-Regular.ttf")
                android.graphics.Typeface.create(tf, android.graphics.Typeface.ITALIC)
            } catch (_: Exception) {
                android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.ITALIC)
            }
        }
        val centerX = (outerRect.left + outerRect.right) / 2f
        val fm = sigPaint.fontMetrics
        val centerY = footerTop + footerHpx / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(footerText, centerX, centerY, sigPaint)

        canvas.restore()
    }

    // 猫咪卡片：奶白卡上补一层浅粉内描边，并在顶部居中画出猫头头像
    private fun drawCatCardChrome(
        canvas: Canvas,
        outerRect: RectF,
        outerPath: Path,
        densityScale: Float,
        style: WidgetStyle
    ) {
        canvas.save()
        canvas.clipPath(outerPath)

        // 内层浅粉细线：与外层粉色粗描边一起构成双层边
        val inset = 5f * densityScale
        val innerRect = RectF(
            outerRect.left + inset,
            outerRect.top + inset,
            outerRect.right - inset,
            outerRect.bottom - inset
        )
        val innerRx = (style.cornerRadiusDp * densityScale - inset).coerceAtLeast(0f)
        val innerPath = Path().apply {
            if (innerRx <= 0f) addRect(innerRect, Path.Direction.CW)
            else addRoundRect(innerRect, innerRx, innerRx, Path.Direction.CW)
        }
        val innerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 1.2f * densityScale
            color = Color.parseColor("#FBDDE9")
        }
        canvas.drawPath(innerPath, innerLinePaint)

        // 猫头头像：顶部居中。尺寸同时受卡宽与卡高约束，避免窄卡/矮卡里比例失调。
        // cy 取 0.85 倍头高，保证耳尖（cy - 1.4r）落在卡片上沿内侧，不会被圆角裁切
        val headSize = minOf(outerRect.width() * 0.24f, outerRect.height() * 0.30f)
        if (headSize > 10f * densityScale) {
            val cx = outerRect.centerX()
            val cy = outerRect.top + inset + headSize * 0.85f
            drawCatHead(canvas, cx, cy, headSize, densityScale)
        }

        canvas.restore()
    }

    // 手绘猫头：白脸 + 粉色内耳 + 棕色眼鼻 + 粉腮红 + 胡须
    private fun drawCatHead(canvas: Canvas, cx: Float, cy: Float, size: Float, densityScale: Float) {
        val furWhite = Color.parseColor("#FAF5F0")
        val furPink = Color.parseColor("#F5A8C0")
        val furPinkLight = Color.parseColor("#FBDDE9")
        val inkBrown = Color.parseColor("#4A2C2A")

        val r = size / 2f
        val fill: (Int) -> Paint = { c ->
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = c
                style = Paint.Style.FILL
            }
        }
        val stroke: (Int, Float) -> Paint = { c, w ->
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = c
                style = Paint.Style.STROKE
                strokeWidth = w
                strokeCap = Paint.Cap.ROUND
            }
        }

        // 双耳：外白内粉的三角，坐在头圆上方
        val earTipY = cy - r * 1.40f
        val earBaseY = cy - r * 0.40f
        canvas.drawPath(
            Path().apply {
                moveTo(cx - r * 0.92f, earBaseY)
                lineTo(cx - r * 0.60f, earTipY)
                lineTo(cx - r * 0.10f, earBaseY)
                close()
            },
            fill(furWhite)
        )
        canvas.drawPath(
            Path().apply {
                moveTo(cx + r * 0.92f, earBaseY)
                lineTo(cx + r * 0.60f, earTipY)
                lineTo(cx + r * 0.10f, earBaseY)
                close()
            },
            fill(furWhite)
        )
        canvas.drawPath(
            Path().apply {
                moveTo(cx - r * 0.72f, earBaseY - r * 0.05f)
                lineTo(cx - r * 0.58f, earTipY + r * 0.24f)
                lineTo(cx - r * 0.32f, earBaseY - r * 0.05f)
                close()
            },
            fill(furPink)
        )
        canvas.drawPath(
            Path().apply {
                moveTo(cx + r * 0.72f, earBaseY - r * 0.05f)
                lineTo(cx + r * 0.58f, earTipY + r * 0.24f)
                lineTo(cx + r * 0.32f, earBaseY - r * 0.05f)
                close()
            },
            fill(furPink)
        )

        // 脸
        canvas.drawCircle(cx, cy, r, fill(furWhite))
        canvas.drawCircle(cx, cy, r, stroke(furPink, 1.6f * densityScale))

        // 眼睛 + 高光
        val eyeDx = r * 0.42f
        val eyeY = cy - r * 0.10f
        val eyeRx = r * 0.15f
        val eyeRy = r * 0.20f
        canvas.drawOval(RectF(cx - eyeDx - eyeRx, eyeY - eyeRy, cx - eyeDx + eyeRx, eyeY + eyeRy), fill(inkBrown))
        canvas.drawOval(RectF(cx + eyeDx - eyeRx, eyeY - eyeRy, cx + eyeDx + eyeRx, eyeY + eyeRy), fill(inkBrown))
        val hlR = r * 0.055f
        canvas.drawCircle(cx - eyeDx - eyeRx * 0.35f, eyeY - eyeRy * 0.35f, hlR, fill(Color.WHITE))
        canvas.drawCircle(cx + eyeDx - eyeRx * 0.35f, eyeY - eyeRy * 0.35f, hlR, fill(Color.WHITE))

        // 腮红
        val blushR = r * 0.20f
        val blushY = cy + r * 0.30f
        canvas.drawCircle(cx - r * 0.62f, blushY, blushR, fill(furPinkLight))
        canvas.drawCircle(cx + r * 0.62f, blushY, blushR, fill(furPinkLight))

        // 鼻子
        canvas.drawPath(
            Path().apply {
                moveTo(cx - r * 0.11f, cy + r * 0.20f)
                lineTo(cx + r * 0.11f, cy + r * 0.20f)
                lineTo(cx, cy + r * 0.36f)
                close()
            },
            fill(furPink)
        )

        // 嘴：两段下弧拼成 w 形
        val mouthPaint = stroke(inkBrown, 1.4f * densityScale)
        val mouthW = r * 0.34f
        val mouthH = r * 0.26f
        val mouthTop = cy + r * 0.30f
        canvas.drawArc(RectF(cx - mouthW, mouthTop, cx, mouthTop + mouthH), 0f, 180f, false, mouthPaint)
        canvas.drawArc(RectF(cx, mouthTop, cx + mouthW, mouthTop + mouthH), 0f, 180f, false, mouthPaint)

        // 胡须
        val whiskerPaint = stroke(inkBrown, 1.1f * densityScale).apply { alpha = 150 }
        val whiskerY = cy + r * 0.24f
        for (i in 0 until 2) {
            val dy = i * r * 0.16f
            canvas.drawLine(cx - r * 1.00f, whiskerY + dy, cx - r * 0.52f, whiskerY + dy - r * 0.06f, whiskerPaint)
            canvas.drawLine(cx + r * 1.00f, whiskerY + dy, cx + r * 0.52f, whiskerY + dy - r * 0.06f, whiskerPaint)
        }
    }

    // 构造居中撕纸信纸矩形路径（四周留出卡片边距，撕纸边缘带轻微锯齿）
    private fun drawFeatherLetterPath(path: Path, width: Float, height: Float, densityScale: Float) {
        val marginX = width * 0.06f
        val marginTop = height * 0.05f
        val marginBottom = height * 0.06f
        val left = marginX
        val right = width - marginX
        val top = marginTop
        val bottom = height - marginBottom
        val jitter = 2.0f * densityScale

        path.reset()
        val random = java.util.Random(2024L)
        path.moveTo(left, top)
        // 上边缘
        var x = left
        while (x < right) {
            x += 8f * densityScale
            if (x > right) x = right
            val jy = (random.nextFloat() - 0.5f) * jitter
            path.lineTo(x, top + jy)
        }
        // 右边缘
        var y = top
        while (y < bottom) {
            y += 8f * densityScale
            if (y > bottom) y = bottom
            val jx = (random.nextFloat() - 0.5f) * jitter
            path.lineTo(right + jx, y)
        }
        // 下边缘
        x = right
        while (x > left) {
            x -= 8f * densityScale
            if (x < left) x = left
            val jy = (random.nextFloat() - 0.5f) * jitter
            path.lineTo(x, bottom + jy)
        }
        // 左边缘
        y = bottom
        while (y > top) {
            y -= 8f * densityScale
            if (y < top) y = top
            val jx = (random.nextFloat() - 0.5f) * jitter
            path.lineTo(left + jx, y)
        }
        path.close()
    }

    // 检测位图四周是否带有统一近白留白相框，若是则返回裁剪到内容区(去掉相框)的矩形。
    // 明信片正方形插画常自带白色描边，直接用会在大横幅两侧露出白边，这里沿中轴线向内收缩定位。
    // 通过一条水平中线探测左右边界、一条垂直中线探测上下边界，避免需要整图逐像素扫描。
    private fun detectLightBorder(bmp: Bitmap): android.graphics.Rect {
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
    private fun centerFitRect(rectF: RectF, imageAspect: Float): RectF {
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

    private fun fastBlur(sentBitmap: Bitmap, radius: Int): Bitmap {
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
    private val presetImageCache = object : android.util.LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    @Synchronized
    private fun getPresetImage(context: Context, resName: String, targetWidth: Int, targetHeight: Int): Bitmap? {
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
    private fun decodeFileSampled(path: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calcInSampleSize(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
        }
        return BitmapFactory.decodeFile(path, opts)
    }

    private fun decodeResourceSampled(res: android.content.res.Resources, resId: Int, targetWidth: Int, targetHeight: Int): Bitmap? {
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

    private fun calcInSampleSize(outWidth: Int, outHeight: Int, reqWidth: Int, reqHeight: Int): Int {
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

    // 随机生成撕纸效果 Path
    private fun generateTornPath(width: Float, height: Float, densityScale: Float): Path {
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

    // 绘制拟物纸张颗粒/纤维纹理
    private fun drawPaperTexture(canvas: Canvas, rectF: RectF, textureType: String, densityScale: Float) {
        val random = java.util.Random(TEXTURE_SEED)
        val width = rectF.width()
        val height = rectF.height()
        val left = rectF.left
        val top = rectF.top

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (textureType == "PAPER") {
            // 短细纤维线条与少量点噪点
            val fiberCount = (width * height / 300f).toInt().coerceIn(100, 1000)
            paint.style = Paint.Style.STROKE
            for (i in 0 until fiberCount) {
                val startX = left + random.nextFloat() * width
                val startY = top + random.nextFloat() * height
                val length = (4f + random.nextFloat() * 11f) * densityScale
                val angle = random.nextFloat() * Math.PI * 2
                val endX = startX + (Math.cos(angle) * length).toFloat()
                val endY = startY + (Math.sin(angle) * length).toFloat()

                val isDark = random.nextBoolean()
                paint.color = if (isDark) Color.BLACK else Color.WHITE
                paint.alpha = if (isDark) random.nextInt(8) + 2 else random.nextInt(12) + 4
                paint.strokeWidth = (0.5f + random.nextFloat() * 0.8f) * densityScale

                val ctrlX = (startX + endX) / 2f + (random.nextFloat() - 0.5f) * 3f * densityScale
                val ctrlY = (startY + endY) / 2f + (random.nextFloat() - 0.5f) * 3f * densityScale
                val path = Path().apply {
                    moveTo(startX, startY)
                    quadTo(ctrlX, ctrlY, endX, endY)
                }
                canvas.drawPath(path, paint)
            }

            paint.style = Paint.Style.FILL
            val noiseCount = (width * height / 200f).toInt().coerceIn(100, 800)
            for (i in 0 until noiseCount) {
                val px = left + random.nextFloat() * width
                val py = top + random.nextFloat() * height
                val r = (0.5f + random.nextFloat() * 1.0f) * densityScale

                val isDark = random.nextBoolean()
                paint.color = if (isDark) Color.BLACK else Color.WHITE
                paint.alpha = if (isDark) random.nextInt(10) + 2 else random.nextInt(15) + 5
                canvas.drawCircle(px, py, r, paint)
            }
        } else if (textureType == "GRAIN") {
            // 高密度的细微噪点
            paint.style = Paint.Style.FILL
            val noiseCount = (width * height / 50f).toInt().coerceIn(500, 3000)
            for (i in 0 until noiseCount) {
                val px = left + random.nextFloat() * width
                val py = top + random.nextFloat() * height
                val r = (0.3f + random.nextFloat() * 0.7f) * densityScale

                val isDark = random.nextBoolean()
                paint.color = if (isDark) Color.BLACK else Color.WHITE
                paint.alpha = if (isDark) random.nextInt(12) + 3 else random.nextInt(18) + 6
                canvas.drawCircle(px, py, r, paint)
            }
        }
    }
}