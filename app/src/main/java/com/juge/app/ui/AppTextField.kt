package com.juge.app.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.dangerRed
import com.juge.app.ui.theme.fieldBg
import com.juge.app.ui.theme.fieldBgFocused
import com.juge.app.ui.theme.textGray
import com.juge.app.ui.theme.textWhite
import java.util.LinkedHashMap
import kotlin.math.ceil
import kotlin.math.roundToInt

// —— 流式逐字晕开的参数（一眼能调的都收在这里） ——

/** 单个新字符从「模糊」收拢到「清晰」的时长 */
private const val STREAM_DURATION_MS = 320L

/** 新字符出生瞬间的模糊半径（dp） */
private const val STREAM_MAX_BLUR_DP = 8f

/** 新字符出生瞬间的透明度（越糊越淡，落到清晰时回到 1） */
private const val STREAM_MIN_ALPHA = 0.35f

/** 新字符出生瞬间向下偏移的距离（dp），落定时归零 */
private const val STREAM_MAX_RISE_DP = 1f

/**
 * 一轮插入超过这么多字符（整段粘贴）就不做逐字动画。
 * 整段粘贴本来就是「一次性落进来」，逐字糊成一片反而更乱，也避免几百个字同时渲染模糊位图。
 */
private const val STREAM_MAX_ANIMATED_INSERT = 32

/** 模糊半径量化档数：渲染位图按「字符 + 半径档」缓存，同一段动画里每档只渲染一次 */
private const val STREAM_BLUR_BUCKETS = 8

/** 位图缓存上限（模糊位图很小，32 张约 0.3~1MB，动画结束即清空） */
private const val STREAM_GLYPH_CACHE_SIZE = 32

/** 字符出生时间的哨兵：一直是清晰状态（静止字符 / 初始内容） */
private const val BIRTH_STATIC = Long.MIN_VALUE

/** 字符出生时间的哨兵：已插入、还没被动画帧认领 */
private const val BIRTH_PENDING = Long.MIN_VALUE + 1

private const val STREAM_DURATION_NANOS = STREAM_DURATION_MS * 1_000_000L

private val EMPTY_BIRTHS = LongArray(0)

