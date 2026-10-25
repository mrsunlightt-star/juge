package com.juge.app

import com.juge.app.data.WidgetShape
import com.juge.app.data.WidgetStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 已下架预设的读档回退回归测试（WidgetStyle.REMOVED_PRESET_FALLBACKS）。
 *
 * 2026-10-24 下架了信纸 / 天空蓝 / 可爱猫咪 / 萌宠猫咪趴四款风格：已保存、已落桌面的
 * 旧样式副本还拿着旧 presetId，引用的素材文件也已随下架删除——若不做迁移，
 * 萌宠猫咪趴的存档（形状枚举已删、底色透明）会被 safeEnum 兜成 RECTANGLE，
 * 渲染成「隐形卡片 + 悬浮文字」。读档时必须整份换成替代预设，
 * 只保留用户可调的圆角与背景不透明度。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemovedPresetFallbackTest {

    /** 模拟一份下架前的萌宠猫咪趴存档：形状枚举与素材名都已不存在 */
    private val legacyCatNapJson = """
        {
          "presetId": "p_pet_cat_nap",
          "shape": "PET_CAT_NAP",
          "cornerRadiusDp": 16.0,
          "backgroundOpacity": 0.7,
          "backgroundColor": 0,
          "presetImageResName": "cute_cat_lying"
        }
    """.trimIndent()

    @Test
    fun `下架的萌宠猫咪趴读档后换成毛绒猫咪`() {
        val migrated = WidgetStyle.fromJsonString(legacyCatNapJson)
        assertEquals("p_fluffy_cat", migrated.presetId)
        assertEquals(WidgetShape.SPLIT_CARD_HORIZONTAL, migrated.shape)
        // 替代预设的素材整份生效，不再引用已删除的 cute_cat_lying
        assertEquals("fluffy_cat", migrated.presetImageResName)
        // 用户可调项保留
        assertEquals(16f, migrated.cornerRadiusDp)
        assertEquals(0.7f, migrated.backgroundOpacity)
    }

    @Test
    fun `下架的可爱猫咪读档后换成毛绒猫咪`() {
        val json = """
            {"presetId":"p_cute_cat","shape":"SPLIT_CARD_HORIZONTAL","presetImageResName":"cute_cat"}
        """.trimIndent()
        val migrated = WidgetStyle.fromJsonString(json)
        assertEquals("p_fluffy_cat", migrated.presetId)
        assertEquals("fluffy_cat", migrated.presetImageResName)
    }

    @Test
    fun `下架的天空蓝读档后换成蓝色画报`() {
        val json = """{"presetId":"p_sky_blue","shape":"RECTANGLE"}"""
        val migrated = WidgetStyle.fromJsonString(json)
        assertEquals("p_postcard_note", migrated.presetId)
    }

    @Test
    fun `下架的信纸读档后换成纯色圆角`() {
        val json = """{"presetId":"p_ruled_paper","shape":"RECTANGLE","backgroundOpacity":0.8}"""
        val migrated = WidgetStyle.fromJsonString(json)
        assertEquals("p_pure_round", migrated.presetId)
        assertEquals(0.8f, migrated.backgroundOpacity)
    }

    @Test
    fun `在售预设与自定义样式不受回退影响`() {
        // 在售预设原样读回，presetId 不变
        val live = WidgetStyle.fromJsonString(WidgetStyle.DEFAULT.toJsonString())
        assertEquals("p_pure_round", live.presetId)
        // 自定义样式（无 presetId）跳过迁移，字段保持解析值
        val custom = WidgetStyle.fromJsonString("""{"shape":"RECTANGLE","backgroundColor":-1}""")
        assertNull("自定义样式不应被强行赋予预设 id", custom.presetId)
    }
}
