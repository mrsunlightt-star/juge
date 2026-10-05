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
import com.juge.app.render.CityRenderer.cityArtDst
import com.juge.app.render.CityRenderer.cityBarPath
import com.juge.app.render.CityRenderer.cityHash

/**
 * 自 WidgetCanvasRenderer 拆分而来。成员由调用点 import，调用写法不变。
 */
internal object SoilRenderer {

    // —— 江西·土层剖面 ——
    // 关键一：**起伏在地层的下沿，不在上沿**。上沿严格平直、紧贴城市底边
    //（草皮顶色从素材底边取色做无缝过渡），下沿才是块状不规则的轮廓，
    // 文字区贴着那条轮廓往下长 —— 这样城市与地层是"同一块地块"，
    // 文字框的上边形状就是地块的断面形状。
    // 关键二：三段配比 **城市 50% / 地层 5% / 文字 45%**，按组件实际像素高度切。
    const val SOIL_ART_RATIO = 0.50f

    const val SOIL_WALL_RATIO = 0.05f

    const val SOIL_GRASS_RATIO = 0.20f

    const val SOIL_EDGE_TRANS_DP = 4f // 素材底边色 → 草皮色的无缝过渡高度

    // 地层下沿的轮廓：粗块（大起伏）+ 细块（碎起伏）叠加，再滑动平均磨圆 →
    // 圆润的下垂地块，而不是城墙垛口。单频方波太机械，真实地块断面是几块深的夹着几块浅的。
    // 幅度必须随 5% 的薄地层同步收小，否则起伏会翻出地层、把草皮顶穿。
    const val SOIL_RIDGE_AMP_RATIO = 0.32f

    const val SOIL_RIDGE_AMP_MIN_DP = 2.5f

    const val SOIL_RIDGE_AMP_MAX_DP = 6.5f

    const val SOIL_RIDGE_BLOCK_DP = 18f // 粗块宽

    const val SOIL_RIDGE_EDGE = 0.20f // 块间过渡带占比（越小越像垂直陡壁）

    const val SOIL_RIDGE_FINE_BLOCK_DP = 8f // 细块宽

    const val SOIL_RIDGE_COARSE_MIX = 0.76f // 粗块权重

    const val SOIL_RIDGE_SMOOTH_DP = 2.5f // 磨圆窗口

    const val SOIL_RIDGE_SAMPLE_DP = 0.5f // 轮廓采样步长

    const val SOIL_GRASS_RIDGE_SCALE = 0.55f // 草皮下沿起伏衰减（凸处草皮薄、凹处厚）

    val SOIL_GRASS_COLOR = 0xFF7E9C4E.toInt()

    val SOIL_TOPSOIL_COLOR = 0xFFB07E52.toInt()

    // 接触阴影：城市底边往下叠淡暗色，柔化"插画底边"与草皮之间那条直切。
    // 上限被草皮高度夹住（见 drawSoilStrata）——5% 地层里草皮只有 1% 组件高，
    // 阴影一旦压满整个草皮，绿色就全被吃掉了。
    const val SOIL_SHADOW_DP = 3f

    const val SOIL_SHADOW_MAX_ALPHA = 52

    const val SOIL_SHADOW_STEPS = 5

    val SOIL_SHADOW_COLOR = 0xFF241B12.toInt()

    // 地层外轮廓的暗边：沿下沿轮廓往上叠渐暗，给下垂的凸块做出体积（只压最外层，
    // 草皮与浅壤之间的分界保持干净）
    const val SOIL_EDGE_SHADE_DP = 5f

    const val SOIL_EDGE_SHADE_ALPHA = 54

    const val SOIL_EDGE_SHADE_STEPS = 6

    // 深土层质感：等厚沉积层理 + 低密度土壤颗粒 + 底部压暗，避免一大块纯色显得空
    const val SOIL_STRATA_SPACING_DP = 22f

    const val SOIL_STRATA_ALPHA = 58

    const val SOIL_STRATA_FOLLOW = 0.5f

    const val SOIL_SPECKLE_BOX_DP = 22f

    const val SOIL_SPECKLE_DENSITY = 0.24f

    const val SOIL_SPECKLE_RADIUS_DP = 1.5f