/**
 * 全 App 唯一的文本输入框。
 *
 * 目标（2026-10 改版）：
 * 1. **不画 1dp 描边**：容器用明度台阶（[fieldBg] / [fieldBgFocused]）与卡片区分；
 * 2. **输入时流式逐字 blur 晕开**：新键入的字符从高斯模糊 + 低透明度收拢到清晰；
 * 3. **高度随内容增长**：[minLines] / [maxLines] 由调用方决定，多行框默认不折叠、
 *    不在框内滚动藏文本（个性定制的金句框就是这种用法）。
 *
 * 渲染原理（本文件下半部分是引擎）：
 * - **输入/光标/选区/IME/无障碍**全部交给 [BasicTextField]：它的 `textStyle.color` 设为透明，
 *   只借它画光标、选区、输入连接与语义树（EditableText / SetText 照旧可用）；
 * - **文字本身**由同一 Box 内的 Canvas 自绘：用 [TextMeasurer] 按与 BasicTextField 相同的
 *   constraints + textStyle 测量 `visualTransformation.filter(value)` 后的文本，再 `drawText` 出来。
 *   所以密码框绘制的是圆点串，明文不会出现在画布上；
 * - **逐字 blur**：每次 value 变化用「公共前缀 / 公共后缀」diff 出真正插入的字符区间，
 *   给每个字符记一个出生时间（与字符索引对齐的 LongArray，插入/删除时同步平移），
 *   [withFrameNanos] 驱动约 320ms 的动画时钟；新字符 blur 8dp→0（二次缓出）、
 *   alpha 0.35→1、y 偏移 1dp→0。逐帧只在动画进行时重绘，动画结束立即停；
 * - 模糊字形用「先用软件 Canvas + [BlurMaskFilter] 把单个字渲染成小位图，再画进位图」实现
 *   （`Modifier.blur` 在 API < 31 无效，minSdk 24 不能靠它；直接给硬件画布的 Paint 挂
 *   BlurMaskFilter 在不同厂商实现里不一定生效），按「字符 + 模糊半径档」缓存位图。
 *
 * 已知局限（都是绘图层的近似，不影响输入与提交）：
 * - **单行超长只保证尾部对齐**：单行文字超出可视宽度时，绘制横向偏移取
 *   `min(0, 可用宽 - 文本宽)`，与「光标贴在右端」一致；用硬件键盘把光标移到中间时，
 *   BasicTextField 自己的滚动位置没有被读出来，绘制仍按尾部对齐，可能和光标有偏差；
 * - **多行 + 有限 maxLines**：超出 maxLines 后 BasicTextField 会在框内纵向滚动，
 *   自绘始终从首行开始（当前调用方要么单行、要么 maxLines = Int.MAX_VALUE，不受影响）；
 * - 动画进行中（约 320ms）整段文字走「逐字 native 绘制」，字形与 Compose 排版在
 *   连字/字距上可能有极细微差异；静止后回到整段 drawText，与 BasicTextField 完全一致；
 * - RTL / 阿拉伯文连写在逐字绘制时不保证与整段排版一致（本 App 输入以中文为主）；
 * - 一次插入超过 [STREAM_MAX_ANIMATED_INSERT] 个字符（整段粘贴）不做逐字动画。
 *
 * 用法与原 `OutlinedTextField` 接近（`appTextFieldColors()` 已随本次改版下线，框体配色由组件内部决定）。
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    placeholder: String? = null,
    textStyle: TextStyle = AppType.body,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    containerColor: Color? = null,
    streaming: Boolean = true,
) {
    // —— 1. 字符出生时间表：value 变化时同步（组合期只改普通字段，不额外触发重组） ——
    val births = remember { StreamingBirths() }
    births.sync(value, animate = streaming && enabled)

    // —— 2. 视觉变换后的绘制文本（密码框在这里已经变成圆点，画布拿不到明文） ——
    val transformed = remember(value, visualTransformation) {
        visualTransformation.filter(AnnotatedString(value))
    }
    // transformed 下标 → 原串下标：出生时间按原串索引存，绘制时逐位查表
    val originalIndices = remember(transformed) {
        IntArray(transformed.text.length) { index ->
            transformed.offsetMapping.transformedToOriginal(index)
        }
    }

    // —— 3. 动画时钟：有字符在动才逐帧跑，跑完立即退出（静止时不产生任何帧回调） ——
    LaunchedEffect(value, streaming) {
        if (!births.animating) return@LaunchedEffect
        while (true) {
            val stillAnimating = withFrameNanos { frameTimeNanos -> births.advanceTo(frameTimeNanos) }
            if (!stillAnimating) break
        }
    }

    // —— 4. 容器外观：无描边，用底色明度台阶表达焦点 / 错误 / 禁用 ——
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val targetContainer = when {
        containerColor != null -> containerColor
        isError -> dangerRed.copy(alpha = 0.10f)
        focused -> fieldBgFocused
        else -> fieldBg
    }
    val animatedContainer by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = tween(durationMillis = 160),
        label = "appTextFieldContainer",
    )
    val contentAlpha = if (enabled) 1f else 0.55f

    // —— 5. 排版与绘制参数（都是不可变值，动画只重绘、不重组） ——
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val textMeasurer = rememberTextMeasurer()
    val textColor = textWhite
    // 字重已在 theme 里按暗色模式缩放好，这里只把 TextStyle 解析成 android.graphics.Typeface 供自绘
    val typeface = remember(fontFamilyResolver, textStyle) {
        val family = textStyle.fontFamily ?: FontFamily.Default
        val weight = textStyle.fontWeight ?: FontWeight.Normal
        val style = textStyle.fontStyle ?: FontStyle.Normal
        (fontFamilyResolver.resolve(family, weight, style).value as? Typeface) ?: Typeface.DEFAULT
    }
    val textSizePx = with(density) { textStyle.fontSize.takeOrElse { 14.sp }.toPx() }
    val maxBlurPx = with(density) { STREAM_MAX_BLUR_DP.dp.toPx() }
    val maxRisePx = with(density) { STREAM_MAX_RISE_DP.dp.toPx() }
    val renderer = remember(typeface, textSizePx, textColor) {
        StreamingGlyphRenderer(typeface, textSizePx, textColor.toArgb())
    }
    // 单行 + 尾部图标（如密码的显示/隐藏按钮）时竖向留白收窄：图标自带 48dp 热区，
    // 再按普通输入框的 12dp 上下留白会把整框撑到 72dp，比其他输入框高出一截
    val verticalPadding = if (singleLine && trailingIcon != null) 4.dp else 12.dp
    // BasicTextField 不画可见文字，所以传透明的同款样式；光标仍是强调色
    val fieldTextStyle = remember(textStyle) { textStyle.copy(color = Color.Transparent) }
    val effectiveMaxLines = if (singleLine) 1 else maxLines
    val effectiveMinLines = if (singleLine) 1 else minLines

    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            // 标签放框上方：框内不再留「浮动标签」的位置，层级靠字号与颜色表达
            Text(text = label, style = AppType.label, color = textGray)
            Spacer(Modifier.height(AppSpacing.tight))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(contentAlpha)
                // 圆角裁切 + 纯色底：整框没有 1dp 描边，焦点只靠底色明度变化表达
                .clip(AppShape.button)
                .background(animatedContainer, AppShape.button)
                .padding(horizontal = 14.dp, vertical = verticalPadding),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                // 文字层：在所有输入层之下，光标 / 选区 / 输入连接都画在它上面。
                // matchParentSize 保证画布与文本区同尺寸同偏移；测量只在 drawWithCache 的
                // build 阶段做（文本 / 样式 / 宽度 / 行数变化时），动画帧只重绘、不重测、不重组。
                Spacer(
                    modifier = Modifier
                        .matchParentSize()
                        .drawWithCache {
                            val constraints = if (singleLine) {
                                Constraints()
                            } else {
                                Constraints(maxWidth = size.width.roundToInt().coerceAtLeast(0))
                            }
                            val layout = textMeasurer.measure(
                                text = transformed.text,
                                style = textStyle,
                                overflow = TextOverflow.Clip,
                                softWrap = !singleLine,
                                maxLines = effectiveMaxLines,
                                constraints = constraints,
                                layoutDirection = layoutDirection,
                            )
                            val layoutWidth = layout.size.width.toFloat()
                            // 单行超宽：尾部对齐（负偏移），与 IME 光标贴右边一致
                            val textOffsetX = if (singleLine) {
                                minOf(0f, size.width - layoutWidth)
                            } else {
                                0f
                            }
                            val glyphs = buildGlyphSlots(layout)
                            onDrawBehind {
                                if (size.width <= 0f || size.height <= 0f) return@onDrawBehind
                                val nativeCanvas = drawContext.canvas.nativeCanvas
                                // 裁到文本区：单行横向溢出、多行超出高度时都不能画到框外
                                clipRect(0f, 0f, size.width, size.height) {
                                    if (!births.animating) {
                                        // 静止态：整段交给 Compose 排版绘制，与 BasicTextField 完全一致
                                        renderer.clearGlyphCache()
                                        drawText(
                                            textLayoutResult = layout,
                                            color = textColor,
                                            topLeft = Offset(textOffsetX, 0f),
                                        )
                                    } else {
                                        // 动画态：逐字绘制，只有新字符带模糊 / 透明度 / 位移
                                        drawStreamingGlyphs(
                                            canvas = nativeCanvas,
                                            glyphs = glyphs,
                                            births = births.births,
                                            originalIndices = originalIndices,
                                            frameTimeNanos = births.frameTimeNanos,
                                            textOffsetX = textOffsetX,
                                            maxBlurPx = maxBlurPx,
                                            maxRisePx = maxRisePx,
                                            renderer = renderer,
                                        )
                                    }
                                }
                            }
                        }
                )

                // 空值时显示 placeholder（Text 组件，跟随同样的字级）
                if (value.isEmpty() && placeholder != null) {
                    Text(
                        text = placeholder,
                        style = textStyle,
                        color = textGray.copy(alpha = 0.72f),
                    )
                }

                // 输入层：透明文字 + 强调色光标；语义树（EditableText / SetText）由它提供
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    textStyle = fieldTextStyle,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    singleLine = singleLine,
                    maxLines = effectiveMaxLines,
                    minLines = effectiveMinLines,
                    visualTransformation = visualTransformation,
                    interactionSource = interactionSource,
                    cursorBrush = SolidColor(accentBlue),
                )
            }

            if (trailingIcon != null) {
                Spacer(Modifier.width(AppSpacing.iconGap))
                trailingIcon()
            }
        }

        if (supportingText != null) {
            Spacer(Modifier.height(AppSpacing.tight))
            supportingText()
        }
    }
}

/**
 * 逐帧绘制：每个字形按自己的出生时间算进度，新字符模糊 / 半透明 / 略下移，静止字符照常清晰。
 *
 * 之所以动画期间整段都逐字画：Compose 的 [TextLayoutResult] 只能整段绘制，没有「跳过一个字」的入口；
 * 而动画只有约 320ms、且通常只有 1~2 个新字符，收益远大于代价。静止后回到整段绘制。
 */
