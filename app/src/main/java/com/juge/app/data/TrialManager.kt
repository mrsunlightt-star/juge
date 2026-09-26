package com.juge.app.data

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import timber.log.Timber
import java.security.MessageDigest

/**
 * 付费激活管理器
 *
 * 安全加固说明：
 * 1. 签名校验：首次运行时锁定 APK 签名哈希，后续每次校验签名是否与锁定值一致，防止被重新打包后沿用激活状态
 * 2. 多键混淆：将激活状态分散存储在多个 key 中，任意一个被篡改都会导致校验失败
 * 3. 校验和：存储激活状态的哈希值，防止直接修改布尔值
 * 4. 密钥派生：使用签名哈希作为混淆密钥，确保不同签名下数据不可互相移植
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
     * 是否已永久激活会员
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

        // 3. 校验验证和
        if (v1) {
            val checksum = prefs.getString(obfuscateKey(KEY_CHECKSUM), null)
            val expected = computeChecksum(true)
            if (checksum != expected) {
                Timber.w("TrialManager: checksum mismatch, resetting")
                resetActivation()
                return false
            }
        }

        return v1
    }

    /**
     * 激活会员。
     *
     * 激活记录（时间 + 支付方式）仅用于「我的」页展示，不参与 isActivated() 的放行判定，
     * 因此故意不纳入 [computeChecksum]——否则新增字段会让存量用户的校验和失配而被误判为未激活。
     */
    fun activate(
        payMethod: String = PAY_METHOD_UNRECORDED,
        activatedAt: Long = System.currentTimeMillis()
    ) {
        prefs.edit()
            .putBoolean(obfuscateKey(KEY_ACTIVATED_1), true)
            .putBoolean(obfuscateKey(KEY_ACTIVATED_2), true)
            .putBoolean(obfuscateKey(KEY_ACTIVATED_3), true)
            .putString(obfuscateKey(KEY_CHECKSUM), computeChecksum(true))
            .putLong(obfuscateKey(KEY_ACTIVATED_AT), activatedAt)
            .putString(obfuscateKey(KEY_PAY_METHOD), payMethod)
            .apply()
        Timber.i("TrialManager: activated via %s", payMethod)
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

    // ==================== 内部安全方法 ====================

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
        private const val CHECKSUM_SALT = "TrialManager_v2_2026"
        private const val DEFAULT_SIGNATURE_KEY = "DefaultKeyForTrialManager2026"

        /** 写入激活记录的支付方式标签 */
        const val PAY_METHOD_ALIPAY = "支付宝"
        const val PAY_METHOD_ACCOUNT = "账号找回"
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
