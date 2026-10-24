package com.juge.app.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize

/**
 * 全 App 的动效令牌。
 *
 * 统一原则：
 * - **位移类动效一律走弹簧**（[standard]/[emphasized]/[entrance]/[resize]）。
 *   Tween 没有速度概念，动画到终点就硬生生停住，物体没有质量；
 *   弹簧的起止速度连续，这是「跟手」「活」的物理来源（iOS 全线弹簧的用意）。
 * - 颜色、透明度这类无位置变化的状态过渡仍走 tween（[color]/[fast]），
 *   颜色插值不需要惯性，弹簧反而拖尾。
 * - 按压缩放走 [press]，松手带回一点回弹，是「按到了实体」的触觉暗示。
 */
object AppMotion {
    /** 颜色过渡时长（tween 专用档位） */
    const val FastMs = 140
    const val StandardMs = 220

    /** 按压缩放：轻微回弹，只做触感提示 */
    const val PressedScale = 0.97f

    val easing = FastOutSlowInEasing
    val decelerate = LinearOutSlowInEasing

    /**
     * 标准弹簧：临界阻尼、中等刚度。选中态、边框宽度、指示器位移等
     * 「点到即止」的位移都走这一档——到位干脆，不弹跳、不拖沓。
     */
    fun <T> standard(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 380f,
    )

    /** 强调弹簧：更低的刚度拉长过程，展开/收起等大块内容的运动感 */
    fun <T> emphasized(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )

    /** 进场弹簧：一次性入场，稍带一点点柔（比 standard 更缓） */
    fun <T> entrance(): SpringSpec<T> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** 按压缩放：按下瞬间跟手、松手轻微回弹 */
    val press: AnimationSpec<Float> = spring(
        dampingRatio = 0.6f,
        stiffness = Spring.StiffnessMedium,
    )

    /** 颜色过渡（选中态、边框、文字色）：无位移，不需要惯性 */
    val color: TweenSpec<Color> = tween(durationMillis = StandardMs, easing = easing)

    /** 短促淡入淡出：折叠面板的 fade 等次要透明度过渡 */
    fun <T> fast(): TweenSpec<T> = tween(durationMillis = FastMs, easing = easing)

    /** 高度自适应：控件显示/隐藏时不要瞬移 */
    val resize: FiniteAnimationSpec<IntSize> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}
