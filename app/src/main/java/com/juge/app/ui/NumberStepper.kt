package com.juge.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.juge.app.ui.theme.AppMotion
import com.juge.app.ui.theme.AppTextRole
import com.juge.app.ui.theme.TextRole
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.disabledGray
import com.juge.app.ui.theme.rememberAppHaptics
import com.juge.app.ui.theme.selectBlue
import com.juge.app.ui.theme.surfaceSubtle
import kotlinx.coroutines.delay

/** 长按开始连发前的等待，和系统长按阈值接近 */
private const val STEP_REPEAT_DELAY_MS = 420L

/** 连发间隔：约 14 步/秒，视觉上能跟上，又不会一步跨太多 */
private const val STEP_REPEAT_INTERVAL_MS = 70L

/**
 * 行内文字统一样式：关闭字体自带的顶部填充。
 *
 * 各角色样式的 lineHeight 都大于 fontSize，Compose 默认把额外行高全部垫在
 * 第一行上方，文字会显得比右侧 48dp 圆钮「下沉」半格（视觉重心偏低）；
 * includeFontPadding = false 让额外空间上下均分，文字视觉中心与圆钮中心对齐。
 */
private fun tightVertical(role: TextRole): TextStyle = role.style.copy(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/**
 * 数值行 + 步进器：「标签 …… − 当前值 +」。
 *
 * 「个性定制」页不再使用横向滑条：滑条在越过 touch slop 后会吃掉横向位移，
 * 和页面级「右滑返回跃然纸上」天然冲突。这里改成纯点击控件——点一下走一步，
 * 长按 `−/+` 连续调整，页面里任何位置右滑都不再需要给滑条让路。
 *
 * @param format 当前值的展示文案（含单位，如 `30 sp`、`85%`）
 * @param snap 把浮点运算结果吸附到合法格点，避免 1.2000001 这类值
 */
@Composable
fun AppNumberStepperRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** 不可调整时在右侧替代步进器显示的说明（例如「此形状无需调整」） */
    disabledHint: String? = null,
    snap: (Float) -> Float = { it },
    decrementDescription: String = "减小$label",
    incrementDescription: String = "增大$label",
) {
    // 端点用半格判定：当前值是 0.05 之类的不在格点上时，减到下限仍可用一次
    val canDecrement = enabled && value > valueRange.start + step / 2f
    val canIncrement = enabled && value < valueRange.endInclusive - step / 2f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val labelRole = AppTextRole.label
        Text(
            text = label,
            style = tightVertical(labelRole),
            color = labelRole.color,
            modifier = Modifier.weight(1f),
        )
        if (!enabled && disabledHint != null) {
            val hintRole = AppTextRole.caption
            Text(
                text = disabledHint,
                style = tightVertical(hintRole),
                color = hintRole.color,
                modifier = Modifier.padding(end = 4.dp),
            )
        } else {
            StepButton(
                enabled = canDecrement,
                contentDescription = decrementDescription,
                icon = Icons.Filled.Remove,
                onStep = {
                    onValueChange(snap((value - step).coerceIn(valueRange.start, valueRange.endInclusive)))
                },
            )
            val valueRole = AppTextRole.bodyStrong
            Text(
                text = format(value),
                style = tightVertical(valueRole),
                color = valueRole.color,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .widthIn(min = 74.dp)
                    .padding(horizontal = 2.dp),
            )
            StepButton(
                enabled = canIncrement,
                contentDescription = incrementDescription,
                icon = Icons.Filled.Add,
                onStep = {
                    onValueChange(snap((value + step).coerceIn(valueRange.start, valueRange.endInclusive)))
                },
            )
        }
    }
}

/**
 * 单颗步进按钮：48dp 触控热区 + 34dp 视觉圆钮；短按走一步，长按连发。
 *
 * 用 [clickable] 的按下状态做长按计时，而不是另写 pointerInput：
 * 这样拖动取消、无障碍点击、水波纹语义都由标准按钮行为兜住——手指横向拖动时
 * clickable 会取消点击，不会误改数值，页面 pager 仍能拿到这次右滑。
 */
@Composable
private fun StepButton(
    enabled: Boolean,
    contentDescription: String,
    icon: ImageVector,
    onStep: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val latestOnStep by rememberUpdatedState(onStep)
    val haptics = rememberAppHaptics()
    var repeating by remember { mutableStateOf(false) }

    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) return@LaunchedEffect
        repeating = false
        delay(STEP_REPEAT_DELAY_MS)
        repeating = true
        while (true) {
            latestOnStep()
            haptics.tap()
            delay(STEP_REPEAT_INTERVAL_MS)
        }
    }

    // 视觉圆钮不画描边（2026-10 改版：结构性分层描边一律去掉），层次靠底色明度表达：
    // 可用态 surfaceSubtle、按下态更亮一档、禁用态整体降透明度并配 disabledGray 图标。
    val background by animateColorAsState(
        targetValue = when {
            !enabled -> surfaceSubtle.copy(alpha = 0.45f)
            pressed -> selectBlue.copy(alpha = 0.16f)
            else -> surfaceSubtle
        },
        animationSpec = AppMotion.color,
        label = "stepButtonBackground",
    )
    val tint = if (enabled) accentBlue else disabledGray

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
            ) {
                // 长按已经连发过，松手不再补一步
                if (!repeating) {
                    latestOnStep()
                    haptics.tap()
                }
                repeating = false
            }
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
