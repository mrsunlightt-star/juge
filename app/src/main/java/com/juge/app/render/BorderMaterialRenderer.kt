package com.juge.app.render

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import com.juge.app.data.WidgetShape
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * 材质边框族（毛绒边框 / 素描线卡 / 绿藤缠绕）：卡片本身就是那种材质，
 * 边是沿轮廓**现画的材质**，而不是一条纯色描边。
 *
 * 这里刻意**不做成能贴到任意风格上的通用开关**：材质若人人可加，34 款风格就只剩
 * "同一张卡换了道边"，用户也就无所谓选哪一款了。因此材质绑定在形状上——
 * 加一种材质 = 加一个 `WidgetShape` + 一份 [BorderMaterialSpec]，见 `ShapeTraits.family()`。
 *
 * 三个形状都在 `ShapeTraits.INSET_CAPABLE_SHAPES` 里，且都带 `decoratesOutsideContour`——
 * 也就是说它们**必定内缩**（不是因为开了投影，而是因为绒毛、藤叶要往轮廓外长），
 * 四周那 4dp 透明边就是材质的生长空间：不内缩就会被位图边界齐齐削平，变成一圈"剪齐的边"。
 * 下面每个材质的内推量都是按这 4dp 预算定的。
 *
 * 三种材质都由代码现画、不依赖素材，因此组件拖多大都清晰，也不增加包体。
 * 轮廓取点走 [PathMeasure]，所以圆角跟着用户的圆角设置走，拧到 0 或 30 都不散架。
 */
internal object BorderMaterialRenderer {

    private const val MATERIAL_SKETCH = "SKETCH"
    private const val MATERIAL_PLUSH = "PLUSH"
    private const val MATERIAL_VINE = "VINE"

    /** 一种材质：画法 + 主色。卡面配色是预设的事，这里只管边 */
    class BorderMaterialSpec(val material: String, val color: Int)

    private val PLUSH_SPEC = BorderMaterialSpec(MATERIAL_PLUSH, 0xFFEFD5B0.toInt()) // 奶油绒
    private val SKETCH_SPEC = BorderMaterialSpec(MATERIAL_SKETCH, 0xFF3F4145.toInt()) // 石墨灰
    private val VINE_SPEC = BorderMaterialSpec(MATERIAL_VINE, 0xFF4E7A3A.toInt()) // 藤叶绿

    /** 本族的形状 → 材质规格。与 `FramedCardRenderer.specFor` 同一套做法：新增风格只加一行 */
    fun specFor(shape: WidgetShape): BorderMaterialSpec? = when (shape) {
        WidgetShape.PLUSH_CARD -> PLUSH_SPEC
        WidgetShape.SKETCH_CARD -> SKETCH_SPEC
        WidgetShape.VINE_CARD -> VINE_SPEC
        else -> null
    }

    fun draw(canvas: Canvas, path: Path, spec: BorderMaterialSpec, densityScale: Float) {
        when (spec.material) {
            MATERIAL_SKETCH -> drawSketch(canvas, path, spec.color, densityScale)
            MATERIAL_PLUSH -> drawPlush(canvas, path, spec.color, densityScale)
            MATERIAL_VINE -> drawVine(canvas, path, spec.color, densityScale)
        }
    }

    // —— 素描 ——
    // 三道笔触**压在一起**才算一条手绘线：把 lane 拉开到 0.85dp 时，三道线会散成三根平行绳，
    // 读起来像打了三遍草稿而不是一条线。
    private const val SKETCH_STEP_DP = 1.2f
    private const val SKETCH_WOBBLE_DP = 0.75f
    private const val SKETCH_INSET_DP = 0.6f

    /**
     * 一道笔触：粗细 + 深浅 + 侧移 + 相位。
     *
     * **每道笔触是一条完整路径、整道一个粗细**，不是切成若干段各自变粗细：
     * 分段改粗细时，相邻段的圆头笔帽会互相顶出来，沿线串成一排"珠子"。
     * 笔压的变化改由三道之间体现——宽而浅的一道 + 细而深的一道 + 极细的一道，
     * 叠起来就是"重描过一遍又轻勾过一遍"的手感。
     */
    private class SketchPass(val widthDp: Float, val alpha: Int, val laneDp: Float, val phase: Float)

