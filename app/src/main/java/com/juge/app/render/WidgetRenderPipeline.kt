package com.juge.app.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.juge.app.data.WidgetShape
import com.juge.app.data.WidgetStyle
import com.juge.app.render.BlueNoteRenderer.drawBlueNoteChrome
import com.juge.app.render.BookshelfRenderer.drawBookshelfChrome
import com.juge.app.render.FeatherRenderer.drawFeatherLetterPath
import com.juge.app.render.PaperRenderer.drawPaperTexture
import com.juge.app.render.SplitCardRenderer.SPLIT_CARD_HORIZONTAL_RATIO
import com.juge.app.render.SplitCardRenderer.SPLIT_CARD_RATIO
import com.juge.app.render.SplitCardRenderer.splitImageRect
import com.juge.app.render.SuborRenderer.drawSuborCrtOverlay
import com.juge.app.render.SuborRenderer.drawSuborScreenGlow
import com.juge.app.render.SuborRenderer.suborScreenRect
import com.juge.app.render.TapeRenderer.drawTape
import com.juge.app.render.TornPaperRenderer.TORN_BORDER_ALPHA
import com.juge.app.render.TornPaperRenderer.generateTornPath
import com.juge.app.render.WeatherBoxRenderer.drawWeatherBoxCavity
import com.juge.app.render.WeatherBoxRenderer.weatherBoxCavityRect
import com.juge.app.render.WidgetRenderKernel.decodeFileSampled
import com.juge.app.render.WidgetRenderKernel.getPresetImage
import java.io.File
import timber.log.Timber

// 卡片四周留出的透明边距：给轮廓**外沿**要放的东西腾地方——投影，或材质边框往外长的绒毛/藤叶。
// 阴影绘制在位图内部、材质往轮廓外探，卡片满幅时都会被位图边界切掉。
// **它不再是所有内缩形状的默认值**：是否内缩由 RenderScene.cardInset 判（要投影或外侧装饰才内缩），
// 没有这两样的样式内缩只是白留一圈透壁纸的透明带。
internal const val CARD_INSET_DP = 4f

// 外框圆角的默认值：撕纸/信纸/椭圆的外框只是投影与底色的兜底形状，不跟随用户的圆角设置
internal const val DEFAULT_OUTER_CORNER_RADIUS_DP = 16f

// 「该用方版素材吗」的分界：目标比例低于它就算方形组件。
// 4×2 卡面约 1.97:1、4×4 约 1:1，取中间值即可；用户把组件拖成别的比例时也落在合理一侧。
internal const val SQUARE_ASSET_ASPECT_THRESHOLD = 1.4f

/**
 * 一次渲染的全部状态。
 *
 * 原先这些都是 render() 里的局部变量，函数被拆成若干「阶段」后由本对象传递。
 * 除 [bgBitmap] / [bgFromCache] 外都是构造时算定的只读值；绘制阶段的产物
 * 一律写回画布，不改变这里的几何。
 */
