package com.juge.app.ui

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.juge.app.ui.theme.AppMotion
import kotlinx.coroutines.delay
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppShadows
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.AppTextRole
import com.juge.app.ui.theme.TextRole
import com.juge.app.ui.theme.LocalAppDarkTheme
import com.juge.app.ui.theme.LocalAppPalette
import com.juge.app.ui.theme.rememberAppHaptics
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.borderBlue
import com.juge.app.ui.theme.cardBg
import com.juge.app.ui.theme.dangerRed
import com.juge.app.ui.theme.selectBlue
import com.juge.app.ui.theme.surfaceSubtle
import com.juge.app.ui.theme.textGray
import com.juge.app.ui.theme.textPrimary
import com.juge.app.ui.theme.textWhite

/**
 * 白色区块卡片：定制页的五个设置区块、教程卡片、跃然纸上的金句卡片共用。
 *
 * 统一项：16dp 圆角、1dp 中性描边 + 低透明度弥散软阴影、16dp 内边距、12dp 行距；
 * 高度变化默认走 [AppMotion.resize]；嵌套展开控件可关闭，避免重复执行高度动画。
 * 描边必须保留：抽屉面板是近白的毛玻璃，只靠阴影白卡会融进面板里
 * （用户反馈「卡片框轮廓要清晰、有层次感」），1dp borderBlue 才勾得出边界。
 * [contentPadding] 放宽为 [PaddingValues]：金句卡片要「左右 16 / 上下 12」的非对称
 * 内边距（用户定档的整卡瘦身 ~10%），四边同值的 Dp 表达不了。
 */
@Composable
fun AppSectionCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(AppSpacing.cardPadding),
    animateSize: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = AppShadows.card,
                shape = AppShape.card,
                clip = false,
                ambientColor = AppShadows.ambientShadow,
                spotColor = AppShadows.spotShadow,
            )
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

/** 区块标题：强调色文案（图标已按产品要求去掉，只留文字层级） */
@Composable
fun AppSectionHeader(title: String) {
    AppText(text = title, role = AppTextRole.sectionTitleAccent)
}

/** 控件标签：12sp 半粗、板岩灰 */
@Composable
fun AppSectionLabel(text: String, modifier: Modifier = Modifier) {
    AppText(
        text = text,
        role = AppTextRole.label,
        modifier = modifier,
    )
}

/**
 * 主操作按钮：明亮浅蓝填充 + 白色文字、12dp 圆角、48dp 最小高度、按压轻微缩放。
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
        animationSpec = AppMotion.press,
        label = "primaryButtonScale",
    )
    val haptics = rememberAppHaptics()
    Button(
        onClick = {
            haptics.confirm()
            onClick()
        },
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
        animationSpec = AppMotion.press,
        label = "secondaryButtonScale",
    )
    val haptics = rememberAppHaptics()
    OutlinedButton(
        onClick = {
            haptics.click()
            onClick()
        },
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
        // 夜间 dangerRed 提亮为红-400（否则红色文字在深底上不达标），白字只剩 2.8:1，
        // 换深墨字（与 Material 夜间 onPrimary=darkBg 同一约定，约 6.8:1）
        contentColor = if (LocalAppDarkTheme.current) LocalAppPalette.current.darkBg else Color.White,
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
    val haptics = rememberAppHaptics()
    TextButton(
        onClick = {
            haptics.click()
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
    ) {
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
    val haptics = rememberAppHaptics()
    FilterChip(
        selected = selected,
        onClick = {
            haptics.tap()
            onClick()
        },
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
    AppText(
        text = text,
        role = AppTextRole.dialogTitle,
        modifier = modifier,
    )
}

/**
 * 语义文字：界面文字的统一入口。字号/字重/颜色由 [role] 一次给定
 * （见 [AppTextRole]），不要再用 `Text(style = …, color = …)` 手工配对。
 * [color] 只给极少数颜色随状态变化的场景覆写用，字级字重仍从角色取。
 */
@Composable
fun AppText(
    text: String,
    role: TextRole,
    modifier: Modifier = Modifier,
    color: Color? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        style = role.style,
        color = color ?: role.color,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
    )
}