    private val SKETCH_PASSES = listOf(
        SketchPass(widthDp = 1.05f, alpha = 120, laneDp = 0.34f, phase = 2.7f),
        SketchPass(widthDp = 0.62f, alpha = 175, laneDp = -0.30f, phase = 0f),
        SketchPass(widthDp = 0.44f, alpha = 205, laneDp = 0.02f, phase = 5.1f),
    )

    // —— 毛绒 ——
    private const val PLUSH_SEED = 20261007L
    private const val PLUSH_STEP_DP = 0.4f
    private const val PLUSH_BASE_WIDTH_DP = 2.8f
    private const val PLUSH_BASE_INSET_DP = 2.0f
    private const val PLUSH_SPREAD_RAD = 0.95f

    /** 一层绒毛：长度区间 + 深浅。短绒暗而贴底、长绒亮而外翘，叠出厚度 */
    private class PlushLayer(val minDp: Float, val maxDp: Float, val alpha: Int, val tint: Float)

    // 四层**同时改变长度与深浅**：只变长度的话，外沿仍是一圈深浅一致的锯齿。
    private val PLUSH_LAYERS = listOf(
        PlushLayer(0.9f, 1.8f, 140, -0.26f),
        PlushLayer(1.6f, 2.8f, 205, -0.06f),
        PlushLayer(2.6f, 4.0f, 240, 0.05f),
        PlushLayer(3.4f, 5.4f, 185, 0.32f),
    )

    // —— 绿藤 ——
    private const val VINE_SEED = 20261008L
    private const val VINE_STEP_DP = 1.4f
    private const val VINE_STEM_INSET_DP = 1.8f
    // 波幅与周期：藤条要"蜿蜒"而不是"锯齿/丝带"。波幅给大了（1.6dp）配上短周期（28dp），
    // 读出来是一条波浪丝带，完全不像藤——藤的波是**又缓又长**的。
    private const val VINE_WAVE_AMP_DP = 1.4f
    private const val VINE_WAVE_LEN_DP = 36f
    private const val VINE_STEM_WIDTH_DP = 1.1f
    private const val VINE_PETIOLE_DP = 1.6f
    private const val VINE_LEAF_SPACING_DP = 11f
    private const val VINE_LEAF_LEN_DP = 6.0f
    // 叶片相对**切线**张开的角度。从这个基准量、而不是从法线量：叶子顺着藤长，
    // 才是"藤上长叶"；从法线量就成了朝外扎的一圈倒刺。
    private const val VINE_LEAF_BASE_ANGLE_RAD = 0.30f
    private const val VINE_LEAF_FAN_RAD = 0.55f

    /** 沿轮廓等距采样出的一条折线，附带每点的朝外法线与切线 */
    private class Contour(
        val count: Int,
        val x: FloatArray,
        val y: FloatArray,
        val ox: FloatArray,
        val oy: FloatArray,
        val tx: FloatArray,
        val ty: FloatArray,
    )

