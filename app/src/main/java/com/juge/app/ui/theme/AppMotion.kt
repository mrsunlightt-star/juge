package com.juge.app.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize

/**
 * 全 App 的动效令牌。
 *
 * 统一原则：
 * - 状态切换（颜色、边框、透明度）走 [StandardMs]，不抢注意力；
 * - 展开/收起、预览切换走 [EmphasizedMs]；
 * - 列表进场走 [EntranceMs]，一次性即可；
 * - 所有动画都带缓动，不用线性，也不使用超过 400ms 的时长。
 */
object AppMotion {
    const val FastMs = 140
    const val StandardMs = 220
    const val EmphasizedMs = 320
    const val EntranceMs = 360

    /** 按压缩放：轻微回弹，只做触感提示 */
    const val PressedScale = 0.97f

    val easing = FastOutSlowInEasing
    val decelerate = LinearOutSlowInEasing

    fun <T> fast(): TweenSpec<T> = tween(durationMillis = FastMs, easing = easing)

    fun <T> standard(): TweenSpec<T> = tween(durationMillis = StandardMs, easing = easing)

    fun <T> emphasized(): TweenSpec<T> = tween(durationMillis = EmphasizedMs, easing = easing)

    fun <T> entrance(): TweenSpec<T> = tween(durationMillis = EntranceMs, easing = decelerate)

    /** 颜色过渡（选中态、边框、文字色） */
    val color: AnimationSpec<Color> = tween(durationMillis = StandardMs, easing = easing)

    /** 高度自适应：控件显示/隐藏时不要瞬移 */
    val resize: FiniteAnimationSpec<IntSize> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}
