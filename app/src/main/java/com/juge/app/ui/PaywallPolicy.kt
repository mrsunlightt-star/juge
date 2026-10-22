package com.juge.app.ui

import com.juge.app.data.WidgetStyle

/**
 * 付费拦截的唯一判据：**有桌面目标 + 未激活 + 当前样式属于付费功能**。
 *
 * 编辑/预览期也用这个判据判断"是否只更新 App 内预览"；真正弹付费框只发生在
 * 「保存并应用到桌面小组件」那一步（见 MainAppScreen 的 onApplyToDesktop）。
 *
 * 付费功能只有两个：会员专属风格、自定义背景图。字体/字号/颜色/圆角/不透明度
 * 等细节调整全部免费，不参与判定。
 */
internal fun requiresProForDesktop(
    style: WidgetStyle,
    isActivated: Boolean,
    widgetId: Int,
): Boolean =
    !isActivated && widgetId != -1 &&
        (WidgetStyle.isProPreset(style) || !style.backgroundImagePath.isNullOrEmpty())
