package com.juge.app.data

/**
 * 独立的桌面微件配置实体
 */
data class WidgetConfig(
    val id: Long = -1L,
    val name: String,         // 配置卡片的名称
    val content: String,      // 微件上展示的文字内容
    val sizeType: String = "4x3", // 尺寸类型: "4x2", "4x3", "4x4"（默认 4x3，与 widget_info.xml 的默认落位尺寸一致）
    val styleJson: String     // 序列化的 WidgetStyle JSON 样式
)