    const val SOIL_SPECKLE_ALPHA = 46

    const val SOIL_VIGNETTE_ALPHA = 24

    val SOIL_DARK_GRAIN = 0xFF221A12.toInt()

    val SOIL_LIGHT_GRAIN = 0xFF967E62.toInt()

    // 江西·土层：素材底边过渡色带的缓存（key = 素材实例 + 目标像素尺寸）。
    // 缓存出来的 bitmap 会交给 Canvas.drawBitmap，硬件加速下 DisplayList 仍持有它的引用，
    // 所以**不能**主动 recycle —— 换新的一份时把旧的引用丢掉、交给 GC 即可。
    var soilFadeKey: Int = 0

    var soilFadeBitmap: Bitmap? = null

    // 城市剪影：地层厚度（px）——严格占组件高度的 5%（配比硬要求，不再设 dp 上下限：
    // 一旦设下限，4×2 那种扁组件的地层会被顶到 16% 以上，把文字区挤掉）
    fun soilWall(outerRect: RectF): Float {
        return (outerRect.height() * SOIL_WALL_RATIO).coerceAtLeast(1f)
    }

    // 城市剪影：地层下沿轮廓的起伏幅度（px）
    fun soilWallAmp(wall: Float, densityScale: Float): Float {
        val wallDp = wall / densityScale
        return (wallDp * SOIL_RIDGE_AMP_RATIO)
            .coerceIn(SOIL_RIDGE_AMP_MIN_DP, SOIL_RIDGE_AMP_MAX_DP) * densityScale
    }

    // 城市剪影：地层轮廓的块状函数 ∈[0,1]——块内基本恒定，块间窄带 smoothstep 陡降，
    // 形成"平顶块 + 陡壁 + 台阶"。幂次拉伸(hash^1.6)让多数块平缓、少数块明显下垂，
    // 等幅方波太机械，真实的地块断面就是几块深的夹着几块浅的。
    fun ridgeBlock(x: Float, step: Float, edge: Float): Float {
        val t = x / step - 0.5f
        val i = Math.floor(t.toDouble()).toInt()
        val f = (t - i).coerceIn(0f, 1f)
        val a = Math.pow(cityHash(i, 21f).toDouble(), 1.6).toFloat()
        val b = Math.pow(cityHash(i + 1, 21f).toDouble(), 1.6).toFloat()
        if (f <= 1f - edge) return a
        val s = (f - (1f - edge)) / edge
        return a + (b - a) * (s * s * (3f - 2f * s))
    }

    /**
     * 城市剪影：地层下沿轮廓的偏移数组（相对基准线的 px 偏移，中心为 0）。
     *
     * 粗块（大起伏，决定地层的块状）+ 细块（碎起伏，让下沿不平板）叠加后做滑动平均，
     * 把方波磨圆 —— 不磨是城墙垛口，磨过之后才像被切开的下垂地块。
     *
     * 走固定 seed 的整数哈希而不是 Random：① 每次渲染同一条轮廓，桌面组件不会抖；
     * ② 与出图脚本 tools/juge_widget_cutout_terrain.py 同算式同 seed，离线预览即真机。
     */
    fun soilRidgeWiggle(
        width: Float,
        sample: Float,
        amp: Float,
        coarseStep: Float,
        fineStep: Float,
        mix: Float,
        smoothPx: Float
    ): FloatArray {
        val count = (width / sample).toInt() + 2
        val raw = FloatArray(count)
        for (i in 0 until count) {
            val x = i * sample
            raw[i] = ridgeBlock(x, coarseStep, SOIL_RIDGE_EDGE) * mix +
                ridgeBlock(x, fineStep, 0.28f) * (1f - mix)
        }
        val win = (smoothPx / sample / 2f).toInt().coerceAtLeast(1)
        val out = FloatArray(count)
        for (i in 0 until count) {
            val lo = (i - win).coerceAtLeast(0)
            val hi = (i + win + 1).coerceAtMost(count)
            var sum = 0f
            for (k in lo until hi) sum += raw[k]
            out[i] = (sum / (hi - lo) - 0.5f) * amp
        }
        return out
    }

