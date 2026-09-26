package com.juge.lklpay.service

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 口令哈希：PBKDF2-HMAC-SHA256。
 *
 * 不用单轮 SHA 的原因很直接——单轮摘要可被彩虹表直接命中；
 * 加盐 + 高迭代次数把离线爆破的成本抬到不划算的水平。
 * 存储串自带算法参数，将来上调迭代次数时旧口令仍能校验通过。
 */
object PasswordHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val PREFIX = "pbkdf2"

    private val random = SecureRandom()
    private val encoder: Base64.Encoder = Base64.getEncoder()
    private val decoder: Base64.Decoder = Base64.getDecoder()

    /** 产出 `pbkdf2$迭代次数$盐$摘要`，四段均可公开 */
    fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val key = derive(password, salt, ITERATIONS)
        return buildString {
            append(PREFIX); append('$'); append(ITERATIONS); append('$')
            append(encoder.encodeToString(salt)); append('$')
            append(encoder.encodeToString(key))
        }
    }

    /** 校验口令。存储串损坏时返回 false，而不是抛异常打断登录流程 */
    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { decoder.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { decoder.decode(parts[3]) }.getOrNull() ?: return false
        // 恒定时间比较：逐字节提前返回会泄露「已匹配多少位」
        return MessageDigest.isEqual(expected, derive(password, salt, iterations))
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}