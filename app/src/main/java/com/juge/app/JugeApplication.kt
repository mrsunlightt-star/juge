package com.juge.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 进程级预热：桌面组件点开的快捷面板（QuickAdjustActivity）与主界面风格列表
 * 都依赖 WidgetCanvasRenderer 的缩略图缓存。缩略图磁盘缓存跨进程存活，而
 * 「桌面 → 快捷面板」几乎总是冷进程进入——进程一启动就把磁盘缓存加载进内存，
 * 面板首帧即可带图。预热只在 IO 线程解码小图，不阻塞任何启动路径。
 */
class JugeApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            WidgetCanvasRenderer.prewarmThumbnailCache(this@JugeApplication)
        }
    }
}
