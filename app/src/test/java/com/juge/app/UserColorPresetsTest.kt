package com.juge.app

import android.content.Context
import android.graphics.Color
import com.juge.app.data.AppPrefs
import com.juge.app.data.ColorPresets
import com.juge.app.data.UserColorPresets
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserColorPresetsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        AppPrefs.migrateLegacyIfNeeded(context)
        AppPrefs.user(context).edit().clear().commit()
    }

    @Test
    fun `删除的内置字体色可重新添加且不重复`() {
        val color = Color.parseColor(ColorPresets.FONT_COLORS.first())
        assertFalse(color in UserColorPresets.deleteFontColor(context, color))
        assertTrue(color in UserColorPresets.addFontColor(context, color))
        assertEquals(1, UserColorPresets.addFontColor(context, color).count { it == color })
    }

    @Test
    fun `恢复一个背景色不影响其他已删除背景色`() {
        val first = Color.parseColor(ColorPresets.BACKGROUND_COLORS[0])
        val second = Color.parseColor(ColorPresets.BACKGROUND_COLORS[1])
        UserColorPresets.deleteBackgroundColor(context, first)
        UserColorPresets.deleteBackgroundColor(context, second)
        val restored = UserColorPresets.addBackgroundColor(context, first)
        assertEquals(1, restored.count { it == first })
        assertFalse(second in restored)
        assertEquals(1, UserColorPresets.addBackgroundColor(context, first).count { it == first })
    }

    @Test
    fun `自定义颜色仍可去重添加和删除`() {
        val color = Color.parseColor("#123456")
        UserColorPresets.addFontColor(context, color)
        assertEquals(1, UserColorPresets.addFontColor(context, color).count { it == color })
        assertFalse(color in UserColorPresets.deleteFontColor(context, color))
        UserColorPresets.addBackgroundColor(context, color)
        assertEquals(1, UserColorPresets.addBackgroundColor(context, color).count { it == color })
        assertFalse(color in UserColorPresets.deleteBackgroundColor(context, color))
    }
}
