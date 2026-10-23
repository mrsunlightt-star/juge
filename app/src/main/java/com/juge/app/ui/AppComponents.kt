package com.juge.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.juge.app.ui.theme.AppMotion
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.borderBlue
import com.juge.app.ui.theme.cardBg
import com.juge.app.ui.theme.dangerRed
import com.juge.app.ui.theme.selectBlue
import com.juge.app.ui.theme.textGray
import com.juge.app.ui.theme.textPrimary
import com.juge.app.ui.theme.textWhite

/**
 * 白色区块卡片：定制页的五个设置区块、教程卡片共用。
 *
 * 统一项：16dp 圆角、1dp 中性描边、无投影、16dp 内边距、12dp 行距；
 * 高度变化默认走 [AppMotion.resize]；嵌套展开控件可关闭，避免重复执行高度动画。
 */
@Composable
fun AppSectionCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = AppSpacing.cardPadding,
    animateSize: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (animateSize) {
                    Modifier.animateContentSize(animationSpec = AppMotion.resize)
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderBlue),
        shape = AppShape.card,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.controlGap),
            content = content,
        )
    }
}

/** 区块标题：16dp 线性图标 + 13sp 加粗 accent 文案 */
@Composable
fun AppSectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentBlue,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(AppSpacing.iconGap))
        Text(
            text = title,
            style = AppType.sectionTitle,
            color = accentBlue,
        )
    }
}

/** 控件标签：12sp 加粗、板岩灰 */
@Composable
fun AppSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AppType.label,
        color = textGray,
        modifier = modifier,
    )
}

/**
 * 主操作按钮：明亮浅蓝填充 + 深色文字、12dp 圆角、48dp 最小高度、按压轻微缩放。
 * 全 App 的“保存/支付/确认/添加”都用这一颗，不再各自 setShadow + background。
 */
@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    containerColor: Color = selectBlue,
    contentColor: Color = Color.White,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) AppMotion.PressedScale else 1f,
        animationSpec = AppMotion.standard(),
        label = "primaryButtonScale",
    )
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .scale(scale),
        enabled = enabled && !loading,
        shape = AppShape.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.45f),
            disabledContentColor = contentColor.copy(alpha = 0.8f),
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        interactionSource = interaction,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = contentColor,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, style = AppType.button)
    }
}

/** 次要操作按钮：白底细描边，和主按钮同高、同圆角 */
@Composable
fun AppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = textGray,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) AppMotion.PressedScale else 1f,
        animationSpec = AppMotion.standard(),
        label = "secondaryButtonScale",
    )
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .scale(scale),
        enabled = enabled,
        shape = AppShape.button,
        border = BorderStroke(1.dp, borderBlue),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        interactionSource = interaction,
    ) {
        Text(text = text, style = AppType.button)
    }
}

/** 危险操作按钮：与主按钮同形，只换颜色 */
@Composable
fun AppDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AppPrimaryButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = dangerRed,
        contentColor = Color.White,
    )
}

/** 文字按钮：链接色/灰色，统一 13sp */
@Composable
fun AppTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = textGray,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(text = text, style = AppType.link, color = color)
    }
}

/**
 * 筛选胶囊：文字库分类、字体选择、图片填充模式共用。
 * 未选中白底灰字，选中浅蓝底白字；圆角与边框都走令牌。
 */
@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = AppType.chip,
    containerColor: Color = cardBg,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label, style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        shape = AppShape.chip,
        colors = FilterChipDefaults.filterChipColors(
            labelColor = textGray,
            selectedLabelColor = Color.White,
            containerColor = containerColor,
            selectedContainerColor = selectBlue,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = borderBlue,
            selectedBorderColor = accentBlue,
        ),
    )
}

/** 弹窗标题：18sp 加粗墨色 */
@Composable
fun AppDialogTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AppType.dialogTitle,
        color = textWhite,
        modifier = modifier,
    )
}

/**
 * 弹窗底板：20dp 圆角、白底、1dp 中性描边，统一弹窗的边界感。
 *
 * - 宽度上限 420dp：`usePlatformDefaultWidth = false` 时平板上不会被拉成一整条横幅；
 * - [imePadding]：输入框获得焦点、键盘弹起时整块弹窗上移，不遮住底部按钮；
 * - [scrollable]：小屏 + 系统放大字号时内容可滚动；内部已有 LazyColumn 的弹窗不要开。
 */
@Composable
fun AppDialogSurface(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 24.dp,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .imePadding(),
        shape = AppShape.dialog,
        color = cardBg,
        border = BorderStroke(1.dp, borderBlue),
    ) {
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .then(
                    if (scrollable) {
                        Modifier.verticalScroll(rememberScrollState())
                    } else {
                        Modifier
                    }
                ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.controlGap),
            content = content,
        )
    }
}

/**
 * 输入框配色：聚焦薄荷青、未聚焦中性灰、正文墨色。
 * 所有 OutlinedTextField 共用，避免有的用 #E5E7EB、有的用 #E2E8F0。
 */
@Composable
fun appTextFieldColors(container: Color = Color.Transparent): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = textWhite,
        unfocusedTextColor = textWhite,
        focusedBorderColor = accentBlue,
        unfocusedBorderColor = borderBlue,
        focusedLabelColor = accentBlue,
        unfocusedLabelColor = textGray,
        cursorColor = accentBlue,
        focusedContainerColor = container,
        unfocusedContainerColor = container,
    )

/** 轻量按压反馈：非 Material Button 的自绘控件（色块、卡片）套这一层 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) AppMotion.PressedScale else 1f,
        animationSpec = AppMotion.standard(),
        label = "pressScale",
    )
    return scale(scale)
}

/** 浅色分隔线：与卡片描边同色，不用 Material 默认的深色 Divider */
@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(borderBlue),
    )
}
