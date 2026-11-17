package com.juge.lklpay.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * 支付订单。
 *
 * 权益**不绑账号**：PRO 绑的是付款人的支付宝用户号 [buyerUserId]。
 * 用户重装 App 或换机后本地激活标记会丢失，届时可在 App 内发起支付宝授权，
 * 服务端凭同一个 user_id 反查这里的订单来核验「他确实买过」，无需账号体系。
 *
 * [buyerLogonId] 入库前由 [com.juge.lklpay.service.Sensitive.maskLogonId] 脱敏，
 * 只保留前 3 后 4，用于人工客服核对「这笔是不是你说的那个账号」，不可还原。
 */
@Entity
@Table(
    name = "pay_order",
    indexes = [
        // 「找回已购」按买家 user_id 反查，是唯一的等值查询入口，且会随订单量线性变慢。
        // 不建索引时这把查询是全表扫。
        Index(name = "idx_pay_order_buyer_user_id", columnList = "buyer_user_id"),
        // 定时清理与运营查「哪些单还没付」都按状态 + 创建时间过滤
        Index(name = "idx_pay_order_status_created_at", columnList = "status,created_at"),
    ],
)
class PayOrder(
    @Id
    @Column(name = "out_trade_no", length = 64, nullable = false)
    var outTradeNo: String = "",

    @Column(name = "product_id", length = 64, nullable = false)
    var productId: String = "",

    @Column(name = "subject", length = 256, nullable = false)
    var subject: String = "",

    /** 金额，单位「分」。取自服务端商品目录，不接受客户端传入 */
    @Column(name = "amount_fen", nullable = false)
    var amountFen: Long = 0,

    /** CREATED / PAID / CLOSED */
    @Column(name = "status", length = 16, nullable = false)
    var status: String = STATUS_CREATED,

    /** 支付宝交易号 */
    @Column(name = "trade_no", length = 64)
    var tradeNo: String? = null,

    /** 支付宝用户唯一 ID：找回激活的主键 */
    @Column(name = "buyer_user_id", length = 64)
    var buyerUserId: String? = null,

    /** **已脱敏**的买家账号（手机号/邮箱），仅用于人工核对，不可还原 */
    @Column(name = "buyer_logon_id", length = 128)
    var buyerLogonId: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Long = 0,

    @Column(name = "paid_at")
    var paidAt: Long? = null,

    /** 原始异步通知留档（其中 buyer_logon_id 已脱敏），用于对账与争议处理 */
    @Column(name = "notify_raw", length = 4000)
    var notifyRaw: String? = null,
) {
    companion object {
        const val STATUS_CREATED = "CREATED"
        const val STATUS_PAID = "PAID"
        const val STATUS_CLOSED = "CLOSED"
    }
}
