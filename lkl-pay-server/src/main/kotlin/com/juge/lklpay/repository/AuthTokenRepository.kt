package com.juge.lklpay.repository

import com.juge.lklpay.domain.AuthToken
import org.springframework.data.jpa.repository.JpaRepository

interface AuthTokenRepository : JpaRepository<AuthToken, String>