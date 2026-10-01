package com.juge.lklpay.service

import com.juge.lklpay.config.AuthProperties
import com.juge.lklpay.domain.AuthToken
import com.juge.lklpay.domain.UserAccount
import com.juge.lklpay.repository.AuthTokenRepository
import com.juge.lklpay.repository.UserAccountRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * 账号注册、登录与登录态校验。
 *
 * 账号在本产品里只承担一件事：让已购的 PRO 能跟着人走，而不是跟着设备走。
 * 所以不登录也能完整使用 App，登录的唯一收益是换机后找回。
 */
@Service
class UserService(
    private val users: UserAccountRepository,
    private val tokens: AuthTokenRepository,
    private val throttle: LoginThrottle,
    private val props: AuthProperties,
) {

    private val log = LoggerFactory.getLogger(UserService::class.java)

    private val random = SecureRandom()
    private val tokenEncoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    /** 登录态：令牌 + 账号快照，令牌明文只在此刻返回一次 */
    data class Session(val token: String, val user: UserAccount)

    @Transactional
    fun register(rawUsername: String, rawPassword: String, rawNickname: String?, clientIp: String? = null): Session {
        // 注册无「失败」概念，按来源 IP 限速，挡住批量注册与用户名枚举
        throttle.checkIpOrThrow(clientIp)
        val username = normalizeUsername(rawUsername)
        validatePassword(rawPassword)
        throttle.recordIpAttempt(clientIp)
        if (users.existsByUsername(username)) {
            throw AuthException(AuthException.USERNAME_TAKEN, "该用户名已被占用，换一个试试")
        }
        val now = System.currentTimeMillis()
        val user = users.save(
            UserAccount(
                username = username,
                passwordHash = PasswordHasher.hash(rawPassword),
                nickname = rawNickname?.trim()?.takeIf { it.isNotEmpty() }?.take(NICKNAME_MAX),
                createdAt = now,
                lastLoginAt = now,
            )
        )
        log.info("账号注册成功 userId={} username={}", user.id, user.username)
        return issue(user)
    }

    @Transactional
    fun login(rawUsername: String, rawPassword: String, clientIp: String? = null): Session {
        val username = normalizeUsername(rawUsername)
        throttle.checkOrThrow(username, clientIp)

        val user = users.findByUsername(username)
        // 账号不存在时也走一次哈希校验：否则响应快慢会泄露「该用户名是否已注册」
        val matched = PasswordHasher.verify(rawPassword, user?.passwordHash ?: DUMMY_HASH)
        if (user == null || !matched) {
            throttle.recordFailure(username, clientIp)
            // 不区分「用户名不存在」与「口令错误」，避免被用来枚举用户名
            throw AuthException(AuthException.BAD_CREDENTIALS, "用户名或密码不正确")
        }

        throttle.clear(username)
        user.lastLoginAt = System.currentTimeMillis()
        log.info("账号登录成功 userId={} username={}", user.id, user.username)
        return issue(user)
    }

    /** 校验登录态并续期最近使用时间；失败一律抛 UNAUTHORIZED */
    @Transactional
    fun authenticate(token: String?): UserAccount = authenticateInternal(token)

    /**
     * 下单场景使用：带着有效令牌就把订单归属到账号，没带令牌按游客处理。
     *
     * 关键点：**令牌存在却已失效时不静默降级为游客**。若降级，用户以为买到了账号上，
     * 实际只落在本机，换机后找不回，只能靠人工凭交易记录处理。这里直接抛 UNAUTHORIZED，
     * 让客户端提示重新登录后再下单。
     */
    @Transactional
    fun resolvePurchaseUserId(token: String?): Long? {
        if (token.isNullOrBlank()) return null
        return authenticateInternal(token).id
    }

    /** 清理已过期令牌，避免表随登录次数无限增长。返回清理条数。 */
    @Transactional
    fun purgeExpiredTokens(now: Long = System.currentTimeMillis()): Long =
        tokens.deleteByExpiresAtLessThan(now)

    @Transactional
    fun logout(token: String?) {
        if (token.isNullOrBlank()) return
        tokens.deleteById(sha256(token))
    }

    private fun authenticateInternal(token: String?): UserAccount {
        if (token.isNullOrBlank()) {
            throw AuthException(AuthException.UNAUTHORIZED, "请先登录")
        }
        val record = tokens.findById(sha256(token)).orElse(null)
            ?: throw AuthException(AuthException.UNAUTHORIZED, "登录已失效，请重新登录")
        val now = System.currentTimeMillis()
        if (record.expiresAt <= now) {
            tokens.delete(record)
            throw AuthException(AuthException.UNAUTHORIZED, "登录已过期，请重新登录")
        }
        val user = users.findById(record.userId).orElse(null)
            ?: throw AuthException(AuthException.UNAUTHORIZED, "账号不存在")
        record.lastUsedAt = now
        return user
    }

    private fun issue(user: UserAccount): Session {
        val token = newToken()
        val now = System.currentTimeMillis()
        tokens.save(
            AuthToken(
                tokenHash = sha256(token),
                userId = user.id!!,
                createdAt = now,
                expiresAt = now + props.tokenTtlDays * DAY_MS,
            )
        )
        return Session(token, user)
    }

    /** 256 位熵的随机令牌，URL 安全字符集，可直接放进 Authorization 头 */
    private fun newToken(): String = tokenEncoder.encodeToString(ByteArray(32).also(random::nextBytes))

    private fun sha256(value: String): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        )

    private fun normalizeUsername(raw: String): String {
        val username = raw.trim().lowercase()
        if (username.length !in USERNAME_MIN..USERNAME_MAX || !USERNAME_PATTERN.matches(username)) {
            throw IllegalArgumentException("用户名需为 $USERNAME_MIN~$USERNAME_MAX 位字母、数字或下划线")
        }
        return username
    }

    private fun validatePassword(password: String) {
        if (password.length < PASSWORD_MIN || password.length > PASSWORD_MAX) {
            throw IllegalArgumentException("密码长度需为 $PASSWORD_MIN~$PASSWORD_MAX 位")
        }
        if (password.isBlank()) {
            throw IllegalArgumentException("密码不能只由空白字符组成")
        }
    }

    private companion object {
        const val USERNAME_MIN = 3
        const val USERNAME_MAX = 32
        const val PASSWORD_MIN = 6
        const val PASSWORD_MAX = 64
        const val NICKNAME_MAX = 32
        const val DAY_MS = 24 * 60 * 60 * 1000L

        /** 长度单独校验，这里只约束字符集 */
        val USERNAME_PATTERN = Regex("^[a-z0-9_]+$")

        /** 账号不存在时用来「陪跑」一次校验，抹平响应时间差 */
        val DUMMY_HASH: String = PasswordHasher.hash("juge-dummy-password-for-timing")
    }
}