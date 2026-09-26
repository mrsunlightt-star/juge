package com.juge.lklpay.service

/**
 * 账号业务的预期内失败。
 *
 * 用 [code] 而非 HTTP 状态码作为契约：客户端据 code 决定文案与后续动作，
 * 状态码只表达大类（409 / 401 / 429）。
 */
class AuthException(
    val code: String,
    override val message: String,
) : RuntimeException(message) {

    companion object {
        const val USERNAME_TAKEN = "USERNAME_TAKEN"
        const val BAD_CREDENTIALS = "BAD_CREDENTIALS"
        const val RATE_LIMITED = "RATE_LIMITED"
        const val UNAUTHORIZED = "UNAUTHORIZED"
    }
}