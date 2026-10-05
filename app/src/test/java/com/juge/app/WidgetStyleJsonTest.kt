package com.juge.app

import com.juge.app.data.TextShadow
import com.juge.app.data.WidgetFont
import com.juge.app.data.WidgetShape
import com.juge.app.data.WidgetStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WidgetStyle JSON 协议与免费/付费预设判定的本地单元测试。
 * 纯 JVM 运行，不依赖 Android 环境（org.json 由 Android Gradle Plugin 的单元测试运行时提供）。
 */
class WidgetStyleJsonTest {

    @Test
    fun `default style round-trips through json`() {
        val json = WidgetStyle().toJsonString()
        val parsed = WidgetStyle.fromJsonString(json)
        assertEquals(WidgetShape.RECTANGLE, parsed.shape)
        assertEquals(12f, parsed.cornerRadiusDp)
        assertEquals(19f, parsed.fontSizeSp)
        assertNull(parsed.backgroundImagePath)
        assertNull(parsed.gradientColors)
        assertFalse(parsed.shadow.enabled)
    }

    @Test
    fun `full style round-trips through json`() {
        val style = WidgetStyle(
            shape = WidgetShape.HANDBOOK_TAPE,
            cornerRadiusDp = 20f,
            backgroundColor = 0xFF112233.toInt(),
            backgroundOpacity = 0.6f,
            backgroundImagePath = "/data/user/0/app/files/bg_images/bg_1.png",
            bgImageScaleMode = com.juge.app.data.ImageScaleMode.TILE,
            font = WidgetFont.LXGW_WENKAI,
            fontSizeSp = 24f,
            fontBold = true,
            fontItalic = true,
            fontColor = 0xFFAABBCC.toInt(),
            textAlign = "LEFT",
            shadow = TextShadow(enabled = true, color = 0x88000000.toInt(), radius = 6f, dx = 2f, dy = 3f),
            gradientColors = listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
            gradientAngle = 135f,
            bgBlurRadius = 8f,
            bgScrimAlpha = 0.2f,
            showCardShadow = true,
            cardBorderWidthDp = 2.5f,
            cardBorderColor = 0xFF123456.toInt(),
            textureType = "PAPER",
            authorSignature = "—— 测试",
            presetImageResName = "bg_illustration_1",
            lineSpacingMultiplier = 1.5f,
            letterSpacing = 2f
        )
        val parsed = WidgetStyle.fromJsonString(style.toJsonString())
        assertEquals(style, parsed)
    }

    @Test
    fun `null and empty json fall back to defaults`() {
        assertEquals(WidgetStyle(), WidgetStyle.fromJsonString(null))
        assertEquals(WidgetStyle(), WidgetStyle.fromJsonString(""))
        assertEquals(WidgetStyle(), WidgetStyle.fromJsonString("not a json"))
    }

    @Test
    fun `unknown enum values degrade to fallback instead of failing`() {
        val json = """
            {"shape":"TRIANGLE","font":"UNKNOWN_FONT","bgImageScaleMode":"ZOOM"}
        """.trimIndent()
        val parsed = WidgetStyle.fromJsonString(json)
        assertEquals(WidgetShape.RECTANGLE, parsed.shape)
        assertEquals(WidgetFont.DEFAULT, parsed.font)
        assertEquals(com.juge.app.data.ImageScaleMode.CENTER_CROP, parsed.bgImageScaleMode)
        // 单个字段非法不影响其余默认值
        assertEquals(12f, parsed.cornerRadiusDp)
    }

    @Test
    fun `legacy font names map correctly`() {
        val json = """{"font":"SERIF"}"""
        assertEquals(WidgetFont.SOURCE_HAN_SERIF, WidgetStyle.fromJsonString(json).font)

        // 旧版本曾使用 SANS_SERIF，应映射到系统默认字体而不是抛异常
        val jsonSans = """{"font":"SANS_SERIF"}"""
        assertEquals(WidgetFont.DEFAULT, WidgetStyle.fromJsonString(jsonSans).font)
    }

    @Test
    fun `legacy partial json keeps default values for missing fields`() {
        val json = """{"shape":"TORN_PAPER","fontSizeSp":30}"""
        val parsed = WidgetStyle.fromJsonString(json)
        assertEquals(WidgetShape.TORN_PAPER, parsed.shape)
        assertEquals(30f, parsed.fontSizeSp)
        assertEquals(12f, parsed.cornerRadiusDp)
        assertFalse(parsed.fontBold)
    }

    @Test
    fun `free presets are not pro`() {
        // 当前规则：只有「纯色圆角」免费，其余风格一律会员专属
        val freePresets = WidgetStyle.PRESETS.filterNot { WidgetStyle.isProPreset(it) }
        assertEquals("当前规则下应仅有「纯色圆角」免费", 1, freePresets.size)
        assertEquals("p_pure_round", freePresets.first().presetId)
    }

