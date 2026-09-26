package com.juge.lklpay.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * 用户账号。
 *
 * 引入账号的唯一目的是「PRO 归属可跨设备恢复」：此前激活状态只存在 App 本地，
 * 重装或换机即丢失，而用户已经付过款。因此 PRO 标记以服务端为权威，
 * App 本地那份只是缓存，登录后由 [pro] 回灌。
 */
@Entity
@Table(
    name = "user_account",
    indexes = [Index(name = "idx_user_account_username", columnList = "username", unique = true)],
)
class UserAccount(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /** 登录名，统一小写存储，避免「Alice」与「alice」被当成两个账号 */
    @Column(name = "username", length = 32, nullable = false, unique = true)
    var username: String = "",

    /** PBKDF2-HMAC-SHA256 派生结果，含盐与迭代次数，格式见 PasswordHasher */
    @Column(name = "password_hash", length = 255, nullable = false)
    var passwordHash: String = "",

    /** 展示用昵称，可空 */
    @Column(name = "nickname", length = 32)
    var nickname: String? = null,

    /** 是否已购 PRO。服务端为权威来源 */
    @Column(name = "pro", nullable = false)
    var pro: Boolean = false,

    @Column(name = "pro_activated_at")
    var proActivatedAt: Long? = null,

    /** 开通 PRO 的那笔订单号，用于对账与争议处理 */
    @Column(name = "pro_out_trade_no", length = 64)
    var proOutTradeNo: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Long = 0,

    @Column(name = "last_login_at")
    var lastLoginAt: Long? = null,
)