package com.juge.app.data

/**
 * 独立的桌面微件配置实体
 */
data class WidgetConfig(
    val id: Long = -1L,
    val name: String,         // 配置卡片的名称
    val content: String,      // 微件上展示的文字内容
    val sizeType: String = "4x2", // 尺寸类型: "4x2", "4x3", "4x4"；默认 4x2 与两个组件入口的默认落位一致
    val styleJson: String,    // 序列化的 WidgetStyle JSON 样式
    /**
     * 是否由 Provider 自动创建（「把金句推到桌面」等后台流程建的行）。
     * 决定无绑定时能不能被 [WidgetConfigBinding.orphanIds] 回收——
     * 用户创建的行绝不能被自动删掉，因此不靠名称推断，落库时显式记录
     * （历史上只看名称前缀，用户自建「微件 …」开头的卡片会被误回收）。
     */
    val autoCreated: Boolean = false,
)
