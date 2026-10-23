package com.juge.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 全 App 的排版阶梯。
 *
 * 界面里不要再手写 `fontSize = …`：同一个层级在不同页面各写一档（13/14/15/16/17/18/20）
 * 会让标题、正文和按钮看起来不是一套。这里只定义**层级**，颜色由调用方按语义指定
 * （标题用 accentBlue、正文用 textWhite/textGray、辅助说明用 textTertiary）。
 *
 * App UI 统一使用系统中文无衬线字体；`assets/fonts` 下的 7 款字体只服务于桌面上
 * 用户可选的组件正文，不参与应用界面排版。
 */
object AppType {
    /** 品牌标题「句阁」 */
    val brand = TextStyle(
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.3).sp,
    )

    /** 品牌副标题「DeskQuotes」 */
    val brandSub = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
    )

    /** 付费/账号弹窗的强调标题（带图标），比普通弹窗标题再重一档 */
    val hero = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.2).sp,
    )

    /** 普通弹窗标题 */
    val dialogTitle = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Bold,
    )

    /** 卡片/区块内的小标题（非 accent 色场景） */
    val title = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Bold,
    )

    /** 定制页各区块标题：14sp 加粗，配 accentBlue，和 13sp 正文拉开层级 */
    val sectionTitle = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.1.sp,
    )

    /** 控件标签（「字体颜色」「外框圆角大小」等） */
    val label = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.1.sp,
    )

    /** 标准正文与说明文字 */
    val body = TextStyle(
        fontSize = 13.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    )

    val bodyStrong = TextStyle(
        fontSize = 13.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** 金句正文：比界面正文大一档，行距更松 */
    val bodyLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Normal,
    )

    /** 次要说明、空态、页脚 */
    val caption = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
    )

    /** 注释、备案号等最小可读字号 */
    val micro = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
    )

    /** 角标：免费 / PRO / 分类标签 */
    val badge = TextStyle(
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.2.sp,
    )

    /** 主按钮文字 */
    val button = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Bold,
    )

    /** 胶囊按钮、页签文字 */
    val chip = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    )

    /** 文字链接、TextButton */
    val link = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    )
}

/**
 * 把上面的阶梯映射进 MaterialTheme，让没有显式指定 style 的 Material 组件
 * （AlertDialog、TextButton、Chip 等）也落在同一套字号上。
 */
internal val AppTypography = Typography(
    headlineSmall = AppType.hero,
    titleLarge = AppType.dialogTitle,
    titleMedium = AppType.title,
    titleSmall = AppType.sectionTitle,
    bodyLarge = AppType.bodyLarge,
    bodyMedium = AppType.body,
    bodySmall = AppType.caption,
    labelLarge = AppType.button,
    labelMedium = AppType.chip,
    labelSmall = AppType.micro,
)
