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
        // 其余形状（撕纸/八角/信纸/萌宠/像素等）有各自按整幅位图绘制的装饰，保持满幅以免错位
        val usesInsetCard = style.shape == WidgetShape.RECTANGLE ||
            style.shape == WidgetShape.HANDBOOK_TAPE ||
            style.shape == WidgetShape.SPLIT_CARD ||
            style.shape == WidgetShape.SPLIT_CARD_HORIZONTAL
        val cardInset = if (usesInsetCard) CARD_INSET_DP * densityScale else 0f
        val offsetY = cardInset
        val rectF = RectF(cardInset, offsetY, targetWidth - cardInset, targetHeight - cardInset)
        
        when (style.shape) {
            WidgetShape.RECTANGLE, WidgetShape.HANDBOOK_TAPE, WidgetShape.SPLIT_CARD, WidgetShape.SPLIT_CARD_HORIZONTAL, WidgetShape.PIXEL_RETRO, WidgetShape.PET_CAT_NAP, WidgetShape.BLUE_NOTE, WidgetShape.ZHU_QING_SI_ZHI -> {
                val rx = style.cornerRadiusDp * densityScale
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
            WidgetShape.METAL_OCTAGON -> {
                // 金属八角形：4 个角切角，构造 8 顶点外框剪裁
                drawOctagonPath(path, targetWidth.toFloat(), targetHeight.toFloat(), densityScale)
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
            WidgetShape.PIXEL_RETRO, WidgetShape.PET_CAT_NAP, WidgetShape.ZHU_QING_SI_ZHI ->
                style.cornerRadiusDp * densityScale
            else -> DEFAULT_OUTER_CORNER_RADIUS_DP * densityScale
        }
        val outerRect = RectF(cardInset, offsetY, targetWidth - cardInset, targetHeight - cardInset)
        if (style.shape == WidgetShape.METAL_OCTAGON) {
            // 金属八角：外框同样用八角形剪裁，保证四角不外露
            drawOctagonPath(outerPath, targetWidth.toFloat(), targetHeight.toFloat(), densityScale)
        } else if (outerRx <= 0f) {
            outerPath.addRect(outerRect, Path.Direction.CW)
        } else {
            outerPath.addRoundRect(outerRect, outerRx, outerRx, Path.Direction.CW)
        }
        
        // 绘制卡片软阴影（移至 clip 外部以防被气泡边界截断）
        if (style.showCardShadow) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.backgroundColor
                if (Color.alpha(style.backgroundColor) < 255) {
                    color = style.backgroundColor or 0xFF000000.toInt()
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
        if (style.gradientColors != null && style.gradientColors.size >= 2) {
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
            bgPaint.color = style.backgroundColor
        }
        bgPaint.alpha = alpha
        // 背景色为透明时不填充，避免 alpha 被强制为 255 后把透明底画成黑色
        if (Color.alpha(style.backgroundColor) > 0) {
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

        // 金属八角骑士比剑：用 Canvas 绘制八角金属边框 + 顶部双骑士 + 浅灰留白文字区
        if (style.shape == WidgetShape.METAL_OCTAGON) {
            drawMetalOctagonFrame(canvas, path, targetWidth.toFloat(), targetHeight.toFloat(), densityScale, style, context)
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
                setShadowLayer(
                    style.shadow.radius,
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
            } else if (style.shape == WidgetShape.METAL_OCTAGON) {
                // 文字落在浅灰留白区：避开顶部双骑士与四周金属边框
                val lateral = targetWidth * 0.12f
                paddingLeft = lateral
                paddingRight = targetWidth - lateral
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = targetHeight * 0.22f // 顶部让出骑士与上边框
                cardHeight = targetHeight - cardTop - targetHeight * 0.14f // 底部让出下边框
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
            } else if (style.shape == WidgetShape.ZHU_QING_SI_ZHI) {
                // 竹青撕纸：米白锯齿纸即主体，文字居中留白避开锯齿边与右下阴影
                val verticalInset = targetHeight * 0.11f
                val lateral = targetWidth * 0.11f
                paddingLeft = lateral
                paddingRight = targetWidth - lateral
                textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                cardTop = verticalInset
                cardHeight = targetHeight - cardTop - verticalInset
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

            // C. 绘制手账胶带贴纸 (HANDBOOK_TAPE)
            if (style.shape == WidgetShape.HANDBOOK_TAPE) {
                drawTape(canvas, 24f * densityScale, 16f * densityScale, 70f * densityScale, 18f * densityScale, -18f, Color.parseColor("#80FFF176"), densityScale) // 左上角黄胶带
                drawTape(canvas, targetWidth - 24f * densityScale, targetHeight - 16f * densityScale, 70f * densityScale, 18f * densityScale, 18f, Color.parseColor("#80FF8A80"), densityScale) // 右下角粉胶带
            }

        return bitmap
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

    // 构造金属八角形路径：4 个角切角，8 个顶点
    private fun drawOctagonPath(path: Path, width: Float, height: Float, densityScale: Float) {
        path.reset()
        val cut = minOf(width, height) * 0.08f
        // 顺时针：从左上切角开始
        path.moveTo(cut, 0f)
        path.lineTo(width - cut, 0f)
        path.lineTo(width, cut)
        path.lineTo(width, height - cut)
        path.lineTo(width - cut, height)
        path.lineTo(cut, height)
        path.lineTo(0f, height - cut)
        path.lineTo(0f, cut)
        path.close()
    }

    // 绘制金属八角边框 + 顶部双骑士 + 浅灰留白文字区
    private fun drawMetalOctagonFrame(
        canvas: Canvas,
        octagonPath: Path,
        width: Float,
        height: Float,
        densityScale: Float,
        style: WidgetStyle,
        context: Context
    ) {
        // 1. 金属拉丝渐变（亮银 → 中灰 → 暗银），模拟金属质感的立体感
        val metalGradient = LinearGradient(
            0f, 0f, width, height,
            intArrayOf(
                Color.parseColor("#F2F4F6"),
                Color.parseColor("#B8BFC7"),
                Color.parseColor("#88929B"),
                Color.parseColor("#C8CFD6")
            ),
            null,
            Shader.TileMode.CLAMP
        )

        canvas.save()

        // 2. 八角形外框整体剪裁，保证金属边框不溢出
        val clip = Path()
        drawOctagonPath(clip, width, height, densityScale)
        canvas.clipPath(clip)

        // 3. 填充整块金属底色（外框区域）
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = metalGradient }
        canvas.drawRect(0f, 0f, width, height, basePaint)

        // 4. 绘制外层金属高光描边（八角轮廓亮边）
        val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 2f * densityScale
            color = Color.parseColor("#F5F7F9")
        }
        canvas.drawPath(clip, edgePaint)

        // 5. 内圈八角凹槽：留出金属边框厚度后，内部填充浅灰留白文字区
        val innerCut = minOf(width, height) * 0.08f + (minOf(width, height) * 0.09f)
        val inset = minOf(width, height) * 0.10f // 边框厚度
        val innerPath = Path()
        innerPath.moveTo(innerCut, inset)
        innerPath.lineTo(width - inset - innerCut + inset, inset) // 上边
        innerPath.lineTo(width - inset, innerCut)
        innerPath.lineTo(width - inset, height - inset - innerCut + inset) // 右边
        innerPath.lineTo(width - inset - innerCut + inset, height - inset)
        innerPath.lineTo(innerCut, height - inset)
        innerPath.lineTo(inset, height - inset - innerCut + inset) // 左边
        innerPath.lineTo(inset, innerCut)
        innerPath.close()

        // 内圈凹槽：先填深灰（金属内圈阴影），形成边框厚度感
        val innerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6B737C")
        }
        canvas.drawPath(innerPath, innerShadowPaint)

        // 6. 填充浅灰留白文字区（比内圈凹槽再往内收缩，留出金属边框宽度）
        val padding = minOf(width, height) * 0.06f
        val textBgRect = RectF(
            inset + padding,
            inset + padding,
            width - inset - padding,
            height - inset - padding
        )
        val textBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.backgroundColor.takeIf { it != 0 } ?: Color.parseColor("#EDEFF2")
        }
        // 留白区用圆角矩形，贴合参考图内部圆角留白
        val textBgRadius = minOf(width, height) * 0.03f
        canvas.drawRoundRect(textBgRect, textBgRadius, textBgRadius, textBgPaint)

        // 7. 绘制顶部中央双骑士比剑
        try {
            val knights = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.metal_knights)
            if (knights != null) {
                val knightsW = width * 0.42f
                val knightsH = height * 0.22f
                val knightsLeft = (width - knightsW) / 2f
                val knightsTop = height * 0.015f
                knights.setBounds(
                    knightsLeft.toInt(),
                    knightsTop.toInt(),
                    (knightsLeft + knightsW).toInt(),
                    (knightsTop + knightsH).toInt()
                )
                knights.draw(canvas)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to draw metal knights")
        }

        // 8. 绘制内圈金属边框的亮色高光（增强立体感）
        val innerEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 1.5f * densityScale
            color = Color.parseColor("#DCE1E6")
        }
        canvas.drawPath(innerPath, innerEdgePaint)

        canvas.restore()
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
                val w = processedBitmap.width
                val h = processedBitmap.height
                val srcRatio = w.toFloat() / h.toFloat()
                val targetRatio = rectF.width() / rectF.height()
                val srcRect = android.graphics.Rect(0, 0, w, h)
                val dstRect: RectF
                if (srcRatio > targetRatio) {
                    // 图更宽：以宽为准，上下留透明
                    val dstH = rectF.width() / srcRatio
                    val top = rectF.centerY() - dstH / 2f
                    dstRect = RectF(rectF.left, top, rectF.right, top + dstH)
                } else {
                    // 图更高：以高为准，左右留透明
                    val dstW = rectF.height() * srcRatio
                    val left = rectF.centerX() - dstW / 2f
                    dstRect = RectF(left, rectF.top, left + dstW, rectF.bottom)
                }
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