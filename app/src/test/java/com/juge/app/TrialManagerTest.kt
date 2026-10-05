package com.juge.app

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.Signature
import com.juge.app.data.TrialManager
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
 * 本地激活状态机的测试。
 *
 * 用 sdk = 27 跑：TrialManager 在 API 28+ 走 SigningInfo，在低版本走已废弃的
 * `PackageInfo.signatures`。当前 Robolectric 的 ShadowPackageManager 只能方便地
 * 注入后者，而这里要验证的「多键一致性 + 校验和 + 换签名失效」与 API 版本无关。
 *
 * 注意：本类不覆盖「APK 被 root 后直接改 SharedPreferences」这类攻击——
 * 对 1.99 元买断产品，那是有意接受的防盗水位（防君子不防高手），不是缺陷。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class TrialManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        installSelfSignature("signature-A")
        resetTrialSingleton()
    }

    @Test
    fun `初始状态未激活`() {
        assertFalse(TrialManager.getInstance(context).isActivated())
    }

    @Test
    fun `激活后可读出激活记录`() {
        val tm = TrialManager.getInstance(context)
        assertNull(tm.activationRecord())

        tm.activate(TrialManager.PAY_METHOD_ALIPAY)

        assertTrue(tm.isActivated())
        val record = tm.activationRecord()
        assertNotNull(record)
        assertEquals(TrialManager.PAY_METHOD_ALIPAY, record!!.payMethod)
        assertTrue(record.activatedAt > 0L)
    }

    @Test
    fun `重置后回到未激活`() {
        val tm = TrialManager.getInstance(context)
        tm.activate(TrialManager.PAY_METHOD_ACCOUNT)
        assertTrue(tm.isActivated())

        tm.resetActivation()

        assertFalse(tm.isActivated())
        assertNull(tm.activationRecord())
    }

    @Test
    fun `篡改任意一个激活键都会失效`() {
        val tm = TrialManager.getInstance(context)
        tm.activate(TrialManager.PAY_METHOD_ALIPAY)
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
        tm.activate(TrialManager.PAY_METHOD_ALIPAY)
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
