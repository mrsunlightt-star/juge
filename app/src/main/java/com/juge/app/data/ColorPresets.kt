package com.juge.app.data

import android.content.Context

/** 内置颜色预设：字体色与背景色各一套，两个面板共用同一份定义。 */
object ColorPresets {
    val FONT_COLORS = listOf(
        "#434446",
        "#1393cf",
        "#23c3c0",
        "#f6c250",
        "#56309f"
    )

    /** 背景色保留 5 个：白 / 米黄 / 浅蓝 / 浅绿 / 深墨，色相与明度都拉开差距 */
    val BACKGROUND_COLORS = listOf(
        "#FFFFFF",
        "#F7E6C4",
        "#CFE4FA",
        "#D8EFD9",
        "#1F2436"
    )
}

/**
 * 颜色预设的本地存储：面板展示的是「内置色（去掉已删除的）+ 用户自添加色」。
 * 两类颜色都允许长按删除：内置色删除后记入隐藏列表，用户自添加色直接从列表移除。
 */
object UserColorPresets {
    private const val KEY_FONT = "user_font_color_presets"
    private const val KEY_BACKGROUND = "user_background_color_presets"
    private const val KEY_FONT_HIDDEN = "hidden_font_color_presets"
    private const val KEY_BACKGROUND_HIDDEN = "hidden_background_color_presets"

    fun fontColors(context: Context): List<Int> =
        merge(context, KEY_FONT, KEY_FONT_HIDDEN, ColorPresets.FONT_COLORS)

    fun backgroundColors(context: Context): List<Int> =
        merge(context, KEY_BACKGROUND, KEY_BACKGROUND_HIDDEN, ColorPresets.BACKGROUND_COLORS)

    fun addFontColor(context: Context, color: Int): List<Int> {
        addUserColor(context, KEY_FONT, ColorPresets.FONT_COLORS, color)
        return fontColors(context)
    }

    fun addBackgroundColor(context: Context, color: Int): List<Int> {
        addUserColor(context, KEY_BACKGROUND, ColorPresets.BACKGROUND_COLORS, color)
        return backgroundColors(context)
    }

    fun deleteFontColor(context: Context, color: Int): List<Int> {
        delete(context, KEY_FONT, KEY_FONT_HIDDEN, ColorPresets.FONT_COLORS, color)
        return fontColors(context)
    }

    fun deleteBackgroundColor(context: Context, color: Int): List<Int> {
        delete(context, KEY_BACKGROUND, KEY_BACKGROUND_HIDDEN, ColorPresets.BACKGROUND_COLORS, color)
        return backgroundColors(context)
    }

    private fun merge(context: Context, userKey: String, hiddenKey: String, builtIns: List<String>): List<Int> {
        val hidden = load(context, hiddenKey)
        val visibleBuiltIns = builtIns
            .map { android.graphics.Color.parseColor(it) }
            .filterNot { hidden.contains(it) }
        // 用户列表再加一层去重：用户自添加色若与某个内置色相同（吸色很容易吸出同值），
        // 历史/手改数据都可能出现重复项，展示成两个一模一样的色块
        val userColors = load(context, userKey).filterNot { it in visibleBuiltIns }
        return visibleBuiltIns + userColors
    }

    private fun delete(context: Context, userKey: String, hiddenKey: String, builtIns: List<String>, color: Int) {
        if (builtIns.any { android.graphics.Color.parseColor(it) == color }) {
            add(context, hiddenKey, color)
        }
        // 不管是不是内置色，用户列表里同值的那份一起清掉：
        // 否则用户自添加的重复色会永久残留（且因命中内置色分支而永远无法删除）
        remove(context, userKey, color)
    }

    private fun load(context: Context, key: String): List<Int> =
        prefs(context).getString(key, null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            .orEmpty()

    private fun add(context: Context, key: String, color: Int) {
        val current = load(context, key)
        if (current.contains(color)) return
        prefs(context).edit().putString(key, (current + color).joinToString(",")).apply()
    }

    /** 用户自添加新色：与任一内置色相同时直接忽略，避免面板出现重复色块 */
    private fun addUserColor(context: Context, key: String, builtIns: List<String>, color: Int) {
        val builtInValues = builtIns.map { android.graphics.Color.parseColor(it) }
        if (color in builtInValues) return
        add(context, key, color)
    }

    private fun remove(context: Context, key: String, color: Int) {
        val updated = load(context, key).filterNot { it == color }
        prefs(context).edit().putString(key, updated.joinToString(",")).apply()
    }

    private fun prefs(context: Context): android.content.SharedPreferences {
        AppPrefs.migrateLegacyIfNeeded(context)
        return AppPrefs.user(context)
    }
}