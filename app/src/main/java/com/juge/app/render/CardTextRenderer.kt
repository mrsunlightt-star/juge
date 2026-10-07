package com.juge.app.render

import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.juge.app.data.WidgetShape
import com.juge.app.render.BookshelfRenderer.SHELF_PANEL_TEXT_PAD_X_DP
import com.juge.app.render.BookshelfRenderer.SHELF_PANEL_TEXT_PAD_Y_DP
import com.juge.app.render.BookshelfRenderer.bookshelfPanelRect
import com.juge.app.render.SplitCardRenderer.SPLIT_CARD_HORIZONTAL_RATIO
import com.juge.app.render.SplitCardRenderer.SPLIT_CARD_RATIO
import com.juge.app.render.SuborRenderer.SUBOR_GLOW_RADIUS_DP
import com.juge.app.render.SuborRenderer.SUBOR_SCREEN_TEXT_PAD_X_DP
import com.juge.app.render.SuborRenderer.SUBOR_SCREEN_TEXT_PAD_Y_DP
import com.juge.app.render.SuborRenderer.suborScreenRect
import com.juge.app.render.WeatherBoxRenderer.WEATHER_BOX_TEXT_PAD_X_DP
import com.juge.app.render.WeatherBoxRenderer.WEATHER_BOX_TEXT_PAD_Y_DP
import com.juge.app.render.WeatherBoxRenderer.weatherBoxCavityRect
import java.util.Locale

/**
 * 正文排版：把样式 + 几何换算成一段落在安全区里的文字，画到卡片上。
 *
 * 从 WidgetCanvasRenderer 的 render() 里抽出，只做「搬家 + 传参」：
 * 绘制顺序、每一处数值、每一支画笔都保持原样。
 */
internal object CardTextRenderer {

    const val FONT_SIZE_SCALE = 1.2f

    /** 正文安全排版区：左上角与可用宽高（宽已按风格收过 100px 下限） */
    class TextBox(val left: Float, val width: Float, val top: Float, val height: Float)

