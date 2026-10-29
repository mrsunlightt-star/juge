package com.juge.app.pay

import android.app.Activity
import android.content.Context
import com.juge.app.account.AccountStore
import com.juge.app.data.TrialManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/** 仅在服务端确认付款且本地激活落盘后返回 [Outcome.Paid]。 */
object ProPurchase {

    /** 商品 ID，真实金额由服务端商品目录决定，App 不参与定价。 */
    const val PRODUCT_ID = "pro_permanent"

    /** 仅用于界面展示，必须与服务端商品目录中的价格保持一致 */
    const val PRICE_TEXT = "￥1.99"

    private const val PREFS = "pro_purchase_prefs"
    private const val KEY_PENDING_ORDER = "pending_order_no"
    private const val SUCCESS_ATTEMPTS = 5
    private const val SUCCESS_INTERVAL_MS = 1_200L
    private const val UNKNOWN_ATTEMPTS = 5
    private const val UNKNOWN_INTERVAL_MS = 2_000L

    // 只锁本地队列的读改写，不在锁内等待网络或收银台。
    private val pendingLock = Any()
    private val recoveryMutex = Mutex()

    /** 每次调用独立注入，正式路径始终使用默认实现，无全局可变测试钩子。 */
    internal data class Dependencies(
        val createOrder: suspend (String, String?) -> Result<AlipayPayApi.OrderCreated> = AlipayPayApi::createOrder,
        val queryOrder: suspend (String, String?) -> Result<AlipayPayApi.OrderStatus> = AlipayPayApi::queryOrder,
        val pay: (Activity, String) -> String = AlipayPay::pay,
        val activate: (Context) -> Boolean = {
            TrialManager.getInstance(it).activate(TrialManager.PAY_METHOD_ALIPAY)
        },
    )

    sealed interface Outcome {
        /** 已确认到账且本地激活已持久化；调用方只刷新 UI / 组件，不再调用 activate。 */
        data object Paid : Outcome

        /** 未完成支付（用户取消、到账确认中），不是错误 */
        data class Unpaid(val message: String) : Outcome

        /** 流程性失败（下单失败、网络异常、本地持久化失败） */
        data class Failed(val message: String) : Outcome
    }

    /** 完整购买；阻塞的收银台调用和本地 commit 均在 IO 线程，可在主线程调用。 */
    suspend fun purchase(activity: Activity): Outcome = purchase(activity, Dependencies())

    internal suspend fun purchase(activity: Activity, dependencies: Dependencies): Outcome {
        // 未登录也能买，只是激活状态只留在本机。
        val token = AccountStore.token(activity)
        val created = dependencies.createOrder(PRODUCT_ID, token).getOrElse { e ->
            Timber.e(e, "ProPurchase: 下单失败")
            val serverMessage = (e as? AlipayPayApi.ApiException)?.message?.takeIf { it.isNotBlank() }
            return Outcome.Failed(serverMessage ?: "下单失败，请检查网络后重试")
        }
        // 必须在唤支付前确认落盘；失败时不能让用户付款后失去唯一的找回凭据。
        val saved = withContext(Dispatchers.IO) { appendPendingOrder(activity, created.outTradeNo) }
        if (!saved) return Outcome.Failed("订单保存失败，请检查存储空间后重试，尚未发起支付")

        val resultStatus = withContext(Dispatchers.IO) {
            dependencies.pay(activity, created.orderStr)
        }
        return when (resultStatus) {
            AlipayPay.STATUS_SUCCESS ->
                if (confirmPaid(created.outTradeNo, token, SUCCESS_ATTEMPTS, SUCCESS_INTERVAL_MS, dependencies)) {
                    finishPaid(activity, created.outTradeNo, dependencies)
                } else {
                    Outcome.Unpaid("支付已提交，到账确认中，稍后重新打开应用会自动找回")
                }

            AlipayPay.STATUS_PROCESSING, AlipayPay.STATUS_UNKNOWN ->
                if (confirmPaid(created.outTradeNo, token, UNKNOWN_ATTEMPTS, UNKNOWN_INTERVAL_MS, dependencies)) {
                    finishPaid(activity, created.outTradeNo, dependencies)
                } else {
                    Outcome.Unpaid("支付处理中，稍后重新打开应用会自动找回")
                }

            AlipayPay.STATUS_CANCELED -> Outcome.Unpaid("已取消支付")
            AlipayPay.STATUS_DUPLICATE -> Outcome.Unpaid("订单重复，请稍后重试")
            AlipayPay.STATUS_FAILED -> Outcome.Failed("支付失败，请稍后重试")
            "" -> Outcome.Failed("未能唤起支付宝，请确认已安装支付宝")
            else -> Outcome.Failed("支付未完成（状态码 $resultStatus）")
        }
    }

