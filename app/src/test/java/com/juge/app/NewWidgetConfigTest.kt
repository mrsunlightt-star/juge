package com.juge.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.view.ViewGroup
import com.juge.app.data.AppPrefs
import com.juge.app.data.DbHelper
import com.juge.app.data.WidgetConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 从选择器新加组件时的配置语义：**不认领任何存量配置**（产品定档 2026-11-01）。
 *
 * 旧行为在没有绑定时会捡一条已有配置克隆给新组件（优先没人用的，再退到第一条），
 * 于是用户之前调过的样式（例如绿色背景）和内容会一起被带过去——新组件看着根本不像
 * 默认的「纯色圆角」。现在直接返回 null，渲染端按"还没配内容"处理：
 * 纯色圆角 + 「打开句阁，添加你的第一条金句」，与首次添加的观感一致。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xxhdpi")
class NewWidgetConfigTest {

    private var nextWidgetId = 300

    private fun db(): DbHelper = DbHelper.getInstance(RuntimeEnvironment.getApplication())

    /** 造一条"用户之前调过的"配置：绿色背景，用来验证它不会被新组件认领 */
    private fun insertGreenConfig(): Long = db().insertWidgetConfig(
        WidgetConfig(
            name = "绿卡",
            content = "弱小和无知不是生存的障碍，傲慢才是。",
            styleJson = """{"backgroundColor":"#3E7A4E","fontColor":"#FFFFFF"}""",
        )
    )

    private fun bindNewWidget(providerClass: Class<*>): Int {
        val context = RuntimeEnvironment.getApplication()
        val id = nextWidgetId++
        shadowOf(AppWidgetManager.getInstance(context))
            .bindAppWidgetId(id, ComponentName(context, providerClass))
        return id
    }

    @Test
    fun `新组件不认领已有配置`() {
        val context = RuntimeEnvironment.getApplication()
        val existingId = insertGreenConfig()
        val widgetId = bindNewWidget(ReminderWidgetProvider::class.java)

        assertNull(
            "从选择器新加的组件不该拿到任何存量配置；拿到就说明用户之前调过的样式会被带过去",
            ReminderWidgetProvider.ensureIndependentConfig(context, widgetId),
        )
        assertEquals(
            "没有认领配置，绑定关系必须保持为空（渲染端据此走默认纯色圆角 + 引导文案）",
            -1L,
            ReminderWidgetProvider.getBoundConfigId(context, widgetId),
        )
        // 存量配置本身不能被改动
        assertEquals("#3E7A4E", db().getWidgetConfigById(existingId)?.styleJson?.let { json ->
            org.json.JSONObject(json).optString("backgroundColor")
        })
    }

    @Test
    fun `显式指定配置时仍然绑定`() {
        val context = RuntimeEnvironment.getApplication()
        val existingId = insertGreenConfig()
        val widgetId = bindNewWidget(ReminderWidgetProvider::class.java)

        val bound = ReminderWidgetProvider.ensureIndependentConfig(context, widgetId, existingId)

        assertNotEquals("显式指定（App 内推送到桌面）时仍需完成绑定", -1L, ReminderWidgetProvider.getBoundConfigId(context, widgetId))
        assertEquals("内容应沿用被指定的配置", "弱小和无知不是生存的障碍，傲慢才是。", bound?.content)
    }

    @Test
    fun `已绑定的组件重复调用保持同一配置`() {
        val context = RuntimeEnvironment.getApplication()
        val existingId = insertGreenConfig()
        val widgetId = bindNewWidget(ReminderWidgetProviderCompact::class.java)
        ReminderWidgetProvider.ensureIndependentConfig(context, widgetId, existingId)

        val again = ReminderWidgetProvider.ensureIndependentConfig(context, widgetId)

        assertEquals("已绑定后再调用应返回同一条配置", existingId, again?.id)
        assertEquals(existingId, ReminderWidgetProvider.getBoundConfigId(context, widgetId))
    }

    @Test
    fun `没有配置的兜底组件也能点开 App`() {
        val context = RuntimeEnvironment.getApplication()
        AppPrefs.setPrivacyAccepted(context)
        insertGreenConfig()
        val widgetId = bindNewWidget(ReminderWidgetProvider::class.java)
        val manager = AppWidgetManager.getInstance(context)

        ReminderWidgetProvider.updateAppWidget(context, manager, widgetId)

        val view = shadowOf(manager).getViewFor(widgetId)
        assertNotNull("兜底组件必须真的渲染出视图", view)
        // 点击事件挂在里层的图片上（RemoteViews 的 setOnClickPendingIntent），根布局本身没有
        val image = (view as ViewGroup).getChildAt(0)
        assertTrue(
            "兜底文案卡片必须挂点击事件——这里漏挂时，所有还没配内容的新组件点了都毫无反应",
            image.hasOnClickListeners(),
        )
    }
}
