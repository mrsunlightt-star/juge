package com.juge.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juge.app.ui.theme.AppMotion
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppShadows
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.chipTextInk
import com.juge.app.ui.theme.disabledGray
import com.juge.app.ui.theme.selectBlue
import com.juge.app.ui.theme.surfaceSubtle
import com.juge.app.ui.theme.rememberAppHaptics

private val controlBlue: Color @Composable get() = selectBlue // 与主界面 selectBlue 同源

private val SliderThumbSize = 18.dp

/**
 * 白色圆形滑块：18dp 视觉 + 2dp 强调色描边 + 柔和阴影，供取色滑条使用。
 *
 * 白芯必须配描边：卡片底本身就是白色，只靠白底 + 阴影时滑块会整个融进面板
 * （用户反馈「看不到按钮在哪」）。描边用 accentBlue 而非轨道色——落在蓝色已填充段上时，
 * 轨道色会和滑块外沿同色，白芯才是唯一能读出轮廓的部分。
 */
@Composable
private fun SliderThumb(enabled: Boolean) {
    val fill = if (enabled) Color.White else disabledGray
    Box(
        modifier = Modifier
            .size(SliderThumbSize)
            .shadow(
                elevation = AppShadows.control,
                shape = CircleShape,
                clip = false,
                ambientColor = AppShadows.ambientShadow,
                spotColor = AppShadows.spotShadow,
            )
            .background(fill, CircleShape)
            .border(2.dp, if (enabled) accentBlue else disabledGray, CircleShape)
    )
}

/**
 * 渐变轨道滑条（取色用）：5dp 渐变胶囊轨道 + 同款白色滑块。
 * 轨道本身即取色区域；外层 48dp 高，滑块热区满足移动端最小触控目标。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradientTrackSlider(
    gradient: List<Color>,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(AppShape.pill)
                .background(Brush.horizontalGradient(gradient))
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
            thumb = { SliderThumb(true) },
            track = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                )
            }
        )
    }
}

/**
 * 带勾选框的胶囊选项（加粗/倾斜/文字阴影/对齐方式）：
 * 未选中为浅灰胶囊 + 蓝框空心勾选框，选中变蓝底白字 + 白框对勾。
 * 纵向 15dp 内边距让整颗胶囊达到 48dp，满足移动端最小触控目标。
 */
@Composable
fun CheckChip(
    checked: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val background by animateColorAsState(
        targetValue = if (checked) controlBlue else surfaceSubtle,
        animationSpec = AppMotion.color,
        label = "chipBackground"
    )
    val textColor by animateColorAsState(
        targetValue = if (checked) Color.White else chipTextInk,
        animationSpec = AppMotion.color,
        label = "chipText"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) Color.White else accentBlue,
        animationSpec = AppMotion.color,
        label = "chipBorder"
    )
    val haptics = rememberAppHaptics()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(AppShape.chip)
            .background(background)
            .pressScale(interaction)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = {
                    haptics.tap()
                    onClick()
                }
            )
            .padding(horizontal = 12.dp, vertical = 16.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(16.dp)
                .clip(AppShape.check)
                .background(if (checked) Color.Transparent else Color.White)
                .border(2.dp, borderColor, AppShape.check)
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            label,
            style = AppType.chip.copy(
                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = textColor,
            maxLines = 1
        )
    }
}
