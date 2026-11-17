package com.juge.app.data

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import com.juge.app.BuildConfig
import timber.log.Timber
import java.security.MessageDigest

/**
 * 付费激活管理器
 *
 * ## v3：把「谁说了算」从本机搬到服务端
 *
 * v2 的判定链是「签名锁 → 三键一致 → checksum」，全部材料都在 APK 里：
 * 签名证书从安装包里取、checksum 的盐与算法照抄，往 SharedPreferences 写六个键
 * 就是永久 PRO。当时认为「对买断制小产品这是可接受的防盗水位（防君子不防高手）」，
 * 但那句话成立的前提是**别的防线在起作用**——而这里它就是唯一的防线，
 * 于是「防君子」实际等于「谁愿意花十分钟反编译谁就能白拿」。
 *
 * v3 保留上面那些本地校验（它们仍能挡住「只改一个布尔」的低成本篡改、
 * 仍能让换个签名重打包的包失效），但**放行的最后一票交给服务端签名**：
 *
 * - 真授权 = [EntitlementVerifier] 用内置公钥验过的一段凭证
 * - 凭证只能由服务端的私钥签出来，而那把私钥不在 APK 里
 * - 改本地任何字段都无法凭空造出验得过的签名
 *
 * 代价：清除数据 / 换设备后必须联网走一次「用支付宝找回」才能恢复，
 * 离线状态下不认手工写的激活记录。这是有意选择的取舍——见 [isActivated]。
 *
 * ## 没有存量迁移问题
 *
 * v2 写的本地记录一律不带凭证，因此升级后会被判为未激活。这是可接受的：
 * 本版本之前服务端的 /api/alipay 这一组接口在生产环境从未接通（反代缺失），
 * 不存在真正付过款却只留下 v2 记录的用户。若将来需要迁移，
 * 正确做法是让服务端对旧订单补发凭证，而不是在客户端放宽判定。
 */
