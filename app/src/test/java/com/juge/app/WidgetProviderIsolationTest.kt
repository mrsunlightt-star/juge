package com.juge.app

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import com.juge.app.data.AppPrefs
import com.juge.app.data.DbHelper
import com.juge.app.data.WidgetConfig
import com.juge.app.data.WidgetStyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi", application = Application::class)
class WidgetProviderIsolationTest {
    private lateinit var context: Context
    private lateinit var db: DbHelper
    private lateinit var manager: AppWidgetManager
    private val styleA = WidgetStyle.DEFAULT.copy(backgroundColor = 0xff123456.toInt())
    private val styleB = WidgetStyle.DEFAULT.copy(backgroundColor = 0xff654321.toInt())

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        DbHelper::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        db = DbHelper.getInstance(context)
        manager = AppWidgetManager.getInstance(context)
    }

    @After
    fun drainRenderQueue() {
        val executor = ReminderWidgetProvider::class.java.getDeclaredField("renderExecutor")
            .apply { isAccessible = true }.get(null) as ExecutorService
        executor.submit {}.get(20, TimeUnit.SECONDS)
        db.close()
    }

    private fun register(id: Int, compact: Boolean = false, restoring: Boolean = false) {
        val provider = if (compact) ReminderWidgetProviderCompact::class.java else ReminderWidgetProvider::class.java
        shadowOf(manager).bindAppWidgetId(id, ComponentName(context, provider))
        manager.updateAppWidgetOptions(id, Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, if (compact) 110 else 250)
            if (restoring) putBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED, false)
        })
    }

    private fun config(content: String = "original", autoCreated: Boolean = false): Long =
        db.insertWidgetConfig(WidgetConfig(name = "测试卡片", content = content, styleJson = styleA.toJsonString(), autoCreated = autoCreated))

    private fun legacyBinding(widgetId: Int, configId: Long, style: WidgetStyle = styleA) {
        context.getSharedPreferences(ReminderWidgetProvider.PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE).edit()
            .putLong("widget_config_id_$widgetId", configId)
            .putString("widget_style_$widgetId", style.toJsonString())
            .commit()
    }

    @Test
    fun `全新安装两个入口不自动创建也不认领配置`() {
        // 产品定档 2026-11-01：从选择器新加的组件一律回到默认态（纯色圆角 + 引导文案），
        // 不再拿库里已有/自动生成的配置当模板——否则用户之前调过的样式和内容会被带过去。
        AppPrefs.setPrivacyAccepted(context)
        register(10)
        register(20, compact = true)
        ReminderWidgetProvider.updateAppWidget(context, manager, 10)
        ReminderWidgetProvider.updateAppWidget(context, manager, 20)

        assertEquals(-1L, ReminderWidgetProvider.getBoundConfigId(context, 10))
        assertEquals(-1L, ReminderWidgetProvider.getBoundConfigId(context, 20))
        // 全新安装库里只有种子行（「默认卡片1」+「英文金句示例」），两个组件都不该认领，也不该多建行
        val seeded = db.getAllWidgetConfigs()
        assertEquals(2, seeded.size)
        val first = seeded.first { it.name == "默认卡片1" }
        assertFalse(first.autoCreated)
        assertFalse(seeded.any { it.autoCreated })

        // 重复更新也不该开始克隆
        ReminderWidgetProvider.updateAppWidget(context, manager, 10)
        ReminderWidgetProvider.updateAppWidget(context, manager, 20)
        assertEquals(2, db.getAllWidgetConfigs().size)
    }

    @Test
    fun `历史共享编辑先拆分且两个组件独立样式不丢失`() {
        val id = config()
        register(10)
        register(20, compact = true)
        legacyBinding(10, id, styleA)
        legacyBinding(20, id, styleB)
        val target = ReminderWidgetProvider.ensureIndependentConfig(context, 20, id)!!
        assertNotEquals(id, target.id)
        assertTrue(target.autoCreated)
        assertEquals(styleB, WidgetStyle.fromJsonString(target.styleJson))
        db.updateWidgetConfig(target.copy(content = "edited"))
        assertEquals("original", db.getWidgetConfigById(id)!!.content)
        assertEquals(id, ReminderWidgetProvider.getBoundConfigId(context, 10))
        assertEquals(styleA, ReminderWidgetProvider.getWidgetStyle(context, 10, null))
        assertEquals(styleB, ReminderWidgetProvider.getWidgetStyle(context, 20, null))
        assertEquals(target.id, ReminderWidgetProvider.ensureIndependentConfig(context, 20, id)!!.id)
        assertEquals(2, db.getAllWidgetConfigs().size)
    }

    @Test
    fun `推送金句不会覆盖历史共享组件的内容和样式`() {
        val id = config()
        legacyBinding(10, id, styleA)
        legacyBinding(20, id, styleB)
        val reminderId = db.insertReminder("new reminder", db.getAllCategories().first().id, styleJson = styleA.toJsonString())
        ReminderWidgetProvider.bindReminderToWidget(context, 20, reminderId)
        val pushed = db.getWidgetConfigById(ReminderWidgetProvider.getBoundConfigId(context, 20))!!
        assertEquals("new reminder", pushed.content)
        assertEquals("original", db.getWidgetConfigById(id)!!.content)
        assertEquals(styleA, ReminderWidgetProvider.getWidgetStyle(context, 20, null))
        assertEquals(styleA, ReminderWidgetProvider.getWidgetStyle(context, 10, null))
        ReminderWidgetProvider.bindReminderToWidget(context, 20, reminderId)
        assertEquals(2, db.getAllWidgetConfigs().size)
    }

    @Test
    fun `并发确保绑定不会让两个组件抢到同一配置或生成多余副本`() {
        // 没有显式指定配置时新组件不再认领存量配置（见 NewWidgetConfigTest），
        // 所以并发场景改成"App 内把同一条金句推到两个组件"的口径来验证隔离性。
        val shared = config()
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results = executor.invokeAll((0 until 20).map { index ->
                Callable {
                    val widgetId = if (index % 2 == 0) 10 else 20
                    ReminderWidgetProvider.ensureIndependentConfig(context, widgetId, shared)!!.id
                }
            }).map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(2, results.toSet().size)
            assertEquals(1, results.filterIndexed { index, _ -> index % 2 == 0 }.toSet().size)
            assertEquals(1, results.filterIndexed { index, _ -> index % 2 != 0 }.toSet().size)
            assertEquals(2, db.getAllWidgetConfigs().size)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `专门克隆入口保留空内容和历史超长内容`() {
        val empty = config(content = "")
        assertEquals("", db.cloneWidgetConfigForBinding(empty, 10, null)!!.content)
        val legacy = "x".repeat(600)
        db.writableDatabase.execSQL("UPDATE widget_config SET content = ? WHERE id = ?", arrayOf<Any>(legacy, empty))
        assertEquals(legacy, db.cloneWidgetConfigForBinding(empty, 20, null)!!.content)
        assertNull(db.cloneWidgetConfigForBinding(Long.MAX_VALUE, 30, null))
    }

    @Test
    fun `普通更新和清理在恢复映射之前不能误绑定或回收自动配置`() {
        val id = config(autoCreated = true)
        legacyBinding(10, id, styleB)
        register(30, restoring = true)
        AppPrefs.setPrivacyAccepted(context)
        ReminderWidgetProvider.updateAppWidget(context, manager, 30)
        ReminderWidgetProvider.clearWidgetData(context, 10)
        ReminderWidgetProvider.pruneOrphanAutoConfigs(context)
        assertEquals(-1L, ReminderWidgetProvider.getBoundConfigId(context, 30))
        assertEquals(id, ReminderWidgetProvider.getBoundConfigId(context, 10))
        assertNotNull(db.getWidgetConfigById(id))
        assertNull(ReminderWidgetProvider.ensureIndependentConfig(context, 30, id))
        ReminderWidgetProvider().onRestored(context, intArrayOf(10), intArrayOf(30))
        assertEquals(id, ReminderWidgetProvider.getBoundConfigId(context, 30))
        assertEquals(styleB, ReminderWidgetProvider.getWidgetStyle(context, 30, null))
        assertEquals(-1L, ReminderWidgetProvider.getBoundConfigId(context, 10))
        assertTrue(manager.getAppWidgetOptions(30).getBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED))
        ReminderWidgetProvider.pruneOrphanAutoConfigs(context)
        assertNotNull(db.getWidgetConfigById(id))
    }

    @Test
    fun `两个Provider均可恢复交错ID绑定和独立样式`() {
        for (provider in listOf(ReminderWidgetProvider(), ReminderWidgetProviderCompact())) {
            val compact = provider is ReminderWidgetProviderCompact
            val firstId = if (compact) 40 else 10
            val secondId = firstId + 10
            val firstConfig = config("first")
            val secondConfig = config("second")
            legacyBinding(firstId, firstConfig, styleA)
            legacyBinding(secondId, secondConfig, styleB)
            register(firstId, compact, restoring = true)
            register(secondId, compact, restoring = true)
            provider.onRestored(context, intArrayOf(firstId, secondId), intArrayOf(secondId, firstId))
            assertEquals(firstConfig, ReminderWidgetProvider.getBoundConfigId(context, secondId))
            assertEquals(secondConfig, ReminderWidgetProvider.getBoundConfigId(context, firstId))
            assertEquals(styleA, ReminderWidgetProvider.getWidgetStyle(context, secondId, null))
            assertEquals(styleB, ReminderWidgetProvider.getWidgetStyle(context, firstId, null))
            assertTrue(manager.getAppWidgetOptions(firstId).getBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED))
            assertTrue(manager.getAppWidgetOptions(secondId).getBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED))
            provider.onRestored(context, intArrayOf(firstId, secondId), intArrayOf(secondId, firstId))
            assertEquals(firstConfig, ReminderWidgetProvider.getBoundConfigId(context, secondId))
        }
    }

    @Test
    fun `跨Provider回调使用同一恢复快照并保护已恢复的目标ID`() {
        val first = config("first", autoCreated = true)
        val second = config("second", autoCreated = true)
        legacyBinding(10, first, styleA)
        legacyBinding(20, second, styleB)
        register(20, restoring = true)
        register(30, compact = true, restoring = true)
        ReminderWidgetProvider().onRestored(context, intArrayOf(10), intArrayOf(20))
        ReminderWidgetProvider.updateAppWidget(context, manager, 30)
        ReminderWidgetProvider.pruneOrphanAutoConfigs(context)
        assertNotNull(db.getWidgetConfigById(second))
        assertEquals(-1L, ReminderWidgetProvider.getBoundConfigId(context, 30))
        ReminderWidgetProviderCompact().onRestored(context, intArrayOf(20), intArrayOf(30))
        assertEquals(first, ReminderWidgetProvider.getBoundConfigId(context, 20))
        assertEquals(second, ReminderWidgetProvider.getBoundConfigId(context, 30))
        assertEquals(styleA, ReminderWidgetProvider.getWidgetStyle(context, 20, null))
        assertEquals(styleB, ReminderWidgetProvider.getWidgetStyle(context, 30, null))
        val prefs = context.getSharedPreferences(ReminderWidgetProvider.PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE)
        assertFalse(prefs.all.keys.any { it.startsWith("restore_") || it == "restored_ids" })
    }

    @Test
    fun `系统尚未登记恢复组件时也不回收备份绑定引用的配置`() {
        val id = config(autoCreated = true)
        legacyBinding(10, id)
        val orphan = config(autoCreated = true)
        ReminderWidgetProvider.pruneOrphanAutoConfigs(context)
        assertNotNull(db.getWidgetConfigById(id))
        assertNull(db.getWidgetConfigById(orphan))
    }

    @Test
    fun `绑定缺失配置时返回空值而不是共享无效ID`() {
        assertNull(ReminderWidgetProvider.ensureIndependentConfig(context, 10, 42L))
        assertNull(ReminderWidgetProvider.ensureIndependentConfig(context, -1))
        assertEquals(-1L, ReminderWidgetProvider.getBoundConfigId(context, 10))
    }

    @Test
    fun `备份包含绑定和样式但仍排除激活与隐私状态`() {
        for (resource in listOf(R.xml.backup_rules, R.xml.data_extraction_rules)) {
            val excluded = mutableListOf<String>()
            var includesSharedPrefs = false
            context.resources.getXml(resource).use { parser ->
                while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG &&
                        parser.getAttributeValue(null, "domain") == "sharedpref"
                    ) {
                        val path = parser.getAttributeValue(null, "path").orEmpty()
                        if (parser.name == "exclude") excluded.add(path)
                        if (parser.name == "include" && path == ".") includesSharedPrefs = true
                    }
                    parser.next()
                }
            }
            assertTrue(includesSharedPrefs)
            assertFalse(excluded.contains("widget_bindings_prefs.xml"))
            // account_prefs.xml 已随账号体系删除，不再出现在排除列表里
            assertFalse(excluded.contains("account_prefs.xml"))
            assertTrue(
                excluded.containsAll(
                    listOf(
                        "trial_activation_v2_prefs.xml",
                        "pro_purchase_prefs.xml",
                        "consent_prefs.xml",
                        "app_settings.xml",
                    )
                )
            )
        }
    }
}