    /**
     * 沿 [path] 等距采样，并把每个采样点按 [insetDp] 往轮廓内侧推。
     *
     * 朝外方向的基准取形状包围盒中心：凸形状（圆角矩形）下始终成立，
     * 不依赖路径的绕向——`addRoundRect` 与撕纸路径的绕向本来就未必一致。
     */
    private fun sampleContour(path: Path, stepPx: Float, insetDp: Float, densityScale: Float): Contour? {
        val measure = PathMeasure(path, false)
        val length = measure.length
        if (length <= 1f) return null

        val bounds = RectF()
        path.computeBounds(bounds, true)
        val cx = bounds.centerX()
        val cy = bounds.centerY()

        val count = (length / stepPx).toInt().coerceAtLeast(4) + 1
        val x = FloatArray(count)
        val y = FloatArray(count)
        val ox = FloatArray(count)
        val oy = FloatArray(count)
        val tx = FloatArray(count)
        val ty = FloatArray(count)
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        val inset = insetDp * densityScale

        for (i in 0 until count) {
            val d = (i * stepPx).coerceAtMost(length)
            measure.getPosTan(d, pos, tan)
            var nx = tan[1]
            var ny = -tan[0]
            if ((pos[0] - cx) * nx + (pos[1] - cy) * ny < 0f) {
                nx = -nx
                ny = -ny
            }
            tx[i] = tan[0]
            ty[i] = tan[1]
            ox[i] = nx
            oy[i] = ny
            x[i] = pos[0] - nx * inset
            y[i] = pos[1] - ny * inset
        }
        return Contour(count, x, y, ox, oy, tx, ty)
    }

    /**
     * 素描：几道互相错开的手绘线叠成一条。
     *
     * 刻意不用"抖动折线"——逐点随机的锯齿会读成毛刺，不是笔触。这里给每道线叠两个
     * 低频正弦当侧向位移、再叠一个更慢的正弦当选笔轻重，于是线条是**缓慢起伏**的，
     * 像手腕带着笔走。频率取无理数比的近似值，避免几道线在同一波长上同步、露出机器味。
     */
    private fun drawSketch(canvas: Canvas, path: Path, color: Int, densityScale: Float) {
        val contour = sampleContour(path, SKETCH_STEP_DP * densityScale, SKETCH_INSET_DP, densityScale) ?: return

        val stepPx = SKETCH_STEP_DP * densityScale
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color
            alpha = 255
        }

