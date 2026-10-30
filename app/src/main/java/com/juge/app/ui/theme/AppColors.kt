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
 *
 * 分层约定（2026-10 改版）：**App 内部页面不再画 1dp 边框**，页面/卡片/控件的层次全部由
 * 明度台阶表达——日间：页面 [Light.darkBg] → 卡片 [Light.cardBg] → 控件 [Light.surfaceSubtle]；
 * 夜间：页面 [Dark.darkBg] → 卡片 [Dark.cardBg] → 控件 [Dark.surfaceSubtle]。
 * [borderBlue] 只留给「必须勾边才成立」的功能性场景（选中态、图片上的白色控件等）。
 */
internal data class AppPalette(
    /** 页面/抽屉面板底色 */
    val darkBg: Color,
    /** 面板上的凹槽底（分段控制器轨道）：比面板深一档才有"嵌进去"的层次 */
    val panelTrack: Color,
    /** 卡片底 */
    val cardBg: Color,
    /** 浮起控件面（分段滑块、悬浮小控件）：比卡片再亮一档；日间白、夜间 #262626 */
    val surfaceRaised: Color,
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
    /** 输入框底色（未聚焦）：靠明度台阶从卡片上分出来，不画 1dp 描边 */
    val fieldBg: Color,
    /** 输入框底色（聚焦）：比未聚焦再提一档明度，替代聚焦描边表达焦点 */
    val fieldBgFocused: Color,
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
        /** 日间调色板：无描边分层——页面比卡片深一档，控件靠明度台阶下沉 */
        val Light = AppPalette(
            // 页面底比卡片深一档：去掉卡片 1dp 描边后，白卡靠「比页面亮 + 软阴影」立起来
            darkBg = Color(0xFFF2F5F9),
            panelTrack = Color(0xFFE8EDF4),
            cardBg = Color(0xFFFFFFFF), // 纯净白卡片
            surfaceRaised = Color(0xFFFFFFFF), // 白卡片上的浮起滑块仍是纯白，靠阴影立起来
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
            surfaceSubtle = Color(0xFFEDF1F7),
            surfaceMuted = Color(0xFFF6F8FB),
            // 输入框：白卡上的「微下沉」一档；聚焦再深一档（替代原来的 1dp 聚焦描边）
            fieldBg = Color(0xFFF1F4F9),
            fieldBgFocused = Color(0xFFE5ECF5),
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

        /**
         * 夜间调色板：**中性深灰**阶梯（用户明确要求「真正的暗黑背景」，不再用蓝紫夜空；
         * 2026-10 二次反馈：底色不要接近纯黑、整体上移一档，页面 #1A1A1A / 卡片 #252525）。
         * 页面 → 卡片 → 控件逐级提亮，层次只靠明度差表达；任何令牌都不带蓝/紫偏色。
         * 金色/薄荷等品牌填充保持不变；**selectBlue 夜间比日间压暗 20%**（亮蓝天做底色时
         * 夜间太扎眼，白字对比度也只有 2.26:1）。
         */
        val Dark = AppPalette(
            // 2026-10 二次调色（用户反馈「夜间还是黑了一些」+「别用纯黑做底色」）：
            // 中性色阶整体上移一档——页面底不再是接近纯黑的 #0A0A0A，而是 #1A1A1A。
            // 上移幅度受对比度硬门槛约束：卡片底再亮下去，辅助灰 textTertiary 在卡片上
            // 就掉出 4.5:1（#252525 时约 4.55:1，已是上限）。要更亮必须同时提亮文字灰阶。
            darkBg = Color(0xFF1A1A1A), // 页面底：中性深灰，不再是接近纯黑
            panelTrack = Color(0xFF232323), // 凹槽轨道：比页面提一档
            cardBg = Color(0xFF252525), // 卡片：比页面亮一档
            surfaceRaised = Color(0xFF313131), // 卡片上的浮起控件（夜间要比卡片再亮一档才看得见）
            accentBlue = Color(0xFF7DD3FC), // 月光天蓝：夜底约 11:1（唯一的「彩色」强调）
            accentFillStrong = Color(0xFF38BDF8), // 非文字填充档，夜底 3:1 以上
            borderBlue = Color(0xFF383838), // 中性描边（仅保留给必须勾边的功能性场景）
            sheetHandle = Color(0xFF6B6B6B),
            textWhite = Color(0xFFEDEDED), // 霜白主文字
            textGray = Color(0xFFA6A6A6), // 月灰：卡片底约 7.2:1
            mintInk = Color(0xFF134E4A),
            // 夜间蓝比日间压暗 20%：日间 #42B8EC 是亮蓝天，夜里同一块蓝仍是全屏最扎眼的面，
            // 白字对比度也只有 2.26:1；压到 80% 后白字约 3.5:1，更沉也更可读。
            selectBlue = Color(0xFF3593BD),
            textTertiary = Color(0xFF8C8C8C), // 卡片底约 5.1:1
            surfaceSubtle = Color(0xFF303030), // 卡片上的控件面（比卡片亮）
            surfaceMuted = Color(0xFF202020), // 下沉面（比卡片暗）
            fieldBg = Color(0xFF202020),
            fieldBgFocused = Color(0xFF2F2F2F),
            dangerRed = Color(0xFFF87171),
            disabledGray = Color(0xFF4D4D4D),
            warningInk = Color(0xFFFCD34D), // 夜间警示字提亮为琥珀亮档：警示底约 11:1
            warningBg = Color(0xFF2B2312),
            warningBorder = Color(0xFF5C4A12),
            premiumGold = Color(0xFFF59E0B),
            premiumGoldDeep = Color(0xFFFBBF24), // 夜底金色文字提亮：卡片底约 8:1
            premiumGoldInk = Color(0xFF431407),
            premiumGoldEnd = Color(0xFFD97706),
            successGreen = Color(0xFF34D399),
            brandInk = Color(0xFF064E3B),
            brandInkSoft = Color(0xFF065F46),
            trackGray = Color(0xFF333333),
            chipTextInk = Color(0xFFC9C9C9),
            // 夜间极光：中性深灰微光，不带蓝紫（用户明确不要紫色背景）
            auroraMint = Color(0xFF1E1E1E),
            auroraSky = Color(0xFF222222),
            auroraMist = Color(0xFF1A1A1A),
            overlayCard = Color(0xFF252525),
            drawerGlassBg = Color(0xFF1A1A1A),
            drawerGlassTint = Color(0x8C1A1A1A), // 深底 55%：与日间同等的透出度
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

internal val surfaceRaised: Color @Composable get() = LocalAppPalette.current.surfaceRaised

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

internal val fieldBg: Color @Composable get() = LocalAppPalette.current.fieldBg

internal val fieldBgFocused: Color @Composable get() = LocalAppPalette.current.fieldBgFocused

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