private fun DrawScope.drawStreamingGlyphs(
    canvas: android.graphics.Canvas,
    glyphs: List<GlyphSlot>,
    births: LongArray,
    originalIndices: IntArray,
    frameTimeNanos: Long,
    textOffsetX: Float,
    maxBlurPx: Float,
    maxRisePx: Float,
    renderer: StreamingGlyphRenderer,
) {
    val blurStepPx = maxBlurPx / STREAM_BLUR_BUCKETS
    glyphs.forEach { slot ->
        val originalIndex = originalIndices.getOrElse(slot.index) { -1 }
        val birth = births.getOrElse(originalIndex) { BIRTH_STATIC }
        // 进度：静止字符恒为 1；待认领（刚插入还没跑过帧）按 0 处理，第一帧就是最糊的状态
        val eased = when {
            birth == BIRTH_STATIC -> 1f
            birth < 0L -> 0f
            else -> {
                val elapsed = (frameTimeNanos - birth).coerceAtLeast(0L)
                val t = (elapsed.toFloat() / STREAM_DURATION_NANOS).coerceIn(0f, 1f)
                // 二次缓出：前半程迅速收拢，尾部收敛到清晰
                1f - (1f - t) * (1f - t)
            }
        }
        val left = textOffsetX + slot.left
        val baseline = slot.baseline + maxRisePx * (1f - eased)
        if (eased >= 1f) {
            renderer.drawCrisp(canvas, slot.glyph, left, baseline, 1f)
            return@forEach
        }
        val blurPx = maxBlurPx * (1f - eased)
        val alpha = STREAM_MIN_ALPHA + (1f - STREAM_MIN_ALPHA) * eased
        if (blurPx >= blurStepPx) {
            val bucket = ceil(blurPx / blurStepPx).toInt().coerceIn(1, STREAM_BLUR_BUCKETS)
            renderer.drawBlurred(
                canvas = canvas,
                glyph = slot.glyph,
                left = left,
                lineTop = slot.lineTop,
                lineHeight = slot.lineHeight,
                baselineInLine = slot.baseline - slot.lineTop,
                advance = slot.width,
                radiusPx = blurPx,
                bucket = bucket,
                alpha = alpha,
            )
        } else {
            // 半径已小于一档：直接画清晰字形，不为止损几像素再建位图
            renderer.drawCrisp(canvas, slot.glyph, left, baseline, alpha)
        }
    }
}

