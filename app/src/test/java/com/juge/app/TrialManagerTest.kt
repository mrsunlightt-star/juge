package com.juge.app

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.Signature
import com.juge.app.data.EntitlementVerifier
import com.juge.app.data.TrialManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 会员判定状态机的测试。
 *
 * 用 sdk = 27 跑：TrialManager 在 API 28+ 走 SigningInfo，在低版本走已废弃的
 * `PackageInfo.signatures`。当前 Robolectric 的 ShadowPackageManager 只能方便地
 * 注入后者，而这里要验证的「多键一致性 + 校验和 + 换签名失效 + 凭证验签」
 * 与 API 版本无关。
 *
 * ## v3 之后这个类多守一条线
 *
 * 旧版本类注释写着「不覆盖 APK 被 root 后直接改 SharedPreferences——对 1.99 元买断
 * 产品那是有意接受的防盗水位」。那句话在 v3 已经不成立了：现在即便把 prefs 里
 * 六个键全写对、checksum 也算对，仍然会因为缺少**服务端签名的凭证**而判为未激活。
 * 下面的 `本地状态全对但没有凭证仍然不放行` 与 `自己造一把密钥签的凭证不放行`
 * 就是这个新边界的回归测试。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class TrialManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        installSelfSignature("signature-A")
        TestEntitlements.installTestPublicKey()
        resetTrialSingleton()
    }

    @After
    fun tearDown() {
        TestEntitlements.clearTestPublicKey()
    }

    @Test
    fun `初始状态未激活`() {
        assertFalse(TrialManager.getInstance(context).isActivated())
    }

    @Test
    fun `使用合法凭证激活后可读出激活记录`() {
        val tm = TrialManager.getInstance(context)
        assertNull(tm.activationRecord())

        assertTrue(
            "激活必须报告持久化成功",
            tm.activateWithEntitlement(
                credential = TestEntitlements.sign(),
                payMethod = TrialManager.PAY_METHOD_ALIPAY,
            ),
        )

        assertTrue(tm.isActivated())
        val record = tm.activationRecord()
        assertNotNull(record)
        assertEquals(TrialManager.PAY_METHOD_ALIPAY, record!!.payMethod)
        assertTrue(record.activatedAt > 0L)
    }

    @Test
    fun `凭证里的付款时间优先于调用方传入的当前时间`() {
        val tm = TrialManager.getInstance(context)
        val paidAt = 1_700_000_000_000L
        tm.activateWithEntitlement(
            credential = TestEntitlements.sign(paidAt = paidAt),
            payMethod = TrialManager.PAY_METHOD_RECOVER,
        )

        assertEquals(paidAt, tm.activationRecord()?.activatedAt)
        assertEquals(paidAt, tm.entitlement()?.paidAt)
    }

    @Test
    fun `重置后回到未激活`() {
        val tm = TrialManager.getInstance(context)
        tm.activateWithEntitlement(TestEntitlements.sign(), TrialManager.PAY_METHOD_RECOVER)
        assertTrue(tm.isActivated())

        tm.resetActivation()

        assertFalse(tm.isActivated())
        assertNull(tm.activationRecord())
        assertNull("重置必须连凭证一起删", tm.entitlement())
    }

    @Test
    fun `篡改任意一个激活键都会失效`() {
        val tm = TrialManager.getInstance(context)
        tm.activateWithEntitlement(TestEntitlements.sign(), TrialManager.PAY_METHOD_ALIPAY)
        assertTrue(tm.isActivated())

        val prefs = context.getSharedPreferences("trial_activation_v2_prefs", Context.MODE_PRIVATE)
        val trueKey = prefs.all.entries.firstOrNull { it.value == true }?.key
        assertNotNull("应先写入至少一个 true 的混淆键", trueKey)
        prefs.edit().putBoolean(trueKey!!, false).commit()

        assertFalse("三键不一致必须判为未激活", tm.isActivated())
    }

    @Test
    fun `换签名重打包后原有激活失效`() {
        val tm = TrialManager.getInstance(context)
        tm.activateWithEntitlement(TestEntitlements.sign(), TrialManager.PAY_METHOD_ALIPAY)
        assertTrue(tm.isActivated())

        // 模拟被重新打包（签名变化）
        installSelfSignature("signature-B")
        resetTrialSingleton()

        assertFalse("签名不匹配时必须拒绝放行", TrialManager.getInstance(context).isActivated())
    }

    @Test
    fun `读不到签名时拒绝放行而不是默认激活`() {
        // 让 getPackageInfo 抛 NameNotFoundException
        shadowOf(context.packageManager).removePackage(context.packageName)
        resetTrialSingleton()

        assertFalse(TrialManager.getInstance(context).isActivated())
    }

    // ── v3 新增：凭证是唯一不可本地伪造的一环 ──

    @Test
    fun `凭证验签失败时拒绝写入激活状态`() {
        val tm = TrialManager.getInstance(context)

        assertFalse(
            "自己造一把密钥签的凭证不能激活",
            tm.activateWithEntitlement(
                TestEntitlements.signWithForeignKey(),
                TrialManager.PAY_METHOD_ALIPAY,
            ),
        )

        assertFalse("被拒绝的写入不能留下任何痕迹", tm.isActivated())
        assertNull(tm.entitlement())
    }

    @Test
    fun `篡改 payload 后签名不再匹配`() {
        val tm = TrialManager.getInstance(context)

        assertFalse(tm.activateWithEntitlement(TestEntitlements.signThenTamper()))
        assertFalse(tm.isActivated())
    }

    @Test
    fun `其它商品的凭证不认`() {
        val tm = TrialManager.getInstance(context)

        assertFalse(
            "productId 不是 pro_permanent 的凭证即便签名合法也不能解锁 PRO",
            tm.activateWithEntitlement(
                TestEntitlements.sign(productId = "pro_monthly"),
                TrialManager.PAY_METHOD_ALIPAY,
            ),
        )
        assertFalse(tm.isActivated())
    }

    @Test
    fun `格式错误的凭证直接拒绝而不是抛异常`() {
        val tm = TrialManager.getInstance(context)

        assertFalse(tm.activateWithEntitlement(""))
        assertFalse(tm.activateWithEntitlement("v2.abc.def"))
        assertFalse(tm.activateWithEntitlement("不是凭证"))
        assertFalse(tm.activateWithEntitlement("v1.only-two-parts"))
        assertFalse(tm.isActivated())
    }

    @Test
    fun `本地状态全对但没有凭证仍然不放行`() {
        val tm = TrialManager.getInstance(context)
        // 先正常激活一次，让签名锁与三键都处于「合理」状态
        tm.activateWithEntitlement(TestEntitlements.sign(), TrialManager.PAY_METHOD_ALIPAY)
        assertTrue(tm.isActivated())

        // 模拟攻击者：把凭证删掉，但保留三键与 checksum
        // 这等价于「反编译照着抄算法、手写 prefs」所能做到的最好结果
        val prefs = context.getSharedPreferences("trial_activation_v2_prefs", Context.MODE_PRIVATE)
        val entitlementKey = prefs.all.keys.firstOrNull { runCatching { prefs.getString(it, null)?.startsWith("v1.") == true }.getOrDefault(false) }
        assertNotNull("激活后应存在凭证键", entitlementKey)
        prefs.edit().remove(entitlementKey).commit()

        assertFalse("没有服务端签名就只能是免费版", tm.isActivated())
    }

    @Test
    fun `开发开关在 release 包不生效`() {
        val tm = TrialManager.getInstance(context)

        // Robolectric 的 unit test 跑在 debug 变体下，BuildConfig.DEBUG 为 true，
        // 因此这里只能验证「activateDebug 不写凭证」这一半；
        // release 行为由 BuildConfig.DEBUG 的分支保证（见 isActivated 第 4 步）。
        assertTrue(tm.activateDebug())
        assertTrue(tm.isActivated())
        assertNull("开发开关不写凭证，因为它本就不该是授权来源", tm.entitlement())
    }

    @Suppress("DEPRECATION")
    private fun installSelfSignature(seed: String) {
        val pm = context.packageManager
        val info: PackageInfo = pm.getPackageInfo(context.packageName, 0)
        info.signatures = arrayOf(Signature(seed.toByteArray()))
        shadowOf(pm).installPackage(info)
    }

    private fun resetTrialSingleton() {
        runCatching {
            // Kotlin 把 companion 里的 @Volatile 属性提升为外层类的静态字段
            val field = Class.forName("com.juge.app.data.TrialManager")
                .getDeclaredField("_instance")
                .apply { isAccessible = true }
            field.set(null, null)
        }
    }
}
