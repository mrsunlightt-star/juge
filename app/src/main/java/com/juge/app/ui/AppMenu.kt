package com.juge.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.juge.app.ui.theme.AppMotion
import com.juge.app.ui.theme.AppPalette
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppShadows
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppTextRole
import com.juge.app.ui.theme.LocalAppDarkThemeProgress
import com.juge.app.ui.theme.TextRole
import com.juge.app.ui.theme.rememberAppHaptics
import com.juge.app.ui.theme.surfaceRaised

/**
 * 全 App 浮动菜单（iOS 式）的统一实现：**一颗卡片 + 一种行**，长按菜单和 ⋮ 溢出菜单共用。
 *
 * 背景：分类管理弹窗里的 ⋮ 菜单此前直接用 Material3 `DropdownMenu`，容器色取的是
 * `MenuDefaults.containerColor`（`surfaceContainer`，本项目 colorScheme 没接这个槽位，
 * 落回 M3 基线淡紫 #F3EDF7）、4dp 圆角、无弥散阴影——和长按菜单的白卡完全不是一套，
 * 在白色弹窗底上像块外来贴纸（用户反馈「菜单 UI 和其他页面对不上」）。
 *
 * 现在两处菜单共用 [AppMenuCard]（`surfaceRaised` + `AppShape.menu` 14dp + `AppShadows.dialog`
 * 弥散阴影 + 上下 4dp 内边距）与 [AppMenuItemRow]（44dp 行高、20dp 图标、16dp 行内边距、
 * 无波纹按压高亮、带触感）。区别只在**怎么浮出来**：
 * - 长按菜单：同窗口透明交互浮层（`ContextMenuOverlay`，指尖弹出，弹出后手指继续拖动
 *   要能取消菜单进入排序，Dialog/Popup 收不到这根手指的后续事件）；
 * - ⋮ 溢出菜单：贴在按钮下方的 `Popup`（[AppDropdownMenu]），同样不压暗背景。
 */
@Immutable
internal data class AppMenuItem(
    @DrawableRes val iconRes: Int,
    val label: String,
    val iconTint: Color,
    /** null = 用 [labelRole] 的默认墨色（如「重命名」）；删除等破坏性项传 dangerRed */
    val labelColor: Color? = null,
    /** 字级角色：null = [AppTextRole.body]；放大字号的设置页（分类管理）传 [AppTextRole.dialogBody] */
    val labelRole: TextRole? = null,
    val onClick: () -> Unit,
) {
    // 注意：onClick 是 lambda，自动生成的 equals 恒不相等——不要拿 AppMenuItem 或
    // List<AppMenuItem> 当 `remember` / `derivedStateOf` 的 key（每次重组都是新实例）。
}

/** 菜单卡片宽度：与长按菜单同一档，短标签（重命名/删除）不撑成一整条 */
internal val AppMenuWidth: Dp = 176.dp

/** 菜单行高：iOS 菜单惯例，比 48dp 触控热区小一档（设计规范 §4.0 有菜单例外说明） */
private val MenuItemHeight: Dp = 44.dp

/** 卡片上下内边距：让首末行不顶着圆角 */
private val MenuCardVerticalPadding: Dp = 4.dp

/** 行间距：不再画分隔线（2026-10 改版），分组感由这一点留白 + 按压底色承担 */
private val MenuItemSpacing: Dp = 2.dp

/**
 * 阴影留白：`Popup` 是独立窗口，窗口按**内容**测量，卡片外侧不留白，
 * `Modifier.shadow(clip = false)` 画出去的弥散阴影会被窗口边界裁掉。
 * 跟随 dialog 档阴影走：以后调高 [AppShadows.dialog]，这里同幅度长，阴影不会被切平。
 */
private val MenuShadowRoom: Dp = AppShadows.dialog + 4.dp

