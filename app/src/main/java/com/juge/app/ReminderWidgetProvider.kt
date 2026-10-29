package com.juge.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import com.juge.app.data.DbHelper
import com.juge.app.data.WidgetStyle
import com.juge.app.data.WidgetConfig
import com.juge.app.data.WidgetConfigBinding
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

open class ReminderWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (ACTION_MIUI_UPDATE == action) {
            // 这个自定义 action 任何应用都能发：receiver 必须 exported=true 才能收到系统的
            // APPWIDGET_UPDATE，而自定义 action 与系统 action 共用同一个入口。
            // 所以不信任广播里自带的组件 ID，只保留「系统当前确实登记在本应用名下」的那些，
            // 否则第三方应用就能用伪造/随机 ID 让本应用做无意义的读库与位图渲染。
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val requested = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            if (requested != null) {
                val owned = getAllAppWidgetIds(context).toSet()
                val ids = requested
                    .take(MAX_BROADCAST_WIDGET_IDS)
                    .filter { it in owned }
                    .toIntArray()
                if (ids.isNotEmpty()) {
                    onUpdate(context, appWidgetManager, ids)
                }
            }
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        dispatchAsync(context) { ctx, manager ->
            for (appWidgetId in appWidgetIds) {
                try {
                    updateAppWidget(ctx, manager, appWidgetId)
                } catch (e: Exception) {
                    Timber.e(e, "onUpdate failed for widget %d", appWidgetId)
                }
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // 桌面缩放组件时会连续触发多次，统一走渲染队列避免主线程卡顿
        dispatchAsync(context) { ctx, manager ->
            try {
                updateAppWidget(ctx, manager, appWidgetId)
            } catch (e: Exception) {
                Timber.e(e, "onAppWidgetOptionsChanged failed for widget %d", appWidgetId)
            }
        }
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        synchronized(bindingLock) {
            if (oldWidgetIds.size != newWidgetIds.size || oldWidgetIds.any { it <= 0 } ||
                newWidgetIds.any { it <= 0 } || oldWidgetIds.toSet().size != oldWidgetIds.size ||
                newWidgetIds.toSet().size != newWidgetIds.size || newWidgetIds.isEmpty()
            ) {
                Timber.w("Ignoring invalid widget restore mapping")
                return
            }
            val manager = AppWidgetManager.getInstance(context)
            if (newWidgetIds.all { manager.getAppWidgetOptions(it).getBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED) }) return
            val prefs = context.getSharedPreferences(PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE)
            val values = prefs.all
            val savedTargets = prefs.getStringSet(KEY_RESTORE_TARGETS, emptySet()).orEmpty()
            val continuing = newWidgetIds.any { it.toString() in savedTargets }
            val bindings = readBindings(values)
            val source = if (continuing) readBindings(values, RESTORE_SOURCE_PREFIX) else bindings
            val restored = if (continuing) {
                prefs.getStringSet(KEY_RESTORED_IDS, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
            } else emptySet()
            val targets = if (continuing) savedTargets else (pendingRestoreIds(context) + newWidgetIds.toSet()).map { it.toString() }.toSet()
            val remapped = WidgetConfigBinding.remap(bindings, oldWidgetIds, newWidgetIds, source, restored)
            val editor = prefs.edit()
            values.keys.filter { it.startsWith(KEY_WIDGET_CONFIG_ID) || it.startsWith(KEY_WIDGET_STYLE) }
                .forEach { editor.remove(it) }
            remapped.forEach { (id, binding) ->
                binding.configId?.let { editor.putLong(KEY_WIDGET_CONFIG_ID + id, it) }
                binding.styleJson?.let { editor.putString(KEY_WIDGET_STYLE + id, it) }
            }
            if (!continuing) {
                values.keys.filter { it.startsWith(RESTORE_SOURCE_PREFIX) }.forEach { editor.remove(it) }
                source.forEach { (id, binding) ->
                    binding.configId?.let { editor.putLong(RESTORE_SOURCE_PREFIX + KEY_WIDGET_CONFIG_ID + id, it) }
                    binding.styleJson?.let { editor.putString(RESTORE_SOURCE_PREFIX + KEY_WIDGET_STYLE + id, it) }
                }
            }
            val completed = (restored + newWidgetIds.toSet()).map { it.toString() }.toSet()
            editor.putStringSet(KEY_RESTORE_TARGETS, targets)
                .putStringSet(KEY_RESTORED_IDS, completed)
            check(editor.commit()) { "Unable to restore widget bindings" }
            newWidgetIds.forEach { id ->
                manager.updateAppWidgetOptions(id, Bundle().apply {
                    putBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED, true)
                })
            }
            if (completed.containsAll(targets)) {
                val cleanup = prefs.edit().remove(KEY_RESTORE_TARGETS).remove(KEY_RESTORED_IDS)
                prefs.all.keys.filter { it.startsWith(RESTORE_SOURCE_PREFIX) }.forEach { cleanup.remove(it) }
                check(cleanup.commit()) { "Unable to finish widget restore" }
                if (restored.isNotEmpty()) triggerUpdateAllWidgets(context)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            clearWidgetData(context, id)
        }
        // 清绑定后回收留下的孤儿配置行（只回收本 Provider 自动创建的那种，见 pruneOrphanAutoConfigs）
        val appContext = context.applicationContext
        renderExecutor.execute {
            try {
                pruneOrphanAutoConfigs(appContext)
            } catch (e: Exception) {
                Timber.w(e, "pruneOrphanAutoConfigs failed after onDeleted")
            }
        }
    }

    /**
     * 广播回调默认在主线程执行，渲染（数据库读取 + Canvas 绘制）放后台完成，
     * 通过 goAsync() 持住广播直到渲染结束。
     * 渲染队列排队或数据库变慢时，超时强制结束广播，避免超出系统广播时限引发 ANR/超时异常；
     * 超时后队列中的渲染仍会继续执行，只是不再持有广播。
     */
    private fun dispatchAsync(context: Context, block: (Context, AppWidgetManager) -> Unit) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        val finishOnce = Runnable {
            if (finished.compareAndSet(false, true)) {
                try {
                    pendingResult.finish()
                } catch (_: Exception) {
                }
            }
        }
        val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
        timeoutHandler.postDelayed(finishOnce, BROADCAST_TIMEOUT_MS)
        renderExecutor.execute {
            try {
                val manager = AppWidgetManager.getInstance(appContext)
                block(appContext, manager)
            } catch (e: Exception) {
                Timber.e(e, "widget dispatch failed")
            } finally {
                timeoutHandler.removeCallbacks(finishOnce)
                finishOnce.run()
            }
        }
    }

