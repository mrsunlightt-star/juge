package com.juge.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 夜色字重缩放系数：用户要求暗色模式「字重整体 -40%」，即按 FontWeight 数值 × 0.6。
 *
 * 缩放结果吸附到 100 档，保证系统字体回退可预期（FontWeight 取值越规整，越容易命中
 * 字体族里真实存在的字重，避免合成粗体发虚）：
 * Black 900→500、Bold 700→400、SemiBold 600→400、Medium 500→300、Normal 400→200。
 *
 * 之所以暗色要压轻：同一个字重在深色底上视觉上更「涨」，笔画边缘的光晕也更明显；
 * 压一档既回正观感，也让深色界面的层次更安静。字重是**模式相关**的量，
 * 所以不走常量表，而是经 CompositionLocal 在组合期解析。
 */
internal val LocalAppFontWeightScale = staticCompositionLocalOf { 1f }

/** 纯函数版字重缩放（普通 JUnit 可直接测）：× scale 后吸附到最近的 100 档，钳在 100..900 */
internal fun scaleFontWeight(weight: FontWeight, scale: Float): FontWeight {
    if (scale == 1f) return weight
    val target = (weight.weight * scale).roundToInt().coerceIn(100, 900)
    val snapped = (((target + 50) / 100) * 100).coerceIn(100, 900)
    return FontWeight(snapped)
}

/** 组合期字重：全 App 的字级都经过这里，暗色模式自动 -40% */
@Composable
internal fun appFontWeight(weight: FontWeight): FontWeight =
    scaleFontWeight(weight, LocalAppFontWeightScale.current)

/**
 * 全 App 的排版阶梯。
 *
 * 界面里不要再手写 `fontSize = …`：同一个层级在不同页面各写一档（13/14/15/16/17/18/20）
 * 会让标题、正文和按钮看起来不是一套。这里只定义**层级**，颜色由调用方按语义指定
 * （标题用 accentBlue、正文用 textWhite/textGray、辅助说明用 textTertiary）。
 *
 * 字重原则：**层级靠字号与颜色拉开，不靠加粗**。全粗只留给一屏最大的品牌标题；
 * 其余层级从 Bold 降到 SemiBold（中文粗体渲染偏重，Bold 到处用会显得「用力」，
 * 「不用力」恰是质感的一部分）。
 *
 * 所有字级都是 `@Composable get()`：暗色模式下字重整体 -40%（见 [LocalAppFontWeightScale]），
 * 调用点写法不变。需要覆写字重的地方，请套 [appFontWeight] 换算，不要直接写死 FontWeight，
 * 否则会在暗色模式里把「整体 -40%」这条规则漏掉。
 *
 * App UI 统一使用系统中文无衬线字体；`assets/fonts` 下的 9 款字体只服务于桌面上
 * 用户可选的组件正文，不参与应用界面排版。
 */
object AppType {

    /** 夜色字重缩放比例：FontWeight × 0.6 = -40% */
    internal const val DarkWeightScale = 0.6f

    @Composable
    private fun type(
        fontSize: TextUnit,
        lineHeight: TextUnit,
        fontWeight: FontWeight,
        letterSpacing: TextUnit = TextUnit.Unspecified,
    ): TextStyle = TextStyle(
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = appFontWeight(fontWeight),
        letterSpacing = letterSpacing,
    )

    /** 品牌标题「句阁」 */
    val brand: TextStyle @Composable get() = type(
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.3).sp,
    )

    /** 品牌副标题「DeskQuotes」 */
    val brandSub: TextStyle @Composable get() = type(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
    )

    /** 付费/账号弹窗的强调标题（带图标），比普通弹窗标题再重一档 */
    val hero: TextStyle @Composable get() = type(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp,
    )

    /** 普通弹窗标题 */
    val dialogTitle: TextStyle @Composable get() = type(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** 卡片/区块内的小标题（非 accent 色场景） */
    val title: TextStyle @Composable get() = type(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** 定制页各区块标题：14sp 半粗，配 accentBlue，和正文拉开层级 */
    val sectionTitle: TextStyle @Composable get() = type(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    )

    /** 控件标签（「字体颜色」「外框圆角大小」等） */
    val label: TextStyle @Composable get() = type(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    )

    /** 标准正文与说明文字：iOS body 17pt 与 Material bodyMedium 14sp 之间的平衡点 */
    val body: TextStyle @Composable get() = type(
        fontSize = 14.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
    )

    val bodyStrong: TextStyle @Composable get() = type(
        fontSize = 14.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** 金句正文：界面正文的阅读档，行距更松 */
    val bodyLarge: TextStyle @Composable get() = type(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    )

    /** 次要说明、空态、页脚 */
    val caption: TextStyle @Composable get() = type(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
    )

    /** 注释、备案号等最小可读字号 */
    val micro: TextStyle @Composable get() = type(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
    )

    /** 角标：免费 / PRO / 分类标签 */
    val badge: TextStyle @Composable get() = type(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp,
    )

    /** 主按钮文字 */
    val button: TextStyle @Composable get() = type(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** 胶囊按钮、页签文字 */
    val chip: TextStyle @Composable get() = type(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    )

    /** 文字链接、TextButton */
    val link: TextStyle @Composable get() = type(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    )
}

/**
 * 把上面的阶梯映射进 MaterialTheme，让没有显式指定 style 的 Material 组件
 * （AlertDialog、TextButton、Chip 等）也落在同一套字号上。
 * 字重同样经过 [appFontWeight]，暗色模式跟随整体 -40%。
 */
@Composable
internal fun appTypography(): Typography = Typography(
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
