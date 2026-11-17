package com.juge.app.pay

import android.app.Activity
import android.content.Context
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

    /**
     * 仅用于界面展示的**本地兜底**文案。
     *
     * 权威价格只有一处：服务端 `pay.products.pro_permanent.amount-fen`。
     * 这里之所以还留一个常量，是因为支付墙在离线时也得显示点东西。
     * 改价时必须同时改这里与 application.yml —— 之前就出现过「App 写 ￥3.99、
     * 服务端 399 分、测试还停在 1.99」的三源不一致。
     * 若要彻底消掉这个重复，可以让服务端在启动时把价格通过某个只读接口下发，
     * 但对一个单品买断的应用来说，两处常量 + 一条注释已经够了。
     */
    const val PRICE_TEXT = "￥3.99"

    private const val PREFS = "pro_purchase_prefs"
    private const val KEY_PENDING_ORDER = "pending_order_no"

    /** 队列上限：超出后丢最旧的。没有上限时，一个反复下单的客户端能把 prefs 撑成几 MB */
    internal const val MAX_PENDING_ORDERS = 10

    /**
     * 队列条目的存活时间。
     *
     * 支付宝订单本身的有效期是 120 分钟（application.yml 的 order-expire-minutes），
     * 超过之后订单只可能是 CLOSED / NOT_EXIST，留在队列里没有任何意义，
     * 只会让每次启动都多发几个必然失败的查单请求（还要吃限流）。
     * 这里给 24 小时，留出一段「用户隔天想起来再打开 App」的窗口。
     */
    private const val PENDING_TTL_MS = 24L * 60 * 60 * 1000

    private const val SUCCESS_ATTEMPTS = 5
    private const val SUCCESS_INTERVAL_MS = 1_200L
    private const val UNKNOWN_ATTEMPTS = 5
    private const val UNKNOWN_INTERVAL_MS = 2_000L

    // 只锁本地队列的读改写，不在锁内等待网络或收银台。
    private val pendingLock = Any()
    private val recoveryMutex = Mutex()

    /**
     * 购买互斥。
     *
     * 之前只有 [pendingLock] 保护队列读改写，purchase 本身没有互斥：
     * 用户双击支付按钮、或「购买」与「找回」同时被点，会并行发起两次下单，
     * 生成两个订单号都进队列，用户可能付两次。
     * 界面层的 `isPaying` 双闸挡得住正常点击，挡不住旋转重建后的状态丢失。
     */
    private val purchaseMutex = Mutex()

    /** 每次调用独立注入，正式路径始终使用默认实现，无全局可变测试钩子。 */
    internal data class Dependencies(
        val createOrder: suspend (String) -> Result<AlipayPayApi.OrderCreated> = AlipayPayApi::createOrder,
        val queryOrder: suspend (String) -> Result<AlipayPayApi.OrderStatus> = AlipayPayApi::queryOrder,
        val pay: (Activity, String) -> String = AlipayPay::pay,
        val activate: (Context, String) -> Boolean = { context, credential ->
            TrialManager.getInstance(context).activateWithEntitlement(
                credential = credential,
                payMethod = TrialManager.PAY_METHOD_ALIPAY,
            )
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

    internal suspend fun purchase(activity: Activity, dependencies: Dependencies): Outcome =
        purchaseMutex.withLock {
            val created = dependencies.createOrder(PRODUCT_ID).getOrElse { e ->
                Timber.e(e, "ProPurchase: 下单失败")
                val serverMessage = (e as? AlipayPayApi.ApiException)?.message?.takeIf { it.isNotBlank() }
                return@withLock Outcome.Failed(serverMessage ?: "下单失败，请检查网络后重试")
            }
            // 必须在唤支付前确认落盘；失败时不能让用户付款后失去唯一的找回凭据。
            val saved = withContext(Dispatchers.IO) { appendPendingOrder(activity, created.outTradeNo) }
            if (!saved) return@withLock Outcome.Failed("订单保存失败，请检查存储空间后重试，尚未发起支付")

            val resultStatus = withContext(Dispatchers.IO) {
                dependencies.pay(activity, created.orderStr)
            }
            when (resultStatus) {
                AlipayPay.STATUS_SUCCESS ->
                    confirmPaid(created.outTradeNo, SUCCESS_ATTEMPTS, SUCCESS_INTERVAL_MS, dependencies)
                        ?.let { finishPaid(activity, created.outTradeNo, it.credential, dependencies) }
                        ?: Outcome.Unpaid("支付已提交，到账确认中，稍后重新打开应用会自动找回")

                AlipayPay.STATUS_PROCESSING, AlipayPay.STATUS_UNKNOWN ->
                    confirmPaid(created.outTradeNo, UNKNOWN_ATTEMPTS, UNKNOWN_INTERVAL_MS, dependencies)
                        ?.let { finishPaid(activity, created.outTradeNo, it.credential, dependencies) }
                        ?: Outcome.Unpaid("支付处理中，稍后重新打开应用会自动找回")

                // 6001「用户中途取消」**仍然保留**在队列里。
                //
                // 直觉上取消就等于没付钱，出队最省事；但这个结论来自客户端的同步返回，
                // 而同步返回码从来不是权威结论（9000 都不能当成功，6001 也一样）。
                // 万一用户在收银台里已经付款、只是在回跳前退出了应用，
                // 出队就等于永久丢掉这笔订单的自动找回路径。
                // 队列不会因此无限增长：有 [PENDING_TTL_MS] 与 [MAX_PENDING_ORDERS] 兜底。
                AlipayPay.STATUS_CANCELED -> Outcome.Unpaid("已取消支付")

                AlipayPay.STATUS_DUPLICATE -> Outcome.Unpaid("订单重复，请稍后重试")

                // 4000「系统异常，支付失败」与「没能唤起支付宝」都是**确定没付款**：
                // 这两种情况出队是安全的，也不会再有查单价值。
                AlipayPay.STATUS_FAILED -> {
                    withContext(Dispatchers.IO) { removePendingOrder(activity, created.outTradeNo) }
                    Outcome.Failed("支付失败，请稍后重试")
                }

                "" -> {
                    withContext(Dispatchers.IO) { removePendingOrder(activity, created.outTradeNo) }
                    Outcome.Failed("未能唤起支付宝，请确认已安装支付宝")
                }
                else -> Outcome.Failed("支付未完成（状态码 $resultStatus）")
            }
        }

    /** 限流后轮转队列，避免前面的未付订单一直挡住后面的已付订单。 */
    suspend fun recoverPending(context: Context): Outcome = recoverPending(context, Dependencies())

    internal suspend fun recoverPending(context: Context, dependencies: Dependencies): Outcome =
        recoveryMutex.withLock {
            val orders = withContext(Dispatchers.IO) { livePendingOrders(context) }
            for (outTradeNo in orders) {
                val result = dependencies.queryOrder(outTradeNo)
                val status = result.getOrNull()
                val error = result.exceptionOrNull() as? AlipayPayApi.ApiException
                when {
                    status?.isPaid == true ->
                        return@withLock finishPaid(context, outTradeNo, status.credential, dependencies)
                    // 终态：不会再有付款可能，出队
                    status?.isNotExist == true || status?.isClosed == true ->
                        withContext(Dispatchers.IO) { removePendingOrder(context, outTradeNo) }
                    // 订单在本地不存在（服务端 404）：同样不会再有付款，出队
                    error?.code == AlipayPayApi.CODE_ORDER_MISSING ->
                        withContext(Dispatchers.IO) { removePendingOrder(context, outTradeNo) }
                    // 服务端明确说「还没付」（WAIT_BUYER_PAY / 空 trade_status）：留在队列里下次再看
                    else -> withContext(Dispatchers.IO) { rotatePendingOrder(context, outTradeNo) }
                }
                if (error?.isRateLimited == true) {
                    return@withLock Outcome.Unpaid("查单过于频繁，稍后重新打开应用会继续找回")
                }
            }
            Outcome.Unpaid("")
        }

    /**
     * 激活与消费之间没有挂起点：即便中途进程退出，也至少留下激活或 pending 之一。
     *
     * 激活必须有服务端凭证。状态是「已支付」但没带凭证，只可能是服务端凭证密钥没配好：
     * 这种情况保留 pending 订单（下次启动会再查一次），并明确告诉用户去联系客服，
     * 而不是写一个下次启动就会被判无效的本地激活状态。
     */
    private suspend fun finishPaid(
        context: Context,
        outTradeNo: String,
        credential: String?,
        dependencies: Dependencies,
    ): Outcome =
        withContext(Dispatchers.IO) {
            if (credential.isNullOrBlank()) {
                Timber.w("ProPurchase: 已支付但服务端未签发权益凭证 outTradeNo=%s", outTradeNo)
                return@withContext Outcome.Failed("支付已到账，但服务端暂时无法签发权益凭证，请稍后重新打开应用或联系客服")
            }
            if (!dependencies.activate(context, credential)) {
                return@withContext Outcome.Failed("支付已到账，但激活保存失败，请检查存储空间后重新打开应用找回")
            }
            // 激活已可靠持久化。清理失败至多留下可重试订单，绝不能先清理再激活。
            // 但失败必须留痕：原实现把返回值丢掉了，清理失败时只会在每次启动重查一遍，
            // 而且没人知道为什么那条单号清不掉。
            if (!removePendingOrder(context, outTradeNo)) {
                Timber.w("ProPurchase: 已激活但待查订单清理失败 outTradeNo=%s", outTradeNo)
            }
            Outcome.Paid
        }

    /**
     * 抹平支付与服务端落库的时间差；限流和终态不继续轮询。
     *
     * 返回**已支付的查单结果**而不是 Boolean：调用方还需要它身上的权益凭证
     * 才能完成激活。返回 null 表示「确认不到已支付」。
     */
    private suspend fun confirmPaid(
        outTradeNo: String,
        attempts: Int,
        intervalMs: Long,
        dependencies: Dependencies,
    ): AlipayPayApi.OrderStatus? {
        repeat(attempts) { index ->
            val result = dependencies.queryOrder(outTradeNo)
            val status = result.getOrNull()
            if (status?.isPaid == true) return status
            val error = result.exceptionOrNull() as? AlipayPayApi.ApiException
            if (status?.isClosed == true || status?.isNotExist == true ||
                error?.isRateLimited == true ||
                // 服务端明确说订单查不到：继续等也不会有结果
                error?.code == AlipayPayApi.CODE_ORDER_MISSING
            ) return null
            if (index < attempts - 1) delay(intervalMs)
        }
        return null
    }

    // ==================== 待查订单队列 ====================
    //
    // 存储格式：`outTradeNo:createdAtMillis` 逗号分隔。
    // 兼容历史版本：没有冒号的老条目按「刚写入」处理（createdAt 取当前时间），
    // 否则升级安装的那一刻会把用户真实的待查订单全判为过期。

    internal data class PendingEntry(val outTradeNo: String, val createdAt: Long)

    /** 读出所有未过期的条目，顺带把过期条目从存储里清掉。调用方持有 IO 线程。 */
    private fun livePendingOrders(context: Context): List<String> {
        val entries = readPendingEntries(context)
        val now = System.currentTimeMillis()
        val live = entries.filter { now - it.createdAt <= PENDING_TTL_MS }
        if (live.size != entries.size) {
            Timber.i("ProPurchase: 丢弃 %d 条过期待查订单", entries.size - live.size)
            synchronized(pendingLock) {
                writePendingEntries(context, live)
            }
        }
        return live.map { it.outTradeNo }
    }

    private fun readPendingEntries(context: Context): List<PendingEntry> =
        prefs(context).getString(KEY_PENDING_ORDER, null)
            ?.split(',')
            ?.mapNotNull { raw ->
                val token = raw.trim()
                if (token.isEmpty()) return@mapNotNull null
                val sep = token.indexOf(':')
                if (sep <= 0) {
                    // 老格式（只有单号）：给一个「现在」的时间，让它至少还能被查一次
                    PendingEntry(token, System.currentTimeMillis())
                } else {
                    val no = token.substring(0, sep)
                    val ts = token.substring(sep + 1).toLongOrNull() ?: return@mapNotNull null
                    PendingEntry(no, ts)
                }
            }
            ?.distinctBy { it.outTradeNo }
            .orEmpty()

    private fun appendPendingOrder(context: Context, outTradeNo: String): Boolean = synchronized(pendingLock) {
        // 顺带清过期，避免队列只增不减
        val now = System.currentTimeMillis()
        val current = readPendingEntries(context)
            .filter { now - it.createdAt <= PENDING_TTL_MS }
            .filter { it.outTradeNo != outTradeNo }
        val next = (current + PendingEntry(outTradeNo, now)).takeLast(MAX_PENDING_ORDERS)
        writePendingEntries(context, next)
    }

    private fun removePendingOrder(context: Context, outTradeNo: String): Boolean = synchronized(pendingLock) {
        val current = readPendingEntries(context)
        if (current.none { it.outTradeNo == outTradeNo }) return@synchronized true
        writePendingEntries(context, current.filter { it.outTradeNo != outTradeNo })
    }

    private fun rotatePendingOrder(context: Context, outTradeNo: String) {
        synchronized(pendingLock) {
            val current = readPendingEntries(context)
            // 另一购买流程可能刚消费了该单，不能用旧网络快照将其重新追加。
            if (current.none { it.outTradeNo == outTradeNo }) return@synchronized
            writePendingEntries(context, current.filter { it.outTradeNo != outTradeNo } + current.filter { it.outTradeNo == outTradeNo })
        }
    }

    /** 调用方持有 pendingLock，且在 IO 线程执行。 */
    private fun writePendingEntries(context: Context, entries: List<PendingEntry>): Boolean {
        val edit = prefs(context).edit()
        if (entries.isEmpty()) edit.remove(KEY_PENDING_ORDER)
        else edit.putString(KEY_PENDING_ORDER, entries.joinToString(",") { "${it.outTradeNo}:${it.createdAt}" })
        return edit.commit().also { saved ->
            if (!saved) Timber.w("ProPurchase: pending orders commit failed")
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
