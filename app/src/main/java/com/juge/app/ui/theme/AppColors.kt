package com.juge.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 全局主题色：主界面与快捷面板此前各抄了一份（值完全相同），改一处必须记得改两处。
 * 色值取自主界面晴空蓝背景与网站同款配色，改动前请同时确认两条界面的对比度。
 */
internal val darkBg = Color(0xFFEEF2F7) // 页面底色（冷调浅灰蓝）：比纯白卡片深一档，让白卡片能"浮"起来，同时保持清爽
internal val cardBg = Color(0xFFFFFFFF) // 纯净白卡片
internal val accentBlue = Color(0xFF0F766E) // 网站薄荷青翠主色 (Fresh Mint Teal)
internal val accentLightBlue = Color(0xFF0284C7) // 晴空天蓝 (Sky Cyan)
internal val borderBlue = Color(0xFFE2E8F0) // 网站同款精细边框与网格线
internal val textWhite = Color(0xFF0F172A) // 现代极简墨色 (Slate Ink)
internal val textGray = Color(0xFF64748B) // 板岩轻灰，副标题/描述文字 (Slate Muted)
internal val mintInk = Color(0xFF134E4A) // 明亮薄荷底上的深色文字/图标，保证对比度
internal val selectBlue = Color(0xFF42B8EC) // 按钮/页签选中态填充·取自主界面晴空蓝背景（配白字）
