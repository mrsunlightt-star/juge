package com.juge.app.data

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃现场落盘。
 *
 * 为什么需要：此前全仓既没有崩溃上报、也没有 `Thread.setDefaultUncaughtExceptionHandler`，
 * 用户报「桌面组件突然没了」「点一下就退」时，手上唯一的证据是「复现不了」。
 * 而 release 包里 `Timber` 只留 WARN 以上、且只转发到 logcat——手机不在手上，
 * logcat 就等于不存在。
 *
 * 做法刻意选最朴素的一种：**不联网、不上传、不引第三方 SDK**，
 * 崩溃时往 `filesDir/crash/` 写一个小文本文件，只留最近 [MAX_REPORTS] 份。
 * 下一次启动，「PRO 会员」弹窗会多出「导出诊断信息 / 清除记录」两个入口
 * （只在确实有记录时出现），由用户决定把内容分享给客服邮箱或直接清掉。
 * 这样既不需要新增权限，也不违背隐私政策里「不收集设备标识」的承诺——
 * 文件里只有包名、版本号、系统版本、机型、堆栈，没有任何持久标识。
 */
internal object CrashReporter {

    private const val DIR_NAME = "crash"
    private const val MAX_REPORTS = 5
    private const val MAX_STACK_CHARS = 16_000

    /** 装上全局兜底；重复调用无副作用。会**保留**系统原有的 handler 并继续转发。 */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is PersistingHandler) return

        Thread.setDefaultUncaughtExceptionHandler(
            PersistingHandler(appContext, previous)
        )
    }

    /** 待导出的崩溃文件，按时间倒序。 */
    fun pendingReports(context: Context): List<File> =
        crashDir(context).listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?.take(MAX_REPORTS)
            ?: emptyList()

    /** 把待导出内容拼成一段文本，供分享 intent 使用。 */
    fun buildReport(context: Context): String = buildString {
        val reports = pendingReports(context)
        if (reports.isEmpty()) {
            append("没有记录到崩溃。\n")
        } else {
            reports.forEach { file ->
                append("===== ").append(file.name).append(" =====\n")
                append(runCatching { file.readText() }.getOrElse { "<读取失败：${it.message}>\n" })
                append('\n')
            }
        }
        append(deviceSummary(context))
    }

    /** 清空已落盘的崩溃记录（用户点「我已反馈过」时调用）。 */
    fun clear(context: Context) {
        crashDir(context).listFiles()?.forEach { runCatching { it.delete() } }
    }

    private class PersistingHandler(
        private val context: Context,
        private val previous: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {

        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            // 落盘失败绝不能盖掉原始崩溃：整个 writeCrash 包在 runCatching 里，
            // 之后无论成败都继续交给系统原 handler（它负责弹「应用已停止」并杀进程）。
            runCatching { writeCrash(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
                ?: run { android.os.Process.killProcess(android.os.Process.myPid()) }
        }

        private fun writeCrash(thread: Thread, throwable: Throwable) {
            val dir = crashDir(context)
            if (!dir.exists() && !dir.mkdirs()) return
            prune(dir)

            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
            val target = File(dir, "crash-$stamp.txt")
            val stack = StringWriter().also { writer ->
                PrintWriter(writer).use { throwable.printStackTrace(it) }
            }.toString().take(MAX_STACK_CHARS)

            target.writeText(
                buildString {
                    append("time=").append(stamp).append('\n')
                    append("thread=").append(thread.name).append('\n')
                    append("throwable=").append(throwable.javaClass.name).append('\n')
                    append(deviceSummary(context))
                    append("\n--- stack ---\n")
                    append(stack)
                }
            )
        }

        /** 只保留最近 MAX_REPORTS 份：崩溃循环时不能把用户存储写满。 */
        private fun prune(dir: File) {
            dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_REPORTS - 1)
                ?.forEach { runCatching { it.delete() } }
        }
    }

    private fun crashDir(context: Context) = File(context.filesDir, DIR_NAME)

    private fun deviceSummary(context: Context): String = buildString {
        val info: PackageInfo? = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        append("app=").append(context.packageName).append('\n')
        append("versionName=").append(info?.versionName ?: "?").append('\n')
        append("versionCode=").append(
            if (info == null) "?" else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toString()
            }
        ).append('\n')
        append("android=").append(Build.VERSION.RELEASE)
            .append(" (sdk ").append(Build.VERSION.SDK_INT).append(")\n")
        append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        append("abi=").append(Build.SUPPORTED_ABIS.joinToString(",")).append('\n')
    }
}