class TrialManager private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 获取签名哈希作为混淆密钥
    private val signatureKey: String = getSignatureHash(appContext)

    /**
     * 一条激活记录，用于「我的」页展示激活时间与支付方式。
     */
    data class ActivationRecord(
        val activatedAt: Long,
        val payMethod: String
    )

    /**
     * 是否已永久激活会员。
     *
     * 判定顺序（任一环不成立即 false）：
     * 1. APK 签名与首次锁定值一致 —— 挡住「换个签名重新打包后沿用激活状态」
     * 2. 三个布尔键一致 —— 挡住「只改一个键」
     * 3. checksum 匹配 —— 挡住「直接把布尔改成 true」
     * 4. **开发开关只在 debug 包放行** —— release 包里即便写进了 DEV 标记也不算数
     * 5. 存在一份验签通过的权益凭证 —— 挡住「照抄算法自造全部本地状态」
     *
     * 前三步都是本地可复算的，所以它们只能提高篡改成本；真正不可伪造的是第 5 步。
     * 这也是为什么第 5 步不成立时**只返回 false、不清库**：清库会让「用户其实买过、
     * 只是数据迁移出问题」变成不可诊断的静默降级，留着记录才好排查。
     */
    fun isActivated(): Boolean {
        // 1. 签名验证：检查 APK 签名是否与首次锁定值一致
        if (!isSignatureValid()) {
            Timber.w("TrialManager: signature mismatch, treating as not activated")
            return false
        }

        // 2. 多键校验：读取三个 key 的值，必须全部一致
        val v1 = prefs.getBoolean(obfuscateKey(KEY_ACTIVATED_1), false)
        val v2 = prefs.getBoolean(obfuscateKey(KEY_ACTIVATED_2), false)
        val v3 = prefs.getBoolean(obfuscateKey(KEY_ACTIVATED_3), false)

        if (v1 != v2 || v2 != v3) {
            // 数据不一致，说明被篡改
            Timber.w("TrialManager: activation state inconsistent, resetting")
            resetActivation()
            return false
        }

        if (!v1) return false

        // 3. 校验验证和
        val checksum = prefs.getString(obfuscateKey(KEY_CHECKSUM), null)
        if (checksum != computeChecksum(true)) {
            Timber.w("TrialManager: checksum mismatch, resetting")
            resetActivation()
            return false
        }

        // 4. 开发开关：只在 debug 包生效。
        //    这样即便有人把 release 包的 prefs 写成 DEV，也换不到 PRO。
        val payMethod = prefs.getString(obfuscateKey(KEY_PAY_METHOD), null)
        if (payMethod == PAY_METHOD_DEV) {
            return BuildConfig.DEBUG.also {
                if (!it) Timber.w("TrialManager: DEV activation ignored in release build")
            }
        }

        // 5. 权益凭证：唯一不可本地伪造的一环
        val credential = prefs.getString(obfuscateKey(KEY_ENTITLEMENT), null)
        val entitlement = EntitlementVerifier.verify(credential)
        if (entitlement == null) {
            Timber.w(
                "TrialManager: 本地状态自洽但缺少有效权益凭证（payMethod=%s），判为未激活",
                payMethod,
            )
            return false
        }
        return true
    }

    /**
     * 用服务端签发的权益凭证激活会员。
     *
     * 验签不过一律不落盘并返回 false —— 这是把「伪造」挡在写入口之前，
     * 而不是写进去之后再靠读路径拦。
     *
     * 在 IO 线程调用；commit 失败仍可能更新内存，不能用 isActivated() 代替返回值判断落盘。
     *
     * @param credential 服务端 `/api/alipay/recover` 返回的 `data.credential`
     * @param payMethod  展示用的支付方式标签
     * @param activatedAt 展示用的激活时间；缺省时依次回退到凭证里的 paidAt / 当前时间
     */
    fun activateWithEntitlement(
        credential: String,
        payMethod: String = PAY_METHOD_RECOVER,
        activatedAt: Long = 0L,
    ): Boolean {
        val entitlement = EntitlementVerifier.verify(credential)
        if (entitlement == null) {
            Timber.w("TrialManager: 权益凭证验签失败，拒绝写入激活状态 payMethod=%s", payMethod)
            return false
        }
        val shownAt = when {
            activatedAt > 0L -> activatedAt
            entitlement.paidAt > 0L -> entitlement.paidAt
            else -> System.currentTimeMillis()
        }
        val saved = prefs.edit()
            .putBoolean(obfuscateKey(KEY_ACTIVATED_1), true)
            .putBoolean(obfuscateKey(KEY_ACTIVATED_2), true)
            .putBoolean(obfuscateKey(KEY_ACTIVATED_3), true)
            .putString(obfuscateKey(KEY_CHECKSUM), computeChecksum(true))
            .putString(obfuscateKey(KEY_ENTITLEMENT), credential)
            .putLong(obfuscateKey(KEY_ACTIVATED_AT), shownAt)
            .putString(obfuscateKey(KEY_PAY_METHOD), payMethod)
            .commit()
        if (saved) Timber.i("TrialManager: activated via %s (credential verified)", payMethod)
        else Timber.w("TrialManager: activation commit failed")
        return saved
    }

    /**
     * 开发开关激活（仅 debug 包）。
     *
     * 与 [activateWithEntitlement] 分开命名，是为了让「不带凭证也能激活」这件事
     * 在调用点就一目了然——它只该出现在 BuildConfig.DEBUG 的分支里。
     */
    fun activateDebug(): Boolean {
        if (!BuildConfig.DEBUG) {
            Timber.w("TrialManager: activateDebug() 在 release 包被忽略")
            return false
        }
        return writeState(payMethod = PAY_METHOD_DEV, activatedAt = System.currentTimeMillis(), credential = null)
    }

    /**
     * 重置激活状态
     */
    fun resetActivation() {
        prefs.edit()
            .remove(obfuscateKey(KEY_ACTIVATED_1))
            .remove(obfuscateKey(KEY_ACTIVATED_2))
            .remove(obfuscateKey(KEY_ACTIVATED_3))
            .remove(obfuscateKey(KEY_CHECKSUM))
            .remove(obfuscateKey(KEY_ENTITLEMENT))
            .remove(obfuscateKey(KEY_ACTIVATED_AT))
            .remove(obfuscateKey(KEY_PAY_METHOD))
            .apply()
        Timber.i("TrialManager: activation reset")
    }

    /**
     * 激活记录，供「我的」页展示。
     * 未激活、或本次升级前就已激活（无记录）时返回 null。
     */
    fun activationRecord(): ActivationRecord? {
        if (!isActivated()) return null
        val at = prefs.getLong(obfuscateKey(KEY_ACTIVATED_AT), 0L)
        val method = prefs.getString(obfuscateKey(KEY_PAY_METHOD), null)
        if (at <= 0L || method.isNullOrBlank()) return null
        return ActivationRecord(activatedAt = at, payMethod = method)
    }

    /** 当前凭证内容（已验签）；未激活或凭证无效时为 null。仅供展示与排查。 */
    fun entitlement(): EntitlementVerifier.Entitlement? =
        EntitlementVerifier.verify(prefs.getString(obfuscateKey(KEY_ENTITLEMENT), null))

    // ==================== 内部安全方法 ====================

    private fun writeState(payMethod: String, activatedAt: Long, credential: String?): Boolean {
        val editor = prefs.edit()
            .putBoolean(obfuscateKey(KEY_ACTIVATED_1), true)
            .putBoolean(obfuscateKey(KEY_ACTIVATED_2), true)
            .putBoolean(obfuscateKey(KEY_ACTIVATED_3), true)
            .putString(obfuscateKey(KEY_CHECKSUM), computeChecksum(true))
            .putLong(obfuscateKey(KEY_ACTIVATED_AT), activatedAt)
            .putString(obfuscateKey(KEY_PAY_METHOD), payMethod)
        if (credential == null) editor.remove(obfuscateKey(KEY_ENTITLEMENT))
        else editor.putString(obfuscateKey(KEY_ENTITLEMENT), credential)
        return editor.commit()
    }

    /**
     * 签名校验：首次运行时锁定当前签名哈希，之后每次与锁定值比对。
     * 重打包（签名变化）后锁定值不匹配，激活状态失效；
     * 清除数据可重新锁定，但激活状态存储在同一名 prefs 中，会一并丢失。
     */
    private fun isSignatureValid(): Boolean {
        val current = getSignatureHash(appContext)
        if (current == DEFAULT_SIGNATURE_KEY) {
            // 无法读取签名（异常场景），拒绝放行
            return false
        }
        val lockKey = obfuscateKey(KEY_SIGNATURE_LOCK)
        val locked = prefs.getString(lockKey, null)
        if (locked == null) {
            prefs.edit().putString(lockKey, current).apply()
            return true
        }
        return current == locked
    }

    private fun obfuscateKey(rawKey: String): String {
        // 使用签名哈希对 key 进行混淆
        val keyBytes = rawKey.toByteArray()
        val sigBytes = signatureKey.toByteArray()
        val result = ByteArray(keyBytes.size)
        for (i in keyBytes.indices) {
            result[i] = (keyBytes[i].toInt() xor sigBytes[i % sigBytes.size].toInt()).toByte()
        }
        return Base64.encodeToString(result, Base64.NO_WRAP)
    }

    private fun computeChecksum(activated: Boolean): String {
        val data = "$CHECKSUM_SALT:$activated:${signatureKey.take(16)}"
        val digest = MessageDigest.getInstance("SHA-256")
        return Base64.encodeToString(digest.digest(data.toByteArray()), Base64.NO_WRAP)
    }

    companion object {
        private const val PREFS_NAME = "trial_activation_v2_prefs"
        private const val KEY_ACTIVATED_1 = "a1"
        private const val KEY_ACTIVATED_2 = "a2"
        private const val KEY_ACTIVATED_3 = "a3"
        private const val KEY_CHECKSUM = "chk"
        private const val KEY_SIGNATURE_LOCK = "siglock"
        private const val KEY_ACTIVATED_AT = "actat"
        private const val KEY_PAY_METHOD = "paym"
        private const val KEY_ENTITLEMENT = "ent"
        private const val CHECKSUM_SALT = "TrialManager_v2_2026"
        private const val DEFAULT_SIGNATURE_KEY = "DefaultKeyForTrialManager2026"

        /** 写入激活记录的支付方式标签 */
        const val PAY_METHOD_ALIPAY = "支付宝"

        /** 换机后凭付款时的支付宝账号找回 */
        const val PAY_METHOD_RECOVER = "支付宝找回"

        const val PAY_METHOD_DEV = "开发开关"
        const val PAY_METHOD_UNRECORDED = "未记录"

        @Volatile
        private var _instance: TrialManager? = null

        fun getInstance(context: Context): TrialManager {
            return _instance ?: synchronized(this) {
                _instance ?: TrialManager(context.applicationContext).also { _instance = it }
            }
        }

        /**
         * 获取 APK 签名证书的 SHA-256 哈希。
         * API 28+ 使用 SigningInfo，低版本回退到废弃的 signatures 字段（minSdk=24）。
         */
        private fun getSignatureHash(context: Context): String {
            return try {
                val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val packageInfo = context.packageManager.getPackageInfo(
                        context.packageName,
                        PackageManager.GET_SIGNING_CERTIFICATES
                    )
                    packageInfo.signingInfo?.apkContentsSigners?.firstOrNull()
                } else {
                    @Suppress("DEPRECATION")
                    val packageInfo = context.packageManager.getPackageInfo(
                        context.packageName,
                        PackageManager.GET_SIGNATURES
                    )
                    packageInfo.signatures?.firstOrNull()
                } ?: return DEFAULT_SIGNATURE_KEY

                val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                Base64.encodeToString(digest, Base64.NO_WRAP)
            } catch (e: Exception) {
                Timber.e(e, "TrialManager: failed to get signature hash")
                DEFAULT_SIGNATURE_KEY
            }
        }
    }
}