/**
 * 菜单卡片：浮起一层的卡片 + 14dp 圆角 + dialog 档弥散阴影，行间 2dp、上下 4dp。
 *
 * 底色用 [surfaceRaised] 而不是 cardBg：日间两者都是纯白，夜间 surfaceRaised 比 cardBg
 * 亮一档——⋮ 菜单与弹窗卡片同处一层，底色相同就会「陷」进弹窗里看不出是浮层。
 * 不画 1dp 描边（2026-10 改版：全 App 结构性分割线下线），层次只由明度台阶 + 阴影给出。
 * 调用方通过 [modifier] 传位移/缩放，视觉部分（阴影/底色/内边距/裁圆角）都在这里，
 * 避免长按菜单和 ⋮ 菜单各画一套卡片。
 */
@Composable
internal fun AppMenuCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(MenuItemSpacing),
        modifier = modifier
            .shadow(
                elevation = AppShadows.dialog,
                shape = AppShape.menu,
                clip = false,
                ambientColor = AppShadows.ambientShadow,
                spotColor = AppShadows.spotShadow,
            )
            .background(surfaceRaised, AppShape.menu)
            // 行底色是直角矩形，要按卡片圆角裁一刀，否则按压高亮会从 14dp 圆角外溢；
            // 放在 background 之后、padding 之前：只裁内容，不吃掉上面的弥散阴影。
            .clip(AppShape.menu)
            .padding(vertical = MenuCardVerticalPadding),
        content = content,
    )
}

/**
 * 菜单项：图标 + 文字，整行 44dp 可点，iOS 式按压高亮（无波纹）。
 *
 * [onSelected] 在 [AppMenuItem.onClick] 之后触发：菜单项点完就该收起来，
 * 由菜单容器传进来，避免每个调用点各自记得 `expanded = false`。
 */