/**
 * 一个「字形槽」的排版位置（相对文本区左上角）。
 * 在 [drawWithCache] 的 build 阶段随排版算一次，动画帧只读不重算。
 */
private data class GlyphSlot(
    /** 在绘制文本中的 UTF-16 起始下标（用来查出生时间） */
    val index: Int,
    val glyph: String,
    /** 字形原点 x（相对文本区左缘） */
    val left: Float,
    /** 字形前进宽度 */
    val width: Float,
    val baseline: Float,
    val lineTop: Float,
    val lineHeight: Float,
)

/** 按码点切分文本，拿到每个可见字形的排版位置（换行 / 零宽字符不参与绘制） */
private fun buildGlyphSlots(layout: TextLayoutResult): List<GlyphSlot> {
    val text = layout.layoutInput.text.text
    if (text.isEmpty()) return emptyList()
    val slots = ArrayList<GlyphSlot>(text.length)
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        val charCount = Character.charCount(codePoint)
        if (codePoint != '\n'.code && codePoint != '\r'.code) {
            val box = layout.getBoundingBox(index)
            if (box.width > 0f) {
                val line = layout.getLineForOffset(index)
                val lineTop = layout.getLineTop(line)
                slots += GlyphSlot(
                    index = index,
                    glyph = text.substring(index, index + charCount),
                    left = box.left,
                    width = box.width,
                    baseline = layout.getLineBaseline(line),
                    lineTop = lineTop,
                    lineHeight = layout.getLineBottom(line) - lineTop,
                )
            }
        }
        index += charCount
    }
    return slots
}