    /** 限流后轮转队列，避免前面的未付订单一直挡住后面的已付订单。 */
    suspend fun recoverPending(context: Context): Outcome = recoverPending(context, Dependencies())

    internal suspend fun recoverPending(context: Context, dependencies: Dependencies): Outcome =
        recoveryMutex.withLock {
            val orders = withContext(Dispatchers.IO) { pendingOrders(context) }
            // 使用当前令牌：历史队列未保存下单令牌，归属拒绝时保留原单。
            val token = AccountStore.token(context)
            for (outTradeNo in orders) {
                val result = dependencies.queryOrder(outTradeNo, token)
                val status = result.getOrNull()
                when {
                    status?.isPaid == true -> return@withLock finishPaid(context, outTradeNo, dependencies)
                    status?.isNotExist == true || status?.isClosed == true ->
                        withContext(Dispatchers.IO) { removePendingOrder(context, outTradeNo) }
                    else -> withContext(Dispatchers.IO) { rotatePendingOrder(context, outTradeNo) }
                }
                if ((result.exceptionOrNull() as? AlipayPayApi.ApiException)?.isRateLimited == true) {
                    return@withLock Outcome.Unpaid("查单过于频繁，稍后重新打开应用会继续找回")
                }
            }
            Outcome.Unpaid("")
        }

    /** 激活与消费之间没有挂起点：即便中途进程退出，也至少留下激活或 pending 之一。 */
    private suspend fun finishPaid(context: Context, outTradeNo: String, dependencies: Dependencies): Outcome =
        withContext(Dispatchers.IO) {
            if (!dependencies.activate(context)) {
                return@withContext Outcome.Failed("支付已到账，但激活保存失败，请检查存储空间后重新打开应用找回")
            }
            // 激活已可靠持久化。清理失败至多留下可重试订单，绝不能先清理再激活。
            removePendingOrder(context, outTradeNo)
            Outcome.Paid
        }

    /** 抹平支付与服务端落库的时间差；限流和终态不继续轮询。 */
    private suspend fun confirmPaid(
        outTradeNo: String,
        token: String?,
        attempts: Int,
        intervalMs: Long,
        dependencies: Dependencies,
    ): Boolean {
        repeat(attempts) { index ->
            val result = dependencies.queryOrder(outTradeNo, token)
            val status = result.getOrNull()
            if (status?.isPaid == true) return true
            if (status?.isClosed == true || status?.isNotExist == true ||
                (result.exceptionOrNull() as? AlipayPayApi.ApiException)?.isRateLimited == true
            ) return false
            if (index < attempts - 1) delay(intervalMs)
        }
        return false
    }

    /** 逗号分隔，兼容历史版本单值。所有访问与读改写都使用同一把锁。 */
    private fun pendingOrders(context: Context): List<String> = synchronized(pendingLock) {
        prefs(context).getString(KEY_PENDING_ORDER, null)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            .orEmpty()
    }

    private fun appendPendingOrder(context: Context, outTradeNo: String): Boolean = synchronized(pendingLock) {
        // commit 失败仍可能更新 prefs 内存，故不能因内存中已有单号就跳过再次提交。
        savePendingOrders(context, (pendingOrders(context) + outTradeNo).distinct())
    }

    private fun removePendingOrder(context: Context, outTradeNo: String): Boolean = synchronized(pendingLock) {
        savePendingOrders(context, pendingOrders(context) - outTradeNo)
    }

    private fun rotatePendingOrder(context: Context, outTradeNo: String) {
        synchronized(pendingLock) {
            val current = pendingOrders(context)
            // 另一购买流程可能刚消费了该单，不能用旧网络快照将其重新追加。
            if (outTradeNo in current) savePendingOrders(context, (current - outTradeNo) + outTradeNo)
        }
    }

    /** 调用方持有 pendingLock，且在 IO 线程执行。 */
    private fun savePendingOrders(context: Context, orders: List<String>): Boolean {
        val edit = prefs(context).edit()
        if (orders.isEmpty()) edit.remove(KEY_PENDING_ORDER)
        else edit.putString(KEY_PENDING_ORDER, orders.joinToString(","))
        return edit.commit().also { saved ->
            if (!saved) Timber.w("ProPurchase: pending orders commit failed")
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
