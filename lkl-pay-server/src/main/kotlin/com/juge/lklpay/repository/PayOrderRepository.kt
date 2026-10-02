package com.juge.lklpay.repository

import com.juge.lklpay.domain.PayOrder
import org.springframework.data.jpa.repository.JpaRepository

interface PayOrderRepository : JpaRepository<PayOrder, String> {

    /** 找回激活：按支付宝用户 ID 反查其历史订单 */
    fun findByBuyerUserId(buyerUserId: String): List<PayOrder>

    /** 注销账号时反查归属订单，用于解除绑定 */
    fun findByUserId(userId: Long): List<PayOrder>
}