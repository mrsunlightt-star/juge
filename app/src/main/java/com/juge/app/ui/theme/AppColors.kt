package com.juge.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 全局主题色统一收在这里，避免各界面各抄一份；改色只需改这一处。
 * 色值取自主界面薄荷青绿与网站同款配色。除用户指定保留的 [AppPalette.selectBlue] 外，
 * 承载文字/图标的前景色按 WCAG AA 校验（正文 4.5:1、非文字元素 3:1）。
 * [AppPalette.selectBlue] 保留为用户喜欢的明亮浅蓝底白字搭配；这是有意保留的品牌例外，白字对比度低于常规 AA 标准。
 *
 * 夜色模式：全部令牌收进 [AppPalette]（纯数据，日/夜两套实例，普通 JUnit 可直接测对比度），
 * 运行期经 [LocalAppPalette] 下发。文件底部的同名顶层 getter 是**唯一的取色入口**：
 * 令牌名字与迁移前完全一致，消费端 import 与调用点无感知；派生令牌
 * （textPrimary/textSecondary/dividerColor/gridLine）在 AppPalette 内按基础令牌计算。
 */
internal data class AppPalette(
    /** 页面/抽屉面板底色 */
    val darkBg: Color,
    /** 面板上的凹槽底（分段控制器轨道）：比面板深一档才有"嵌进去"的层次 */
    val panelTrack: Color,
    /** 卡片底 */
    val cardBg: Color,
    /** 强调色（全局唯一 accent）的文字/描边档：用于区块标题/链接/图标/选中描边；填充角色用 selectBlue / accentFillStrong */
    val accentBlue: Color,
    /** 强调色填充档（Material secondary 等需要 3:1 对比的非文字填充） */
    val accentFillStrong: Color,
    /** 网站同款精细边框与网格线 */
    val borderBlue: Color,
    /** 抽屉拖拽把手 */
    val sheetHandle: Color,
    /** 主文字墨色 */
    val textWhite: Color,
    /** 副标题/描述文字 */
    val textGray: Color,
    /** 明亮薄荷底上的深色文字/图标（填充不变，故日夜同值） */
    val mintInk: Color,
    /** 用户指定的明亮浅蓝选中填充（品牌例外，日夜不变） */
    val selectBlue: Color,
    /** 12sp 说明与图标的最小可用灰 */
    val textTertiary: Color,
    /** 凹陷浅色面 */
    val surfaceSubtle: Color,
    /** 更浅的页面底 */
    val surfaceMuted: Color,
    val dangerRed: Color,
    val disabledGray: Color,
    /** 警示横幅三件套（底/字/描边） */
    val warningInk: Color,
    val warningBg: Color,
    val warningBorder: Color,
    /** 明亮金：只作填充/渐变，不承载白字 */
    val premiumGold: Color,
    /** 底色上的金色文字/图标/描边 */
    val premiumGoldDeep: Color,
    /** 金色底上的深棕文字（金底不变，故日夜同值） */
    val premiumGoldInk: Color,
    /** 金色渐变终点 */
    val premiumGoldEnd: Color,
    val successGreen: Color,
    /** 品牌墨（固定浅底如顶栏白圆上使用，日夜同值） */
    val brandInk: Color,
    val brandInkSoft: Color,
    /** 中性轨道灰：保留给需要轨道底色的控件，不带蓝调 */
    val trackGray: Color,
    /** 未选中胶囊文字 */
    val chipTextInk: Color,
    /** 主界面极光渐变：薄荷起点 */
    val auroraMint: Color,
    /** 主界面极光渐变：天蓝中段 */
    val auroraSky: Color,
    /** 主界面极光渐变：末段（与页面底衔接） */
    val auroraMist: Color,
    /** 深色遮罩上的浮层卡片（裁剪/加载；本就是深色场景专用，日夜同值） */
    val overlayCard: Color,
    /** 抽屉毛玻璃的模糊底色（HazeStyle.backgroundColor） */
    val drawerGlassBg: Color,
    /** 抽屉毛玻璃 tint（HazeStyle.tint）：玻璃质感由它提供 */
    val drawerGlassTint: Color,
) {
    // —— 派生令牌：永远跟着基础令牌走 ——
    val textPrimary: Color get() = textWhite
    val textSecondary: Color get() = textGray
    val dividerColor: Color get() = borderBlue
    val gridLine: Color get() = accentBlue.copy(alpha = 0.12f)

    companion object {
        /** 日间调色板：迁移前的全部原值 */
        val Light = AppPalette(
            darkBg = Color(0xFFFAFBFD), // 几乎全白、只留一丝冷调，靠卡片软阴影分层（用户确认的明度）
            panelTrack = Color(0xFFEEF2F7),
            cardBg = Color(0xFFFFFFFF), // 纯净白卡片
            // 晴空蓝 deep 档，白底 5.9:1、面板底约 5.4:1。
            // 历史上这里是青绿 #0F766E，与 selectBlue、accentLightBlue 三套蓝并存——已统一到 selectBlue 同一色相
            accentBlue = Color(0xFF0369A1),
            accentFillStrong = Color(0xFF0284C6), // 白底 4.1:1
            borderBlue = Color(0xFFE2E8F0),
            sheetHandle = Color(0xFF94A3B8), // 面板底上 2.3:1，够亮也够安静
            textWhite = Color(0xFF0F172A), // 现代极简墨色 (Slate Ink)
            textGray = Color(0xFF52627A), // 板岩灰：白底 6.1:1、面板底约 5.5:1
            mintInk = Color(0xFF134E4A),
            selectBlue = Color(0xFF42B8EC),
            textTertiary = Color(0xFF64748B), // 白底 4.8:1
            surfaceSubtle = Color(0xFFF1F5F9),
            surfaceMuted = Color(0xFFF8FAFC),
            dangerRed = Color(0xFFDC2626),
            disabledGray = Color(0xFFCBD5E1),
            warningInk = Color(0xFF78350F),
            warningBg = Color(0xFFFFFBEB),
            warningBorder = Color(0xFFFDE68A),
            premiumGold = Color(0xFFF59E0B),
            premiumGoldDeep = Color(0xFFB45309), // 白底 5.0:1
            premiumGoldInk = Color(0xFF431407), // 金底上 7.3:1
            premiumGoldEnd = Color(0xFFD97706), // 深棕字 4.9:1
            successGreen = Color(0xFF047857), // 白底 5.5:1
            brandInk = Color(0xFF064E3B),
            brandInkSoft = Color(0xFF065F46),
            trackGray = Color(0xFFE6E6E6),
            chipTextInk = Color(0xFF334155),
            auroraMint = Color(0xFF5EEAD4), // 薄荷
            auroraSky = Color(0xFF38BDF8), // 晴空蓝
            auroraMist = Color(0xFFBAE6FD), // 晨露浅青
            overlayCard = Color(0xFF1E2436),
            drawerGlassBg = Color(0xFFFFFFFF),
            // 白 45% tint（比 HazeMaterials.thin 的 60% 更透一档，用户确认）
            drawerGlassTint = Color(0x73FFFFFF),
        )

        /** 夜间调色板：深蓝夜空系，与浅色 slate 墨族同源；品牌填充（selectBlue/金/薄荷）保持不变 */
        val Dark = AppPalette(
            darkBg = Color(0xFF0B1220), // 深海蓝黑页面底
            panelTrack = Color(0xFF101A2E), // 比卡片深一档的凹槽
            cardBg = Color(0xFF1B2540), // 夜空板岩卡片
            accentBlue = Color(0xFF7DD3FC), // 月光天蓝：夜底约 11:1
            accentFillStrong = Color(0xFF38BDF8), // 非文字填充档，夜底 3:1 以上
            borderBlue = Color(0xFF2A3A55),
            sheetHandle = Color(0xFF64748B),
            textWhite = Color(0xFFF1F5F9), // 霜白主文字
            textGray = Color(0xFF94A3B8), // 月灰：卡片底约 5.9:1
            mintInk = Color(0xFF134E4A),
            selectBlue = Color(0xFF42B8EC),
            textTertiary = Color(0xFF7E8FA6), // 页面底约 5.7:1
            surfaceSubtle = Color(0xFF22304A),
            surfaceMuted = Color(0xFF0E1830),
            dangerRed = Color(0xFFF87171),
            disabledGray = Color(0xFF475569),
            warningInk = Color(0xFFFCD34D), // 夜间警示字提亮为琥珀亮档：警示底约 11:1
            warningBg = Color(0xFF33270B),
            warningBorder = Color(0xFF7A5C10),
            premiumGold = Color(0xFFF59E0B),
            premiumGoldDeep = Color(0xFFFBBF24), // 夜底金色文字提亮：卡片底约 8:1
            premiumGoldInk = Color(0xFF431407),
            premiumGoldEnd = Color(0xFFD97706),
            successGreen = Color(0xFF34D399),
            brandInk = Color(0xFF064E3B),
            brandInkSoft = Color(0xFF065F46),
            trackGray = Color(0xFF33415C),
            chipTextInk = Color(0xFFC6D2E2),
            auroraMint = Color(0xFF14B8A6), // 夜光版：压暗的青
            auroraSky = Color(0xFF0EA5E9), // 夜光版：压暗的天蓝
            auroraMist = Color(0xFF1E3A5F), // 夜光版末段：没入夜色
            overlayCard = Color(0xFF1E2436),
            drawerGlassBg = Color(0xFF0B1220),
            drawerGlassTint = Color(0x8C0B1220), // 深底 55%：与日间同等的透出度
        )
    }
}

