package com.juge.app.ui.main

import android.content.Context
import com.juge.app.ReminderWidgetProvider
import com.juge.app.data.WidgetConfig
import com.juge.app.data.WidgetStyle

/**
 * 预览 Pager 某一页所需的全部输入。
 *
 * 之所以要单独做一份快照：这些值以前是在组合函数体里现算的——
 * `getBoundConfigId` 里要 `synchronized(bindingLock)` 再读 SharedPreferences，
 * `getWidgetStyle` 再读一次 prefs 并 `WidgetStyle.fromJsonString`（解析 29 个字段的 JSON），
 * 而这段计算发生在 `HorizontalPager` 的每一页 content lambda 里。
 * 结果是每重组一次、每滑一页，就要在主线程上做 N 次锁 + N 次 prefs 读 + N 次 JSON 解析；
 * 用户拖动调色板时每帧都在付这笔钱（掉帧的直接来源之一）。
 *
 * 现在改成：在 IO 线程一次性算出整份列表（[buildWidgetPages]），组合阶段只做下标查表。
 * 与「预览高度」相关的那部分计算因此退化成纯查表 + 纯算术，可以放心地被 Compose 反复调用。
 */
data class WidgetPage(
    val widgetId: Int,
    val configId: Long,
    val content: String,
    val style: WidgetStyle,
)

/** 没有绑定配置时的兜底正文，与历史行为保持一致 */
const val DEFAULT_PAGE_CONTENT = "静静地，坐一会。"

/**
 * 一次性把所有页的「绑定 + 样式 + 正文」读出来。
 *
 * 必须在后台线程调用：两个 `ReminderWidgetProvider` 方法都会拿 `bindingLock` 并读 prefs，
 * 而 `WidgetStyle.fromJsonString` 会解析一份几十个字段的 JSON。
 * 锁本身是进程内轻量的，但读 prefs 会走 SharedPreferences 的加载与锁竞争，
 * 叠在组合线程上就是可观测的卡顿，所以这里统一挪出去。
 */
fun buildWidgetPages(
    context: Context,
    appWidgetIds: IntArray,
    widgetConfigs: List<WidgetConfig>,
): List<WidgetPage> {
    val fallbackConfig = widgetConfigs.firstOrNull()
    return appWidgetIds.map { widgetId ->
        val configId = ReminderWidgetProvider.getBoundConfigId(context, widgetId)
        val config = if (configId != -1L) {
            widgetConfigs.find { it.id == configId } ?: fallbackConfig
        } else {
            fallbackConfig
        }
        val style = if (widgetId != -1) {
            ReminderWidgetProvider.getWidgetStyle(context, widgetId, config?.styleJson)
        } else {
            WidgetStyle.fromJsonString(config?.styleJson)
        }
        WidgetPage(
            widgetId = widgetId,
            configId = configId,
            content = config?.content ?: DEFAULT_PAGE_CONTENT,
            style = style,
        )
    }
}