@Composable
internal fun AppMenuItemRow(
    item: AppMenuItem,
    onSelected: (() -> Unit)? = null,
) {
    val haptics = rememberAppHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 按压高亮要相对菜单卡片「压下一层」：日夜两端用不同令牌，中间随主题动画连续插值。
    val pressedColor = lerp(
        AppPalette.Light.surfaceSubtle,
        AppPalette.Dark.cardBg,
        LocalAppDarkThemeProgress.current,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MenuItemHeight)
            .background(if (pressed) pressedColor else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) {
                haptics.click()
                item.onClick()
                onSelected?.invoke()
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(item.iconRes),
            contentDescription = null,
            tint = item.iconTint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(AppSpacing.iconGap))
        AppText(
            text = item.label,
            role = item.labelRole ?: AppTextRole.body,
            color = item.labelColor,
            // 定宽卡片里标签必须单行省略：行高固定 44dp，换行会把第二行直接切掉
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 贴在触发按钮下方的溢出菜单（⋮）。
 *
 * 直接挂在**触发按钮所在的 Box 里**即可，`Popup` 会自动以该 Box 为锚点：
 * ```
 * Box {
 *     IconButton(onClick = { expanded = true }) { … }
 *     AppDropdownMenu(expanded, items, onDismissRequest = { expanded = false })
 * }
 * ```
 *
 * 行为与 iOS/Material 溢出菜单一致：卡片右边缘贴按钮右边缘、向下 4dp；下方放不下就翻到上方；
 * 左右上下都钳在父窗口内留 8dp（钳制按**卡片**算，阴影留白可能探出窗口，属预期）；
 * 点空白处或返回键关闭（`focusable` 的 Popup 自带）；点任一菜单项后自动收起。
 * 不压暗背景：菜单卡自身用底色、圆角与阴影建立层级。
 */
@Composable
internal fun AppDropdownMenu(
    expanded: Boolean,
    items: List<AppMenuItem>,
    onDismissRequest: () -> Unit,
) {
    if (!expanded) return
    if (items.isEmpty()) {
        // 空菜单没有可点的收口，别让调用方的 expanded 永远停在 true
        LaunchedEffect(Unit) { onDismissRequest() }
        return
    }

    val density = LocalDensity.current
    val shadowRoomPx = with(density) { MenuShadowRoom.roundToPx() }
    // 弹出方向决定缩放原点：从靠近按钮的那一角「长」出来（iOS 同款手感）。
    // 真正的值由 [AppMenuPositionProvider.calculatePosition] 在布局期写入；
    // 这里的默认值只管首帧（此时入场 alpha≈0，看不见）。
    val originState = remember { mutableStateOf(TransformOrigin(1f, 0f)) }

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = AppMotion.entrance(),
        label = "dropdownMenuProgress",
    )

    Popup(
        popupPositionProvider = remember(density, shadowRoomPx) {
            AppMenuPositionProvider(
                density = density,
                shadowRoomPx = shadowRoomPx,
                originState = originState,
            )
        },
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        // 留白包在卡片外：Popup 窗口 = 卡片 + 四周留白，弥散阴影才不会被窗口裁掉
        Box(modifier = Modifier.padding(MenuShadowRoom)) {
            AppMenuCard(
                modifier = Modifier
                    .width(AppMenuWidth)
                    .graphicsLayer {
                        alpha = progress
                        scaleX = 0.9f + 0.1f * progress
                        scaleY = 0.9f + 0.1f * progress
                        transformOrigin = originState.value
                    },
            ) {
                items.forEach { AppMenuItemRow(it, onSelected = onDismissRequest) }
            }
        }
    }
}

/**
 * 溢出菜单落点：纯几何计算，单测直接喂坐标断言（不需要真机/Robolectric）。
 *
 * 卡片的真实尺寸**从 `popupContentSize` 反推**（内容 = 卡片 + 四周阴影留白），而不是按
 * 「行高 × 项数」另算一遍——卡片内部还有行间距（[MenuItemSpacing]），两处各算一次迟早会漂：
 * 翻到上方时少算 2dp 就会让卡片几乎贴住按钮，多算则压进窗口安全边距。
 *
 * 输入的 `anchorBounds` 与返回的偏移量都是**父窗口坐标**（`PopupPositionProvider`
 * 的契约）；返回的是弹窗窗口左上角，即卡片左上角再外扩一层阴影留白。
 */
internal class AppMenuPositionProvider(
    private val density: Density,
    /** 四周阴影留白（px）；卡片尺寸 = popupContentSize - 2 × 该值 */
    private val shadowRoomPx: Int,
    /** 由 [calculatePosition] 在布局期写入：卡片从哪一角长出来 */
    private val originState: MutableState<TransformOrigin>,
) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = with(density) {
        val cardWidth = (popupContentSize.width - shadowRoomPx * 2).coerceAtLeast(1)
        val cardHeight = (popupContentSize.height - shadowRoomPx * 2).coerceAtLeast(1)
        val margin = 8.dp.roundToPx()
        val gap = 4.dp.roundToPx()
        val maxX = (windowSize.width - margin - cardWidth).coerceAtLeast(margin)
        val x = (anchorBounds.right - cardWidth).coerceIn(margin, maxX)

        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - gap - cardHeight
        val roomBelow = windowSize.height - margin - below
        val roomAbove = anchorBounds.top - gap - margin

        val y: Int
        when {
            roomBelow >= cardHeight -> {
                y = below
                originState.value = TransformOrigin(pivotFractionX = 1f, pivotFractionY = 0f)
            }
            roomAbove >= cardHeight -> {
                y = above
                originState.value = TransformOrigin(pivotFractionX = 1f, pivotFractionY = 1f)
            }
            // 上下都放不下（小屏/窗口很短）：贴着窗口下沿，原点取离按钮近的一侧
            else -> {
                y = (windowSize.height - margin - cardHeight).coerceAtLeast(margin)
                originState.value = TransformOrigin(
                    pivotFractionX = 1f,
                    pivotFractionY = if (y < anchorBounds.top) 1f else 0f,
                )
            }
        }
        IntOffset(x - shadowRoomPx, y - shadowRoomPx)
    }
}
