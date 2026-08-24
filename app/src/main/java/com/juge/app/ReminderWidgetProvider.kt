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
import com.juge.app.data.TrialManager
import com.juge.app.data.WidgetStyle
import com.juge.app.data.WidgetConfig
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

open class ReminderWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (ACTION_MIUI_UPDATE == action) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            if (appWidgetIds != null) {
                onUpdate(context, appWidgetManager, appWidgetIds)
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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            clearWidgetData(context, id)
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
        private const val PREFS_WIDGET_BINDINGS = "widget_bindings_prefs"
        // goAsync 持有广播的安全上限，需小于系统广播超时（约 10s），留出余量
        private const val BROADCAST_TIMEOUT_MS = 8_000L
        private const val KEY_WIDGET_CONFIG_ID = "widget_config_id_"
        private const val KEY_WIDGET_STYLE = "widget_style_"

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

        // 绑定组件对应的微件配置 ID
        fun bindConfigToWidget(context: Context, appWidgetId: Int, configId: Long) {
            context.getSharedPreferences(PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_WIDGET_CONFIG_ID + appWidgetId, configId)
                .apply()
        }

        // 兼容原有名言一键推送至桌面小组件的功能
        fun bindReminderToWidget(context: Context, appWidgetId: Int, reminderId: Long) {
            val dbHelper = DbHelper.getInstance(context)
            val reminder = dbHelper.getReminderById(reminderId) ?: return

            val configId = getBoundConfigId(context, appWidgetId)
            val boundConfig = dbHelper.getWidgetConfigById(configId)

            if (boundConfig != null) {
                dbHelper.updateWidgetConfig(
                    boundConfig.copy(
                        content = reminder.content,
                        styleJson = reminder.styleJson ?: boundConfig.styleJson
                    )
                )
            } else {
                val newId = dbHelper.insertWidgetConfig(
                    WidgetConfig(
                        name = "微件 ${appWidgetId}",
                        content = reminder.content,
                        // 记录组件实际网格尺寸（getWidgetSizeString 返回 "4*4" 形式，转为配置表使用的 "4x4"）
                        sizeType = getWidgetSizeString(context, appWidgetId).replace("*", "x"),
                        styleJson = reminder.styleJson ?: WidgetStyle().toJsonString()
                    )
                )
                if (newId != -1L) {
                    bindConfigToWidget(context, appWidgetId, newId)
                }
            }
        }

        fun getBoundConfigId(context: Context, appWidgetId: Int): Long {
            return context.getSharedPreferences(PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE)
                .getLong(KEY_WIDGET_CONFIG_ID + appWidgetId, -1L)
        }

        // 写入组件的独立样式
        fun saveWidgetStyle(context: Context, appWidgetId: Int, style: WidgetStyle) {
            context.getSharedPreferences(PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_WIDGET_STYLE + appWidgetId, style.toJsonString())
                .apply()
        }

        // 读取组件的独立样式
        fun getWidgetStyle(context: Context, appWidgetId: Int, fallbackConfigStyleJson: String?): WidgetStyle {
            val prefs = context.getSharedPreferences(PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE)
            val savedStyleJson = prefs.getString(KEY_WIDGET_STYLE + appWidgetId, null)
            return if (savedStyleJson != null) {
                WidgetStyle.fromJsonString(savedStyleJson)
            } else {
                WidgetStyle.fromJsonString(fallbackConfigStyleJson)
            }
        }

        // 清空小组件的所有绑定和样式缓存
        fun clearWidgetData(context: Context, appWidgetId: Int) {
            context.getSharedPreferences(PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_WIDGET_CONFIG_ID + appWidgetId)
                .remove(KEY_WIDGET_STYLE + appWidgetId)
                .apply()
        }

        /**
         * 同步渲染并更新单个小组件。包含数据库与位图操作，必须在后台线程调用；
         * 主线程请使用 [updateAppWidgetAsync]。
         */
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val trialManager = TrialManager.getInstance(context)

            // 检查隐私政策是否已同意
            val isPrivacyAccepted = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getBoolean("privacy_accepted", false)

            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 150)
            val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 150)

            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            var currentConfigId = -1L

            val bitmap = if (!isPrivacyAccepted) {
                // 未同意隐私政策：渲染合规兜底文本，不读取本地数据库
                WidgetCanvasRenderer.render(
                    context = context,
                    widthDp = minWidthDp,
                    heightDp = minHeightDp,
                    content = "请先打开应用同意《隐私政策》与《用户协议》",
                    style = WidgetStyle(), // 默认极简风格
                    trialManager = trialManager
                )
            } else {
                val dbHelper = DbHelper.getInstance(context)
                // 触发老数据升级检测迁移，确保数据已从 reminders 导入到 widget_config
                dbHelper.migrateLegacyDataIfNeeded()

                currentConfigId = getBoundConfigId(context, appWidgetId)
                var config = if (currentConfigId != -1L) {
                    dbHelper.getWidgetConfigById(currentConfigId)
                } else {
                    null
                }

                if (config == null) {
                    // 未绑定或绑定的配置已被删除：优先分发"当前尚未被桌面其他小组件绑定"的配置，避免重复
                    val configs = dbHelper.getAllWidgetConfigs()
                    if (configs.isEmpty()) return
                    val allIds = getAllAppWidgetIds(context)
                    val boundConfigIds = allIds.filter { it != appWidgetId }
                        .map { getBoundConfigId(context, it) }
                        .toSet()
                    config = configs.find { it.id !in boundConfigIds } ?: configs.firstOrNull() ?: return
                }

                if (currentConfigId != config.id) {
                    bindConfigToWidget(context, appWidgetId, config.id)
                    // 换绑后清除旧的组件级样式覆盖：避免上一个配置的样式残留在新配置上继续优先生效，
                    // 新配置以自身 styleJson 作为样式来源（保存路径会同时写 config 与组件样式，数据不丢）
                    context.getSharedPreferences(PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE)
                        .edit()
                        .remove(KEY_WIDGET_STYLE + appWidgetId)
                        .apply()
                    currentConfigId = config.id
                }

                // 获取绑定的样式配置
                val style = getWidgetStyle(context, appWidgetId, config.styleJson)

                // 使用 Canvas 渲染生成 Bitmap
                WidgetCanvasRenderer.render(
                    context = context,
                    widthDp = minWidthDp,
                    heightDp = minHeightDp,
                    content = config.content,
                    style = style,
                    trialManager = trialManager
                )
            }

            views.setImageViewBitmap(R.id.widget_image_view, bitmap)

            // 设置点击事件：启动半透明快捷编辑面板 QuickAdjustActivity
            val clickIntent = Intent(context, QuickAdjustActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
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
         * 获取桌面上所有的小组件 ID (包括 4*2 和 4*4 两种尺寸)
         */
        fun getAllAppWidgetIds(context: Context): IntArray {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val provider4x2 = android.content.ComponentName(context, ReminderWidgetProvider::class.java)
            val provider4x4 = android.content.ComponentName(context, ReminderWidgetProvider4x4::class.java)
            val ids4x2 = appWidgetManager.getAppWidgetIds(provider4x2)
            val ids4x4 = appWidgetManager.getAppWidgetIds(provider4x4)
            return (ids4x2 + ids4x4).distinct().toIntArray()
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