    /**
     * 各风格的正文安全边界，防止文字遮挡人物/装饰。
     *
     * 这是「加风格要显式决定文字落点」的唯一入口：新形状要么落进默认分支，
     * 要么在这里补一个分支，不必回头改 render() 的主管线。
     */
    fun textBoxFor(scene: RenderScene): TextBox {
        val style = scene.style
        val densityScale = scene.densityScale
        val targetWidth = scene.targetWidth.toFloat()
        val targetHeight = scene.targetHeight.toFloat()
        val outerRect = scene.outerRect

        // 穷举 when 且不写 else：新增形状时编译器强制在这里给出文字安全区，
        // 不会静默落进通用布局、把正文压到人物或装饰上。
        return when (style.shape) {
            WidgetShape.SPLIT_CARD_HORIZONTAL -> {
                val paddingLeft = targetWidth * SPLIT_CARD_HORIZONTAL_RATIO + 12f * densityScale
                val paddingRight = targetWidth - 12f * densityScale
                val textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                TextBox(paddingLeft, textWidth, 12f * densityScale, targetHeight - 24f * densityScale)
            }
            WidgetShape.SPLIT_CARD -> {
                val paddingLeft = 16f * densityScale
                val paddingRight = targetWidth - 16f * densityScale
                val cardTop = targetHeight * SPLIT_CARD_RATIO + 12f * densityScale
                TextBox(paddingLeft, paddingRight - paddingLeft, cardTop, targetHeight - cardTop - 12f * densityScale)
            }
            WidgetShape.FEATHER_LETTER -> {
                // 羽毛信纸：文字落在信纸留白区（扩大区域，右侧多留避羽毛笔，顶部避开尖角）
                val verticalInset = targetHeight * 0.16f
                val leftLateral = targetWidth * 0.10f
                val rightLateral = targetWidth * 0.20f
                val paddingLeft = leftLateral
                val paddingRight = targetWidth - rightLateral
                val textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                val cardTop = verticalInset // 顶部让出信封尖角
                TextBox(paddingLeft, textWidth, cardTop, targetHeight - cardTop - targetHeight * 0.14f)
            }
            WidgetShape.PIXEL_RETRO -> {
                // 复古像素：文字落在薄荷绿背景区，避开四周深蓝虚线边框与红框
                val verticalInset = targetHeight * 0.12f
                val lateral = targetWidth * 0.10f
                val paddingLeft = lateral
                val paddingRight = targetWidth - lateral
                val textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                val cardTop = verticalInset // 顶部让出虚线边框
                TextBox(paddingLeft, textWidth, cardTop, targetHeight - cardTop - targetHeight * 0.12f)
            }
            WidgetShape.PET_CAT_NAP -> {
                // 萌宠猫咪趴：橘猫趴在卡片顶部,文字落在卡片渐变中下部(避开猫)
                val verticalInset = targetHeight * 0.42f // 顶部让出猫
                val lateral = targetWidth * 0.12f
                val paddingLeft = lateral
                val paddingRight = targetWidth - lateral
                val textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                val cardTop = verticalInset
                TextBox(paddingLeft, textWidth, cardTop, targetHeight - cardTop - targetHeight * 0.08f)
            }
            WidgetShape.BLUE_NOTE -> {
                // 蓝色便签：顶部让出 NOTE + 信息钮，底部让出米白签条
                val footerH = targetHeight * (76f / 363f)
                val headerH = targetHeight * (76f / 363f) * 0.85f
                val lateral = targetWidth * 0.08f
                val paddingLeft = lateral
                val paddingRight = targetWidth - lateral
                val textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                val cardTop = headerH
                TextBox(paddingLeft, textWidth, cardTop, targetHeight - cardTop - footerH - 8f * densityScale)
            }
            WidgetShape.NIUPI_SHOUZHANG -> {
                // 竹青撕纸 / 撕边牛皮手账：纸即主体，文字居中留白避开撕边与右下阴影
                val verticalInset = targetHeight * 0.11f
                val lateral = targetWidth * 0.11f
                val paddingLeft = lateral
                val paddingRight = targetWidth - lateral
                val textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                val cardTop = verticalInset
                TextBox(paddingLeft, textWidth, cardTop, targetHeight - cardTop - verticalInset)
            }
            WidgetShape.CLASSROOM_BLACKBOARD -> {
                // 教室黑板：文字写在绿色板面上，四周避开木框，底部让出粉笔槽
                val lateral = targetWidth * 0.09f
                val paddingLeft = lateral
                val paddingRight = targetWidth - lateral
                val textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                TextBox(paddingLeft, textWidth, targetHeight * 0.12f, targetHeight * 0.70f)
            }
            WidgetShape.BOOKSHELF -> {
                // 书香书架：正文落在底部米色摘录面板内。书籍区高度固定，面板吃掉组件多出来的高度，
                // 因此组件变高时只有文本区域变大（面板与正文区域用同一个面板矩形，保证文字不越界）
                val panel = bookshelfPanelRect(outerRect, densityScale)
                val textPadX = SHELF_PANEL_TEXT_PAD_X_DP * densityScale
                val textPadY = SHELF_PANEL_TEXT_PAD_Y_DP * densityScale
                val paddingLeft = panel.left + textPadX
                val paddingRight = panel.right - textPadX
                val textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                val cardTop = panel.top + textPadY
                TextBox(paddingLeft, textWidth, cardTop, (panel.bottom - textPadY - cardTop).coerceAtLeast(1f))
            }
            WidgetShape.GIANT_SWORD -> {
                // 巨剑：左侧是扛剑武士，正文只压在右侧剑身金属面上，避开剑柄/护手与上下剑棱。
                // 剑身纵向只占画面约 1/3，这里把可用高度吃满，保证 4×2 规格下也能排出两行
                val paddingLeft = targetWidth * 0.33f
                val paddingRight = targetWidth * 0.93f
                val textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                TextBox(paddingLeft, textWidth, targetHeight * 0.39f, targetHeight * 0.33f)
            }
            WidgetShape.PLUSH_FOREST -> {
                // 毛绒森林：顶部毛绒小树/蘑菇与粉色花边不可压，正文落在奶油色毛绒面板内
                val paddingLeft = targetWidth * 0.115f
                val paddingRight = targetWidth * 0.885f
                val textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                TextBox(paddingLeft, textWidth, targetHeight * 0.40f, targetHeight * 0.47f)
            }
            WidgetShape.SPRING_DOG -> {
                // 春天与小狗：绿框白卡在素材里，正文落在绿框以内的白卡面上。
                // 比例取自素材实测（素材 1812×1012：框内白卡 x 46~1767 / y 388~964），
                // 再各留一点余量，文字不贴框；素材按 STRETCH 铺满，故比例与组件尺寸无关
                val paddingLeft = targetWidth * 0.045f
                val paddingRight = targetWidth * 0.955f
                val textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                TextBox(paddingLeft, textWidth, targetHeight * 0.40f, targetHeight * 0.545f)
            }
            WidgetShape.SUBOR_CONSOLE -> {
                // 小霸王游戏机：素材按 CENTER_FIT 等比完整显示，正文只落在机身屏幕的玻璃区域内。
                // 屏幕矩形由素材内测得的相对位置换算，组件是 4×3 还是 4×4 文字都始终贴在屏幕上
                val screen = suborScreenRect(outerRect)
                val textPadX = SUBOR_SCREEN_TEXT_PAD_X_DP * densityScale
                val textPadY = SUBOR_SCREEN_TEXT_PAD_Y_DP * densityScale
                val paddingLeft = screen.left + textPadX
                val paddingRight = screen.right - textPadX
                val textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                val cardTop = screen.top + textPadY
                TextBox(paddingLeft, textWidth, cardTop, (screen.bottom - textPadY - cardTop).coerceAtLeast(1f))
            }
            WidgetShape.WEATHER_BOX -> {
                // 天气盒子：正文落在腔体下方的白色留白区。留白区从腔体下沿切起，
                // 因此组件变高时多出来的高度全给正文，腔体本身不会被拉长变形
                val cavity = weatherBoxCavityRect(outerRect, densityScale)
                val textPadX = WEATHER_BOX_TEXT_PAD_X_DP * densityScale
                val textPadY = WEATHER_BOX_TEXT_PAD_Y_DP * densityScale
                val paddingLeft = outerRect.left + textPadX
                val paddingRight = outerRect.right - textPadX
                val textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                val cardTop = cavity.bottom + textPadY
                TextBox(paddingLeft, textWidth, cardTop, (outerRect.bottom - textPadY - cardTop).coerceAtLeast(1f))
            }
            WidgetShape.WINTER_PALACE,
            WidgetShape.DEEP_SEA,
            WidgetShape.SUMMER_SEA,
            WidgetShape.SUMMER_LOTUS -> {
                // 画框卡片：正文落在相框下方的整幅留白带（与绘制层同一套布局）
                val spec = FramedCardRenderer.specFor(style.shape)
                val layout = FramedCardRenderer.framedCardRects(outerRect, densityScale, spec)
                val paddingLeft = layout.textRect.left
                val paddingRight = layout.textRect.right
                val textWidth = (paddingRight - paddingLeft).coerceAtLeast(100f)
                val cardTop = layout.textRect.top
                TextBox(paddingLeft, textWidth, cardTop, (layout.textRect.bottom - layout.textRect.top).coerceAtLeast(1f))
            }
            // 内容完全按 rectF / outerRect 布局的形状：通用内边距即可
            // （材质边框族同此：边材质只占轮廓外沿几个 dp，16dp 内边距本就压不到它）
            WidgetShape.RECTANGLE,
            WidgetShape.HANDBOOK_TAPE,
            WidgetShape.TORN_PAPER,
            WidgetShape.PLUSH_CARD,
            WidgetShape.SKETCH_CARD,
            WidgetShape.VINE_CARD,
            WidgetShape.ELLIPSE -> {

            val paddingLeft = 16f * densityScale
            val paddingRight = targetWidth - 16f * densityScale
            // 卡片内缩后文字区域同步内缩，避免长文本越过卡片下沿
            val cardTop = scene.cardInset
            TextBox(paddingLeft, paddingRight - paddingLeft, cardTop, targetHeight - 2f * scene.cardInset)
            }
        }
    }

