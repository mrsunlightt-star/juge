package com.juge.lklpay.service

import com.juge.lklpay.config.AuthProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 登录失败节流。
 *
 * 登录接口是公网无鉴权入口，不做限制就能被离线字典在线化：逐个试口令直到命中。
 * 这里按「用户名」维度计数，连续失败到阈值后锁定一个窗口期。
 *
 * 计数放在内存而非数据库：单实例部署下够用，且登录失败是高频事件，
 * 不值得为它写库；重启后计数清零可接受。
 */
@Component
class LoginThrottle(private val props: AuthProperties) {

    private val log = LoggerFactory.getLogger(LoginThrottle::class.java)

    private class Attempt {
        var failures: Int = 0
        var windowStart: Long = 0
        var lockedUntil: Long = 0
    }

    private val attempts = ConcurrentHashMap<String, Attempt>()

    /** 上次清理时间，避免被大量随机用户名撑爆内存 */
    private val lastSweep = AtomicLong(0)

    /** 已锁定则直接拒绝，不进入口令校验 */
    fun checkOrThrow(username: String) {
        val attempt = attempts[username] ?: return
        val now = System.currentTimeMillis()
        if (attempt.lockedUntil > now) {
            val minutes = (attempt.lockedUntil - now) / 60_000 + 1
            throw AuthException(
                AuthException.RATE_LIMITED,
                "尝试过于频繁，请 $minutes 分钟后再试",
            )
        }
    }

    fun recordFailure(username: String) {
        val now = System.currentTimeMillis()
        val windowMs = props.loginWindowMinutes * 60_000L
        val attempt = attempts.compute(username) { _, existing ->
            val current = existing ?: Attempt().also { it.windowStart = now }
            if (now - current.windowStart > windowMs) {
                current.failures = 0
                current.windowStart = now
            }
            current.failures++
            if (current.failures >= props.maxLoginFailures) {
                current.lockedUntil = now + windowMs
                current.failures = 0
                current.windowStart = now
            }
            current
        }
        if (attempt != null && attempt.lockedUntil > now) {
            log.warn("账号因连续登录失败被暂时锁定 username={} 解锁时间={}", username, attempt.lockedUntil)
        }
        sweepIfNeeded(now, windowMs)
    }

    fun clear(username: String) {
        attempts.remove(username)
    }

    /**
     * 条目数超阈值时清掉已过期的记录。
     * 随机用户名刷接口会让 map 无限增长，这一步是防内存耗尽，不是防爆破。
     */
    private fun sweepIfNeeded(now: Long, windowMs: Long) {
        val last = lastSweep.get()
        if (now - last < SWEEP_INTERVAL_MS || !lastSweep.compareAndSet(last, now)) return
        val expireBefore = now - windowMs
        attempts.entries.removeIf { it.value.lockedUntil < expireBefore && it.value.windowStart < expireBefore }
    }

    private companion object {
        const val SWEEP_INTERVAL_MS = 10 * 60_000L
    }
}