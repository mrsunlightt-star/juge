package com.juge.app

import android.content.ContentValues
import android.content.Context
import com.juge.app.data.DbHelper
import com.juge.app.data.WidgetConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * SQLite 层的回归测试。
 *
 * 重点锁定三件事：
 *  1. 外键级联真的生效——UI 文案承诺「删除分类会同时清空该目录下的金句」，
 *     如果 PRAGMA foreign_keys 没开，金句会变成悬挂行继续出现在「全部」里；
 *  2. widget_config 的 size_type 默认值与当前双规格（4×2 / 4×4）一致；
 *  3. 旧数据迁移只跑一次，用户清空配置后不会被重新"恢复"出来。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DbHelperTest {

    private lateinit var context: Context
    private lateinit var db: DbHelper

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        resetDbSingleton()
        db = DbHelper.getInstance(context)
    }

    @Test
    fun `分类与金句可以正常读写`() {
        val catId = db.insertCategory("测试分类")
        assertTrue(catId > 0)
        assertTrue(db.getAllCategories().any { it.id == catId && it.name == "测试分类" })

        val rid = db.insertReminder("测试金句", catId)
        assertTrue(rid > 0)
        val loaded = db.getReminderById(rid)
        assertNotNull(loaded)
        assertEquals("测试金句", loaded!!.content)
        assertEquals(catId, loaded.categoryId)
    }

    @Test
    fun `删除分类会级联删除该分类下的金句`() {
        val catId = db.insertCategory("待删分类")
        val keepCatId = db.insertCategory("保留分类")
        val victim1 = db.insertReminder("会被级联删除 A", catId)
        val victim2 = db.insertReminder("会被级联删除 B", catId)
        val survivor = db.insertReminder("必须留下", keepCatId)

        db.deleteCategory(catId)

        assertTrue(db.getRemindersByCategory(catId).isEmpty())
        // 关键断言：走「全部」列表也不能再看到它们，否则会变成 UI 上的幽灵金句
        val all = db.getAllReminders().map { it.id }
        assertFalse(all.contains(victim1))
        assertFalse(all.contains(victim2))
        assertTrue(all.contains(survivor))
    }

    @Test
    fun `widget_config 的 size_type 默认值是 4x2`() {
        // 不走实体默认值，直接裸插一行验证表结构默认值本身
        val values = ContentValues().apply {
            put("name", "schema-default")
            put("content", "内容")
            put("style_json", "{}")
        }
        val id = db.writableDatabase.insert("widget_config", null, values)
        assertTrue(id > 0)
        assertEquals("4x2", db.getWidgetConfigById(id)!!.sizeType)
    }

    @Test
    fun `widget_config 实体默认尺寸是 4x2`() {
        assertEquals("4x2", WidgetConfig(name = "n", content = "c", styleJson = "{}").sizeType)
    }

    @Test
    fun `旧数据迁移只执行一次`() {
        val catId = db.getAllCategories().first().id
        db.insertReminder("待迁移的唯一金句", catId)
        assertFalse(db.getAllWidgetConfigs().any { it.content == "待迁移的唯一金句" })

        db.migrateLegacyDataIfNeeded()
        val migrated = db.getAllWidgetConfigs()
        assertTrue(migrated.any { it.content == "待迁移的唯一金句" })

        // 用户清空全部配置后，迁移不应把金句再"恢复"成配置
        migrated.forEach { db.deleteWidgetConfig(it.id) }
        db.migrateLegacyDataIfNeeded()
        assertFalse(db.getAllWidgetConfigs().any { it.content == "待迁移的唯一金句" })
    }

    @Test
    fun `删除配置行后不再出现在列表里`() {
        val id = db.insertWidgetConfig(WidgetConfig(name = "微件 1", content = "x", styleJson = "{}"))
        assertTrue(db.getAllWidgetConfigs().any { it.id == id })

        db.deleteWidgetConfig(id)

        assertFalse(db.getAllWidgetConfigs().any { it.id == id })
    }

    /** DbHelper 是静态单例，Robolectric 每个测试方法换一个 Application，必须重置才能拿到干净库 */
    private fun resetDbSingleton() {
        runCatching {
            // Kotlin 把 companion 里的 @Volatile 属性提升为外层类的静态字段
            val field = Class.forName("com.juge.app.data.DbHelper")
                .getDeclaredField("instance")
                .apply { isAccessible = true }
            field.set(null, null)
        }
    }
}