    @Test
    fun `distinct pro presets are pro`() {
        // 选取形状独特的 PRO 预设，避免本地单元测试（mockable android.jar 下颜色全部为 0）
        // 导致多个预设关键字段碰撞、indexOfFirst 匹配到列表靠前的预设。
        for (index in listOf(1, 2, 3, 4, 5, 6)) {
            assertTrue("预设 $index 应为 PRO", WidgetStyle.isProPreset(WidgetStyle.PRESETS[index]))
        }
    }

    @Test
    fun `custom style tweaks on a pro preset stay pro`() {
        val proPreset = WidgetStyle.PRESETS[2] // 复古手账 (PRO)
        val tweaked = proPreset.copy(fontSizeSp = 30f)
        assertTrue(WidgetStyle.isProPreset(tweaked))
        // 付费判定改按 presetId 身份：任意关键字段微调后仍应识别为 PRO，杜绝改字段绕过会员校验
        val recolored = proPreset.copy(backgroundColor = 0xFF123456.toInt())
        assertTrue("预设身份随 copy 保留，改色后仍应识别为 PRO", WidgetStyle.isProPreset(recolored))
        val rebolded = proPreset.copy(fontBold = !proPreset.fontBold)
        assertTrue("改粗细后仍应识别为 PRO", WidgetStyle.isProPreset(rebolded))
    }

    @Test
    fun `legacy style json without preset id falls back to field matching`() {
        val proPreset = WidgetStyle.PRESETS[2] // 复古手账 (PRO)
        val legacyJson = org.json.JSONObject(proPreset.toJsonString()).apply { remove("presetId") }.toString()
        val legacyStyle = WidgetStyle.fromJsonString(legacyJson)
        assertNull(legacyStyle.presetId)
        assertTrue("旧数据无 presetId 时按字段匹配兜底识别 PRO", WidgetStyle.isProPreset(legacyStyle))
        // 旧数据微调关键字段后不再匹配预设，判定为非 PRO
        val tweakedLegacy = legacyStyle.copy(fontBold = !legacyStyle.fontBold)
        assertFalse(WidgetStyle.isProPreset(tweakedLegacy))
    }

    @Test
    fun `free preset identity survives tweaks`() {
        val freePreset = WidgetStyle.PRESETS[0] // 纯色圆角 (免费)
        val tweaked = freePreset.copy(fontSizeSp = 30f, cornerRadiusDp = 0f)
        assertFalse("免费预设微调后仍应为免费", WidgetStyle.isProPreset(tweaked))
    }

    @Test
    fun `every preset id survives json round trip`() {
        // presetId 是免费/付费判定的身份依据，落库再读出后必须原样保留，
        // 否则用户下次打开组件时风格会被误判为会员专属或被降级。
        WidgetStyle.PRESETS.forEachIndexed { index, preset ->
            val restored = WidgetStyle.fromJsonString(preset.toJsonString())
            assertEquals("预设 $index 的 presetId 在 JSON 往返后丢失", preset.presetId, restored.presetId)
            assertEquals(
                "预设 $index 往返后付费判定发生变化",
                WidgetStyle.isProPreset(preset),
                WidgetStyle.isProPreset(restored),
            )
        }
    }

    @Test
    fun `pro identity survives json round trip after tweaks`() {
        // 改字段后再经 JSON 往返，presetId 身份仍应保留，付费判定不得被绕过
        val proPreset = WidgetStyle.PRESETS[2] // 复古手账 (PRO)
        val tweaked = proPreset.copy(backgroundColor = 0xFF123456.toInt(), fontSizeSp = 33f)
        val restoredPro = WidgetStyle.fromJsonString(tweaked.toJsonString())
        assertEquals(proPreset.presetId, restoredPro.presetId)
        assertTrue("改字段并往返后仍应识别为 PRO", WidgetStyle.isProPreset(restoredPro))

        val freePreset = WidgetStyle.PRESETS[0] // 纯色圆角 (免费)
        val restoredFree = WidgetStyle.fromJsonString(freePreset.copy(cornerRadiusDp = 0f).toJsonString())
        assertEquals(freePreset.presetId, restoredFree.presetId)
        assertFalse("免费预设往返后仍应为免费", WidgetStyle.isProPreset(restoredFree))
    }

    @Test
    fun `recommended presets lists are consistent`() {
        val allListed = WidgetStyle.CLASSIC_PRESETS + WidgetStyle.PET_PRESETS
        assertFalse(allListed.isEmpty())
        // 两个分类列表不重叠
        val classicNames = WidgetStyle.CLASSIC_PRESETS.map { it.first }.toSet()
        val petNames = WidgetStyle.PET_PRESETS.map { it.first }.toSet()
        assertTrue("经典风格与萌宠风格不应包含相同项", classicNames.intersect(petNames).isEmpty())
        // 列表内每个预设都能在 PRESETS 中找到对应资源名，未出现索引漂移
        for ((name, preset) in allListed) {
            assertNotNull("推荐项 $name 缺少预设", preset)
            if (preset.presetImageResName != null) {
                val matched = WidgetStyle.PRESETS.any { it.presetImageResName == preset.presetImageResName }
                assertTrue("推荐项 $name 的资源名未在 PRESETS 中找到", matched)
            }
        }
    }
}
