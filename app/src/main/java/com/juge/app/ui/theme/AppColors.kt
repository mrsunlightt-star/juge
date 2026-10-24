package com.juge.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 全局主题色统一收在这里，避免各界面各抄一份；改色只需改这一处。
 * 色值取自主界面薄荷青绿与网站同款配色。除用户指定保留的 [selectBlue] 外，
 * 承载文字/图标的前景色按 WCAG AA 校验（正文 4.5:1、非文字元素 3:1）。
 * [selectBlue] 保留为用户喜欢的明亮浅蓝底白字搭配；这是有意保留的品牌例外，白字对比度低于常规 AA 标准。
 */
internal val darkBg = Color(0xFFFAFBFD) // 页面/抽屉面板底色：几乎全白、只留一丝冷调，靠卡片软阴影分层（用户确认的明度）。

internal val panelTrack = Color(0xFFEEF2F7) // 面板上的凹槽底（分段控制器轨道）：比面板深一档才有"嵌进去"的层次

internal val cardBg = Color(0xFFFFFFFF) // 纯净白卡片
internal val accentBlue = Color(0xFF0369A1) // 强调色（全局唯一 accent）：晴空蓝 deep 档，白底 5.9:1、面板底约 5.4:1。
// 用于区块标题/链接/图标/选中描边等文字与描边角色；填充角色用 selectBlue / accentFillStrong。
// 历史上这里是青绿 #0F766E，与 selectBlue、accentLightBlue 三套蓝并存——已统一到 selectBlue 同一色相
internal val accentFillStrong = Color(0xFF0284C6) // 强调色填充档（滑条轨道等需要 3:1 对比的非文字填充），白底 4.1:1
internal val borderBlue = Color(0xFFE2E8F0) // 网站同款精细边框与网格线
internal val sheetHandle = Color(0xFF94A3B8) // 抽屉拖拽把手：面板底上 2.3:1，够亮也够安静
internal val textWhite = Color(0xFF0F172A) // 现代极简墨色 (Slate Ink)
internal val textGray = Color(0xFF52627A) // 板岩灰：副标题/描述文字，白底 6.1:1、面板底约 5.5:1
internal val mintInk = Color(0xFF134E4A) // 明亮薄荷底上的深色文字/图标，保证对比度
internal val selectBlue = Color(0xFF42B8EC) // 用户指定的明亮浅蓝选中填充；与白色文字搭配

// —— 语义色：界面只从这里取色，不再各写一份 0xFF 字面量 ——

internal val textPrimary = textWhite
internal val textSecondary = textGray // 白底 6.1:1、面板底约 5.5:1：与主文字（15:1）拉开一整档，对齐 iOS 的层级感
internal val textTertiary = Color(0xFF64748B) // 白底 4.8:1：12sp 说明与图标的最小可用灰
internal val surfaceSubtle = Color(0xFFF1F5F9)
internal val surfaceMuted = Color(0xFFF8FAFC)
internal val dividerColor = borderBlue
internal val dangerRed = Color(0xFFDC2626)
internal val disabledGray = Color(0xFFCBD5E1)
internal val warningInk = Color(0xFF78350F)
internal val warningBg = Color(0xFFFFFBEB)
internal val warningBorder = Color(0xFFFDE68A)
internal val premiumGold = Color(0xFFF59E0B) // 明亮金：只作填充/渐变，不承载白字
internal val premiumGoldDeep = Color(0xFFB45309) // 白底上的金色图标/文字/描边：5.0:1
internal val premiumGoldInk = Color(0xFF431407) // 金色底上的深棕文字：7.3:1
internal val premiumGoldEnd = Color(0xFFD97706) // 金色渐变终点：深棕字 4.9:1
internal val successGreen = Color(0xFF047857) // 白底 5.5:1
internal val brandInk = Color(0xFF064E3B)
internal val brandInkSoft = Color(0xFF065F46)
internal val trackGray = Color(0xFFE6E6E6) // 滑条未激活轨道：中性灰，不带蓝调
internal val chipTextInk = Color(0xFF334155) // 未选中胶囊文字
internal val gridLine = accentBlue.copy(alpha = 0.12f)
internal val auroraMint = Color(0xFF5EEAD4) // 主界面极光渐变起点：薄荷
internal val auroraSky = Color(0xFF38BDF8) // 主界面极光渐变中段：晴空蓝
internal val auroraMist = Color(0xFFBAE6FD) // 主界面极光渐变末段：晨露浅青
internal val overlayCard = Color(0xFF1E2436) // 深色遮罩上的浮层卡片（裁剪/加载）