    // 城市剪影：由轮廓偏移数组派生一条地层分界线（基准 depth + 偏移 × 衰减）
    fun strataEdgeYs(baseY: Float, depth: Float, wiggle: FloatArray, scale: Float): FloatArray {
        val out = FloatArray(wiggle.size)
        for (i in wiggle.indices) out[i] = baseY + depth + wiggle[i] * scale
        return out
    }

    // 城市剪影：由一条地层分界线生成"该线以下"的区域路径
    fun strataRegionPath(
        edgeYs: FloatArray,
        left: Float,
        sample: Float,
        right: Float,
        bottom: Float
    ): Path {
        val p = Path()
        for (i in edgeYs.indices) {
            val x = (left + i * sample).coerceAtMost(right)
            val y = edgeYs[i]
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.lineTo(right, bottom)
        p.lineTo(left, bottom)
        p.close()
        return p
    }

    // 城市剪影：两条地层分界线之间的带状路径（草皮带 / 浅壤带 / 轮廓暗边）
    fun strataBandPath(
        topYs: FloatArray,
        botYs: FloatArray,
        left: Float,
        sample: Float,
        right: Float
    ): Path {
        val p = Path()
        for (i in topYs.indices) {
            val x = (left + i * sample).coerceAtMost(right)
            val y = topYs[i]
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        val n = minOf(topYs.size, botYs.size)
        for (i in (n - 1) downTo 0) {
            val x = (left + i * sample).coerceAtMost(right)
            p.lineTo(x, botYs[i])
        }
        p.close()
        return p
    }

    /**
     * 江西·土层剖面：城市底边往下接「草皮 → 浅壤 → 深土」，
     * 最深一层用「小组件背景颜色」，文字就落在这一层里。
     * 这是**江西这一个风格**的衔接，不是城市类的通用做法——上海那版走水面。
     *
     * 整段地层只占组件高度的 5%（`SOIL_WALL_RATIO`），城市 50%、文字 45%。
     * 因为地层薄，腔体内的每一层都要按比例收：草皮 20%、接触阴影 ≤ 草皮 70%、
     * 过渡带 ≤ 草皮 45%，否则 1% 组件高的草皮会被上面几层叠满、绿意全无。
     *
     * 三条分界线共用同一条**块状轮廓偏移**（等厚地层），文字区的上边就是最外面那条轮廓，
     * 所以"文字框的上边形状"等于地块的断面形状 —— 两段是同一块地块，不会各说各话。
     *
     * 与更早版本（起伏做在土层顶边）的根本区别：**起伏搬到了下沿**。
     * 上沿严格平直、紧贴城市底边，再用素材底边色做一段无缝过渡，城市与地层才是一个整体。
     * 深土里再叠底部压暗 / 等厚沉积层理 / 土壤颗粒，避免一大块纯色显得空。
     */
    fun drawSoilStrata(
        canvas: Canvas,
        outerRect: RectF,
        artRect: RectF,
        bgBitmap: Bitmap?,
        densityScale: Float,
        style: WidgetStyle,
        deepPaint: Paint
    ) {
        val left = outerRect.left
        val right = outerRect.right
        val bottom = outerRect.bottom
        val baseY = artRect.bottom
        val wall = soilWall(outerRect)
        val grassH = wall * SOIL_GRASS_RATIO
        val amp = soilWallAmp(wall, densityScale)
        val sample = SOIL_RIDGE_SAMPLE_DP * densityScale
        val soilAlpha = (style.backgroundOpacity * 255).toInt().coerceIn(0, 255)
        if (soilAlpha <= 0) return

        val wiggle = soilRidgeWiggle(
            outerRect.width(), sample, amp,
            SOIL_RIDGE_BLOCK_DP * densityScale,
            SOIL_RIDGE_FINE_BLOCK_DP * densityScale,
            SOIL_RIDGE_COARSE_MIX,
            SOIL_RIDGE_SMOOTH_DP * densityScale
        )
        val flatYs = FloatArray(wiggle.size) { baseY }
        // 草皮的起伏衰减：凸处草皮薄、凹处厚，符合真实剖面
        val grassYs = strataEdgeYs(baseY, grassH, wiggle, SOIL_GRASS_RIDGE_SCALE)
        val wallYs = strataEdgeYs(baseY, wall, wiggle, 1f)
        val deepTop = baseY + wall + amp / 2f

        canvas.save()
        // 裁剪范围包住接触阴影（在 baseY 之下）与底部两角圆角
        canvas.clipPath(
            cityBarPath(outerRect, baseY - 1f, style.cornerRadiusDp * densityScale)
        )

        // 1. 深土（= 文字区底色）：沿最外层轮廓以下铺满
        canvas.drawPath(strataRegionPath(wallYs, left, sample, right, bottom), deepPaint)

        // 2. 浅壤带：[baseY, 最外层轮廓]
        val topsoilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SOIL_TOPSOIL_COLOR
            alpha = soilAlpha
        }
        canvas.drawPath(strataBandPath(flatYs, wallYs, left, sample, right), topsoilPaint)

        // 3. 草皮带：[baseY, 草皮分界]
        val grassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SOIL_GRASS_COLOR
            alpha = soilAlpha
        }
        canvas.drawPath(strataBandPath(flatYs, grassYs, left, sample, right), grassPaint)

        // 4. 素材底边色 → 草皮色的无缝过渡，藏掉"插画底边"与"地层顶边"之间那条直切。
        //    高度被草皮高度夹死在 45% 以内 —— 5% 地层里草皮只有组件高的 1%，
        //    过渡带一旦超过草皮就会把整条草皮盖成素材的灰绿色。
        //    横向只铺**素材实际落位**那一段：素材等比 contain 后比组件窄时，
        //    把素材底行拉满全宽等于把过渡色涂到壁纸上去。
        val transH = minOf(SOIL_EDGE_TRANS_DP * densityScale, grassH * 0.45f)
        val artSpan = if (bgBitmap != null) cityArtDst(bgBitmap, artRect) else outerRect
        if (bgBitmap != null && transH > 1f) {
            drawSoilBottomFade(
                canvas, bgBitmap, artSpan.left, baseY, artSpan.right, transH, soilAlpha)
        }

        // 5. 接触阴影：城市底边往下压几 px 淡暗色，越靠上越深。
        //    同样被草皮高度夹住，否则阴影会吃掉整条草皮；横向同样只压城市那一档
        val shadowH = minOf(SOIL_SHADOW_DP * densityScale, grassH * 0.7f)
        val shadowSteps = SOIL_SHADOW_STEPS
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (k in 0 until shadowSteps) {
            val a = SOIL_SHADOW_MAX_ALPHA *
                Math.pow((1.0 - k.toDouble() / shadowSteps), 1.6)
            shadowPaint.color = SOIL_SHADOW_COLOR
            shadowPaint.alpha = (a * soilAlpha / 255.0).toInt()
            canvas.drawRect(
                artSpan.left,
                baseY + shadowH * k / shadowSteps,
                artSpan.right,
                baseY + shadowH * (k + 1) / shadowSteps,
                shadowPaint
            )
        }

        // 6. 地层外轮廓的暗边：沿最外层轮廓往上叠渐暗，给下垂的凸块做出体积。
        //    只压最外层 —— 草皮与浅壤之间的分界要保持干净，两条线都压就糊成一片。
        val shadeH = minOf(SOIL_EDGE_SHADE_DP * densityScale, (wall - grassH) * 0.9f)
        val shadeSteps = SOIL_EDGE_SHADE_STEPS
        val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (k in 0 until shadeSteps) {
            val a = SOIL_EDGE_SHADE_ALPHA *
                Math.pow((1.0 - k.toDouble() / shadeSteps), 1.4)
            shadePaint.color = SOIL_SHADOW_COLOR
            shadePaint.alpha = (a * soilAlpha / 255.0).toInt()
            val outer = -shadeH * k / shadeSteps
            val inner = -shadeH * (k + 1) / shadeSteps
            canvas.drawPath(
                strataBandPath(
                    FloatArray(wallYs.size) { wallYs[it] + inner },
                    FloatArray(wallYs.size) { wallYs[it] + outer },
                    left, sample, right
                ),
                shadePaint
            )
        }

        // 7. 深土区质感：底部压暗 → 等厚沉积层理 → 土壤颗粒
        val regionH = bottom - deepTop
        if (regionH > 6f * densityScale) {
            val vPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, deepTop, 0f, bottom,
                    Color.TRANSPARENT,
                    Color.argb(SOIL_VIGNETTE_ALPHA, 0, 0, 0),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawPath(strataRegionPath(wallYs, left, sample, right, bottom), vPaint)

            val lines = (regionH / (SOIL_STRATA_SPACING_DP * densityScale))
                .toInt().coerceIn(2, 8)
            val strataPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = SOIL_DARK_GRAIN
                alpha = (SOIL_STRATA_ALPHA * soilAlpha / 255f).toInt()
                strokeWidth = 1.2f * densityScale
                this.style = Paint.Style.STROKE
            }
            for (k in 1..lines) {
                val y0 = deepTop + regionH * k / (lines + 1f)
                val path = Path()
                for (i in wallYs.indices) {
                    val x = (left + i * sample).coerceAtMost(right)
                    // 层理跟随地层下沿起伏但幅度衰减一半：看上去就是一套等厚地层
                    val y = (y0 + (wallYs[i] - baseY - wall) * SOIL_STRATA_FOLLOW)
                        .coerceIn(deepTop, bottom)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                canvas.drawPath(path, strataPaint)
            }

            val box = SOIL_SPECKLE_BOX_DP * densityScale
            val radius = SOIL_SPECKLE_RADIUS_DP * densityScale
            val specklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (SOIL_SPECKLE_ALPHA * soilAlpha / 255f).toInt()
            }
            var j = 0
            var y = deepTop
            while (y < bottom) {
                var i = 0
                var x = left
                while (x < right) {
                    if (cityHash(i * 31 + j * 977, 7f) <= SOIL_SPECKLE_DENSITY) {
                        val px = x + cityHash(i * 13 + j * 71, 11f) * box
                        val py = y + cityHash(i * 17 + j * 53, 13f) * box
                        if (py < bottom) {
                            specklePaint.color = if (cityHash(i * 7 + j * 19, 17f) > 0.5f) {
                                SOIL_LIGHT_GRAIN
                            } else {
                                SOIL_DARK_GRAIN
                            }
                            canvas.drawCircle(px, py, radius, specklePaint)
                        }
                    }
                    i++
                    x += box
                }
                j++
                y += box
            }
        }
        canvas.restore()
    }

    /**
     * 江西·土层：取素材最底一行的颜色拉成一条色带，再向下渐入草皮色。
     * 城市底边（水面 / 广场 / 道路）与草皮之间的那条硬切就藏在这段过渡里。
     */
    fun drawSoilBottomFade(
        canvas: Canvas,
        bitmap: Bitmap,
        left: Float,
        top: Float,
        right: Float,
        height: Float,
        alpha: Int
    ) {
        val bw = bitmap.width
        val bh = bitmap.height
        val w = (right - left).toInt()
        val h = height.toInt()
        if (bw < 2 || bh < 2 || w < 2 || h < 1) return
        val key = System.identityHashCode(bitmap) * 31 + w * 1009 + h
        var band = soilFadeBitmap
        if (band == null || soilFadeKey != key) {
            val row = IntArray(bw)
            bitmap.getPixels(row, 0, bw, 0, bh - 1, bw, 1)
            val rowBmp = Bitmap.createBitmap(bw, 1, Bitmap.Config.ARGB_8888)
            rowBmp.setPixels(row, 0, bw, 0, 0, bw, 1)
            band = Bitmap.createScaledBitmap(rowBmp, w, h, true)
            rowBmp.recycle() // 只喂给 createScaledBitmap，没进过 Canvas，可安全回收
            soilFadeBitmap = band
            soilFadeKey = key
        }
        val fadeBand = band ?: return
        val dst = RectF(left, top, right, top + h)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.alpha = alpha
        }
        canvas.drawBitmap(fadeBand, null, dst, paint)
        val fade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, top, 0f, top + h,
                Color.TRANSPARENT, SOIL_GRASS_COLOR, Shader.TileMode.CLAMP
            )
            this.alpha = alpha
        }
        canvas.drawRect(dst, fade)
    }

}
