package com.juge.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * 从 Compose 里的 Context 找回宿主 Activity。
 *
 * 组合里的 Context 可能是被包装过的（ContextWrapper 链），需要逐层剥开才能拿到 Activity；
 * 找不到时返回 null，调用方自行决定降级行为。
 */
internal tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}
