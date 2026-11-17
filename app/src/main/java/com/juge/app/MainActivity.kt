package com.juge.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.juge.app.ui.legal.PrivacyPolicyDialog
import com.juge.app.ui.main.MainAppScreen
import com.juge.app.ui.theme.AppPalette
import com.juge.app.ui.theme.LocalAppDarkThemeProgress
import com.juge.app.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.juge.app.data.AppPrefs
import com.juge.app.data.DbHelper
import com.juge.app.data.TrialManager

class MainActivity : ComponentActivity() {

    companion object {
        private var _dbHelper: DbHelper? = null
        private var _trialManager: TrialManager? = null

        /** 桌面快捷方式（长按图标弹出）的 Intent action */
        const val ACTION_ADD_WIDGET = "com.juge.app.action.ADD_WIDGET"
    }

    /** 来自桌面快捷方式的待固定组件类型（big/compact），在 onResume 中消费 */
    private var pendingPinWidgetKind: String? = null

    /** 一键固定不可用时置 true：进 App 后自动切到「个性定制」并展开添加教程 */
    private var startOnAdjustTutorial = false

    private val dbHelper: DbHelper get() = _dbHelper!!
    private val trialManager: TrialManager get() = _trialManager!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (_dbHelper == null) {
            _dbHelper = DbHelper.getInstance(this)
            _trialManager = TrialManager.getInstance(this)
        }
        // 清理上次编辑残留的临时背景图（超 24h 未提交的），避免孤儿文件长期堆积
        lifecycleScope.launch(Dispatchers.IO) { CropImageHelper.cleanupTempBackgrounds(this@MainActivity) }

        // 夜色模式冷启动防闪：Compose 接管前先按持久化偏好铺窗口底和系统栏图标
        applySettledTheme(AppPrefs.isDarkTheme(this))

        val goToActivate = intent.getBooleanExtra("go_to_activate", false)
        val editConfigId = intent.getLongExtra("config_id", -1L)

        // 桌面长按图标的「添加组件」快捷方式入口：记下待固定组件，等 onResume 后再拉起系统确认框
        // （部分桌面在 Activity 尚未恢复时会忽略 requestPinAppWidget）
        pendingPinWidgetKind = intent?.takeIf { it.action == ACTION_ADD_WIDGET }?.getStringExtra("widget_kind")

        setContent {
            // 夜色模式：纯手动两态，点顶栏图标切换，偏好落 user_prefs（与 isPrivacyAccepted 同套路：
            // SharedPreferences 同步读 + 调用方自行 mutableStateOf 包快照）
            var isDarkTheme by remember { mutableStateOf(AppPrefs.isDarkTheme(this@MainActivity)) }
            val toggleDarkTheme: () -> Unit = {
                val next = !isDarkTheme
                isDarkTheme = next
                AppPrefs.setDarkTheme(this@MainActivity, next)
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                // 窗口底色和系统栏图标无法参与 Compose 的逐帧颜色动画。等调色板进度真正到终点后再同步，
                // 避免它们先跳到目标模式、内容层仍在渐变而出现一次明暗闪烁。
                val themeProgress = LocalAppDarkThemeProgress.current
                val targetProgress = if (isDarkTheme) 1f else 0f
                LaunchedEffect(isDarkTheme, themeProgress) {
                    if (themeProgress == targetProgress) applySettledTheme(isDarkTheme)
                }

                var isPrivacyAccepted by remember {
                    mutableStateOf(AppPrefs.isPrivacyAccepted(this@MainActivity))
                }

                PrivacyPolicyDialog(
                    show = !isPrivacyAccepted,
                    onAgree = {
                            AppPrefs.setPrivacyAccepted(this@MainActivity)
                            isPrivacyAccepted = true
                            // 刷新桌面小组件以恢复正常内容
                            ReminderWidgetProvider.triggerUpdateAllWidgets(this)
                        },
                    onReject = {
                        finish()
                    }
                )
                if (isPrivacyAccepted) {
                    MainAppScreen(
                        dbHelper = dbHelper,
                        trialManager = trialManager,
                        activity = this@MainActivity,
                        startOnActivate = goToActivate,
                        targetEditConfigId = editConfigId,
                        startOnAdjust = startOnAdjustTutorial,
                        startTutorialExpanded = startOnAdjustTutorial,
                        isDarkTheme = isDarkTheme,
                        onToggleDarkTheme = toggleDarkTheme
                    )
                }
            }
        }
    }

    /** 等主题过渡结束后同步窗口底和系统栏（themes.xml 的 windowBackground 是日间值） */
    private fun applySettledTheme(dark: Boolean) {
        window.setBackgroundDrawable(
            ColorDrawable((if (dark) AppPalette.Dark else AppPalette.Light).darkBg.toArgb())
        )
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = !dark
    }

    override fun onResume() {
        super.onResume()
        val kind = pendingPinWidgetKind ?: return
        pendingPinWidgetKind = null
        if (tryRequestPinWidget(kind)) return
        // 一键固定不可用（如 ColorOS 对白名单外的应用会静默拒绝固定请求）：
        // 自动切到「个性定制」页并展开添加教程，引导用户手动添加
        startOnAdjustTutorial = true
        Toast.makeText(this, "当前系统不支持一键添加，已为你打开桌面组件添加教程", Toast.LENGTH_LONG).show()
    }

    /**
     * 尝试拉起系统的固定小组件确认框。返回 true 表示请求已被桌面受理（确认框会展示），
     * false 表示该设备/桌面不支持（需要走教程引导的降级路径）。
     */
    private fun tryRequestPinWidget(widgetKind: String): Boolean {
        // requestPinAppWidget 需要 API 26+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        // ColorOS/OPPO 系桌面存在组件固定管控：仅系统应用、预装可卸载名单、
        // 持有 oplus.permission.OPLUS_COMPONENT_SAFE 特权或 RUS 云端白名单的应用可用，
        // 其它应用会被静默拒绝（连提示都不显示），因此直接走教程引导
        if (isOplusFamilyDevice()) return false
        val provider = if (widgetKind == "compact") {
            ComponentName(this, ReminderWidgetProviderCompact::class.java)
        } else {
            ComponentName(this, ReminderWidgetProvider::class.java)
        }
        return try {
            getSystemService(AppWidgetManager::class.java)?.requestPinAppWidget(provider, null, null) ?: false
        } catch (t: Throwable) {
            false
        }
    }

    /** 是否为 OPPO/OnePlus/realme 设备（共享同一套 OPlus 桌面与组件管控策略） */
    private fun isOplusFamilyDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER ?: return false
        return manufacturer.contains("oppo", ignoreCase = true) ||
            manufacturer.contains("oneplus", ignoreCase = true) ||
            manufacturer.contains("realme", ignoreCase = true)
    }

}