    companion object {
        private const val ACTION_MIUI_UPDATE = "miui.appwidget.action.APPWIDGET_UPDATE"
        /** 组件绑定与组件级样式缓存所在 prefs。CropImageHelper 清理背景图时也按它查引用 */
        internal const val PREFS_WIDGET_BINDINGS = "widget_bindings_prefs"
        // goAsync 持有广播的安全上限，需小于系统广播超时（约 10s），留出余量
        private const val BROADCAST_TIMEOUT_MS = 8_000L
        private const val KEY_WIDGET_CONFIG_ID = "widget_config_id_"
        private const val KEY_WIDGET_STYLE = "widget_style_"
        private const val KEY_RESTORE_TARGETS = "restore_targets"
        private const val KEY_RESTORED_IDS = "restored_ids"
        private const val RESTORE_SOURCE_PREFIX = "restore_source_"
        private val bindingLock = Any()
        // 公开广播可能被伪造，限制一次最多处理多少个组件 ID
        private const val MAX_BROADCAST_WIDGET_IDS = 64

        // 两个入口声明的默认尺寸（dp）：与 xml 里的 minWidth/minHeight 保持一致。
        // 4 列 = 4×70-30 = 250dp，2 行 = 110dp，4 行 = 250dp
        private val DECLARED_COMPACT_SIZE_DP = 250 to 110
        private val DECLARED_BIG_SIZE_DP = 250 to 250

        // 单线程队列：多个组件依次渲染，避免并发解码位图导致内存峰值过高
        private val renderExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
            Thread(r, "widget-render").apply { isDaemon = true }
        }

