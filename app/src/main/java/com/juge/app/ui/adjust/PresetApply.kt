package com.juge.app.ui.adjust

import com.juge.app.data.WidgetStyle

/**
 * 点风格预设缩略图的统一语义：先落到 App 内预览，再决定要不要真正套用。
 *
 * 会员专属风格在未激活时**只到预览这一步**——不落库，也不当场弹激活框：
 * 点缩略图只是"看看效果"，接着拖滑块、改颜色、改文字也只预览，同样不弹框；
 * 只有用户点「保存并应用到桌面小组件」时才由 `onApplyToDesktop` 拦截。
 * 经典 / 萌宠 / 明信片（代码绘制、场景卡、插图素材）五行预设共用这一条规则；
 * 新增风格行时也走这里，不要各自写一份 `if (locked)`，否则又会出现"某一行点了就弹框"。
 */
internal fun previewOrApplyPreset(
    style: WidgetStyle,
    isActivated: Boolean,
    onPreview: (WidgetStyle) -> Unit,
    onApply: (WidgetStyle) -> Unit
) {
    onPreview(style)
    if (isActivated || !WidgetStyle.isProPreset(style)) {
        onApply(style)
    }
}