    /**
     * 5. 正文：构建 StaticLayout 处理折行与省略号，垂直居中落在安全区里。
     *
     * 新付费规则：预览任意风格、桌面默认免费、仅在"同步到桌面"时弹付费，渲染层不感知会员状态。
     */
    fun drawContent(scene: RenderScene) {
        val canvas = scene.canvas
        val context = scene.context
        val style = scene.style
        val scale = scene.scale
        val densityScale = scene.densityScale

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

        // 天空之蓝 "NOTE" 标签绘制
        if (style.presetImageResName == "rectangle_1") {
            val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 14f * densityScale
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            canvas.drawText("NOTE", 20f * densityScale, 28f * densityScale, notePaint)
        }

        val box = textBoxFor(scene)
        val textWidth = box.width
        val cardHeight = box.height

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
        val fullLayout = StaticLayout.Builder.obtain(scene.content, 0, scene.content.length, textPaint, textWidth.toInt())
            .setAlignment(textAlignment)
            .setLineSpacing(0f, style.lineSpacingMultiplier)
            .setIncludePad(true)
            .apply { if (isJustify) setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD) }
            .build()
        val staticLayout = if (fullLayout.lineCount > maxLines) {
            StaticLayout.Builder.obtain(scene.content, 0, scene.content.length, textPaint, textWidth.toInt())
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
        val centerY = box.top + (cardHeight - totalContentHeight) / 2f
        canvas.translate(box.left, centerY.coerceAtLeast(box.top + 4f * densityScale))
        staticLayout.draw(canvas)

        canvas.restore()
    }
}