/**
 * 弹窗底板：20dp 圆角、白底、宽而淡的弥散阴影，统一弹窗的边界感；
 * API 31+ 上弹窗背后的 App 内容会被实时高斯模糊（iOS 式材质），低版本自动退化为纯压暗。
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
    DialogBackdropBlur()
    Surface(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .imePadding()
            .shadow(
                elevation = AppShadows.dialog,
                shape = AppShape.dialog,
                clip = false,
                ambientColor = AppShadows.ambientShadow,
                spotColor = AppShadows.spotShadow,
            ),
        shape = AppShape.dialog,
        color = cardBg,
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
 * 输入框配色：**描边式**输入框——透明底 + 1dp 中性描边，聚焦时描边转强调色。
 * 抽屉面板是近白的毛玻璃，填充式浅灰底在面板上几乎看不出来，用户反馈要
 * 「轮廓清晰、像之前一样有层次感」，故恢复描边。所有 OutlinedTextField 共用。
 * [container] 可覆写底色（取色弹窗等特殊底色场景）。
 */
@Composable
fun appTextFieldColors(container: Color? = null): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = textWhite,
        unfocusedTextColor = textWhite,
        focusedBorderColor = accentBlue,
        unfocusedBorderColor = borderBlue,
        focusedLabelColor = accentBlue,
        unfocusedLabelColor = textGray,
        cursorColor = accentBlue,
        focusedContainerColor = container ?: Color.Transparent,
        unfocusedContainerColor = container ?: Color.Transparent,
    )

/**
 * 纵向滚动条（可拖动）：与滚动内容**同层绘制**的独立覆盖层，用法：
 *
 * ```
 * Box(modifier = Modifier.heightIn(max = …)) {
 *     Column(Modifier.fillMaxWidth().verticalScroll(state).padding(end = 14.dp)) { … }
 *     AppScrollbar(state, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
 * }
 * ```
 *
 * - 覆盖层在滚动容器**之外**：滑块画在视口坐标系里，不会随内容滚动被裁断（此前
 *   把 drawBehind 挂在 verticalScroll 之后的教训）；
 * - 正文需预留 `end` 内边距，滑块落在留白里，不压文字；
 * - 整条 24dp 触摸带支持按住上下拖动直接滚动内容（拖满一圈 = 滚完全程）；
 * - 内容不溢出（maxValue == 0）时滑块与拖动都不响应；
 * - **动效**：滚动/拖动时淡入，松手静止 [SCROLLBAR_FADE_DELAY_MS] 后淡出（iOS 同款行为），
 *   初始不显示——滚动条只在用户正在动的时候存在，不再常驻占视觉。
 */
@Composable
fun AppScrollbar(
    state: ScrollState,
    modifier: Modifier = Modifier,
    color: Color = textPrimary.copy(alpha = 0.55f), // 墨色 55%（夜色下随主文字转为霜白 55%）
    barWidth: Dp = 6.dp,
    touchWidth: Dp = 24.dp,
) {
    var viewportHeight by remember { mutableFloatStateOf(0f) }
    // 滚动条自身的「活动」判定：内容在滚（手势/惯性/程序滚动）或用户正按住滑块
    var dragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val active = state.isScrollInProgress || dragging
    LaunchedEffect(active) {
        if (active) {
            visible = true
        } else {
            delay(SCROLLBAR_FADE_DELAY_MS)
            visible = false
        }
    }
    val thumbAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(200, easing = AppMotion.easing),
        label = "scrollbarAlpha",
    )
    Box(
        modifier = modifier
            .onSizeChanged { viewportHeight = it.height.toFloat() }
            .pointerInput(state) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, dragAmount ->
                    change.consume()
                    if (state.maxValue > 0 && viewportHeight > 0f) {
                        // 拖动条带全程 = 滚完全部内容
                        state.dispatchRawDelta(dragAmount * state.maxValue / viewportHeight)
                    }
                }
            }
            .drawBehind {
                if (state.maxValue <= 0 || thumbAlpha <= 0.01f) return@drawBehind
                val trackHeight = size.height
                val thumbHeight = (trackHeight * trackHeight / (trackHeight + state.maxValue))
                    .coerceIn(56.dp.toPx(), trackHeight)
                val progress = state.value.toFloat() / state.maxValue
                val bar = barWidth.toPx()
                val touch = touchWidth.toPx()
                drawRoundRect(
                    color = color.copy(alpha = color.alpha * thumbAlpha),
                    topLeft = Offset(touch - bar - 3.dp.toPx(), (trackHeight - thumbHeight) * progress),
                    size = Size(bar, thumbHeight),
                    cornerRadius = CornerRadius(bar / 2f, bar / 2f),
                )
            },
    )
}

