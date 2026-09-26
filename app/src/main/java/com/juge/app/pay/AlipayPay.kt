package com.juge.app.pay

import android.app.Activity
import com.alipay.sdk.app.PayTask
import timber.log.Timber

/**
 * 支付宝收银台调用封装。
 *
 * [pay] 内部是阻塞调用，会一直等到用户在支付宝内完成或取消支付才返回，
 * **必须在子线程执行**（调用方见 [ProPurchase]）。
 *
 * resultStatus 只是客户端同步结果：9000 表示支付宝侧已确认支付成功，
 * 但订单是否真正到账仍需以服务端查单 / 异步通知为准。
 */
object AlipayPay {

    /** 支付成功 */
    const val STATUS_SUCCESS = "9000"

    /** 正在处理中，结果未知，必须主动查单确认 */
    const val STATUS_PROCESSING = "8000"

    /** 处理结果未知（未安装支付宝、网络异常等），必须主动查单确认 */
    const val STATUS_UNKNOWN = "6004"

    /** 用户中途取消 */
    const val STATUS_CANCELED = "6001"

    /** 支付失败 */
    const val STATUS_FAILED = "4000"

    /** 重复请求 */
    const val STATUS_DUPLICATE = "5000"

    /**
     * 拉起支付宝收银台并等待用户操作结束。
     *
     * @return resultStatus，取值见本类常量；调用异常时返回空串
     */
    fun pay(activity: Activity, orderStr: String): String {
        return try {
            val result = PayTask(activity).payV2(orderStr, true)
            result["resultStatus"].orEmpty()
        } catch (e: Exception) {
            Timber.e(e, "AlipayPay: payV2 调用异常")
            ""
        }
    }
}