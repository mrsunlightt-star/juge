package com.juge.lklpay.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * PRO 权益凭证的签名密钥配置。
 *
 * 与 [AlipayProperties] 分开：支付宝那两把钥匙是「跟支付宝对话」用的，
 * 这一对是「跟自己出的 App 对话」用的。混在一个类里的后果是换支付宝密钥时
 * 有人顺手把凭证密钥也换了，已发出的凭证全部失效。
 *
 * 默认值指向 `certs/` 下的文件；生产建议用启动参数指向 `/etc/juge/`：
 *   --entitlement.private-key-path=/etc/juge/entitlement_private_key.pem
 */
@ConfigurationProperties(prefix = "entitlement")
data class EntitlementProperties(
    /** 凭证签名私钥（PKCS#8，PEM 头尾会被自动剥离），只有服务端持有 */
    var privateKeyPath: String = "certs/entitlement_private_key.pem",
    /** 凭证签名公钥，仅用于启动自检时打指纹、与客户端内置公钥比对 */
    var publicKeyPath: String = "certs/entitlement_public_key.pem",
)