/** 当前生效的调色板；夜间模式由 Theme.kt 提供 Dark，默认 Light（含未包主题的组合预览场景） */
internal val LocalAppPalette = staticCompositionLocalOf { AppPalette.Light }

// —— 主题感知取色入口：名字与迁移前的顶层令牌完全一致 ——
// 消费端照旧 `import com.juge.app.ui.theme.xxx` 后直接引用；getter 在组合期解析。

internal val darkBg: Color @Composable get() = LocalAppPalette.current.darkBg

internal val panelTrack: Color @Composable get() = LocalAppPalette.current.panelTrack

internal val cardBg: Color @Composable get() = LocalAppPalette.current.cardBg

internal val accentBlue: Color @Composable get() = LocalAppPalette.current.accentBlue

internal val accentFillStrong: Color @Composable get() = LocalAppPalette.current.accentFillStrong

internal val borderBlue: Color @Composable get() = LocalAppPalette.current.borderBlue

internal val sheetHandle: Color @Composable get() = LocalAppPalette.current.sheetHandle

internal val textWhite: Color @Composable get() = LocalAppPalette.current.textWhite

internal val textGray: Color @Composable get() = LocalAppPalette.current.textGray

internal val mintInk: Color @Composable get() = LocalAppPalette.current.mintInk