        for (pass in SKETCH_PASSES) {
            // 每道笔触画进独立图层，整层只在最后合成一次、用同一个 alpha。
            // 若直接给路径设 alpha 再叠加，三道相交处会 double、颜色发脏。
            val layer = canvas.saveLayerAlpha(null, pass.alpha)
            paint.strokeWidth = pass.widthDp * densityScale

            val stroke = Path()
            for (i in 0 until contour.count) {
                val d = i * stepPx
                val wobble = sin(d * 0.052f + pass.phase) * 0.62f +
                    sin(d * 0.019f + pass.phase * 1.7f) * 0.38f
                val off = wobble * SKETCH_WOBBLE_DP * densityScale + pass.laneDp * densityScale
                val px = contour.x[i] + contour.ox[i] * off
                val py = contour.y[i] + contour.oy[i] * off
                if (i == 0) stroke.moveTo(px, py) else stroke.lineTo(px, py)
            }
            canvas.drawPath(stroke, paint)
            canvas.restoreToCount(layer)
        }
    }

    /**
     * 毛绒：一条绒面基带 + 四层朝外生长的绒毛。
     *
     * 绒毛是**一根根画的**——曾经试过"把外沿做成多尺度起伏的填充多边形再整体模糊"，
     * 结果读成一圈柔光边，毛的走向被整个抹平。绒毛的观感来自**高频的不规则**：
     * 每根毛的长短、方向、深浅都要各自不同，模糊只用来柔化单像素笔触（0.45dp），
     * 给大了就把质感糊没了。
     */
    private fun drawPlush(canvas: Canvas, path: Path, color: Int, densityScale: Float) {
        val contour = sampleContour(path, PLUSH_STEP_DP * densityScale, PLUSH_BASE_INSET_DP, densityScale) ?: return

        val random = Random(PLUSH_SEED)
        val hairs = List(PLUSH_LAYERS.size) { Path() }
        val baseline = Path()

        for (i in 0 until contour.count) {
            val baseX = contour.x[i]
            val baseY = contour.y[i]
            if (i == 0) baseline.moveTo(baseX, baseY) else baseline.lineTo(baseX, baseY)

            // 长度先由多尺度波铺一个"成簇"的底子（相邻的毛长短接近，才会一撮一撮），
            // 再叠逐根随机，避免整圈同起同落。系数上限 1.05 把最长的毛压在参数区间内。
            val t = i.toFloat()
            val clump = 0.55f * (sin(t * 0.045f + 1.3f) * 0.5f + 0.5f) +
                0.45f * (sin(t * 0.31f + 0.7f) * 0.5f + 0.5f)
            val factor = (0.45f + 0.75f * clump + random.nextFloat() * 0.35f).coerceAtMost(1.05f)

            val layerIndex = pickPlushLayer(random)
            val layer = PLUSH_LAYERS[layerIndex]
            val len = (layer.minDp + random.nextFloat() * (layer.maxDp - layer.minDp)) * factor * densityScale
            // 三角分布：多数毛贴着法线、少数大幅摆开，比均匀随机自然
            val spread = (random.nextFloat() - random.nextFloat()) * PLUSH_SPREAD_RAD
            val c = cos(spread)
            val s = sin(spread)
            val dx = contour.ox[i] * c - contour.oy[i] * s
            val dy = contour.ox[i] * s + contour.oy[i] * c

            val hair = hairs[layerIndex]
            hair.moveTo(baseX, baseY)
            hair.lineTo(baseX + dx * len, baseY + dy * len)
        }
        baseline.close()

        // 绒面基带：绒毛的根，边界也是软的
        val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = PLUSH_BASE_WIDTH_DP * densityScale
            strokeCap = Paint.Cap.ROUND
            this.color = shade(color, -0.16f)
            alpha = 255
            maskFilter = BlurMaskFilter(0.9f * densityScale, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(baseline, bandPaint)

        // 绒毛：四层各画一遍。模糊只给 0.45dp，只为柔化单像素笔触
        val hairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 0.6f * densityScale
            maskFilter = BlurMaskFilter(0.45f * densityScale, BlurMaskFilter.Blur.NORMAL)
        }
        PLUSH_LAYERS.forEachIndexed { index, layer ->
            hairPaint.color = shade(color, layer.tint)
            hairPaint.alpha = layer.alpha
            canvas.drawPath(hairs[index], hairPaint)
        }
    }

    /**
     * 绿藤：一条沿轮廓**蜿蜒**的藤条，沿藤每隔一段长一片叶子。
     *
     * 两个形状参数都刻意选得"缓"：波幅压到 1.4dp、周期拉到 36dp。试过 1.6dp / 28dp 的组合，
     * 出来是一条波浪丝带，完全不是藤——藤的波是**又缓又长**的。
     *
     * 叶子从**切线**方向张开、而不是从法线：从法线量的结果是沿边扎出去的一圈倒刺，
     * 顺着藤长才是"藤上长叶"。周期与叶距都按**行程长度**算，组件变宽变高时疏密不变，
     * 4×2 与 4×4 看起来是同一种花纹。
     *
     * 叶子带一层浅投影：藤叶要有"压在卡面上"的厚度，纯平的色块会读成印花。
     */
    private fun drawVine(canvas: Canvas, path: Path, color: Int, densityScale: Float) {
        val contour = sampleContour(path, VINE_STEP_DP * densityScale, VINE_STEM_INSET_DP, densityScale) ?: return

        val waveLen = VINE_WAVE_LEN_DP * densityScale
        val amp = VINE_WAVE_AMP_DP * densityScale
        val stepPx = VINE_STEP_DP * densityScale

        // -0.35 把整条藤往里挪一点：藤条摆到最外时贴着轮廓、不外溢
        fun stemPoint(i: Int): Pair<Float, Float> {
            val off = sin(i * stepPx / waveLen * (2f * Math.PI).toFloat()) * amp - amp * 0.35f
            return contour.x[i] + contour.ox[i] * off to contour.y[i] + contour.oy[i] * off
        }

        val stem = Path()
        for (i in 0 until contour.count) {
            val (px, py) = stemPoint(i)
            if (i == 0) stem.moveTo(px, py) else stem.lineTo(px, py)
        }
        stem.close()

        // 叶片单位形：长 6dp、宽 4.8dp。叶子要宽一点才读得出是叶子，窄了就成了倒刺。
        val leafLen = VINE_LEAF_LEN_DP * densityScale
        val leafHalfW = leafLen * 0.40f
        val unitLeaf = Path().apply {
            moveTo(0f, 0f)
            quadTo(leafLen * 0.36f, -leafHalfW, leafLen, 0f)
            quadTo(leafLen * 0.36f, leafHalfW, 0f, 0f)
            close()
        }

        val random = Random(VINE_SEED)
        val leaves = List(2) { Path() }
        val petioles = Path()
        val matrix = Matrix()
        val petioleLen = VINE_PETIOLE_DP * densityScale
        val spacing = VINE_LEAF_SPACING_DP * densityScale
        var leafIndex = 0
        var nextD = spacing * 0.5f
        val total = contour.count * stepPx
        while (nextD < total) {
            val i = (nextD / stepPx).toInt().coerceIn(0, contour.count - 1)
            val (px, py) = stemPoint(i)
            // 以**切线**为基准张开、左右交替
            val tangentAngle = kotlin.math.atan2(contour.ty[i].toDouble(), contour.tx[i].toDouble()).toFloat()
            val side = if (leafIndex % 2 == 0) 1f else -1f
            val angle = tangentAngle + side * (VINE_LEAF_BASE_ANGLE_RAD + random.nextFloat() * VINE_LEAF_FAN_RAD)
            val scale = 0.8f + random.nextFloat() * 0.45f
            val dx = cos(angle)
            val dy = sin(angle)

            // 叶柄：从藤条上伸出一小截，叶子才像"挂"在藤上而不是浮在旁边
            petioles.moveTo(px, py)
            petioles.lineTo(px + dx * petioleLen, py + dy * petioleLen)

            matrix.setRotate(Math.toDegrees(angle.toDouble()).toFloat())
            matrix.postScale(scale, scale)
            matrix.postTranslate(px + dx * petioleLen, py + dy * petioleLen)
            leaves[leafIndex % leaves.size].addPath(unitLeaf, matrix)

            leafIndex++
            nextD += spacing
        }

        val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = VINE_STEM_WIDTH_DP * densityScale
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = shade(color, -0.24f)
            alpha = 255
        }
        canvas.drawPath(stem, stemPaint)

        val petiolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = VINE_STEM_WIDTH_DP * 0.8f * densityScale
            strokeCap = Paint.Cap.ROUND
            this.color = color
            alpha = 255
        }
        canvas.drawPath(petioles, petiolePaint)

        val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            setShadowLayer(1.4f * densityScale, 0f, 1.0f * densityScale, 0x3A000000)
        }
        leaves.forEachIndexed { index, leaf ->
            leafPaint.color = if (index == 0) color else shade(color, 0.28f)
            leafPaint.alpha = 255
            canvas.drawPath(leaf, leafPaint)
        }
    }

    /** 长短四层按权重投放：中间两层最多，短绒与超长绒各占少数 */
    private fun pickPlushLayer(random: Random): Int {
        val r = random.nextFloat()
        return when {
            r < 0.30f -> 0
            r < 0.64f -> 1
            r < 0.90f -> 2
            else -> 3
        }
    }

    /** 朝白（[tint] > 0）或黑（[tint] < 0）混合 |tint|，alpha 保持 */
    private fun shade(color: Int, tint: Float): Int {
        val target = if (tint >= 0f) Color.WHITE else Color.BLACK
        val f = kotlin.math.abs(tint).coerceIn(0f, 1f)
        fun mix(a: Int, b: Int) = (a + (b - a) * f).toInt().coerceIn(0, 255)
        return Color.argb(
            Color.alpha(color),
            mix(Color.red(color), Color.red(target)),
            mix(Color.green(color), Color.green(target)),
            mix(Color.blue(color), Color.blue(target))
        )
    }
}