        /**
         * 根据 appWidgetId 的 options 估算桌面的网格尺寸 (如 "4*2", "4*3", "4*4")
         */
        fun getWidgetSizeString(context: Context, appWidgetId: Int): String {
            if (appWidgetId == -1) return "4*2"
            return try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                if (minWidth <= 0 || minHeight <= 0) {
                    "4*2"
                } else {
                    val spanX = ((minWidth + 30) / 70).coerceAtLeast(1)
                    val spanY = ((minHeight + 30) / 70).coerceAtLeast(1)
                    "${spanX}*${spanY}"
                }
            } catch (e: Exception) {
                "4*2"
            }
        }

        /**
         * 组件当前的实际尺寸（dp）。用于让裁剪框比例与组件真实宽高比一致，
         * 避免用户在固定比例框里裁图、桌面上却显示成另一种比例。
         */
        fun getWidgetSizeDp(context: Context, appWidgetId: Int): Pair<Int, Int> {
            if (appWidgetId == -1) return 250 to 110
            return try {
                val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
                val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                if (w <= 0 || h <= 0) 250 to 110 else w to h
            } catch (e: Exception) {
                250 to 110
            }
        }

        /**
         * 组件**声明的默认尺寸**（dp）：主入口 4×4 → 250×250，紧凑入口 4×2 → 250×110，
         * 与 widget_info.xml / widget_info_compact.xml 里的 minWidth/minHeight 一致。
         *
         * ⚠️ **App 内预览已不再用它**（2026-10-08 起预览按风格自己的最佳显示尺寸出图，
         * 见 `WidgetStyle.bestDisplaySize`）：同一个风格从哪个入口添加、在桌面上拉成多大，
         * 预览都不再跟着变。这里保留两个入口的声明尺寸映射，目前只有回归测试
         * （WidgetDeclaredSizeTest）在调用，留作后续「按入口提示尺寸」类功能的落点。
         */
        fun getWidgetDeclaredSizeDp(context: Context, appWidgetId: Int): Pair<Int, Int> {
            if (appWidgetId == -1) return DECLARED_COMPACT_SIZE_DP
            // 问不到入口归属（系统查询失败、组件刚添加还没登记）时按实时行数猜：
            // 3 行及以上当作 4×4 入口
            val fromCompactEntry = isCompactEntryWidget(context, appWidgetId)
                ?: (liveSpanY(context, appWidgetId) < 3)
            return if (fromCompactEntry) DECLARED_COMPACT_SIZE_DP else DECLARED_BIG_SIZE_DP
        }

        /**
         * 组件来自哪个入口：两个入口的已放置组件列表由系统分别维护，
         * 按 ID 落在哪个列表里即可判定；两个列表都没有这个 ID 时返回 null。
         */
        private fun isCompactEntryWidget(context: Context, appWidgetId: Int): Boolean? = try {
            val manager = AppWidgetManager.getInstance(context)
            val compactIds = manager.getAppWidgetIds(
                android.content.ComponentName(context, ReminderWidgetProviderCompact::class.java)
            )
            when {
                compactIds.contains(appWidgetId) -> true
                manager.getAppWidgetIds(android.content.ComponentName(context, ReminderWidgetProvider::class.java))
                    .contains(appWidgetId) -> false
                else -> null
            }
        } catch (e: Exception) {
            null
        }

        /** 组件在桌面上的实时行数；问不到按 2 行（4×2 的声明落位） */
        private fun liveSpanY(context: Context, appWidgetId: Int): Int =
            getWidgetSizeString(context, appWidgetId).split("*").getOrNull(1)?.toIntOrNull() ?: 2

        /** 组件声明尺寸的网格写法（"4*2" / "4*4"），与 [getWidgetSizeString] 同一套换算 */
        fun getWidgetDeclaredSizeString(context: Context, appWidgetId: Int): String {
            val (w, h) = getWidgetDeclaredSizeDp(context, appWidgetId)
            return "${(w + 30) / 70}*${(h + 30) / 70}"
        }

        fun ensureIndependentConfig(
            context: Context,
            appWidgetId: Int,
            preferredConfigId: Long = -1L
        ): WidgetConfig? = synchronized(bindingLock) {
            if (appWidgetId <= 0 || hasPendingRestore(context)) return@synchronized null
            val db = DbHelper.getInstance(context)
            val prefs = context.getSharedPreferences(PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE)
            val bindings = readBindings(prefs.all)
            val currentId = bindings[appWidgetId]?.configId
            val current = currentId?.let(db::getWidgetConfigById)
            val source = current ?: db.getWidgetConfigById(preferredConfigId) ?: run {
                val configs = db.getAllWidgetConfigs()
                val boundIds = bindings.values.mapNotNull { it.configId }.toSet()
                configs.firstOrNull { it.id !in boundIds } ?: configs.firstOrNull()
            } ?: return@synchronized null
            bindIndependentConfig(context, appWidgetId, source, bindings, current != null)
        }

        private fun bindIndependentConfig(
            context: Context,
            appWidgetId: Int,
            source: WidgetConfig,
            bindings: Map<Int, WidgetConfigBinding.Binding>,
            preserveStyle: Boolean
        ): WidgetConfig? {
            val db = DbHelper.getInstance(context)
            val styleJson = bindings[appWidgetId]?.styleJson.takeIf { preserveStyle }
            val shared = WidgetConfigBinding.isShared(source.id, appWidgetId, bindings)
            if (!shared && preserveStyle && bindings[appWidgetId]?.configId == source.id) return source
            val config = if (shared) {
                db.cloneWidgetConfigForBinding(source.id, appWidgetId, styleJson) ?: return null
            } else source
            val editor = context.getSharedPreferences(PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_WIDGET_CONFIG_ID + appWidgetId, config.id)
            if (!preserveStyle) editor.remove(KEY_WIDGET_STYLE + appWidgetId)
            check(editor.commit()) { "Unable to persist widget binding" }
            return config
        }

        fun bindConfigToWidget(context: Context, appWidgetId: Int, configId: Long) {
            synchronized(bindingLock) {
                if (appWidgetId <= 0 || hasPendingRestore(context)) return
                val source = DbHelper.getInstance(context).getWidgetConfigById(configId) ?: return
                val bindings = readBindings(context.getSharedPreferences(PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE).all)
                bindIndependentConfig(context, appWidgetId, source, bindings, bindings[appWidgetId]?.configId == configId)
            }
        }

        fun bindReminderToWidget(context: Context, appWidgetId: Int, reminderId: Long) {
            synchronized(bindingLock) {
                if (appWidgetId <= 0 || hasPendingRestore(context)) return
                val db = DbHelper.getInstance(context)
                val reminder = db.getReminderById(reminderId) ?: return
                val prefs = context.getSharedPreferences(PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE)
                val bindings = readBindings(prefs.all)
                val existing = bindings[appWidgetId]?.configId?.let(db::getWidgetConfigById)
                val config = if (existing != null) {
                    ensureIndependentConfig(context, appWidgetId) ?: return
                } else {
                    val boundIds = bindings.values.mapNotNull { it.configId }.toSet()
                    val reusable = WidgetConfigBinding.findReusableAutoConfig(db.getAllWidgetConfigs(), boundIds)
                    val source = reusable ?: run {
                        val id = db.insertWidgetConfig(
                            WidgetConfig(
                                name = "${WidgetConfigBinding.AUTO_CONFIG_NAME_PREFIX}$appWidgetId",
                                content = reminder.content,
                                sizeType = getWidgetSizeString(context, appWidgetId).replace("*", "x"),
                                styleJson = reminder.styleJson ?: WidgetStyle.DEFAULT.toJsonString(),
                                autoCreated = true
                            )
                        )
                        db.getWidgetConfigById(id) ?: return
                    }
                    bindIndependentConfig(context, appWidgetId, source, bindings, false) ?: return
                }
                val styleJson = reminder.styleJson ?: getWidgetStyle(context, appWidgetId, config.styleJson).toJsonString()
                db.updateWidgetConfig(config.copy(content = reminder.content, styleJson = styleJson))
                prefs.edit().putString(KEY_WIDGET_STYLE + appWidgetId, styleJson).apply()
            }
        }

        fun getBoundConfigId(context: Context, appWidgetId: Int): Long = synchronized(bindingLock) {
            context.getSharedPreferences(PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE)
                .getLong(KEY_WIDGET_CONFIG_ID + appWidgetId, -1L)
        }

        fun saveWidgetStyle(context: Context, appWidgetId: Int, style: WidgetStyle) {
            synchronized(bindingLock) {
                if (appWidgetId <= 0 || hasPendingRestore(context)) return
                context.getSharedPreferences(PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_WIDGET_STYLE + appWidgetId, style.toJsonString())
                    .apply()
            }
        }

        fun getWidgetStyle(context: Context, appWidgetId: Int, fallbackConfigStyleJson: String?): WidgetStyle =
            synchronized(bindingLock) {
                val prefs = context.getSharedPreferences(PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE)
                WidgetStyle.fromJsonString(prefs.getString(KEY_WIDGET_STYLE + appWidgetId, fallbackConfigStyleJson))
            }

        fun clearWidgetData(context: Context, appWidgetId: Int) {
            synchronized(bindingLock) {
                if (hasPendingRestore(context)) return
                context.getSharedPreferences(PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_WIDGET_CONFIG_ID + appWidgetId)
                    .remove(KEY_WIDGET_STYLE + appWidgetId)
                    .apply()
            }
        }

        private fun readBindings(values: Map<String, *>, prefix: String = ""): Map<Int, WidgetConfigBinding.Binding> {
            val ids = values.keys.mapNotNull { key ->
                when {
                    key.startsWith(prefix + KEY_WIDGET_CONFIG_ID) -> key.removePrefix(prefix + KEY_WIDGET_CONFIG_ID).toIntOrNull()
                    key.startsWith(prefix + KEY_WIDGET_STYLE) -> key.removePrefix(prefix + KEY_WIDGET_STYLE).toIntOrNull()
                    else -> null
                }
            }.toSet()
            return ids.associateWith { id ->
                WidgetConfigBinding.Binding(
                    (values[prefix + KEY_WIDGET_CONFIG_ID + id] as? Long)?.takeIf { it > 0L },
                    values[prefix + KEY_WIDGET_STYLE + id] as? String
                )
            }
        }

        private fun pendingRestoreIds(context: Context): Set<Int> {
            val manager = AppWidgetManager.getInstance(context)
            return getAllAppWidgetIds(context).filter { id ->
                val options = manager.getAppWidgetOptions(id)
                options.containsKey(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED) &&
                    !options.getBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED)
            }.toSet()
        }

        private fun hasPendingRestore(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE)
            val targets = prefs.getStringSet(KEY_RESTORE_TARGETS, emptySet()).orEmpty()
            val completed = prefs.getStringSet(KEY_RESTORED_IDS, emptySet()).orEmpty()
            return targets.any { it !in completed } || pendingRestoreIds(context).isNotEmpty()
        }

        /**
         * 回收孤儿配置行：名称是本 Provider 自动生成的、且当前没有任何组件绑定。
         *
         * 只碰自动生成的行，用户手动创建/旧数据迁移出来的配置一律不动，避免误删内容。
         * 组件被删除时调用一次，防止「加一个组件再删掉」反复累积无主配置。
         */
        internal fun pruneOrphanAutoConfigs(context: Context) {
            synchronized(bindingLock) {
                if (hasPendingRestore(context)) return
                val dbHelper = DbHelper.getInstance(context)
                val values = context.getSharedPreferences(PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE).all
                val boundIds = (readBindings(values).values + readBindings(values, RESTORE_SOURCE_PREFIX).values)
                    .mapNotNull { it.configId }.toSet()
                val orphans = WidgetConfigBinding.orphanIds(dbHelper.getAllWidgetConfigs(), boundIds)
                for (id in orphans) {
                    dbHelper.deleteWidgetConfig(id)
                    Timber.i("pruned orphan widget config id=%d", id)
                }
            }
        }

        /**
         * 同步渲染并更新单个小组件。包含数据库与位图操作，必须在后台线程调用；
         * 主线程请使用 [updateAppWidgetAsync]。
         */
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            if (synchronized(bindingLock) { hasPendingRestore(context) }) return
            // 检查隐私政策是否已同意（consent_prefs，不参与系统备份）
            val isPrivacyAccepted = com.juge.app.data.AppPrefs.isPrivacyAccepted(context)

            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 150)
            val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 150)
            // 位图按 MIN 尺寸渲染，而桌面把位图按 fitXY 拉满整个组件视图：
            // 两者比例不一致时画面会被非等比拉伸，卡片描边（尤其深色卡片上的白边）会沿拉伸方向变粗。
            // 打点记下实际用的尺寸与比例，便于和桌面实测的组件尺寸对账。
            // OPTION_APPWIDGET_SIZES（API 31+）是 launcher 声明的候选尺寸列表——
            // 实测（PJF110 / ColorOS）它与 MIN/MAX 一致、却按更大尺寸布局，所以拿不到真实视图尺寸；
            // 留着这条日志是为了下次出现"画面被拉扁/拉长"时能一眼看出是哪个数在变。
            @Suppress("DEPRECATION")
            val declaredSizes = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                options.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                    ?.joinToString(",") { "${it.width.toInt()}x${it.height.toInt()}" }
            } else {
                null
            }
            Timber.i(
                "widget render id=%d  %ddp x %ddp  ratio=%.3f  max=%dx%d  sizes=[%s]",
                appWidgetId, minWidthDp, minHeightDp,
                minWidthDp.toFloat() / minHeightDp.coerceAtLeast(1),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0),
                declaredSizes ?: "-"
            )

            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            var currentConfigId = -1L

            val bitmap = if (!isPrivacyAccepted) {
                // 未同意隐私政策：渲染合规兜底文本，不读取本地数据库
                WidgetCanvasRenderer.renderForWidget(
                    context = context,
                    widthDp = minWidthDp,
                    heightDp = minHeightDp,
                    content = "请先打开应用同意《隐私政策》与《用户协议》",
                    style = WidgetStyle.DEFAULT // 默认样式：纯色圆角
                )
            } else {
                val dbHelper = DbHelper.getInstance(context)
                // 触发老数据升级检测迁移，确保数据已从 reminders 导入到 widget_config
                dbHelper.migrateLegacyDataIfNeeded()

                val config = ensureIndependentConfig(context, appWidgetId)
                if (config == null) {
                    if (synchronized(bindingLock) { hasPendingRestore(context) }) return
                    val fallback = WidgetCanvasRenderer.renderForWidget(
                        context = context,
                        widthDp = minWidthDp,
                        heightDp = minHeightDp,
                        content = "打开句阁，添加你的第一条金句",
                        style = WidgetStyle.DEFAULT
                    )
                    views.setImageViewBitmap(R.id.widget_image_view, fallback)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    fallback.recycle()
                    return
                }
                currentConfigId = config.id

                // 获取绑定的样式配置
                val style = getWidgetStyle(context, appWidgetId, config.styleJson)

                // 使用 Canvas 渲染生成 Bitmap
                WidgetCanvasRenderer.renderForWidget(
                    context = context,
                    widthDp = minWidthDp,
                    heightDp = minHeightDp,
                    content = config.content,
                    style = style
                )
            }

            views.setImageViewBitmap(R.id.widget_image_view, bitmap)

            // 设置点击事件：直接打开 App 主界面，并带上 config_id 让主界面定位到这个组件。
            // 用 CLEAR_TASK 保证 MainActivity 一定重建：定位逻辑只在 onCreate 消费一次，
            // 复用已存在的实例不会重新定位，会停在用户上次停留的组件上。
            val clickIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("config_id", currentConfigId)
                // 确保每次 PendingIntent 都是独立的以带入正确参数
                data = Uri.parse("custom://$appWidgetId")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_image_view, pendingIntent)

            // 更新 Widget
            appWidgetManager.updateAppWidget(appWidgetId, views)

            // 回收 Bitmap 释放内存（RemoteViews 内部已拷贝，原始 Bitmap 可安全回收）
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }

        /**
         * 主线程安全的异步更新入口，渲染在后台队列完成。
         */
        fun updateAppWidgetAsync(context: Context, appWidgetId: Int) {
            val appContext = context.applicationContext
            renderExecutor.execute {
                try {
                    val manager = AppWidgetManager.getInstance(appContext)
                    updateAppWidget(appContext, manager, appWidgetId)
                } catch (e: Exception) {
                    Timber.e(e, "async update failed for widget %d", appWidgetId)
                }
            }
        }

        /**
         * 获取桌面上所有的小组件 ID（4×4 主入口 + 4×2 紧凑入口）
         */
        fun getAllAppWidgetIds(context: Context): IntArray {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val providerMain = android.content.ComponentName(context, ReminderWidgetProvider::class.java)
            val providerCompact = android.content.ComponentName(context, ReminderWidgetProviderCompact::class.java)
            val idsMain = appWidgetManager.getAppWidgetIds(providerMain)
            val idsCompact = appWidgetManager.getAppWidgetIds(providerCompact)
            return (idsMain + idsCompact).distinct().toIntArray()
        }

        // 提供外部刷新方法（异步，渲染在后台队列完成）
        fun triggerUpdateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            renderExecutor.execute {
                try {
                    val appWidgetManager = AppWidgetManager.getInstance(appContext)
                    val ids = getAllAppWidgetIds(appContext)
                    for (id in ids) {
                        try {
                            updateAppWidget(appContext, appWidgetManager, id)
                        } catch (e: Exception) {
                            Timber.e(e, "update failed for widget %d", id)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "triggerUpdateAllWidgets failed")
                }
            }
        }
    }
}
