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
import com.juge.app.render.SoilRenderer.SOIL_ART_RATIO
import com.juge.app.render.SoilRenderer.soilWall
import com.juge.app.render.SoilRenderer.soilWallAmp

/**
 * 自 WidgetCanvasRenderer 拆分而来。成员由调用点 import，调用写法不变。
 */
internal object CityRenderer {

    // ==================== 城市微缩（CITY_CUTOUT） ====================
    // 一个风格一张素材：presetImageResName 就是素材名，抠掉天空的微缩城市按**原始宽高比**
    // 摆进组件，天空透明处露出壁纸。没有"按尺寸换素材"那套机制 —— 素材比例与组件比例
    // 不合时，由 drawCityArt 用「等比 contain + 底边贴住衔接线」消化：宁可两侧留壁纸，
    // 也不把天际线切平（切平就没有剪影了）。
    //
    // 城市与文字区怎么接，是**每个风格自己的设计**，不共用画法、也不共用配比：
    //   SOIL —— 江西「城市剪影」：山地古城像从地里整块挖出来，接草皮 → 浅壤 → 深土的剖面
    //   WATER —— 上海「上海微缩」：现代城市立在黄浦江面上，接倒影与波纹，向下渐入深水面
    //   FADE —— 用户自定义素材：不假造任何材质，只把城市底边柔进背景色
    // 新增城市风格时在 cityJunctionOf 里认领一种，别默认套土层。
    enum class CityJunction { SOIL, WATER, FADE }

    const val CITY_NOISE_SEED = 2026f // 固定种子：每次渲染必须同一条曲线，否则桌面组件会抖

    // —— 上海·水面与倒影 ——
    // 城市底边就是水线。水线以下：先一段被压暗、竖向压缩、模糊过的**镜像倒影**，
    // 再叠横向波纹高光，最后向下渐进"小组件背景颜色"那池深水，文字浮在水面上。
    // 现代玻璃楼群做土层剖面会读成"城市被挖出来"，很突兀；立在水面上才是陆家嘴。
    const val WATER_ART_RATIO = 0.52f // 水线在组件高度上的位置（城市占这个高度）

    const val WATER_REFLECT_RATIO = 0.16f // 倒影画多高（占组件高）

    const val WATER_REFLECT_SRC_RATIO = 0.42f // 取城市下部多少高度做倒影源

    // 文字只让出组件高的 7%，其余压给倒影的尾巴：倒影本来就靠向下溶解收尾，
    // 文字落在它淡掉的那段上正好，不必为它单独腾出一整条带 ——
    // 腾多了 4×2 就只剩一行字的位置（18sp 一行约 100px，扁组件根本经不起让）。
    const val WATER_TEXT_BAND_RATIO = 0.07f

    const val WATER_REFLECT_ALPHA = 74 // 倒影本体透明度

    const val WATER_RIPPLE_COUNT = 7 // 波纹条数

    const val WATER_RIPPLE_THIN_DP = 1.1f // 波纹基础粗细

    const val WATER_RIPPLE_ALPHA = 34

    const val WATER_RIPPLE_SALT = 41f // cityHash 的盐：与土层的轮廓噪声分开

    val WATER_SURFACE_COLOR = 0xFF2C5C72.toInt() // 水线处偏亮的江面

    val WATER_HILITE_COLOR = 0xFFBFE3F0.toInt() // 波纹高光

    const val WATER_LINE_ALPHA = 70 // 水线本身那道亮边

    const val WATER_VIGNETTE_ALPHA = 70 // 底部压暗，把文字从水面里托出来

    // —— 各衔接共用 ——
    const val CITY_TEXT_PAD_X_DP = 16f

    // 上下内边距只保底"一行正文不贴边"即可：FADE 下城市要尽量顶满宽度，
    // 文字区每省 1dp 内边距，城市就能多占一截宽度（见 fadeArtHeight）。
    const val CITY_TEXT_PAD_Y_DP = 5f

