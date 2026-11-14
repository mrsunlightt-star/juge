package com.juge.lklpay.service

import com.juge.lklpay.domain.PayOrder
import com.juge.lklpay.repository.PayOrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * 订单落库与状态流转。
 *
 * 支付结果的三个来源（异步通知、主动查单、客户端同步回调）都可能重复到达，
 * 且顺序无法保证，因此所有状态变更必须幂等。
 *
 * 权益归属**不在本类处理**：PRO 绑的是付款人的支付宝用户号
 * （[PayOrder.buyerUserId]），由 App 侧「找回已购」凭同一账号核验后自行激活。
 * 服务端只负责把「这笔钱是谁付的」忠实记下来。
 */
@Service
class PayOrderService(
    private val orders: PayOrderRepository,
) {

    private val log = LoggerFactory.getLogger(PayOrderService::class.java)

    @Transactional
    fun recordCreated(
        outTradeNo: String,
        productId: String,
        subject: String,
        amountFen: Long,
    ): PayOrder =
        orders.save(
            PayOrder(
                outTradeNo = outTradeNo,
                productId = productId,
                subject = subject,
                amountFen = amountFen,
                createdAt = System.currentTimeMillis(),
            )
        )

    fun find(outTradeNo: String): PayOrder? = orders.findById(outTradeNo).orElse(null)

    /**
     * 找回入口：按支付宝用户号反查其已支付订单。
     *
     * 只返回 PAID：CREATED / CLOSED 都不能用来恢复权益，
     * 否则用户凭一个「创建过但没付钱」的订单号就能骗到 PRO。
     */
    fun findPaidByBuyer(buyerUserId: String): List<PayOrder> =
        orders.findByBuyerUserId(buyerUserId).filter { it.status == PayOrder.STATUS_PAID }

    /**
     * 幂等地把订单置为已支付。异步通知与主动查单共用此入口。
     *
     * 三处防护：
     *   1. 状态变更走带条件的 UPDATE（compare-and-set）——同一订单支付宝会在 25 小时内重投多次，
     *      且可能并发到达；只有 affected rows = 1 的那次才真正完成流转
     *   2. 金额核对——下单金额由服务端商品目录决定，客户端无法篡改；
     *      金额不符只可能是自身缺陷或伪造通知，一律拒绝置为已支付
     *   3. 买家标识只在首次流转时写入，重投不会覆盖
     */
    @Transactional
    fun markPaid(
        outTradeNo: String,
        tradeNo: String?,
        buyerUserId: String?,
        buyerLogonId: String?,
        paidAmountYuan: String?,
        notifyRaw: String? = null,
    ): MarkPaidOutcome {
        val order = orders.findById(outTradeNo).orElse(null)
            ?: return MarkPaidOutcome.OrderMissing

        // 已付款也必须核对本次结果的金额，不能让幂等分支绕过查询的校验闸门。
        val paidFen = yuanToFen(paidAmountYuan)
        if (paidFen == null) {
            // 金额缺失/解析失败时绝不放行：防改价是最后一道闸，没有「跳过核对」的安全分支
            log.error(
                "支付结果缺少可解析金额，拒绝置为已支付 outTradeNo={} totalAmount={}",
                outTradeNo, paidAmountYuan,
            )
            return MarkPaidOutcome.AmountMissing
        }
        if (paidFen != order.amountFen) {
            log.error(
                "支付金额与订单不符，拒绝置为已支付 outTradeNo={} 订单={}分 实付={}分",
                outTradeNo, order.amountFen, paidFen,
            )
            return MarkPaidOutcome.AmountMismatch
        }
        if (order.status == PayOrder.STATUS_PAID) {
            return MarkPaidOutcome.AlreadyPaid
        }

        // compare-and-set：只有把状态从 CREATED 改成 PAID 的那一次才继续发货
        val affected = orders.markPaidIfCreated(
            outTradeNo = outTradeNo,
            paid = PayOrder.STATUS_PAID,
            created = PayOrder.STATUS_CREATED,
            paidAt = System.currentTimeMillis(),
            tradeNo = tradeNo?.takeIf { it.isNotBlank() } ?: order.tradeNo,
            buyerUserId = buyerUserId?.takeIf { it.isNotBlank() } ?: order.buyerUserId,
            buyerLogonId = buyerLogonId?.takeIf { it.isNotBlank() } ?: order.buyerLogonId,
            notifyRaw = notifyRaw?.takeIf { it.isNotBlank() }?.take(4000) ?: order.notifyRaw,
        )
        if (affected == 0) {
            // CAS 失败也可能是 CLOSED 或订单已删除，只有重新确认 PAID 才算幂等成功。
            return unchangedOutcome(outTradeNo)
        }

        // 批量 UPDATE 已清掉持久化上下文；违反事务内状态不变量时回滚，不能报告成功。
        val paidOrder = checkNotNull(orders.findById(outTradeNo).orElse(null))
        check(paidOrder.status == PayOrder.STATUS_PAID)

        log.info(
            "订单已置为已支付 outTradeNo={} tradeNo={} buyerUserId={} amountFen={}",
            outTradeNo, paidOrder.tradeNo, paidOrder.buyerUserId, order.amountFen,
        )
        return MarkPaidOutcome.Marked
    }

    /** 交易关闭（超时未付或用户主动关闭），未支付过的订单才需要落状态 */
    @Transactional
    fun markClosed(outTradeNo: String): MarkPaidOutcome {
        val affected = orders.markClosedIfCreated(outTradeNo, PayOrder.STATUS_CLOSED, PayOrder.STATUS_CREATED)
        if (affected == 0) return unchangedOutcome(outTradeNo)
        log.info("订单已置为已关闭 outTradeNo={}", outTradeNo)
        return MarkPaidOutcome.Marked
    }

    /** 只能在清理过旧实体的 CAS 之后调用，避免把并发闭单误报为付款成功。 */
    private fun unchangedOutcome(outTradeNo: String): MarkPaidOutcome {
        val current = orders.findById(outTradeNo).orElse(null) ?: return MarkPaidOutcome.OrderMissing
        return if (current.status == PayOrder.STATUS_PAID) MarkPaidOutcome.AlreadyPaid else MarkPaidOutcome.NotPaid
    }

    /** 解析「元」为「分」；非整分或溢出不能截断成一个恰好匹配订单的金额。 */
    private fun yuanToFen(yuan: String?): Long? {
        if (yuan.isNullOrBlank()) return null
        return runCatching { BigDecimal(yuan).movePointRight(2).longValueExact() }.getOrNull()
    }

    enum class MarkPaidOutcome {
        /** 本次成功流转状态（markPaid 为 PAID，markClosed 为 CLOSED） */
        Marked,

        /** 已确认是 PAID，幂等短路 */
        AlreadyPaid,

        /** 未能转为 PAID（如 CLOSED），绝非支付成功 */
        NotPaid,

        /** 本地无此订单，需人工核查 */
        OrderMissing,

        /** 金额不符，需人工介入 */
        AmountMismatch,

        /** 支付结果缺少可解析金额，需人工介入 */
        AmountMissing,
    }
}