/**
 * 全 App 弹窗统一挂载器：iOS 式缩放淡入（92% → 原尺寸，弹簧）、快速缩小淡出。
 *
 * 裸 `Dialog` 没有任何窗口动画，内容瞬间出现/消失；调用方 `showX = false` 时
 * Dialog 立即摘除，退出动画根本没机会播。这里把「逻辑可见」与「窗口存活」拆开：
 * 退出时窗口多活 [EXIT_MS]，动画播完才真正摘除。
 *
 * 与 [AppDialogSurface] 配对使用：`AppDialog(visible = showX, onDismissRequest = { showX = false })`
 */
@Composable
fun AppDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    // 窗口实际存活 = 正在显示 或 退出动画未播完
    var windowAlive by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            windowAlive = true
        } else {
            // 等 exit 动画（fast 档 140ms）播完再摘窗口；多留 40ms 余量防丢帧截尾
            delay((EXIT_MS + 40).toLong())
            windowAlive = false
        }
    }
    if (windowAlive) {
        Dialog(onDismissRequest = onDismissRequest, properties = properties) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(AppMotion.StandardMs)) +
                    scaleIn(
                        animationSpec = AppMotion.entrance(),
                        initialScale = 0.92f,
                        transformOrigin = TransformOrigin.Center,
                    ),
                exit = fadeOut(tween(AppMotion.FastMs)) +
                    scaleOut(
                        animationSpec = tween(AppMotion.FastMs, easing = AppMotion.easing),
                        targetScale = 0.96f,
                        transformOrigin = TransformOrigin.Center,
                    ),
                modifier = modifier,
            ) {
                content()
            }
        }
    }
}

/** 退出动画时长：与 AppMotion.fast() 一致 */
private val EXIT_MS = AppMotion.FastMs

/** 滚动静止后滚动条保持可见的时长，超过后淡出 */
private const val SCROLLBAR_FADE_DELAY_MS = 800L

/** 轻量按压反馈：非 Material Button 的自绘控件（色块、卡片）套这一层 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) AppMotion.PressedScale else 1f,
        animationSpec = AppMotion.press,
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

/**
 * iOS 分组列表（inset grouped）容器：白底圆角卡，配合 [AppFormRow] 使用。
 * 录入/编辑弹窗的分类单选、分类管理列表、协议入口等「设置页式」内容的统一底座。
 */
@Composable
fun AppFormGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShape.swatch)
            .background(cardBg),
        content = content,
    )
}

/**
 * iOS 分组列表行：左侧标签、右侧勾选/控件，整行 44dp+ 可点。
 *
 * 按压反馈是 iOS 式的灰底高亮（surfaceSubtle 渐入），不是 Material 波纹——
 * 波纹在全宽行上会拉出一道矩形光晕，与圆角分组卡割裂。
 * [selected] 为 true 时右侧显示强调色对勾（iOS 单选行的惯例）；
 * [showDivider] 控制行底分隔线，分组最后一行传 false。
 */
@Composable
fun AppFormRow(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberAppHaptics()
    val pressed by interaction.collectIsPressedAsState()
    // 选中 = 浅蓝底（selectBlue 低透明度，与筛选胶囊选中态同色系）+ 右侧勾号；
    // 按压仍是全 app 统一的浅灰高亮，二者同帧叠加时选中色优先
    val background by animateColorAsState(
        targetValue = when {
            selected -> selectBlue.copy(alpha = 0.12f)
            pressed -> surfaceSubtle
            else -> Color.Transparent
        },
        animationSpec = AppMotion.color,
        label = "formRowBackground",
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(background)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) {
                            haptics.tap()
                            onClick()
                        }
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = AppSpacing.cardPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = label,
                role = AppTextRole.body,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Spacer(modifier = Modifier.width(AppSpacing.iconGap))
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "已选择",
                    tint = accentBlue,
                    modifier = Modifier.size(20.dp),
                )
            }
            trailing()
        }
        if (showDivider) {
            AppDivider(modifier = Modifier.padding(start = AppSpacing.cardPadding))
        }
    }
}

/**
 * 弹窗背景实时模糊（iOS 式材质）：在 Compose Dialog 所在的 window 上开
 * FLAG_BLUR_BEHIND，让弹窗背后的 App 内容变成高斯模糊的背景层——弹窗因此
 * 「浮」在界面之上而不是把界面遮黑。只在 API 31+ 生效；系统无障碍关闭了
 * 「模糊效果」或设备不支持时静默退化为默认压暗，无需分支。
 */
@Composable
private fun DialogBackdropBlur() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    val density = LocalDensity.current
    LaunchedEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.also { attrs ->
            attrs.blurBehindRadius = with(density) { 24.dp.toPx() }.toInt()
        }
    }
}
