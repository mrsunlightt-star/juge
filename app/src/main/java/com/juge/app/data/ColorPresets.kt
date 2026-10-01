package com.juge.app.data

import android.content.Context

/** 内置颜色预设：字体色与背景色各一套，两个面板共用同一份定义。 */
object ColorPresets {
    val FONT_COLORS = listOf(
        "#434446",
        "#1393cf",
        "#23c3c0",
        "#f6c250",
        "#56309f",
        "#aa6790"
    )

    /** 背景色只保留 6 个：白 / 米黄 / 浅粉 / 浅蓝 / 浅绿 / 深墨，色相与明度都拉开差距 */
    val BACKGROUND_COLORS = listOf(
        "#FFFFFF",
        "#F7E6C4",
        "#FBD3D8",
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
        add(context, KEY_FONT, color)
        return fontColors(context)
    }

    fun addBackgroundColor(context: Context, color: Int): List<Int> {
        add(context, KEY_BACKGROUND, color)
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
        return visibleBuiltIns + load(context, userKey)
    }

    private fun delete(context: Context, userKey: String, hiddenKey: String, builtIns: List<String>, color: Int) {
        if (builtIns.any { android.graphics.Color.parseColor(it) == color }) {
            add(context, hiddenKey, color)
        } else {
            remove(context, userKey, color)
        }
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

    private fun remove(context: Context, key: String, color: Int) {
        val updated = load(context, key).filterNot { it == color }
        prefs(context).edit().putString(key, updated.joinToString(",")).apply()
    }

    private fun prefs(context: Context): android.content.SharedPreferences {
        AppPrefs.migrateLegacyIfNeeded(context)
        return AppPrefs.user(context)
    }
}