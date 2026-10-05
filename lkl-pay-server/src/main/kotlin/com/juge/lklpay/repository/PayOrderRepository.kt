package com.juge.lklpay.repository

import com.juge.lklpay.domain.PayOrder
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface PayOrderRepository : JpaRepository<PayOrder, String> {

    /** 找回激活：按支付宝用户 ID 反查其历史订单 */
    fun findByBuyerUserId(buyerUserId: String): List<PayOrder>

    /** 注销账号时反查归属订单，用于解除绑定 */
    fun findByUserId(userId: Long): List<PayOrder>

    /**
     * 原子地把订单从 [created] 置为 [paid]（compare-and-set）。
     *
     * 支付宝会对同一笔订单并发重投通知，服务端也可能同时被查单接口触发。
     * 「先读状态再改」在两个事务里都会读到 CREATED，导致重复发货；
     * 把状态作为 WHERE 条件后，只有一个 UPDATE 能命中（返回 1），另一个返回 0。
     *
     * clearAutomatically：批量 UDPATE 不走持久化上下文，必须清掉缓存的旧实体，
     * 否则紧随其后的 findById 会读到过期的状态。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update PayOrder o
           set o.status = :paid,
               o.paidAt = :paidAt,
               o.tradeNo = :tradeNo,
               o.buyerUserId = :buyerUserId,
               o.buyerLogonId = :buyerLogonId,
               o.notifyRaw = :notifyRaw
         where o.outTradeNo = :outTradeNo
           and o.status = :created
        """
    )
    fun markPaidIfCreated(
        outTradeNo: String,
        paid: String,
        created: String,
        paidAt: Long,
        tradeNo: String?,
        buyerUserId: String?,
        buyerLogonId: String?,
        notifyRaw: String?,
    ): Int
}
