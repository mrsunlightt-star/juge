package com.juge.lklpay.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 支付宝 App 支付（alipay.trade.app.pay）接入配置。
 *
 * 密钥体系为「公钥模式 + RSA2」，共三把钥匙：
 *   - 应用私钥：本地保存，仅用于请求加签，绝不上传、绝不入仓库
 *   - 应用公钥：上传至开放平台，供支付宝验证我方请求
 *   - 支付宝公钥：开放平台获取，用于验证支付宝响应与异步通知的签名
 */
@ConfigurationProperties(prefix = "alipay")
data class AlipayProperties(
    /** 开放平台应用 APPID */
    var appId: String = "",
    /** 应用私钥文件路径（PKCS#8，PEM 头尾会被自动剥离） */
    var privateKeyPath: String = "",
    /** 支付宝公钥文件路径（Base64 单行） */
    var alipayPublicKeyPath: String = "",
    /** 开放平台网关 */
    var gatewayUrl: String = "https://openapi.alipay.com/gateway.do",
    /** 异步通知地址，必须公网可达，且与开放平台「应用网关」填写的一致 */
    var notifyUrl: String = "",
    /** 签名算法，官方推荐 RSA2（SHA256withRSA） */
    var signType: String = "RSA2",
    /** 字符编码 */
    var charset: String = "UTF-8",
    /** 报文格式，固定 json */
    var format: String = "json",
    /** 订单有效期（分钟），到期支付宝自动关闭交易 */
    var orderExpireMinutes: Long = 120,
)