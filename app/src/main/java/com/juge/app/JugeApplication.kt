package com.juge.app

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 进程级预热：主界面的风格列表与小组件预览都依赖 WidgetCanvasRenderer 的缩略图缓存。
 * 缩略图磁盘缓存跨进程存活，而「桌面组件 → App」几乎总是冷进程进入——
 * 进程一启动就把磁盘缓存加载进内存，主界面首帧即可带图。
 * 预热只在 IO 线程解码小图，不阻塞任何启动路径。
 */
class JugeApplication : Application() {

    /**
     * 进程级作用域：Application 存活期间不取消。PersistText 落库等
     * 「比 Activity 生命周期更长寿、但不能永不取消」的写入用它承载——
     * 挂在 Activity 的 lifecycleScope 上时，返回键销毁 Activity 的那一下
     * 恰好会把尚未落库的防抖协程一起取消掉。
     */
    internal val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        plantLogging()
        // 崩溃现场落盘：release 没有崩溃上报，用户报「用着用着就退」
        // 时手机上的 logcat 早已滚掉，只剩这一份文件能当证据（见 CrashReporter）
        com.juge.app.data.CrashReporter.install(this)
        // 收银台/找回跑在这个 scope 上：Activity 重建不会掐掉在途流程（见 PayFlow）
        com.juge.app.pay.PayFlow.init(this)
        appScope.launch {
            WidgetCanvasRenderer.prewarmThumbnailCache(this@JugeApplication)
        }
        registerComponentCallbacks(object : ComponentCallbacks2 {
            /**
             * 系统内存吃紧时的第一响应点。
             *
             * 之前这里是空的（全仓 `onTrimMemory` / `onLowMemory` 零命中）：
             * 两块位图缓存（缩略图 32MB + 预设插画 24MB）只会等系统直接杀进程，
             * 而杀进程恰恰发生在桌面正在重画组件的时候。
             * Application 级的回调会在进程内任何时刻收到，比 Activity 级更可靠——
             * 组件渲染本来就不依赖任何 Activity 存活。
             */
            override fun onTrimMemory(level: Int) {
                WidgetCanvasRenderer.trimMemory(level)
            }

            /** API 24 以下没有 onTrimMemory 的 Application 级等价回调，只有这个老入口 */
            override fun onLowMemory() {
                WidgetCanvasRenderer.trimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit
        })
    }

    /**
     * Timber **必须显式 plant 才会输出任何东西**，而此前全项目没有调用过 plant——
     * 于是几十处 `catch { Timber.e(...) }` 的日志全部被静默丢弃，
     * 出问题时既看不到异常也看不到上下文，排障等于瞎的。
     *
     * debug 包全量输出；release 只留 WARN 以上（转发到 logcat），
     * 免得生产包里 Timer/IO 之类的信息把日志刷满。
     */
    private fun plantLogging() {
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) {
            Timber.plant(Timber.DebugTree())
            return
        }
        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                if (priority < Log.WARN) return
                val text = if (t != null) "$message\n${Log.getStackTraceString(t)}" else message
                Log.println(priority, tag ?: "JuGe", text)
            }
        })
    }
}
