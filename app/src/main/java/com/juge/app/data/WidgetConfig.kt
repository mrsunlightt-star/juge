package com.juge.app.data

/**
 * 独立的桌面微件配置实体
 */
data class WidgetConfig(
    val id: Long = -1L,
    val name: String,         // 配置卡片的名称
    val content: String,      // 微件上展示的文字内容
    val sizeType: String = "4x2", // 尺寸类型: "2x2", "4x2"
    val styleJson: String     // 序列化的 WidgetStyle JSON 样式
)
