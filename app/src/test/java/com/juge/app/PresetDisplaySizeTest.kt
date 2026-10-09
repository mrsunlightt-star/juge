package com.juge.app

import com.juge.app.data.WidgetDisplaySize
import com.juge.app.data.WidgetStyle
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 「最佳显示尺寸」的回归测试（产品规则，2026-10-08）。
 *
 * 规则：App 内预览按每款风格**自己的**最佳显示尺寸出图——4×4 款始终方形、4×2 款始终长条，
 * 不跟桌面组件的入口/实时尺寸走（尺寸声明在 `WidgetStyle.bestDisplaySize` 上，每条预设一份；
 * 依据是 docs/component-style-guide.md §0.1「一款风格，一个尺寸，一份素材」）。
 *
 * 这里只钉**有外部依据**的取值（文档写明尺寸的 / 方版素材已交付的 / 产品点名的），
 * 其余按素材画布推断的取值不重复一遍——改表时先看这里的失败信息，再改数据。
 */
class PresetDisplaySizeTest {

    private fun sizeOf(presetId: String): WidgetDisplaySize =
        WidgetStyle.PRESETS.firstOrNull { it.presetId == presetId }
            ?.bestDisplaySize
            ?: error("预设 $presetId 不存在")

    @Test
    fun `文档写明尺寸的风格按文档定`() {
        val expected = linkedMapOf(
            // 尺寸专属（style-guide §0.1 的「尺寸专属」表）
            "p_cute_four_kids" to WidgetDisplaySize.WIDE_4X2,
            "p_youth_sculpture" to WidgetDisplaySize.SQUARE_4X4,
            // 4×2 原生 / 按 4×2 设计
            "p_texture_landscape" to WidgetDisplaySize.WIDE_4X2,
            "p_ruled_paper" to WidgetDisplaySize.WIDE_4X2,
            // 产品点名：蜡笔彩虹框的最佳显示尺寸是 4:2（4×2 原生，方版素材未交付）
            "p_crayon_frame" to WidgetDisplaySize.WIDE_4X2,
            // 明信片行按 4×4 设计：天气盒子 + 画框卡片族
            "p_weather_box" to WidgetDisplaySize.SQUARE_4X4,
            "p_winter_palace" to WidgetDisplaySize.SQUARE_4X4,
            "p_deep_sea" to WidgetDisplaySize.SQUARE_4X4,
            "p_summer_sea" to WidgetDisplaySize.SQUARE_4X4,
            "p_summer_lotus" to WidgetDisplaySize.SQUARE_4X4,
            // 方版素材已交付：4×4 上不再把 1.75 的横版纵向拉 76%
            "p_plush_forest" to WidgetDisplaySize.SQUARE_4X4,
            "p_panda_bamboo" to WidgetDisplaySize.SQUARE_4X4,
            // 产品点名：萌宠乐园的最佳显示尺寸是 4:4
            "p_pet_park" to WidgetDisplaySize.SQUARE_4X4,
            // 产品点名：小霸王游戏机改为 4:4（方形下模型更大、屏幕放得下两行正文）
            "p_subor_console" to WidgetDisplaySize.SQUARE_4X4,
        )
        for ((presetId, size) in expected) {
            assertEquals("$presetId 的最佳显示尺寸与既定依据不符", size, sizeOf(presetId))
        }
    }

    @Test
    fun `带方版素材的风格一律按 4×4 预览`() {
        // 毛绒森林 / 竹林熊猫 / 萌宠乐园（青年雕塑的方版直接写在 presetImageResName 里）
        val withSquareAsset = WidgetStyle.PRESETS.filter { it.presetImageResNameSquare != null }
        assertEquals("带方版素材的风格数变了，检查尺寸与素材是否配对", 3, withSquareAsset.size)
        withSquareAsset.forEach {
            assertEquals(
                "${it.presetId} 已交付方版素材，最佳显示尺寸应为 4×4",
                WidgetDisplaySize.SQUARE_4X4,
                it.bestDisplaySize,
            )
        }
    }

    @Test
    fun `尺寸不落 JSON：读档时按 presetId 取当前定义`() {
        val petPark = WidgetStyle.PRESETS.first { it.presetId == "p_pet_park" }
        assertFalse(
            "bestDisplaySize 是派生属性，不应写进 JSON",
            petPark.toJsonString().contains("bestDisplaySize"),
        )

        // 老存档（JSON 里没有这个字段）读出来也要是 4×4，老组件不必重新套用预设
        val legacyJson = JSONObject(petPark.toJsonString()).toString()
        assertEquals(WidgetDisplaySize.SQUARE_4X4, WidgetStyle.fromJsonString(legacyJson).bestDisplaySize)
    }

    @Test
    fun `用户微调风格不影响最佳显示尺寸`() {
        val petPark = WidgetStyle.PRESETS.first { it.presetId == "p_pet_park" }
        val tweaked = petPark.copy(fontSizeSp = 30f, cornerRadiusDp = 4f, fontBold = true)
        assertEquals(WidgetDisplaySize.SQUARE_4X4, tweaked.bestDisplaySize)
        assertEquals(
            "微调并往返 JSON 后仍应为 4×4",
            WidgetDisplaySize.SQUARE_4X4,
            WidgetStyle.fromJsonString(tweaked.toJsonString()).bestDisplaySize,
        )
    }

    @Test
    fun `没有 presetId 的样式按 4×2 兜底`() {
        assertEquals(WidgetDisplaySize.WIDE_4X2, WidgetStyle().bestDisplaySize)
        val legacy = WidgetStyle.fromJsonString("""{"shape":"RECTANGLE","fontSizeSp":30}""")
        assertEquals(WidgetDisplaySize.WIDE_4X2, legacy.bestDisplaySize)
    }

    @Test
    fun `尺寸枚举与组件入口声明一致`() {
        // 预览位图按这两个尺寸渲染，必须与 widget_info*.xml 的 minWidth/minHeight 对得上
        assertEquals(250 to 110, WidgetDisplaySize.WIDE_4X2.widthDp to WidgetDisplaySize.WIDE_4X2.heightDp)
        assertEquals(250 to 250, WidgetDisplaySize.SQUARE_4X4.widthDp to WidgetDisplaySize.SQUARE_4X4.heightDp)
    }
}
