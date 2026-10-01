package com.juge.lklpay.service

import com.juge.lklpay.config.AuthProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 登录/注册节流。
 *
 * 登录接口是公网无鉴权入口，不做限制就能被离线字典在线化：逐个试口令直到命中。
 * 因此按两个维度同时计数：
 *  - 用户名维度：挡住「盯住一个账号逐个试口令」
 *  - 来源 IP 维度：挡住「一个来源换着用户名刷」，以及批量注册
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

    /** 登录前调用：用户名或来源 IP 任一处于锁定期即拒绝，不进入口令校验 */
    fun checkOrThrow(username: String, clientIp: String? = null) {
        throwIfLocked(userKey(username))
        checkIpOrThrow(clientIp)
    }

    /** 仅按来源 IP 检查，用于注册等无用户名的入口 */
    fun checkIpOrThrow(clientIp: String?) {
        clientIp?.takeIf { it.isNotBlank() }?.let { throwIfLocked(ipKey(it)) }
    }

    fun recordFailure(username: String, clientIp: String? = null) {
        record(userKey(username), props.maxLoginFailures)
        recordIpAttempt(clientIp)
    }

    /** 注册尝试计入 IP 维度：注册无「失败」概念，每次尝试都算一次消耗 */
    fun recordIpAttempt(clientIp: String?) {
        clientIp?.takeIf { it.isNotBlank() }?.let { record(ipKey(it), props.maxIpFailures) }
        sweepIfNeeded(System.currentTimeMillis())
    }

    /** 登录成功：只清该用户名的失败计数。IP 计数不清——避免用合法账号「洗白」来源 IP */
    fun clear(username: String) {
        attempts.remove(userKey(username))
    }

    private fun throwIfLocked(key: String) {
        val attempt = attempts[key] ?: return
        val now = System.currentTimeMillis()
        if (attempt.lockedUntil > now) {
            val minutes = (attempt.lockedUntil - now) / 60_000 + 1
            throw AuthException(
                AuthException.RATE_LIMITED,
                "尝试过于频繁，请 $minutes 分钟后再试",
            )
        }
    }

    private fun record(key: String, threshold: Int) {
        val now = System.currentTimeMillis()
        val windowMs = props.loginWindowMinutes * 60_000L
        val attempt = attempts.compute(key) { _, existing ->
            val current = existing ?: Attempt().also { it.windowStart = now }
            if (now - current.windowStart > windowMs) {
                current.failures = 0
                current.windowStart = now
            }
            current.failures++
            if (current.failures >= threshold) {
                current.lockedUntil = now + windowMs
                current.failures = 0
                current.windowStart = now
            }
            current
        }
        if (attempt != null && attempt.lockedUntil > now) {
            log.warn("来源因连续失败被暂时锁定 key={} 解锁时间={}", key, attempt.lockedUntil)
        }
    }

    private fun userKey(username: String) = "u:$username"

    private fun ipKey(ip: String) = "ip:$ip"

    /**
     * 条目数超阈值时清掉已过期的记录。
     * 随机用户名刷接口会让 map 无限增长，这一步是防内存耗尽，不是防爆破。
     */
    private fun sweepIfNeeded(now: Long) {
        val windowMs = props.loginWindowMinutes * 60_000L
        val last = lastSweep.get()
        if (now - last < SWEEP_INTERVAL_MS || !lastSweep.compareAndSet(last, now)) return
        val expireBefore = now - windowMs
        attempts.entries.removeIf { it.value.lockedUntil < expireBefore && it.value.windowStart < expireBefore }
    }

    private companion object {
        const val SWEEP_INTERVAL_MS = 10 * 60_000L
    }
}