/**
 * 逐字模糊渲染：清晰字形直接用 android Paint 画；模糊字形先在软件位图里用
 * [BlurMaskFilter] 渲染（软件画布一定支持，API 24 起可用），再作为位图画进 Compose 画布。
 *
 * 位图按「字符 + 模糊半径档」缓存：一段动画里每个字符最多产生 [STREAM_BLUR_BUCKETS] 张，
 * 动画结束由调用方 [clearGlyphCache] 清空。
 *
 * internal 而非 private：单测要直接验证「模糊字形确实比清晰字形边缘更软」，
 * 不能只靠语义树（语义树和绘制层是两套东西）。
 */
internal class StreamingGlyphRenderer(
    typeface: Typeface,
    private val textSizePx: Float,
    colorArgb: Int,
) {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        this.textSize = textSizePx
        this.color = colorArgb
        isSubpixelText = true
    }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val glyphBitmaps = LinkedHashMap<String, RenderedGlyph>(STREAM_GLYPH_CACHE_SIZE, 0.75f, true)
    private val dstRect = RectF()

    fun clearGlyphCache() {
        if (glyphBitmaps.isNotEmpty()) glyphBitmaps.clear()
    }

    /** 清晰字形：直接画字（动画中未参与晕开的字符走这里） */
    fun drawCrisp(canvas: android.graphics.Canvas, glyph: String, x: Float, baseline: Float, alpha: Float) {
        textPaint.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        canvas.drawText(glyph, x, baseline, textPaint)
    }

    /** 模糊字形：取（或现场渲染）该字符在该半径档下的小位图，按 alpha 画到目标矩形 */
    fun drawBlurred(
        canvas: android.graphics.Canvas,
        glyph: String,
        left: Float,
        lineTop: Float,
        lineHeight: Float,
        baselineInLine: Float,
        advance: Float,
        radiusPx: Float,
        bucket: Int,
        alpha: Float,
    ) {
        val rendered = glyphBitmaps.getOrPut("$bucket:$glyph") {
            renderGlyphBitmap(glyph, radiusPx, advance, lineHeight, baselineInLine)
        }
        // LinkedHashMap(accessOrder = true) 的头部就是最久未用的，超限就淘汰
        while (glyphBitmaps.size > STREAM_GLYPH_CACHE_SIZE) {
            val iterator = glyphBitmaps.entries.iterator()
            if (!iterator.hasNext()) break
            iterator.next()
            iterator.remove()
        }
        val pad = rendered.pad
        dstRect.set(
            left - pad,
            lineTop - pad,
            left - pad + rendered.bitmap.width,
            lineTop - pad + rendered.bitmap.height,
        )
        bitmapPaint.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        canvas.drawBitmap(rendered.bitmap, null, dstRect, bitmapPaint)
    }

    private fun renderGlyphBitmap(
        glyph: String,
        radiusPx: Float,
        advance: Float,
        lineHeight: Float,
        baselineInLine: Float,
    ): RenderedGlyph {
        val pad = ceil(radiusPx).toInt() + 2
        val width = ceil(advance).toInt().coerceAtLeast(1) + pad * 2
        val height = ceil(lineHeight).toInt().coerceAtLeast(1) + pad * 2
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = textPaint.typeface
            textSize = textSizePx
            color = textPaint.color
            isSubpixelText = true
            maskFilter = BlurMaskFilter(radiusPx.coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawText(glyph, pad.toFloat(), pad + baselineInLine, paint)
        return RenderedGlyph(bitmap, pad)
    }

    private class RenderedGlyph(val bitmap: Bitmap, val pad: Int)
}

