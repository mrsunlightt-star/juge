package com.juge.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 全 App 的**语义文字角色**：字号、字重、颜色三位一体，在这里只有一个出处。
 *
 * 之前界面里 `style = AppType.x, color = y` 成对散落在几十个调用点，同一层级
 * 在不同页面各配一色，层级管理无从谈起。现在界面文字一律写
 * `AppText(text, role = AppTextRole.bodySecondary)`——想改「次级正文」的任何一项，
 * 只改这里的定义。
 *
 * 角色命名 = 字级 + 语义变体：
 * - 无后缀：该字级的标准色（标题/正文用主墨色，caption/micro 用辅助灰）；
 * - `Secondary/Tertiary`：同字级的次级/辅助色；
 * - `Accent`：强调色（链接、区块标题、可点提示）；
 * - `Neutral`：分组标题等不参与强调的场景；
 * - `OnFill`：填充色（蓝底/彩底）上的白字。
 *
 * 少数动态颜色场景（页签随滑动渐变、徽章随状态换色）用 `AppText` 的 `color`
 * 参数覆写：**字级与字重仍从角色取**，只有颜色临时决定。
 * 上下文色（Material 按钮内的 `contentColor`、输入框 label 等）继续用
 * `AppType` 字级 + 组件给的动态色，不经过角色。
 */
data class TextRole(val style: TextStyle, val color: Color)

/**
 * 语义文字角色。带主题色/主题字重的角色都是 `@Composable get()`：组合期从 [LocalAppPalette]
 * 解析颜色、从 [LocalAppFontWeightScale] 解析字重（暗色 -40%），日/夜切换即随之变化，
 * 调用点写法不变。固定白字角色（OnFill/brand 系）不吃调色板，但字重仍跟随模式。
 */
object AppTextRole {

    // —— 弹窗/卡片标题 ——
    val dialogTitle: TextRole @Composable get() = TextRole(AppType.dialogTitle, textPrimary)
    val title: TextRole @Composable get() = TextRole(AppType.title, textPrimary)
    val hero: TextRole @Composable get() = TextRole(AppType.hero, textPrimary)

    // —— 区块/分组标题 ——
    val sectionTitleAccent: TextRole @Composable get() = TextRole(AppType.sectionTitle, accentBlue)
    val sectionTitleInk: TextRole @Composable get() = TextRole(AppType.sectionTitle, textPrimary)
    val sectionTitleNeutral: TextRole @Composable get() = TextRole(AppType.sectionTitle, textSecondary)

    // —— 控件标签 ——
    // 14sp：与它们身下的选项文字（chip 14sp）同级——标签比选项还小会显得层级倒挂
    // （用户反馈）。AppType.label(12sp) 保留给徽章类小字（PRO 徽章等直接用 AppType）。
    private val labelStyle: TextStyle @Composable get() =
        AppType.label.copy(fontSize = 14.sp, lineHeight = 20.sp)
    val label: TextRole @Composable get() = TextRole(labelStyle, textSecondary)
    val labelAccent: TextRole @Composable get() = TextRole(labelStyle, accentBlue)

    // —— 正文 ——
    val body: TextRole @Composable get() = TextRole(AppType.body, textPrimary)

    /**
     * 逐行阅读/逐行操作的设置页（金句分类管理中心）正文：16sp / Medium，比常规弹窗正文大一档
     * （用户反馈小字读起来吃力）。设计规范 §6.1.4：同一弹窗内的菜单、输入、按钮必须一起放大，
     * 所以这里的菜单项、输入框、按钮 style 都从这个令牌取，不要再写 `fontSize = 16.sp` 字面量。
     */
    val dialogBody: TextRole @Composable get() = TextRole(
        AppType.body.copy(
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = appFontWeight(FontWeight.Medium),
        ),
        textPrimary,
    )
    val bodySecondary: TextRole @Composable get() = TextRole(AppType.body, textSecondary)
    val bodyTertiary: TextRole @Composable get() = TextRole(AppType.body, textTertiary)
    val bodyStrong: TextRole @Composable get() = TextRole(AppType.bodyStrong, textPrimary)
    val bodyStrongSecondary: TextRole @Composable get() = TextRole(AppType.bodyStrong, textSecondary)
    val bodyStrongAccent: TextRole @Composable get() = TextRole(AppType.bodyStrong, accentBlue)
    val bodyLarge: TextRole @Composable get() = TextRole(AppType.bodyLarge, textPrimary)

    // —— 辅助/注释 ——
    val caption: TextRole @Composable get() = TextRole(AppType.caption, textTertiary)
    val captionSecondary: TextRole @Composable get() = TextRole(AppType.caption, textSecondary)
    val captionAccent: TextRole @Composable get() = TextRole(AppType.caption, accentBlue)
    val micro: TextRole @Composable get() = TextRole(AppType.micro, textTertiary)

    // —— 链接 ——
    val link: TextRole @Composable get() = TextRole(AppType.link, accentBlue)

    // —— 填充色上的白字（角标、按钮、选中胶囊） ——
    val badgeOnFill: TextRole @Composable get() = TextRole(AppType.badge, Color.White)
    val buttonOnFill: TextRole @Composable get() = TextRole(AppType.button, Color.White)
    val chipOnFill: TextRole @Composable get() = TextRole(AppType.chip, Color.White)

    // —— 未选中胶囊 ——
    val chip: TextRole @Composable get() = TextRole(AppType.chip, textSecondary)

    // —— 品牌标题（英雄图上的白字，阴影由调用方叠加） ——
    val brand: TextRole @Composable get() = TextRole(AppType.brand, Color.White)
    val brandSub: TextRole @Composable get() = TextRole(AppType.brandSub, Color.White.copy(alpha = 0.82f))
}
