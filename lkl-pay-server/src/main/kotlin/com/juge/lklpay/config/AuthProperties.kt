package com.juge.lklpay.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 账号体系配置。
 *
 * 产品定位是「可选登录」：不登录也能正常记便签、用桌面组件，
 * 账号只承载「换机/重装后找回已购 PRO」这一件事，因此不做强制登录。
 */
@ConfigurationProperties(prefix = "auth")
data class AuthProperties(
    /** 登录态有效期（天）。取较长值，避免用户隔一阵子打开就被登出 */
    var tokenTtlDays: Long = 365,
    /** 同一用户名在窗口期内允许的连续登录失败次数，超过即暂时锁定 */
    var maxLoginFailures: Int = 5,
    /** 登录失败的统计窗口与锁定时长（分钟） */
    var loginWindowMinutes: Long = 10,
)