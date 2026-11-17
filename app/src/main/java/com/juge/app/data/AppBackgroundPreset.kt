package com.juge.app.data

import com.juge.app.R

/**
 * 主界面预览区可用背景。
 *
 * 只决定顶部 Hero 区域的图片，不进入 [WidgetStyle]，因此不会影响组件内的文字、
 * 颜色或桌面小组件配置。key 会持久化到 user_prefs，改名时必须保留旧 key 的兼容映射。
 */
enum class AppBackgroundPreset(
    val key: String,
    val displayName: String,
    val imageRes: Int,
) {
    SUNNY_BEACH(
        key = "sunny_beach",
        displayName = "阳光海滩",
        imageRes = R.drawable.sunny_beach,
    ),
    SEA(
        key = "sea",
        displayName = "大海",
        imageRes = R.drawable.sea,
    ),
    ;

    companion object {
        val DEFAULT = SUNNY_BEACH

        fun fromKey(key: String?): AppBackgroundPreset =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
