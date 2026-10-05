package com.juge.app.data

/**
 * 组件与微件配置的绑定规则。
 *
 * 抽成纯函数而不是留在 [com.juge.app.ReminderWidgetProvider] 里，原因有两个：
 *  1. 这些规则决定「配置行会不会无限增长」「新组件会不会捡到旧内容」，
 *     属于需要回归测试的领域规则，放在 Provider 里就只能靠真机验证；
 *  2. 规则本身不依赖 Android，可以直接用 JVM 单测覆盖。
 *
 * 「自动创建」与「用户创建」的区分只靠名称前缀：Provider 自动建的行统一以
 * [AUTO_CONFIG_NAME_PREFIX] 开头，用户在 App 内建的、以及旧数据迁移导入的行都不带这个前缀。
 * 回收时只碰自动创建的行，避免误删用户内容。
 */
object WidgetConfigBinding {

    /** 自动创建的配置行名称前缀（例如「微件 42」） */
    const val AUTO_CONFIG_NAME_PREFIX = "微件 "

    /** 是否为本 Provider 自动创建（而非用户手动创建 / 迁移导入）的配置行 */
    fun isAutoCreated(config: WidgetConfig): Boolean =
        config.name.startsWith(AUTO_CONFIG_NAME_PREFIX)

    /**
     * 找出可以回收的孤儿配置 id：自动创建、且当前没有任何组件绑定。
     *
     * @param configs 全部配置行
     * @param boundConfigIds 当前仍有组件在用的配置 id 集合
     */
    fun orphanIds(configs: List<WidgetConfig>, boundConfigIds: Set<Long>): List<Long> =
        configs.filter { isAutoCreated(it) && it.id !in boundConfigIds }.map { it.id }

    /**
     * 为「把金句推到桌面」找一个可复用的空配置：同样是自动创建、且当前无人绑定的行。
     *
     * 找不到时返回 null，调用方再新建——这样反复推送不会每次新增一行。
     */
    fun findReusableAutoConfig(configs: List<WidgetConfig>, boundConfigIds: Set<Long>): WidgetConfig? =
        configs.firstOrNull { isAutoCreated(it) && it.id !in boundConfigIds }
}
