package com.juge.lklpay.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 支付订单。
 *
 * 权益**不绑账号**：PRO 绑的是付款人的支付宝用户号 [buyerUserId]。
 * 用户重装 App 或换机后本地激活标记会丢失，届时可在 App 内发起支付宝授权，
 * 服务端凭同一个 user_id 反查这里的订单来核验「他确实买过」，无需账号体系。
 *
 * [buyerLogonId] 是脱敏后的买家账号，只在人工客服核对时使用，不参与自动找回。
 */
@Entity
@Table(name = "pay_order")
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

    /** 脱敏后的买家账号（手机号/邮箱），仅用于人工核对 */
    @Column(name = "buyer_logon_id", length = 128)
    var buyerLogonId: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Long = 0,

    @Column(name = "paid_at")
    var paidAt: Long? = null,

    /** 原始异步通知留档，用于对账与争议处理 */
    @Column(name = "notify_raw", length = 4000)
    var notifyRaw: String? = null,
) {
    companion object {
        const val STATUS_CREATED = "CREATED"
        const val STATUS_PAID = "PAID"
        const val STATUS_CLOSED = "CLOSED"
    }
}