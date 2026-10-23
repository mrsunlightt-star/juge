package com.juge.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import timber.log.Timber

// 分类数据结构
data class Category(
    val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

// 提醒内容数据结构
data class Reminder(
    val id: Long = 0,
    val content: String,
    val categoryId: Long,
    val isFavorite: Boolean = false,
    val styleJson: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

class DbHelper private constructor(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val appContext: Context = context.applicationContext

    override fun onCreate(db: SQLiteDatabase) {
        // 创建分类表
        db.execSQL("""
            CREATE TABLE categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                sort_order INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())

        // 创建提醒表
        db.execSQL("""
            CREATE TABLE reminders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                content TEXT NOT NULL,
                category_id INTEGER NOT NULL,
                is_favorite INTEGER NOT NULL DEFAULT 0,
                style_json TEXT,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE
            )
        """.trimIndent())

        // 创建微件配置表
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS widget_config (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                content TEXT NOT NULL,
                size_type TEXT NOT NULL DEFAULT '4x2',
                style_json TEXT,
                auto_created INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        createIndexes(db)

        // 插入初始默认分类
        val now = System.currentTimeMillis()
        db.execSQL("INSERT INTO categories (name, sort_order, created_at) VALUES ('文学', 1, $now)")
        db.execSQL("INSERT INTO categories (name, sort_order, created_at) VALUES ('诗歌', 2, $now)")
        db.execSQL("INSERT INTO categories (name, sort_order, created_at) VALUES ('激励', 3, $now)")

        // 插入初始默认提醒
        db.execSQL("INSERT INTO reminders (content, category_id, is_favorite, created_at) VALUES ('弱小和无知不是生存的障碍，傲慢才是。————刘慈欣《三体》', 1, 1, $now)")
        db.execSQL("INSERT INTO reminders (content, category_id, is_favorite, created_at) VALUES ('\"你站在桥上看风景，看风景人在楼上看你。明月装饰了你的窗子，你装饰了别人的梦。\" —— 卞之琳《断章》', 2, 0, $now)")
        db.execSQL("INSERT INTO reminders (content, category_id, is_favorite, created_at) VALUES ('life is hard，always', 3, 0, $now)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Timber.i("onUpgrade: oldVersion=%d, newVersion=%d", oldVersion, newVersion)

        // 版本 5：新增 widget_config 表
        if (oldVersion < 5) {
            Timber.i("Migrating to version 5: creating widget_config table")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS widget_config (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    content TEXT NOT NULL,
                    size_type TEXT NOT NULL DEFAULT '4x2',
                    style_json TEXT
                )
            """.trimIndent())
        }

        // 版本 6：为按分类查询、按 ID 查询补建索引
        if (oldVersion < 6) {
            Timber.i("Migrating to version 6: creating indexes")
            createIndexes(db)
        }

        // 版本 7：widget_config 增加 auto_created 列，显式区分「Provider 自动建」与「用户建」的行，
        // 存量行按旧的名称前缀推断回填（用户起了同前缀名字的存量行会被记成自动行，属历史数据的
        // 已发生状态，往前只有走名称推断这一条路可走；新建行二不再靠前缀）
        if (oldVersion < 7) {
            Timber.i("Migrating to version 7: adding widget_config.auto_created")
            addWidgetConfigAutoCreatedColumn(db)
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 降级安装（旧版 APK 覆盖新版）时，SQLiteOpenHelper 默认实现会抛 SQLiteException 导致崩溃；
        // 这里按当前代码所需结构幂等补建缺失的表与索引，已有数据全部保留。
        Timber.w("onDowngrade: oldVersion=%d, newVersion=%d, keeping data and ensuring tables exist", oldVersion, newVersion)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                sort_order INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS reminders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                content TEXT NOT NULL,
                category_id INTEGER NOT NULL,
                is_favorite INTEGER NOT NULL DEFAULT 0,
                style_json TEXT,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS widget_config (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                content TEXT NOT NULL,
                size_type TEXT NOT NULL DEFAULT '4x2',
                style_json TEXT,
                auto_created INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        addWidgetConfigAutoCreatedColumn(db)
        createIndexes(db)
    }

    /**
     * 幂等补上 widget_config.auto_created 列（兼容旧库直接建表的 onDowngrade 路径），
     * 存量行按旧版规则（名称前缀「微件 」）回填。
     */
    private fun addWidgetConfigAutoCreatedColumn(db: SQLiteDatabase) {
        val hasColumn = runCatching {
            db.rawQuery("PRAGMA table_info(widget_config)", null).use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow("name")
                var found = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIdx) == "auto_created") found = true
                }
                found
            }
        }.getOrDefault(false)
        if (hasColumn) return
        db.execSQL("ALTER TABLE widget_config ADD COLUMN auto_created INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE widget_config SET auto_created = 1 WHERE name LIKE '微件 %'")
    }

    private fun createIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reminders_category_id ON reminders(category_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reminders_created_at ON reminders(created_at)")
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    // --- 分类 CRUD ---

    fun insertCategory(name: String): Long {
        require(name.isNotBlank()) { "分类名称不能为空" }
        require(name.length <= 100) { "分类名称过长，最多100个字符" }

        val db = writableDatabase
        var maxOrder = 0
        db.rawQuery("SELECT MAX(sort_order) FROM categories", null).use { cursor ->
            if (cursor.moveToFirst()) {
                maxOrder = cursor.getInt(0)
            }
        }

        val values = ContentValues().apply {
            put("name", name)
            put("sort_order", maxOrder + 1)
            put("created_at", System.currentTimeMillis())
        }
        return db.insert("categories", null, values)
    }

    fun getAllCategories(): List<Category> {
        val list = mutableListOf<Category>()
        val db = readableDatabase
        db.rawQuery("SELECT * FROM categories ORDER BY sort_order ASC, created_at DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(parseCategory(cursor))
            }
        }
        return list
    }

    private fun parseCategory(cursor: Cursor): Category {
        return Category(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow("sort_order")),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
        )
    }

    fun swapCategoryOrder(cat1Id: Long, cat1Order: Int, cat2Id: Long, cat2Order: Int) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val val1 = ContentValues().apply { put("sort_order", cat2Order) }
            db.update("categories", val1, "id = ?", arrayOf(cat1Id.toString()))

            val val2 = ContentValues().apply { put("sort_order", cat1Order) }
            db.update("categories", val2, "id = ?", arrayOf(cat2Id.toString()))

            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Timber.e(e, "swapCategoryOrder failed")
        } finally {
            db.endTransaction()
        }
    }

    fun deleteCategory(id: Long) {
        val db = writableDatabase
        db.delete("categories", "id = ?", arrayOf(id.toString()))
    }

    fun renameCategory(id: Long, newName: String) {
        require(newName.isNotBlank()) { "分类名称不能为空" }
        require(newName.length <= 100) { "分类名称过长，最多100个字符" }

        val db = writableDatabase
        val values = ContentValues().apply {
            put("name", newName)
        }
        db.update("categories", values, "id = ?", arrayOf(id.toString()))
    }

    // --- 提醒内容 CRUD ---

    fun insertReminder(content: String, categoryId: Long, isFavorite: Boolean = false, styleJson: String? = null): Long {
        require(content.isNotBlank()) { "提醒内容不能为空" }
        require(content.length <= 500) { "提醒内容过长，最多500个字符" }

        val db = writableDatabase
        val values = ContentValues().apply {
            put("content", content)
            put("category_id", categoryId)
            put("is_favorite", if (isFavorite) 1 else 0)
            put("style_json", styleJson)
            put("created_at", System.currentTimeMillis())
        }
        return db.insert("reminders", null, values)
    }

    fun getAllReminders(): List<Reminder> {
        val list = mutableListOf<Reminder>()
        val db = readableDatabase
        db.rawQuery("SELECT * FROM reminders ORDER BY created_at DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(parseReminder(cursor))
            }
        }
        return list
    }

    fun getRemindersByCategory(categoryId: Long): List<Reminder> {
        val list = mutableListOf<Reminder>()
        val db = readableDatabase
        db.rawQuery("SELECT * FROM reminders WHERE category_id = ? ORDER BY created_at DESC", arrayOf(categoryId.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(parseReminder(cursor))
            }
        }
        return list
    }

    fun getReminderById(id: Long): Reminder? {
        val db = readableDatabase
        db.rawQuery("SELECT * FROM reminders WHERE id = ?", arrayOf(id.toString())).use { cursor ->
            if (cursor.moveToFirst()) {
                return parseReminder(cursor)
            }
        }
        return null
    }

    fun updateReminder(id: Long, content: String, categoryId: Long, isFavorite: Boolean, styleJson: String?) {
        require(content.isNotBlank()) { "提醒内容不能为空" }
        require(content.length <= 500) { "提醒内容过长，最多500个字符" }

        val db = writableDatabase
        val values = ContentValues().apply {
            put("content", content)
            put("category_id", categoryId)
            put("is_favorite", if (isFavorite) 1 else 0)
            put("style_json", styleJson)
        }
        db.update("reminders", values, "id = ?", arrayOf(id.toString()))
    }

    fun updateReminderStyle(id: Long, styleJson: String?) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("style_json", styleJson)
        }
        db.update("reminders", values, "id = ?", arrayOf(id.toString()))
    }

    fun deleteReminder(id: Long) {
        val db = writableDatabase
        db.delete("reminders", "id = ?", arrayOf(id.toString()))
    }

    private fun parseReminder(cursor: Cursor): Reminder {
        return Reminder(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            content = cursor.getString(cursor.getColumnIndexOrThrow("content")),
            categoryId = cursor.getLong(cursor.getColumnIndexOrThrow("category_id")),
            isFavorite = cursor.getInt(cursor.getColumnIndexOrThrow("is_favorite")) == 1,
            styleJson = cursor.getString(cursor.getColumnIndexOrThrow("style_json")),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
        )
    }

    // --- 微件配置 CRUD ---

    fun insertWidgetConfig(config: WidgetConfig): Long {
        require(config.name.isNotBlank()) { "微件配置名称不能为空" }
        require(config.name.length <= 100) { "微件配置名称过长，最多100个字符" }
        require(config.content.isNotBlank()) { "微件配置内容不能为空" }
        require(config.content.length <= 500) { "微件配置内容过长，最多500个字符" }

        val db = writableDatabase
        val values = ContentValues().apply {
            put("name", config.name)
            put("content", config.content)
            put("size_type", config.sizeType)
            put("style_json", config.styleJson)
            put("auto_created", if (config.autoCreated) 1 else 0)
        }
        return db.insert("widget_config", null, values)
    }

    fun updateWidgetConfig(config: WidgetConfig) {
        require(config.name.isNotBlank()) { "微件配置名称不能为空" }
        require(config.name.length <= 100) { "微件配置名称过长，最多100个字符" }
        require(config.content.isNotBlank()) { "微件配置内容不能为空" }
        require(config.content.length <= 500) { "微件配置内容过长，最多500个字符" }

        val db = writableDatabase
        val values = ContentValues().apply {
            put("name", config.name)
            put("content", config.content)
            put("size_type", config.sizeType)
            put("style_json", config.styleJson)
        }
        db.update("widget_config", values, "id = ?", arrayOf(config.id.toString()))
    }

    fun deleteWidgetConfig(id: Long) {
        val db = writableDatabase
        db.delete("widget_config", "id = ?", arrayOf(id.toString()))
    }

    fun getAllWidgetConfigs(): List<WidgetConfig> {
        val list = mutableListOf<WidgetConfig>()
        val db = readableDatabase
        db.rawQuery(
            "SELECT id, name, content, size_type, style_json, auto_created FROM widget_config ORDER BY id ASC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(parseWidgetConfig(cursor))
            }
        }
        return list
    }

    fun getWidgetConfigById(id: Long): WidgetConfig? {
        val db = readableDatabase
        db.rawQuery(
            "SELECT id, name, content, size_type, style_json, auto_created FROM widget_config WHERE id = ?",
            arrayOf(id.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return parseWidgetConfig(cursor)
            }
        }
        return null
    }

    private fun parseWidgetConfig(cursor: Cursor): WidgetConfig {
        // onDowngrade 补建的是带列的新表，但极端场景（外部覆盖旧 APK 建的老结构）没有该列：
        // 列缺失时按旧版规则用名称前缀兜底，避免getColumnIndexOrThrow 直接炸
        val autoIdx = cursor.getColumnIndex("auto_created")
        return WidgetConfig(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            content = cursor.getString(cursor.getColumnIndexOrThrow("content")),
            sizeType = cursor.getString(cursor.getColumnIndexOrThrow("size_type")),
            styleJson = cursor.getString(cursor.getColumnIndexOrThrow("style_json")),
            autoCreated = if (autoIdx >= 0) cursor.getInt(autoIdx) == 1 else cursor.getString(
                cursor.getColumnIndexOrThrow("name")
            ).startsWith(WidgetConfigBinding.AUTO_CONFIG_NAME_PREFIX)
        )
    }

    /**
     * 老版本数据（reminders 一把梭远早于 widget_config）导入配置行。只跑一次。
     *
     * 两个入口（主界面启动的 IO 协程、组件刷新的后台线程）会并发进入，
     * 因此整段用 synchronized 包住：否则两个线程都能看到「未迁移 + 配置为空」，
     * 把全部金句各导入一遍，产生成对的重复配置行。
     *
     * 配置为空时的三条分支：老用户（升级前装过老版本 → 有历史 app_settings 数据）导入 readings；
     * 全新安装没有老数据，只放一张「默认卡片1」当起点；
     * 迁移标记完成写入在最后，同样受锁保护。
     */
    @Synchronized
    fun migrateLegacyDataIfNeeded() {
        if (AppPrefs.isLegacyMigrationDone(appContext)) return

        val db = writableDatabase
        // 1. 查询 widget_config 记录数
        var configCount = 0
        try {
            db.rawQuery("SELECT COUNT(*) FROM widget_config", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    configCount = cursor.getInt(0)
                }
            }
        } catch (e: Exception) {
            // 表未创建时兜底
            Timber.w(e, "migrateLegacyDataIfNeeded: widget_config table not available")
            return
        }

        // 2. 如果 widget_config 为空，则把 reminders 转换并写入配置库中
        if (configCount == 0) {
            if (AppPrefs.isLegacyInstall(appContext)) {
                val reminders = getAllReminders()
                if (reminders.isNotEmpty()) {
                    db.beginTransaction()
                    try {
                        for (reminder in reminders) {
                            val name = if (reminder.content.length > 5) {
                                "导入卡片 - " + reminder.content.take(5) + "..."
                            } else {
                                "导入卡片 - " + reminder.content
                            }
                            val values = ContentValues().apply {
                                put("name", name)
                                put("content", reminder.content)
                                put("size_type", "4x2")
                                put("style_json", reminder.styleJson ?: WidgetStyle().toJsonString())
                                put("auto_created", 0) // 导入行是用户内容，不参与孤儿回收
                            }
                            db.insert("widget_config", null, values)
                        }
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            } else {
                // 全新安装：库里只有种子金句，不值得包装成「导入卡片」；
                // 放一张默认卡片当起点，语义上也没了"从老版本带来"的误导
                insertWidgetConfig(
                    WidgetConfig(
                        name = "默认卡片1",
                        content = "弱小和无知不是生存的障碍，傲慢才是。————刘慈欣《三体》",
                        sizeType = "4x2",
                        styleJson = WidgetStyle().toJsonString()
                    )
                )
            }
        }

        // 迁移只执行一次：用户有意清空全部配置后，不应在下次组件刷新时被重新"恢复"出来
        AppPrefs.setLegacyMigrationDone(appContext)
    }

    companion object {
        private const val DATABASE_NAME = "reminders.db"
        private const val DATABASE_VERSION = 7

        @Volatile
        private var instance: DbHelper? = null

        fun getInstance(context: Context): DbHelper {
            return instance ?: synchronized(this) {
                instance ?: DbHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}
