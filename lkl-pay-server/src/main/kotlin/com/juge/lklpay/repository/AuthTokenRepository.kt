package com.juge.lklpay.repository

import com.juge.lklpay.domain.AuthToken
import org.springframework.data.jpa.repository.JpaRepository

interface AuthTokenRepository : JpaRepository<AuthToken, String> {

    /** 清理过期令牌；返回删除条数 */
    fun deleteByExpiresAtLessThan(cutoff: Long): Long

    /** 注销账号时删除该账号的全部令牌；返回删除条数 */
    fun deleteByUserId(userId: Long): Long
}