package com.juge.app

import com.juge.app.data.WidgetStyle

/**
 * 渲染测试用例的唯一定义处。
 *
 * `WidgetPreviewRenderTest`（人工看效果）与 `WidgetRenderBaselineTest`（回归比对）
 * 共用同一份枚举，避免覆盖范围随时间漂移——只往一边加用例最危险：
 * 基线里没有的风格，拆坏了也不会被测出来。
 */
internal object RenderCases {

    /** 默认文本：与用户在编辑页看到的长度接近 */
    const val SAMPLE_TEXT = "生活不止眼前的苟且，还有诗和远方的田野。"

    /** 超长文本：走省略号 / 自动缩放分支，与默认文本覆盖的排版路径不同 */
    const val LONG_TEXT =
        "永和九年，岁在癸丑，暮春之初，会于会稽山阴之兰亭，修禊事也。" +
            "群贤毕至，少长咸集。此地有崇山峻岭，茂林修竹，又有清流激湍，映带左右。"

    /** 桌面组件规格；与真机 4x2 / 4x4 的宽高比一致 */
    val SIZES: List<Pair<String, Pair<Int, Int>>> = listOf(
        "4x2" to (250 to 110),
        "4x4" to (250 to 250),
    )

    /** 全部风格：预设 + 插图。name 同时用作基线 key 与预览文件名 */
    fun allStyles(): List<Pair<String, WidgetStyle>> = buildList {
        WidgetStyle.PRESETS.forEachIndexed { index, style ->
            add((style.presetId ?: "preset_$index") to style)
        }
        WidgetStyle.ILLUSTRATION_PRESETS.forEach { (resName, displayName) ->
            add("插图_$displayName" to illustrationStyle(resName))
        }
    }

    // 插图类风格与首页一致：直接走主界面点击插图预设时用的同一份换算，
    // 形状/配色随预设（明信片预设的文字区底色就是这里的白色），背景图按资源名加载
    fun illustrationStyle(resName: String): WidgetStyle =
        WidgetStyle.illustrationStyle(resName, WidgetStyle())

    fun sanitize(name: String): String = name.replace(Regex("[^\\p{L}\\p{N}_-]"), "_")
}
