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
 * App 内预览用尺寸的回归测试。
 *
 * 背景：预览原先按桌面**实时尺寸**（OPTION_APPWIDGET_MIN_WIDTH/HEIGHT）出图，
 * 用户在桌面拉伸组件、或同一组件在别的机型上落在不同 dp 的格子里，预览比例就跟着变——
 * 同一个组件的预览会在「圆角矩形」和「方形」之间来回跳。
 * 现在预览只看组件是**从哪个入口添加的**：4×4 入口 250×250，4×2 入口 250×110。
 *
 * 这里刻意把实时尺寸设成与声明尺寸不同的值，验证预览尺寸不跟着实时尺寸走。
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
    fun `预览尺寸不随桌面拉伸变化`() {
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
    fun `声明尺寸能换算成预览行数`() {
        // 4×4 入口 250dp → 4 行，4×2 入口 110dp → 2 行
        assertEquals(4, com.juge.app.ui.PreviewMetrics.spanYForHeightDp(250))
        assertEquals(2, com.juge.app.ui.PreviewMetrics.spanYForHeightDp(110))
    }
}

/** 占位用：模拟「既不是主入口、也不是紧凑入口」的组件（如未来新增的入口） */
private class ForeignWidgetProvider : android.appwidget.AppWidgetProvider()