internal class RenderScene(
    val context: Context,
    val content: String,
    val style: WidgetStyle,
    val scale: Float,
    val densityScale: Float,
    val targetWidth: Int,
    val targetHeight: Int,
    val bitmap: Bitmap,
) {
    val canvas = Canvas(bitmap)

    val traits = style.shape.traits()

    /** 内圈形状路径用的矩形，卡片内缩后文字区域同步内缩，避免长文本越过卡片下沿 */
    val rectF: RectF

    /** 外圈兜底矩形：投影 / 底色 / 背景图都按它绘制，防止非铺满形状在外部露出黑色透明像素 */
    val outerRect: RectF

    /**
     * 卡片四周留出的透明边距，**只为「轮廓外沿还有东西要放」留空间**：
     * 投影画在位图内部、材质边往轮廓外探，满幅时都会被位图边界硬切一刀
     * （投影被切 = 卡片看起来"贴平"；绒毛被切 = 变成一圈"剪齐的边"）。
     *
     * 没有投影、也没有外侧装饰的样式一律**不内缩**——那圈透明边除了透出壁纸
     * （以及在 launcher 重新挂载组件、垫上自己的白色占位底时透出那片白）别无用处。
     */
    val cardInset: Float =
        if (traits.insetCapable && style.showCardShadow) CARD_INSET_DP * densityScale else 0f

    /** 用户设置的圆角；整幅插画被强制直角，避免套用预设后继承上一个风格的圆角值把画面切掉 */
    val effectiveCornerRadiusDp: Float =
        if (traits.forcesSquareCorners) 0f else style.cornerRadiusDp

    /** 形状裁切路径（内圈）：撕纸/信纸/椭圆各不相同 */
    val path = Path()

    /** 外框路径：投影、底色、背景图与外圈圆角都用它 */
    val outerPath = Path()

    /**
     * 这次渲染该用**方版素材**吗：目标比例接近方形（4×4）、且这款风格确实带了方版素材。
     *
     * 4×2 卡面约 1.97:1、4×4 约 1:1，取 [SQUARE_ASSET_ASPECT_THRESHOLD] 作分界。
     * 没带方版素材时一律用横版（老数据、以及只出了一份素材的风格，行为与从前一致）。
     */
    val useSquareAsset: Boolean =
        style.presetImageResNameSquare != null &&
            targetWidth.toFloat() / targetHeight.coerceAtLeast(1) < SQUARE_ASSET_ASPECT_THRESHOLD

    val alpha: Int = (style.backgroundOpacity * 255).toInt().coerceIn(0, 255)

    /**
     * 主体四周透明的形状不支持背景色：渲染时强制按透明处理，
     * 让已经落库/落到桌面的旧组件不必重新保存也不会露出包裹卡片
     */
    val effectiveBgColor: Int =
        if (WidgetStyle.supportsBackgroundColor(style.shape)) style.backgroundColor else Color.TRANSPARENT

    /** 整卡底色画笔 */
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 本次渲染加载到的背景图（自定义路径或内置预设），由加载它的阶段负责回收 */
    var bgBitmap: Bitmap? = null

    /** 背景图是否来自预设缓存：来自缓存的图不能回收，由缓存统一管理 */
    var bgFromCache = false

    init {
        val inset = cardInset
        rectF = RectF(inset, inset, targetWidth - inset, targetHeight - inset)
        outerRect = RectF(inset, inset, targetWidth - inset, targetHeight - inset)
        buildShapePaths()
    }

    /**
     * 1. 形状裁切路径。
     *
     * 只对「纯圆角矩形」这一族做内缩：它们的内容完全按 rectF/outerRect 布局，内缩不会溢出；
     * 其余形状（撕纸/八角/信纸/萌宠/像素/书架等）有各自按整幅位图绘制的装饰，保持满幅以免错位。
     */
    private fun buildShapePaths() {
        when (traits.family) {
            ShapeFamily.TORN_PAPER -> {
                path.set(generateTornPath(targetWidth.toFloat(), targetHeight.toFloat(), densityScale))
            }
            ShapeFamily.FEATHER_LETTER -> {
                // 羽毛信纸：居中撕纸信纸矩形，四周留出卡片边距
                drawFeatherLetterPath(path, targetWidth.toFloat(), targetHeight.toFloat(), densityScale)
            }
            ShapeFamily.ELLIPSE -> {
                path.addOval(rectF, Path.Direction.CW)
            }
            else -> {
                val rx = effectiveCornerRadiusDp * densityScale
                if (rx <= 0f) {
                    path.addRect(rectF, Path.Direction.CW)
                } else {
                    path.addRoundRect(rectF, rx, rx, Path.Direction.CW)
                }
            }
        }

        val outerRx = if (traits.followsUserCornerRadius) {
            effectiveCornerRadiusDp * densityScale
        } else {
            DEFAULT_OUTER_CORNER_RADIUS_DP * densityScale
        }
        if (outerRx <= 0f) {
            outerPath.addRect(outerRect, Path.Direction.CW)
        } else {
            outerPath.addRoundRect(outerRect, outerRx, outerRx, Path.Direction.CW)
        }
    }
}

/**
 * 渲染管线。每个阶段只做一件事，`WidgetCanvasRenderer.render` 按顺序调用它们。
 *
 * 与拆分之前的唯一区别是「阶段」被写成了函数、形状判断收敛到 [ShapeTraits]：
 * 绘制顺序、每一处数值、每一支画笔都保持原样。
 */
internal object WidgetRenderPipeline {

