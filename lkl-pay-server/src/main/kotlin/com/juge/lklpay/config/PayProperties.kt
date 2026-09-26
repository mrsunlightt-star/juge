package com.juge.lklpay.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 商品目录。
 *
 * 价格以服务端为准——App 只传商品 ID，不传金额。
 * 若金额由客户端传入，任何人都能以 1 分钱下单并取得一笔真实「已支付」的订单，
 * 付费墙将形同虚设。
 */
@ConfigurationProperties(prefix = "pay")
data class PayProperties(
    var products: Map<String, Product> = emptyMap(),
) {
    data class Product(
        /** 订单标题，下单时会做特殊字符清洗 */
        var subject: String = "",
        /** 单价，单位「分」 */
        var amountFen: Long = 0,
    )
}