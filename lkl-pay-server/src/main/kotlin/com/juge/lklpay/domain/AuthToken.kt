package com.juge.lklpay.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * 登录令牌。
 *
 * 主键存的是令牌的 SHA-256 摘要而非令牌本身：数据库若被拖走，
 * 攻击者拿到的是摘要，无法反推出可用的令牌。
 * 令牌本身只在签发那一刻返回给客户端，服务端不留副本。
 *
 * 选择「不透明随机串 + 数据库查表」而非 JWT，是因为需要可撤销（登出即失效），
 * 且本服务单实例、查表成本可忽略。
 */
@Entity
@Table(
    name = "auth_token",
    indexes = [
        Index(name = "idx_auth_token_user", columnList = "user_id"),
    ],
)
class AuthToken(
    @Id
    @Column(name = "token_hash", length = 64, nullable = false)
    var tokenHash: String = "",

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Long = 0,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Long = 0,

    @Column(name = "last_used_at")
    var lastUsedAt: Long? = null,
)