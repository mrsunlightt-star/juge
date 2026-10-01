package com.juge.lklpay

import com.juge.lklpay.domain.PayOrder
import com.juge.lklpay.repository.AuthTokenRepository
import com.juge.lklpay.repository.UserAccountRepository
import com.juge.lklpay.service.AuthException
import com.juge.lklpay.service.PayOrderService
import com.juge.lklpay.service.UserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 账号体系的行为约束。
 *
 * 三条底线：口令不能明文落库、登录态必须可撤销、
 * 付过款的账号必须能拿回 PRO（这正是引入账号的唯一理由）。
 */
@SpringBootTest(properties = ["spring.datasource.url=jdbc:h2:mem:auth-test;DB_CLOSE_DELAY=-1"])
class UserAccountFlowTest {

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var payOrderService: PayOrderService

    @Autowired
    private lateinit var users: UserAccountRepository

    @Autowired
    private lateinit var tokens: AuthTokenRepository

    @Test
    fun `口令以哈希形式落库且可正常登录`() {
        userService.register("alice", "s3cret-pass", null)

        val stored = users.findByUsername("alice")!!
        assertFalse(stored.passwordHash.contains("s3cret-pass"), "口令不得以明文出现在存储串里")
        assertTrue(stored.passwordHash.startsWith("pbkdf2$"), "应带算法标识，便于日后上调迭代次数")

        val session = userService.login("alice", "s3cret-pass")
        assertEquals("alice", session.user.username)
        assertTrue(session.token.isNotBlank())
    }

    @Test
    fun `用户名大小写归一为同一账号`() {
        userService.register("Bob_01", "s3cret-pass", "小明")

        val session = userService.login("bob_01", "s3cret-pass")

        assertEquals("bob_01", session.user.username)
        assertEquals("小明", session.user.nickname)
    }

    @Test
    fun `重复注册同名账号被拒绝`() {
        userService.register("carol", "s3cret-pass", null)

        val e = assertFailsWith<AuthException> { userService.register("Carol", "another-pass", null) }

        assertEquals(AuthException.USERNAME_TAKEN, e.code)
    }

    @Test
    fun `口令错误只报笼统原因，不泄露用户名是否存在`() {
        userService.register("dave", "s3cret-pass", null)

        val wrongPassword = assertFailsWith<AuthException> { userService.login("dave", "nope-nope") }
        val unknownUser = assertFailsWith<AuthException> { userService.login("dave_ghost", "nope-nope") }

        assertEquals(AuthException.BAD_CREDENTIALS, wrongPassword.code)
        assertEquals(AuthException.BAD_CREDENTIALS, unknownUser.code)
    }

    @Test
    fun `登出后令牌立即失效`() {
        val session = userService.register("erin", "s3cret-pass", null)

        userService.logout(session.token)

        val e = assertFailsWith<AuthException> { userService.authenticate(session.token) }
        assertEquals(AuthException.UNAUTHORIZED, e.code)
    }

    @Test
    fun `连续登录失败达阈值后暂时锁定`() {
        userService.register("frank", "s3cret-pass", null)
        repeat(5) {
            assertFailsWith<AuthException> { userService.login("frank", "wrong-pass") }
        }

        // 此时即使口令正确也应被节流拦下，否则节流形同虚设
        val e = assertFailsWith<AuthException> { userService.login("frank", "s3cret-pass") }
        assertEquals(AuthException.RATE_LIMITED, e.code)
    }

    @Test
    fun `登录下单并支付成功后账号获得 PRO`() {
        val session = userService.register("grace", "s3cret-pass", null)
        val no = "T_ACCOUNT_PRO"
        payOrderService.recordCreated(no, "pro_permanent", "句阁 PRO 会员", 199, userId = session.user.id)

        assertEquals(PayOrderService.MarkPaidOutcome.Marked, payOrderService.markPaid(no, "TRADE_A", "BUYER_A", null, "1.99"))

        val me = userService.authenticate(session.token)
        assertTrue(me.pro, "付过款的账号必须能拿回 PRO，这是账号存在的唯一理由")
        assertEquals(no, me.proOutTradeNo)
    }

    @Test
    fun `重复通知不会重复开通 PRO`() {
        val session = userService.register("heidi", "s3cret-pass", null)
        val no = "T_ACCOUNT_IDEMPOTENT"
        payOrderService.recordCreated(no, "pro_permanent", "句阁 PRO 会员", 199, userId = session.user.id)
        payOrderService.markPaid(no, "TRADE_B", "BUYER_B", null, "1.99")

        val firstActivatedAt = users.findByUsername("heidi")!!.proActivatedAt
        payOrderService.markPaid(no, "TRADE_B", "BUYER_B", null, "1.99")

        assertEquals(firstActivatedAt, users.findByUsername("heidi")!!.proActivatedAt)
    }

    @Test
    fun `游客订单支付成功后不产生账号归属`() {
        val no = "T_GUEST_ORDER"
        payOrderService.recordCreated(no, "pro_permanent", "句阁 PRO 会员", 199)

        assertEquals(PayOrderService.MarkPaidOutcome.Marked, payOrderService.markPaid(no, "TRADE_C", null, null, "1.99"))

        assertEquals(PayOrder.STATUS_PAID, payOrderService.find(no)!!.status)
        assertNull(payOrderService.find(no)!!.userId)
    }

    @Test
    fun `游客下单不带令牌按游客处理`() {
        // 没带令牌是正常游客购买，PRO 只落在本机
        assertNull(userService.resolvePurchaseUserId(null))
        assertNull(userService.resolvePurchaseUserId(""))
    }

    @Test
    fun `令牌失效时下单不静默降级为游客`() {
        // 静默降级会让用户以为买到了账号上、实际只落在本机，换机后找不回，
        // 因此令牌存在却无效时必须抛 UNAUTHORIZED，让客户端提示重新登录
        val e = assertFailsWith<AuthException> { userService.resolvePurchaseUserId("not-a-real-token") }
        assertEquals(AuthException.UNAUTHORIZED, e.code)

        // 登出后令牌同样失效，也不得降级
        val session = userService.register("ivan", "s3cret-pass", null)
        userService.logout(session.token)
        assertFailsWith<AuthException> { userService.resolvePurchaseUserId(session.token) }
    }

    @Test
    fun `有效令牌下单归属到账号`() {
        val session = userService.register("judy", "s3cret-pass", null)
        assertEquals(session.user.id, userService.resolvePurchaseUserId(session.token))
    }

    @Test
    fun `过期令牌会被清理`() {
        val session = userService.register("karl", "s3cret-pass", null)
        // 令牌 TTL 为 365 天，正常不会过期；把它拨到过去，模拟自然过期
        val stored = tokens.findAll().first { it.userId == session.user.id }
        stored.expiresAt = System.currentTimeMillis() - 1_000
        tokens.save(stored)

        val removed = userService.purgeExpiredTokens()

        assertEquals(1L, removed, "过期令牌应被清理")
        assertFailsWith<AuthException> { userService.authenticate(session.token) }
    }
}