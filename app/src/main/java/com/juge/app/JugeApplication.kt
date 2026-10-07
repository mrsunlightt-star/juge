package com.juge.app

import android.app.Application
import android.content.pm.ApplicationInfo
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        plantLogging()
        appScope.launch {
            WidgetCanvasRenderer.prewarmThumbnailCache(this@JugeApplication)
        }
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