    /** 2. 整卡投影 + 底色（渐变 / 纯色） */
    fun drawCardBackground(scene: RenderScene) {
        val canvas = scene.canvas
        val style = scene.style
        val traits = scene.traits
        val densityScale = scene.densityScale

        // 绘制卡片软阴影（移至 clip 外部以防被气泡边界截断）。
        // 整幅透明底的形状没有卡片外框，阴影只该跟着各自的文本框走，因此在家族绘制里单独处理
        if (style.showCardShadow && !traits.transparentCard) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = scene.effectiveBgColor
                if (Color.alpha(scene.effectiveBgColor) < 255) {
                    color = scene.effectiveBgColor or 0xFF000000.toInt()
                }
                // 环境光式的柔影：模糊大、下移小、色浅。
                // 原先 6dp / 下移 3dp / 25% 黑把黑度全挤在卡下沿 1~2dp 里，而内缩只有 4dp，
                // 阴影在组件边界还留着约 10% 黑度就被硬切——浅色壁纸上一圈硬边，看着"重"。
                // 改成大模糊 + 浅色后，到边界只剩 2~3%，切痕看不出来（幅度参照效果图量得的约 8%）
                setShadowLayer(
                    10f * densityScale,
                    0f,
                    1.5f * densityScale,
                    Color.parseColor("#24000000")
                )
            }
            canvas.drawPath(if (traits.fillsAlongTornPath) scene.path else scene.outerPath, shadowPaint)
        }

        // 填充全局大底色（颜色与透明度）
        val bgPaint = scene.bgPaint
        if (style.gradientColors != null && style.gradientColors.size >= 2 &&
            WidgetStyle.supportsBackgroundColor(style.shape)
        ) {
            val w = scene.rectF.width()
            val h = scene.rectF.height()
            val r = Math.sqrt((w * w + h * h).toDouble()) / 2.0
            val cx = scene.rectF.centerX()
            val cy = scene.rectF.centerY()
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
            bgPaint.color = scene.effectiveBgColor
        }
        bgPaint.alpha = scene.alpha
        // 背景色为透明时不填充，避免 alpha 被强制为 255 后把透明底画成黑色。
        if (Color.alpha(scene.effectiveBgColor) > 0 && !traits.transparentCard) {
            if (traits.isSplitCard) {
                // 图文明信片：文字显示区（下半/右半）的底色由下方 panelPaint 单独绘制，
                // 这里只铺图片区，避免同一底色叠两遍导致不透明度失真
                canvas.save()
                canvas.clipPath(scene.outerPath)
                canvas.drawRect(splitImageRect(style.shape, scene.outerRect), bgPaint)
                canvas.restore()
            } else {
                canvas.drawPath(if (traits.fillsAlongTornPath) scene.path else scene.outerPath, bgPaint)
            }
        }
    }

    /** 3. 背景图：优先自定义路径，次之内置预设插画 */
    fun drawBackgroundImage(scene: RenderScene) {
        val canvas = scene.canvas
        val context = scene.context
        val style = scene.style
        val traits = scene.traits

        if (!style.backgroundImagePath.isNullOrEmpty()) {
            try {
                val file = File(style.backgroundImagePath)
                if (file.exists()) {
                    scene.bgBitmap = decodeFileSampled(file.absolutePath, scene.targetWidth, scene.targetHeight)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load background image from path")
            }
        } else {
            val resName = if (traits.isSplitCard) {
                // 图文明信片：没有指定素材时退回默认插画
                style.presetImageResName ?: "bg_illustration_1"
            } else if (scene.useSquareAsset) {
                // 整幅贴图类：带了方版素材就用方版，避免一张 1.75:1 的图在 4×4 上被纵向拉 76%
                style.presetImageResNameSquare
            } else {
                // 一个风格一张素材，presetImageResName 就是素材名
                style.presetImageResName
            }
            if (!resName.isNullOrEmpty()) {
                try {
                    val preset = getPresetImage(context, resName, scene.targetWidth, scene.targetHeight)
                    scene.bgBitmap = preset
                    scene.bgFromCache = preset != null
                } catch (t: Throwable) {
                    Timber.e(t, "Failed to load preset background image")
                }
            }
        }

        // 绘制背景图片（若有）。
        // 天气盒子 / 画框卡片族的素材由家族绘制单独摆放，不走这里的整卡铺图
        val bg = scene.bgBitmap
        if (bg != null && !traits.drawsOwnBackground) {
            canvas.save()
            canvas.clipPath(if (traits.fillsAlongTornPath) scene.path else scene.outerPath)
            if (traits.isSplitCard) {
                BgImageRenderer.drawBgImage(canvas, bg, splitImageRect(style.shape, scene.outerRect), style)
            } else {
                BgImageRenderer.drawBgImage(
                    canvas, bg,
                    if (traits.fillsAlongTornPath) scene.rectF else scene.outerRect,
                    style
                )
            }
            canvas.restore()

            // 自定义路径图片随本次渲染释放；预设图由缓存统一管理
            if (!scene.bgFromCache && !bg.isRecycled) {
                bg.recycle()
            }
        }
    }

    /**
     * 4. 家族装饰。
     *
     * 每个形状只会命中一个分支，因此这里用 `when` 是安全的：原先那串
     * `if (style.shape == X)` 之间互斥，顺序无关，收敛成家族分派后也一样。
     */
    fun drawFamilyChrome(scene: RenderScene) {
        when (scene.traits.family) {
            ShapeFamily.SPLIT_CARD -> drawSplitCardPanel(scene)
            // 小霸王游戏机：素材的显像管玻璃原本是"未通电"的深灰玻璃，
            // 这里在屏幕区域内叠一层绿色荧光底，让屏幕看起来是开机的
            ShapeFamily.SUBOR_CONSOLE -> drawSuborScreenGlow(
                scene.canvas, suborScreenRect(scene.outerRect), scene.densityScale
            )
            // 蓝色便签：在蓝色大底上追加顶部 NOTE 区域与底部米白签条
            ShapeFamily.BLUE_NOTE -> drawBlueNoteChrome(
                scene.canvas, scene.targetWidth.toFloat(), scene.targetHeight.toFloat(),
                scene.outerRect, scene.outerPath, scene.densityScale, scene.style, scene.context
            )
            // 书香书架：顶部彩色书脊立在横板上，底部米色摘录面板
            ShapeFamily.BOOKSHELF -> drawBookshelfChrome(
                scene.canvas, scene.outerRect, scene.densityScale, scene.style, scene.context
            )
            ShapeFamily.WEATHER_BOX -> drawWeatherBoxChrome(scene)
            ShapeFamily.FRAMED_CARD -> drawFramedCardChrome(scene)

            ShapeFamily.ROUND_RECT,
            ShapeFamily.TORN_PAPER,
            ShapeFamily.FEATHER_LETTER,
            ShapeFamily.ELLIPSE,
            ShapeFamily.HANDBOOK_TAPE -> Unit
        }
    }

    /**
     * 图文明信片：文字显示区（下半/右半）的底色由代码绘制，不是背景图片的一部分，
     * 因此跟随"小组件背景颜色"自定义，并同样受背景不透明度控制。
     * 预设背景色为白色，默认观感与旧版一致。
     */
    private fun drawSplitCardPanel(scene: RenderScene) {
        val canvas = scene.canvas
        val style = scene.style
        val outerRect = scene.outerRect
        val densityScale = scene.densityScale

        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.backgroundColor
            this.alpha = scene.alpha
        }
        canvas.save()
        canvas.clipPath(scene.outerPath)
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

    /**
     * 天气盒子：白色盒体正面挖一个内凹方腔，腔底铺蓝天微缩城市素材，
     * 腔口画内壁暗面 + 下沿高光，形成"凹进去"的体积感；腔下留白即正文区。
     */
    private fun drawWeatherBoxChrome(scene: RenderScene) {
        val cavity = weatherBoxCavityRect(scene.outerRect, scene.densityScale)
        drawWeatherBoxCavity(scene.canvas, cavity, scene.bgBitmap, scene.style, scene.densityScale, scene.alpha)
        val bg = scene.bgBitmap
        if (bg != null && !scene.bgFromCache && !bg.isRecycled) {
            bg.recycle()
        }
    }

    /**
     * 画框卡片族（雪落宫墙/深海鲸歌/夏天的海/夏日荷花）：卡纸由背景色铺好
     *（含纸张颗粒），这里叠相框与点缀层
     */
    private fun drawFramedCardChrome(scene: RenderScene) {
        val spec = FramedCardRenderer.specFor(scene.style.shape)
        val layout = FramedCardRenderer.framedCardRects(scene.outerRect, scene.densityScale, spec)
        FramedCardRenderer.drawFramedCard(
            scene.canvas, scene.context, scene.outerPath, spec, layout,
            scene.targetWidth, scene.targetHeight, scene.densityScale, scene.alpha
        )
    }

    /** 5. 纸张颗粒/纤维纹理与卡片描边 */
    fun drawTextureAndBorder(scene: RenderScene) {
        val canvas = scene.canvas
        val style = scene.style
        val traits = scene.traits
        val densityScale = scene.densityScale

        // 纸张颗粒/纤维纹理 (作用于全局大卡片上，效果更拟真一致)
        if (style.textureType == "PAPER" || style.textureType == "GRAIN") {
            drawPaperTexture(
                canvas,
                if (traits.fillsAlongTornPath) scene.rectF else scene.outerRect,
                style.textureType, densityScale
            )
        }

        // 绘制卡片描边 (Border)与撕裂白边
        if (traits.fillsAlongTornPath) {
            val tornBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                this.strokeWidth = 1.0f * densityScale
                this.color = Color.parseColor("#EAEAEA")
                this.alpha = TORN_BORDER_ALPHA
            }
            drawBorderInsideCard(canvas, scene.path, tornBorderPaint)
        }

        if (style.cardBorderWidthDp > 0f) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                // ×2 是刻意的：描边是**居中**画的，而 drawBorderInsideCard 会把外侧那一半裁掉，
                // 所以想得到 cardBorderWidthDp 那么宽的可见边框，标称线宽得给两倍。
                // 这样字段的含义就是「可见边框宽度」，与名字一致——
                // 此前它实际只画出标称值的一半，纯色圆角的 1dp 一直只画出 0.5dp。
                this.strokeWidth = style.cardBorderWidthDp * 2f * densityScale
                this.color = style.cardBorderColor
            }
            drawBorderInsideCard(canvas, scene.path, borderPaint)
        }
    }

    /**
     * 描边只画在卡片**内部**。
     *
     * STROKE 是居中描边：不裁的话有一半落在卡片外的透明内缩上、压在桌面壁纸上——
     * 浅色壁纸上看是一条脏边，深色壁纸上看就是「卡片外多出一圈白框」，
     * 而设计稿里的边框（如纹理山水那圈白）本来就在图片内部。
     * 裁到卡片路径后描边全部落在卡面内，外侧那半截不再压到壁纸上。
     *
     * ⚠️ 代价是**只剩一半线宽可见**：调用方给 `cardBorderWidthDp` 这类"可见宽度"字段时，
     * 标称线宽要按两倍给（见 [drawTextureAndBorder] 里卡片描边那处）。撕纸那条白边是内部固定宽度，
     * 按原样给即可。
     */
    private fun drawBorderInsideCard(canvas: Canvas, path: Path, paint: Paint) {
        val save = canvas.save()
        canvas.clipPath(path)
        canvas.drawPath(path, paint)
        canvas.restoreToCount(save)
    }

    /** 6. 压在正文之上的叠加层 */
    fun drawTopOverlays(scene: RenderScene) {
        val densityScale = scene.densityScale

        // 小霸王游戏机：扫描线 + 暗角 + 玻璃反光压在文字之上，
        // 让文字看起来是"透过显像管玻璃"看到的，而不是贴在图上
        if (scene.traits.family == ShapeFamily.SUBOR_CONSOLE) {
            drawSuborCrtOverlay(scene.canvas, suborScreenRect(scene.outerRect), densityScale)
        }

        // 手账胶带贴纸：左上角黄胶带 + 右下角粉胶带
        if (scene.traits.family == ShapeFamily.HANDBOOK_TAPE) {
            drawTape(
                scene.canvas,
                24f * densityScale, 16f * densityScale,
                70f * densityScale, 18f * densityScale,
                -18f, Color.parseColor("#80FFF176"), densityScale
            )
            drawTape(
                scene.canvas,
                scene.targetWidth - 24f * densityScale, scene.targetHeight - 16f * densityScale,
                70f * densityScale, 18f * densityScale,
                18f, Color.parseColor("#80FF8A80"), densityScale
            )
        }
    }
}
