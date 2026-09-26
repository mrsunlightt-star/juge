package com.juge.app.pay

import android.app.Activity
import android.content.Context
import com.juge.app.account.AccountStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * PRO 会员购买流程编排：下单 → 拉起支付宝 → 服务端查单确认 → 交由调用方激活。
 *
 * 关键约束（支付宝官方）：客户端 resultStatus=9000 只是同步结果，
 * 必须以服务端查单 trade_status=TRADE_SUCCESS 为最终结论，才允许激活。
 */
object ProPurchase {

    /**
     * 商品 ID，对应服务端 application.yml 的 pay.products。
     * 真实金额由服务端商品目录决定，App 不参与定价。
     */
    const val PRODUCT_ID = "pro_permanent"

    /** 仅用于界面展示，必须与服务端商品目录中的价格保持一致 */
    const val PRICE_TEXT = "￥1.99"

    private const val PREFS = "pro_purchase_prefs"
    private const val KEY_PENDING_ORDER = "pending_order_no"

    /** 9000 之后的到账确认节奏 */
    private const val SUCCESS_ATTEMPTS = 5
    private const val SUCCESS_INTERVAL_MS = 1_200L

    /** 8000 / 6004 结果未知，需更耐心地查单 */
    private const val UNKNOWN_ATTEMPTS = 5
    private const val UNKNOWN_INTERVAL_MS = 2_000L

    sealed interface Outcome {
        /** 已确认到账，调用方应执行 TrialManager.activate() */
        data object Paid : Outcome

        /** 未完成支付（用户取消、到账确认中），不是错误 */
        data class Unpaid(val message: String) : Outcome

        /** 流程性失败（下单失败、网络异常） */
        data class Failed(val message: String) : Outcome
    }

    /**
     * 发起一次完整购买。内部会把阻塞的收银台调用切到 IO 线程，可在主线程调用。
     */
    suspend fun purchase(activity: Activity): Outcome {
        // 已登录则把令牌带上：PRO 会开到账号上，换机登录即可找回；
        // 未登录也能买，只是激活状态只留在本机
        val token = AccountStore.token(activity)
        val created = AlipayPayApi.createOrder(PRODUCT_ID, token).getOrElse { e ->
            Timber.e(e, "ProPurchase: 下单失败")
            return Outcome.Failed("下单失败，请检查网络后重试")
        }
        // 先落盘订单号：支付途中 App 被杀也能在下次启动时找回
        savePendingOrder(activity, created.outTradeNo)

        val resultStatus = withContext(Dispatchers.IO) {
            AlipayPay.pay(activity, created.orderStr)
        }

        val outcome = when (resultStatus) {
            AlipayPay.STATUS_SUCCESS ->
                if (confirmPaid(created.outTradeNo, SUCCESS_ATTEMPTS, SUCCESS_INTERVAL_MS)) {
                    Outcome.Paid
                } else {
                    Outcome.Unpaid("支付已提交，到账确认中，稍后重新打开应用会自动找回")
                }

            AlipayPay.STATUS_PROCESSING, AlipayPay.STATUS_UNKNOWN ->
                if (confirmPaid(created.outTradeNo, UNKNOWN_ATTEMPTS, UNKNOWN_INTERVAL_MS)) {
                    Outcome.Paid
                } else {
                    Outcome.Unpaid("支付处理中，稍后重新打开应用会自动找回")
                }

            AlipayPay.STATUS_CANCELED -> Outcome.Unpaid("已取消支付")
            AlipayPay.STATUS_DUPLICATE -> Outcome.Unpaid("订单重复，请稍后重试")
            AlipayPay.STATUS_FAILED -> Outcome.Failed("支付失败，请稍后重试")
            "" -> Outcome.Failed("未能唤起支付宝，请确认已安装支付宝")
            else -> Outcome.Failed("支付未完成（状态码 $resultStatus）")
        }

        if (outcome is Outcome.Paid) clearPendingOrder(activity)
        return outcome
    }

    /**
     * 找回：上次支付流程被中断（App 被杀、切后台）时，用留存订单号复查一次，
     * 兜底「已付款但没激活成功」的情况。无留存订单时直接返回 [Outcome.Unpaid]。
     */
    suspend fun recoverPending(context: Context): Outcome {
        val outTradeNo = pendingOrderNo(context) ?: return Outcome.Unpaid("")
        val status = AlipayPayApi.queryOrder(outTradeNo).getOrNull() ?: return Outcome.Unpaid("")
        return when {
            status.isPaid -> {
                clearPendingOrder(context)
                Outcome.Paid
            }
            // 订单已过期/不存在，继续留着只会反复查询
            status.isNotExist -> {
                clearPendingOrder(context)
                Outcome.Unpaid("")
            }
            else -> Outcome.Unpaid("")
        }
    }

    /** 轮询查单直到确认到账，用于抹平「支付成功但异步通知尚未落库」的时间差 */
    private suspend fun confirmPaid(outTradeNo: String, attempts: Int, intervalMs: Long): Boolean {
        repeat(attempts) { index ->
            if (AlipayPayApi.queryOrder(outTradeNo).getOrNull()?.isPaid == true) return true
            if (index < attempts - 1) delay(intervalMs)
        }
        return false
    }

    private fun pendingOrderNo(context: Context): String? =
        prefs(context).getString(KEY_PENDING_ORDER, null)

    private fun savePendingOrder(context: Context, outTradeNo: String) {
        prefs(context).edit().putString(KEY_PENDING_ORDER, outTradeNo).apply()
    }

    private fun clearPendingOrder(context: Context) {
        prefs(context).edit().remove(KEY_PENDING_ORDER).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}