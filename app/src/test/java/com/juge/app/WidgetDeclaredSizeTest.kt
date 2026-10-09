package com.juge.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 两个组件入口「声明尺寸」的回归测试。
 *
 * 背景：两个入口各自在 xml 里声明了默认落位（4×4 → 250×250、4×2 → 250×110），
 * 这里锁定这份映射，并把实时尺寸设成与声明尺寸不同的值，验证它不跟着桌面拉伸走。
 *
 * ⚠️ App 内预览**已不再**按它出图（2026-10-08 起改按风格的最佳显示尺寸，
 * 见 WidgetStyle.bestDisplaySize 与 PresetDisplaySizeTest）：同一个风格挂在哪个入口、
 * 在桌面上拉成多大，预览都不该跟着变。这份映射现由回归测试与后续
 * 「按入口提示尺寸」类功能取用。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xxhdpi")
class WidgetDeclaredSizeTest {

    /** 每个用例只绑定自己用到的组件；ID 递增保证同一个测试里多次绑定不会互相覆盖 */
    private var nextWidgetId = 100

    private fun bindWidget(providerClass: Class<*>, liveWidthDp: Int, liveHeightDp: Int): Int {
        val context = RuntimeEnvironment.getApplication()
        val manager = AppWidgetManager.getInstance(context)
        val id = nextWidgetId++
        shadowOf(manager).bindAppWidgetId(id, ComponentName(context, providerClass))
        manager.updateAppWidgetOptions(
            id,
            android.os.Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, liveWidthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, liveHeightDp)
            }
        )
        return id
    }

    @Test
    fun `紧凑入口声明为 4×2`() {
        val context = RuntimeEnvironment.getApplication()
        val id = bindWidget(ReminderWidgetProviderCompact::class.java, liveWidthDp = 250, liveHeightDp = 110)

        assertEquals(250 to 110, ReminderWidgetProvider.getWidgetDeclaredSizeDp(context, id))
        assertEquals("4*2", ReminderWidgetProvider.getWidgetDeclaredSizeString(context, id))
    }

    @Test
    fun `主入口声明为 4×4`() {
        val context = RuntimeEnvironment.getApplication()
        val id = bindWidget(ReminderWidgetProvider::class.java, liveWidthDp = 250, liveHeightDp = 250)

        assertEquals(250 to 250, ReminderWidgetProvider.getWidgetDeclaredSizeDp(context, id))
        assertEquals("4*4", ReminderWidgetProvider.getWidgetDeclaredSizeString(context, id))
    }

    @Test
    fun `声明尺寸不随桌面拉伸变化`() {
        val context = RuntimeEnvironment.getApplication()
        // 4×2 入口的组件被拉到 4 行高：声明尺寸仍是 250×110
        val stretched = bindWidget(ReminderWidgetProviderCompact::class.java, liveWidthDp = 250, liveHeightDp = 250)

        assertEquals(250 to 110, ReminderWidgetProvider.getWidgetDeclaredSizeDp(context, stretched))
    }

    @Test
    fun `App 内没有桌面组件时按 4×2 兜底`() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals(250 to 110, ReminderWidgetProvider.getWidgetDeclaredSizeDp(context, -1))
        assertEquals("4*2", ReminderWidgetProvider.getWidgetDeclaredSizeString(context, -1))
    }

    /** 不属于任何一个入口的组件（入口归属问不到）按实时行数兜底 */
    @Test
    fun `问不到入口归属时按实时行数兜底`() {
        val context = RuntimeEnvironment.getApplication()
        val fourRows = bindWidget(ForeignWidgetProvider::class.java, liveWidthDp = 250, liveHeightDp = 250)
        val twoRows = bindWidget(ForeignWidgetProvider::class.java, liveWidthDp = 250, liveHeightDp = 110)

        assertEquals(250 to 250, ReminderWidgetProvider.getWidgetDeclaredSizeDp(context, fourRows))
        assertEquals(250 to 110, ReminderWidgetProvider.getWidgetDeclaredSizeDp(context, twoRows))
    }

    @Test
    fun `声明尺寸能换算成网格行数`() {
        // 250dp → 4 行、110dp → 2 行；最佳显示尺寸（WidgetDisplaySize）也走这一套换算
        assertEquals(4, com.juge.app.ui.PreviewMetrics.spanYForHeightDp(250))
        assertEquals(2, com.juge.app.ui.PreviewMetrics.spanYForHeightDp(110))
    }
}

/** 占位用：模拟「既不是主入口、也不是紧凑入口」的组件（如未来新增的入口） */
private class ForeignWidgetProvider : android.appwidget.AppWidgetProvider()
