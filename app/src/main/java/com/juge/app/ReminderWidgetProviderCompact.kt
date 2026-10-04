package com.juge.app

/**
 * 紧凑入口（默认落位 4×2）。
 *
 * 与主入口 ReminderWidgetProvider 复用同一套渲染逻辑，仅通过 manifest 里的
 * appwidget-provider 元数据区分「默认落位尺寸」：主入口 4×4，本入口 4×2。
 * 两者都声明 resizeMode=horizontal|vertical，用户可以自由拖拽成 2~4 行。
 */
class ReminderWidgetProviderCompact : ReminderWidgetProvider()
