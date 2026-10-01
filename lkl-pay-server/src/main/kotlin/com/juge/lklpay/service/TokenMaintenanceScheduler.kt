package com.juge.lklpay.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 定期维护任务。
 *
 * 登录令牌有效期 365 天，过期后不再可用却仍留在表里；
 * 每天清一次，避免表随登录次数无限增长（也避免过期记录干扰排查）。
 */
@Component
class TokenMaintenanceScheduler(private val userService: UserService) {

    private val log = LoggerFactory.getLogger(TokenMaintenanceScheduler::class.java)

    /** 每天凌晨 4 点清理过期登录令牌 */
    @Scheduled(cron = "0 0 4 * * *")
    fun purgeExpiredTokens() {
        val removed = userService.purgeExpiredTokens()
        if (removed > 0) {
            log.info("已清理过期登录令牌 {} 条", removed)
        }
    }
}