/**
 * 字符出生时间表：与 `value` 的字符索引一一对齐。
 *
 * 每次 value 变化用「公共前缀 + 公共后缀」找出插入 / 删除的区间：
 * - 插入的字符标记为 [BIRTH_PENDING]，由动画第一帧写入出生时间；
 * - 删除区间之后的字符整体平移，保留已有出生时间（正在晕开的字符不会因为后面删字而重播）；
 * - 一次插入过多（整段粘贴）或 streaming 关闭时，新字符直接记为 [BIRTH_STATIC]。
 */
private class StreamingBirths {
    private var lastValue: String = ""
    private var initialized = false

    var births: LongArray = EMPTY_BIRTHS
        private set

    /** 是否有字符还在晕开；动画循环和绘制层都看这个开关 */
    var animating: Boolean = false
        private set

    /** 当前动画帧时间（nanos）：绘制层读它只触发重绘、不触发重组 */
    private val frameTimeState = mutableLongStateOf(0L)

    val frameTimeNanos: Long get() = frameTimeState.longValue

    fun sync(newValue: String, animate: Boolean) {
        if (!initialized) {
            // 首次组合：已有内容直接算「早就落定」，避免一进页面整段文字糊一遍
            initialized = true
            lastValue = newValue
            births = LongArray(newValue.length) { BIRTH_STATIC }
            animating = false
            return
        }
        if (newValue == lastValue) return

        val previous = lastValue
        val oldBirths = births
        val maxPrefix = minOf(previous.length, newValue.length)
        var prefix = 0
        while (prefix < maxPrefix && previous[prefix] == newValue[prefix]) prefix++
        var suffix = 0
        while (
            suffix < maxPrefix - prefix &&
            previous[previous.length - 1 - suffix] == newValue[newValue.length - 1 - suffix]
        ) {
            suffix++
        }

        val insertedStart = prefix
        val insertedEnd = newValue.length - suffix
        val insertedCount = insertedEnd - insertedStart
        val removedCount = previous.length - suffix - prefix
        val next = LongArray(newValue.length)
        for (index in 0 until insertedStart) {
            next[index] = oldBirths.getOrElse(index) { BIRTH_STATIC }
        }
        val animateInsert = animate && insertedCount in 1..STREAM_MAX_ANIMATED_INSERT
        val insertedBirth = if (animateInsert) BIRTH_PENDING else BIRTH_STATIC
        for (index in insertedStart until insertedEnd) {
            next[index] = insertedBirth
        }
        for (index in insertedEnd until newValue.length) {
            // 后缀字符：新下标 → 旧下标，出生时间跟着平移
            val oldIndex = index - insertedCount + removedCount
            next[index] = oldBirths.getOrElse(oldIndex) { BIRTH_STATIC }
        }

        births = next
        lastValue = newValue
        animating = animateInsert
    }

    /**
     * 推进一帧：给待认领的字符写入出生时间，返回是否还有字符在动画中。
     * 全部结束时顺手把出生时间清成静止，绘制层回到整段 drawText 的快路径。
     */
    fun advanceTo(nowNanos: Long): Boolean {
        val current = births
        var active = false
        for (index in current.indices) {
            val birth = current[index]
            when {
                birth == BIRTH_PENDING -> {
                    current[index] = nowNanos
                    active = true
                }
                birth != BIRTH_STATIC && nowNanos - birth < STREAM_DURATION_NANOS -> active = true
            }
        }
        frameTimeState.longValue = nowNanos
        if (!active) {
            for (index in current.indices) {
                if (current[index] != BIRTH_STATIC) current[index] = BIRTH_STATIC
            }
            animating = false
        }
        return active
    }
}