internal val selectBlue: Color @Composable get() = LocalAppPalette.current.selectBlue

internal val textTertiary: Color @Composable get() = LocalAppPalette.current.textTertiary

internal val surfaceSubtle: Color @Composable get() = LocalAppPalette.current.surfaceSubtle

internal val surfaceMuted: Color @Composable get() = LocalAppPalette.current.surfaceMuted

internal val dangerRed: Color @Composable get() = LocalAppPalette.current.dangerRed

internal val disabledGray: Color @Composable get() = LocalAppPalette.current.disabledGray

internal val warningInk: Color @Composable get() = LocalAppPalette.current.warningInk

internal val warningBg: Color @Composable get() = LocalAppPalette.current.warningBg

internal val warningBorder: Color @Composable get() = LocalAppPalette.current.warningBorder

internal val premiumGold: Color @Composable get() = LocalAppPalette.current.premiumGold

internal val premiumGoldDeep: Color @Composable get() = LocalAppPalette.current.premiumGoldDeep

internal val premiumGoldInk: Color @Composable get() = LocalAppPalette.current.premiumGoldInk

internal val premiumGoldEnd: Color @Composable get() = LocalAppPalette.current.premiumGoldEnd

internal val successGreen: Color @Composable get() = LocalAppPalette.current.successGreen

internal val brandInk: Color @Composable get() = LocalAppPalette.current.brandInk

internal val brandInkSoft: Color @Composable get() = LocalAppPalette.current.brandInkSoft

internal val trackGray: Color @Composable get() = LocalAppPalette.current.trackGray

internal val chipTextInk: Color @Composable get() = LocalAppPalette.current.chipTextInk

internal val auroraMint: Color @Composable get() = LocalAppPalette.current.auroraMint

internal val auroraSky: Color @Composable get() = LocalAppPalette.current.auroraSky

internal val auroraMist: Color @Composable get() = LocalAppPalette.current.auroraMist

internal val overlayCard: Color @Composable get() = LocalAppPalette.current.overlayCard

internal val drawerGlassBg: Color @Composable get() = LocalAppPalette.current.drawerGlassBg

internal val drawerGlassTint: Color @Composable get() = LocalAppPalette.current.drawerGlassTint

internal val textPrimary: Color @Composable get() = LocalAppPalette.current.textPrimary

internal val textSecondary: Color @Composable get() = LocalAppPalette.current.textSecondary

internal val dividerColor: Color @Composable get() = LocalAppPalette.current.dividerColor

internal val gridLine: Color @Composable get() = LocalAppPalette.current.gridLine
