package com.juge.app.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * 全 App 的触感反馈。
 *
 * 质感的一半在指尖：iOS app 的每个选中、切换、确认都有系统触感在回应手指，
 * 没有触感的界面再漂亮也像隔着一层玻璃在操作。这里把框架的 HapticFeedbackConstants
 * 收口成四个语义动作，界面里不要直接摸 View：
 *
 * - [tap]：轻点一下。选中预设、点页签、点分类等「改变选中状态」的轻交互；
 * - [click]：普通按钮。次要按钮、链接类操作；
 * - [confirm]：主操作确认。保存、支付、应用到桌面——需要比 tap 更「实」的一下；
 * - [reject]：操作被拦下（付费墙、校验失败），用重而短的双击感表达「不行」。
 *
 * 全部走 View.performHapticFeedback：不申请振动权限、跟随系统的触感强度设置，
 * 关闭触感的用户不会被打扰。CONFIRM/REJECT 需要 API 30+，低版本退回等价常量。
 */
class AppHaptics(private val view: View) {

    /** 轻点：选中/切换/翻页 */
    fun tap() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** 普通按钮按下 */
    fun click() {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** 主操作确认：保存/支付/应用 */
    fun confirm() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(constant)
    }

    /** 被拦下/失败：重而短，表达「不行」 */
    fun reject() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }
}

/** 在组合里取 [AppHaptics]：clickable 的 onClick 里直接 `haptics.tap()` 一行接入 */
@Composable
fun rememberAppHaptics(): AppHaptics {
    val view = LocalView.current
    return remember(view) { AppHaptics(view) }
}
