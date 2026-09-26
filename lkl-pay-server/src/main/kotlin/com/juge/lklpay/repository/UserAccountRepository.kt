package com.juge.lklpay.repository

import com.juge.lklpay.domain.UserAccount
import org.springframework.data.jpa.repository.JpaRepository

interface UserAccountRepository : JpaRepository<UserAccount, Long> {

    fun findByUsername(username: String): UserAccount?

    fun existsByUsername(username: String): Boolean
}