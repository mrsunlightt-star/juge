package com.juge.app

import android.content.Context
import com.juge.app.data.AppBackgroundPreset
import com.juge.app.data.AppPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppBackgroundSelectionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        AppPrefs.user(context).edit().clear().commit()
    }

    @After
    fun tearDown() {
        AppPrefs.user(context).edit().clear().commit()
    }

    @Test
    fun `没有选择记录时默认使用阳光海滩`() {
        assertEquals(AppBackgroundPreset.SUNNY_BEACH, AppPrefs.appBackground(context))
    }

    @Test
    fun `选择大海后重启读取仍然保留`() {
        AppPrefs.setAppBackground(context, AppBackgroundPreset.SEA)

        assertEquals(AppBackgroundPreset.SEA, AppPrefs.appBackground(context))
    }

    @Test
    fun `已下架的旧 key 回退到默认背景`() {
        assertEquals(
            AppBackgroundPreset.SUNNY_BEACH,
            AppBackgroundPreset.fromKey("removed_background"),
        )
    }
}