    const val FADE_ART_RATIO = 0.55f // FADE 衔接：城市占组件高度的**下限**

    const val CITY_FADE_BAND_RATIO = 0.10f // FADE 衔接：城市底边柔进背景色的带高

    // 一行正文的高度 ≈ 字号 × 该系数（含行距）。用来估文字区下限，宁可估大不估小。
    const val FADE_LINE_HEIGHT_FACTOR = 1.1f

    // 垫平带取色时认定"内容"的最低 alpha：软边/噪声边缘像素不算内容，免得取到半透明的脏色。
    const val CITY_PAD_ALPHA_MIN = 128

    // 城市微缩：素材"底边垫平带"的缓存（key = 素材实例 + 像素尺寸）。
    // 同样交给 Canvas 绘制过，不能主动 recycle。
    var cityPadKey: Int = 0

    var cityPadBitmap: Bitmap? = null

    // 城市微缩：这个风格用哪种衔接。按素材名认领——素材就是那座城市的长相，
    // 衔接得跟着材质走：山地古城像从地里挖出来的，配土层剖面；
    // 玻璃楼群立在水边上，配江面倒影。认不出来的（用户自定义抠图）走 FADE，
    // 不假造任何材质，只把城市底边柔进背景色。
    fun cityJunctionOf(style: WidgetStyle): CityJunction =
        when (style.presetImageResName) {
            "shanghai_city_cutout" -> CityJunction.WATER
            // 浙江素材是一整座**飘着的岛**：底边本来就是不规则轮廓，没有"城 ↔ 地/水"的
            // 平直接缝，硬接土层或倒影反而会在岛底两侧造出假材质。走 FADE，
            // 只把岛底柔进文字栏底色，读起来就是一座浮在夜色里的微缩浙江。
            "zhejiang_city_cutout" -> CityJunction.FADE
            // 北京素材底边是模型底座的一条平直边缘，底下没有"地/水"要接，
            // 且文字栏已经取了底座同色 —— 走 FADE 让底座柔进文字栏，接缝直接消失。
            "beijing_city_cutout" -> CityJunction.FADE
            else -> CityJunction.FADE
        }

    // 城市微缩：城市可占的矩形——通栏铺满宽度，**底边就是衔接线**。
    // 高度按衔接各自定：土层要留出剖面层，水面把城市压到水线上沿，
    // FADE 则按素材比例反推"铺满整宽"需要多高（见 fadeArtHeight）。
    fun cityArtRect(
        outerRect: RectF,
        junction: CityJunction,
        style: WidgetStyle,
        densityScale: Float,
        artBitmap: Bitmap?
    ): RectF {
        val artH = when (junction) {
            CityJunction.SOIL -> outerRect.height() * SOIL_ART_RATIO
            CityJunction.WATER -> outerRect.height() * WATER_ART_RATIO
            CityJunction.FADE -> fadeArtHeight(outerRect, style, densityScale, artBitmap)
        }.coerceAtLeast(1f)
        return RectF(outerRect.left, outerRect.top, outerRect.right, outerRect.top + artH)
    }

    // FADE 衔接：城市矩形要多高，素材才能按原始宽高比**铺满整宽**。
    //
    // 铺满不是白给的——城市越高，留给文字区的高度越少，所以给它一道下限：
    // 至少放得下一行正文（按当前字号 + 上下内边距估算）。够，就把城市顶到满宽；
    // 不够，就退回等比留白（cityArtDst 的 contain 会自然接管，宁可两侧露壁纸
    // 也不把天际线切平）。
    //
    // 同时不允许比 FADE_ART_RATIO 更矮：4×4 这类高组件本来就已铺满，
    // 别反过来把它改小、把城市往上挪。
    fun fadeArtHeight(
        outerRect: RectF,
        style: WidgetStyle,
        densityScale: Float,
        artBitmap: Bitmap?
    ): Float {
        val h = outerRect.height()
        val floor = h * FADE_ART_RATIO
        if (artBitmap == null || artBitmap.height <= 0 || outerRect.width() <= 0f) return floor
        val need = outerRect.width() / (artBitmap.width.toFloat() / artBitmap.height)
        val oneLine = style.fontSizeSp * densityScale * FADE_LINE_HEIGHT_FACTOR +
            2f * CITY_TEXT_PAD_Y_DP * densityScale
        val cap = (h - h * CITY_FADE_BAND_RATIO - oneLine).coerceAtLeast(floor)
        return need.coerceIn(floor, cap)
    }

