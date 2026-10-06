package com.juge.app

import com.juge.app.data.WidgetStyle
import com.juge.app.ui.adjust.previewOrApplyPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 点风格预设缩略图的统一规则（[previewOrApplyPreset]）：经典 / 萌宠 / 明信片五行共用。
 *
 * 规则：会员专属风格在未激活时只改 App 内预览，不落库 —— 点缩略图只是"看看"，
 * 真正保存时（拖滑块、改颜色、改文字）才由 onStyleChange 拦下来弹激活框。
 * 之前明信片行的插画素材没有这条拦截，未激活用户点一下就把 PRO 风格存到桌面了。
 */
class PresetApplyTest {

    private val freeStyle = WidgetStyle.PRESETS.first { it.presetId == "p_pure_round" }
    private val proStyle = WidgetStyle.PRESETS.first { it.presetId == "p_fight_club" }

    private class Recorder {
        val previews = mutableListOf<WidgetStyle>()
        val applied = mutableListOf<WidgetStyle>()
    }

    private fun click(style: WidgetStyle, isActivated: Boolean): Recorder {
        val rec = Recorder()
        previewOrApplyPreset(style, isActivated, { rec.previews += it }, { rec.applied += it })
        return rec
    }

    @Test
    fun `未激活时会员风格只预览`() {
        val rec = click(proStyle, isActivated = false)

        assertEquals(1, rec.previews.size)
        assertTrue("未激活用户点会员预设不应落库", rec.applied.isEmpty())
    }

    @Test
    fun `未激活时免费风格直接套用`() {
        val rec = click(freeStyle, isActivated = false)

        assertEquals(1, rec.previews.size)
        assertEquals(1, rec.applied.size)
    }

    @Test
    fun `已激活时会员风格正常套用`() {
        val rec = click(proStyle, isActivated = true)

        assertEquals(1, rec.previews.size)
        assertEquals(1, rec.applied.size)
    }
}