    // 城市微缩：衔接带高度（衔接线 → 文字可用顶边）。文字不许压进倒影/剖面层，
    // 所以取这条带的最低点：土层轮廓的起伏谷底、倒影带的最下沿。
    fun cityBand(junction: CityJunction, outerRect: RectF, densityScale: Float): Float =
        when (junction) {
            CityJunction.SOIL -> {
                val wall = soilWall(outerRect)
                wall + soilWallAmp(wall, densityScale) / 2f
            }
            CityJunction.WATER -> outerRect.height() * WATER_TEXT_BAND_RATIO
            CityJunction.FADE -> outerRect.height() * CITY_FADE_BAND_RATIO
        }

    // 城市剪影：正文区矩形——地层（草皮 + 浅壤）之下，通栏到组件底部。
    // 传入的地层厚度是 wall + amp/2（轮廓最低点），这样文字绝不会压到浅壤上。
    fun cityTextBoxRect(outerRect: RectF, artRect: RectF, band: Float): RectF = RectF(
        outerRect.left,
        artRect.bottom + band,
        outerRect.right,
        outerRect.bottom
    )

    // 城市剪影：底部两角按用户的圆角设置收圆，上沿（土层顶边）保持直角
    fun cityBarPath(outerRect: RectF, top: Float, radius: Float): Path {
        val box = RectF(outerRect.left, top, outerRect.right, outerRect.bottom)
        val path = Path()
        val r = radius.coerceIn(0f, minOf(box.width(), box.height()) / 2f)
        if (r <= 0f) {
            path.addRect(box, Path.Direction.CW)
        } else {
            // 圆角数组顺序：左上x,左上y, 右上x,右上y, 右下x,右下y, 左下x,左下y
            path.addRoundRect(box, floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r), Path.Direction.CW)
        }
        return path
    }

    // 城市剪影：土层轮廓的取值——fract(sin(i·12.9898 + seed·78.233)·43758.5453)。
    // 与出图脚本 tools/juge_widget_cutout_preview.py 是同一算式、同一 seed，
    // 所以离线预览和这里跑出来是同一条曲线、同一批颗粒位置。
    fun cityHash(i: Int, salt: Float = 0f): Float {
        val x = Math.sin(i * 12.9898 + (CITY_NOISE_SEED + salt) * 78.233) * 43758.5453
        return (x - Math.floor(x)).toFloat()
    }

    /**
     * 上海·水面与倒影：城市底边就是水线，水线以下是一整片江面，文字浮在水面上。
     *
     * 为什么这里不用土层：这张图是玻璃幕墙的陆家嘴，把它"从地里整块挖出来"会读成
     * 一截断头楼坐在土上，很突兀。而真实建筑沙盘就是立在水景台座上的。
     * 水也顺带解决了土层那个通栏难题 —— 江面本来就比城市宽，铺满组件宽度是成立的；
     * 土层铺满则会在城市两侧露出"飘在壁纸上的一条草皮"。
     *
     * 四层：水体竖向渐变 → 城市下部镜像压扁成倒影 → 横向波纹把倒影打断 → 底部压暗托字。
     */
    fun drawCityWater(
        canvas: Canvas,
        outerRect: RectF,
        artRect: RectF,
        art: Bitmap?,
        densityScale: Float,
        style: WidgetStyle,
        deepPaint: Paint
    ) {
        val left = outerRect.left
        val right = outerRect.right
        val bottom = outerRect.bottom
        val line = artRect.bottom
        val waterH = bottom - line
        val alpha = (style.backgroundOpacity * 255).toInt().coerceIn(0, 255)
        if (waterH <= 1f || alpha <= 0) return
        val deepColor = deepPaint.color and 0x00FFFFFF

        canvas.save()
        canvas.clipPath(cityBarPath(outerRect, line - 1f, style.cornerRadiusDp * densityScale))

        // 1. 水体：水线处偏亮的江面 → 组件背景色那池深水
        val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, line, 0f, bottom, WATER_SURFACE_COLOR, deepColor, Shader.TileMode.CLAMP
            )
            this.alpha = alpha
        }
        canvas.drawRect(left, line, right, bottom, waterPaint)

        // 2. 倒影：城市下部竖压 + 镜像贴在水线下方，只占城市那一档宽度
        if (art != null && !art.isRecycled) {
            val span = cityArtDst(art, artRect)
            val srcH = (art.height * WATER_REFLECT_SRC_RATIO).toInt().coerceIn(1, art.height)
            val reflectH = outerRect.height() * WATER_REFLECT_RATIO
            val flip = Matrix().apply {
                setScale(1f, -1f)
                postTranslate(0f, srcH.toFloat())
            }
            val band = Bitmap.createBitmap(art, 0, art.height - srcH, art.width, srcH, flip, true)
            try {
                canvas.drawBitmap(
                    band, null, RectF(span.left, line, span.right, line + reflectH),
                    Paint(Paint.FILTER_BITMAP_FLAG).apply {
                        this.alpha = (WATER_REFLECT_ALPHA * alpha / 255f).toInt()
                    }
                )
                // 倒影向下溶解进水里：再压一层水色渐变，越往下越实
                val dissolve = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, line, 0f, line + reflectH,
                        Color.TRANSPARENT, deepColor, Shader.TileMode.CLAMP
                    )
                    this.alpha = alpha
                }
                canvas.drawRect(span.left, line, span.right, line + reflectH, dissolve)
            } finally {
                if (band !== art && !band.isRecycled) band.recycle()
            }
        }

        // 3. 波纹：横向亮线。越往下越宽、越淡、越粗——近处水面才看得清纹理
        val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WATER_HILITE_COLOR
            this.style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val width = right - left
        for (k in 0 until WATER_RIPPLE_COUNT) {
            val t = (k + 1f) / (WATER_RIPPLE_COUNT + 1f)
            val y = line + waterH * t
            val cx = (left + right) / 2f +
                (cityHash(k * 71, WATER_RIPPLE_SALT) - 0.5f) * width * 0.5f
            val len = width * (0.16f + 0.40f * cityHash(k * 13 + 5, WATER_RIPPLE_SALT))
            ripplePaint.strokeWidth = WATER_RIPPLE_THIN_DP * densityScale * (1f + t)
            ripplePaint.alpha = (WATER_RIPPLE_ALPHA * (1f - 0.55f * t) * alpha / 255f).toInt()
            canvas.drawLine(cx - len / 2f, y, cx + len / 2f, y, ripplePaint)
        }

        // 4. 水线：城市与江面交界那道亮边，把"贴在图上"变成"浮在水上"
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WATER_HILITE_COLOR
            this.alpha = (WATER_LINE_ALPHA * alpha / 255f).toInt()
            strokeWidth = 1.2f * densityScale
            this.style = Paint.Style.STROKE
        }
        canvas.drawLine(left, line, right, line, linePaint)

        // 5. 底部压暗：深水托住文字，长句也不会和波纹抢对比度
        val vigTop = line + waterH * 0.35f
        val vig = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, vigTop, 0f, bottom,
                Color.TRANSPARENT, Color.argb(WATER_VIGNETTE_ALPHA, 0, 0, 0),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(left, vigTop, right, bottom, vig)

        canvas.restore()
    }

    /**
     * 兜底衔接：素材名认不出是哪座城（用户自定义抠图）时，不假造任何材质 ——
     * 文字栏刷成背景色，城市底边往下压一段柔和暗裙就当它坐在那儿。
     */
    fun drawCityFade(
        canvas: Canvas,
        outerRect: RectF,
        artRect: RectF,
        densityScale: Float,
        style: WidgetStyle,
        deepPaint: Paint
    ) {
        val line = artRect.bottom
        val alpha = (style.backgroundOpacity * 255).toInt().coerceIn(0, 255)
        if (alpha <= 0) return
        canvas.save()
        canvas.clipPath(cityBarPath(outerRect, line - 1f, style.cornerRadiusDp * densityScale))
        canvas.drawRect(outerRect.left, line, outerRect.right, outerRect.bottom, deepPaint)
        val bandH = outerRect.height() * CITY_FADE_BAND_RATIO
        val skirt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, line, 0f, line + bandH,
                Color.argb(90, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            this.alpha = alpha
        }
        canvas.drawRect(outerRect.left, line, outerRect.right, line + bandH, skirt)
        canvas.restore()
    }

    // 城市微缩：素材等比 contain 进城市矩形之后的**实际落位**——底边贴住衔接线、水平居中。
    // 土层的水线过渡带要按这个矩形铺，不能按整幅组件宽铺：素材左右留白时，
    // 把素材底行拉满全宽会让过渡色跑到壁纸上去。
    fun cityArtDst(bitmap: Bitmap, rectF: RectF): RectF {
        val bw = bitmap.width
        val bh = bitmap.height
        if (bw <= 0 || bh <= 0 || rectF.width() <= 0f || rectF.height() <= 0f) return rectF
        val scale = minOf(rectF.width() / bw, rectF.height() / bh)
        val w = bw * scale
        val h = bh * scale
        return RectF(rectF.centerX() - w / 2f, rectF.bottom - h, rectF.centerX() + w / 2f, rectF.bottom)
    }

    // 城市微缩：把素材**等比 contain** 进城市矩形，底边贴住衔接线、水平居中。
    //
    // 为什么不再裁高度：剪影的全部价值在于天际线是不规则的。一旦按宽度铺满、
    // 把多出来的高度从顶部切平，天线和楼顶就没了，天空抠得再干净也看不出镂空。
    // 所以素材比例与城市矩形不合时，宁可左右留白交给壁纸（读起来仍是"一座飘着的城市"），
    // 也不动天际线一根。
    //
    // 刻意跳过 detectLightBorder：那个检测器是给"自带白色相框的方形明信片插画"准备的，
    // 它只看左右边缘中线是否近白(RGB>228)，而剪影素材的边缘就是城市像素，
    // 一旦某块楼体/广场恰好是浅色就会被判成留白、从两侧往内裁。
    fun drawCityArt(canvas: Canvas, bitmap: Bitmap, rectF: RectF, style: WidgetStyle) {
        val dst = cityArtDst(bitmap, rectF)
        if (dst.width() <= 0f || dst.height() <= 0f) return
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (style.backgroundOpacity * 255).toInt().coerceIn(0, 255)
        }
        canvas.drawBitmap(bitmap, null, dst, paint)
    }

    // 城市微缩：哪些素材需要"垫平底边"。
    //
    // 抠图后素材底边有两种：
    //   ① 噪声化的半透明软边（棋盘格抠图的残留，北京就是这种）—— 底边不是轮廓，是脏边。
    //      按像素底边对齐衔接线，这段透明区就在模型与文字栏之间露出一条壁纸，看着像上下没接上。
    //   ② 真实的轮廓（浙江是一整座飘着的岛，底边就是岛缘）—— 那是造型本身，垫平会把它拉成方块。
    // 所以只有 ① 走垫平，② 保持悬空。
    fun cityNeedsBottomPad(style: WidgetStyle): Boolean =
        when (style.presetImageResName) {
            "beijing_city_cutout" -> true
            else -> false
        }

    // 城市微缩：把素材底边的透明垫高区垫平——画在模型之下、文字栏之上，
    // 用文字栏底色把壁纸挡掉，模型底座看起来就直接"坐"在文字栏上。
    //
    // 填的是**文字栏底色**而不是逐列取模型底边色：模型底边是噪声软边，
    // 逐列取色会把底座边缘那条朱红宫墙也一路拉成竖条（一道一道的脏streak），
    // 反而比缝隙更显眼。统一底色等于让底座边缘直接融进文字栏，正是这个风格要的"无分界"。
    fun drawCityBottomPad(canvas: Canvas, bitmap: Bitmap, rectF: RectF, style: WidgetStyle) {
        val fillColor = if (WidgetStyle.supportsBackgroundColor(style.shape)) {
            style.backgroundColor
        } else {
            Color.TRANSPARENT
        }
        if (Color.alpha(fillColor) <= 0) return
        val pad = cityPadBand(bitmap) ?: return
        if (pad.isRecycled) return
        val dst = cityArtDst(bitmap, rectF)
        if (dst.width() <= 0f || dst.height() <= 0f) return
        val scale = dst.height() / bitmap.height
        val top = dst.bottom - pad.height * scale
        if (top >= dst.bottom - 0.5f) return
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (style.backgroundOpacity * 255).toInt().coerceIn(0, 255)
            colorFilter = PorterDuffColorFilter(fillColor, PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(pad, null, RectF(dst.left, top, dst.right, dst.bottom), paint)
    }

    // 城市微缩：生成"底边垫平带"的**形状蒙版**——宽 = 素材宽，
    // 高 = 整幅最高的一条底边到素材底边的距离。有内容的列整列填白，没内容的列留透明，
    // 所以带子只覆盖模型所在的横向范围；拉伸后由 paint 的 ColorFilter 统一上色。
    fun cityPadBand(bitmap: Bitmap): Bitmap? {
        val key = System.identityHashCode(bitmap) * 31 + bitmap.width * 1009 + bitmap.height
        cityPadBitmap?.let { if (!it.isRecycled && cityPadKey == key) return it }

        val bw = bitmap.width
        val bh = bitmap.height
        if (bw <= 1 || bh <= 2) return null

        val rowBuf = IntArray(bw)
        val hasContent = BooleanArray(bw)
        var remaining = bw
        var minBottom = -1
        var y = bh - 1
        while (y >= 0 && remaining > 0) {
            bitmap.getPixels(rowBuf, 0, bw, 0, y, bw, 1)
            var foundThisRow = false
            for (x in 0 until bw) {
                if (!hasContent[x] && (rowBuf[x] ushr 24) >= CITY_PAD_ALPHA_MIN) {
                    hasContent[x] = true
                    remaining--
                    foundThisRow = true
                }
            }
            if (foundThisRow) minBottom = y
            y--
        }
        if (minBottom < 0) return null

        // 兜底：个别列只有一根细高物、底边高得离谱时，别把带子撑到半张素材高
        val padRows = (bh - 1 - minBottom).coerceAtMost(bh / 4)
        if (padRows <= 0) return null

        val pixels = IntArray(bw * padRows)
        for (x in 0 until bw) {
            if (!hasContent[x]) continue
            for (r in 0 until padRows) {
                pixels[r * bw + x] = 0xFFFFFFFF.toInt()
            }
        }

        val band = Bitmap.createBitmap(bw, padRows, Bitmap.Config.ARGB_8888)
        band.setPixels(pixels, 0, bw, 0, 0, bw, padRows)
        cityPadKey = key
        cityPadBitmap = band
        return band
    }

}
