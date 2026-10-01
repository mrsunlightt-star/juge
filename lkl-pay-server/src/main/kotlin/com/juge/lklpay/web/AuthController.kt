package com.juge.lklpay.web

import com.juge.lklpay.domain.UserAccount
import com.juge.lklpay.service.AuthException
import com.juge.lklpay.service.UserService
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 账号接口。
 *
 * 全部为可选能力：App 不登录也能完整使用，登录的唯一收益是换机/重装后找回已购 PRO。
 * 登录态走 `Authorization: Bearer <token>`，令牌明文只在注册/登录响应里出现一次。
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(private val userService: UserService) {

    private val log = LoggerFactory.getLogger(AuthController::class.java)

    data class RegisterRequest(
        val username: String = "",
        val password: String = "",
        val nickname: String? = null,
    )

    data class LoginRequest(
        val username: String = "",
        val password: String = "",
    )

    @PostMapping("/register")
    fun register(@RequestBody req: RegisterRequest, request: HttpServletRequest): ResponseEntity<*> =
        respond { sessionBody(userService.register(req.username, req.password, req.nickname, clientIp(request))) }

    @PostMapping("/login")
    fun login(@RequestBody req: LoginRequest, request: HttpServletRequest): ResponseEntity<*> =
        respond { sessionBody(userService.login(req.username, req.password, clientIp(request))) }

    /** 登出：作废当前令牌。重复调用无副作用 */
    @PostMapping("/logout")
    fun logout(@RequestHeader(value = "Authorization", required = false) authorization: String?): ResponseEntity<*> =
        respond {
            userService.logout(bearerToken(authorization))
            mapOf("success" to true)
        }

    /**
     * 当前账号信息。App 启动时用它回灌 PRO 状态——
     * 换机后本地没有激活记录，这一步是「找回」的唯一入口。
     */
    @GetMapping("/me")
    fun me(@RequestHeader(value = "Authorization", required = false) authorization: String?): ResponseEntity<*> =
        respond { mapOf("success" to true, "data" to userBody(userService.authenticate(bearerToken(authorization)))) }

    private fun respond(block: () -> Any): ResponseEntity<*> =
        try {
            ResponseEntity.ok(block())
        } catch (e: AuthException) {
            if (e.code == AuthException.BAD_CREDENTIALS) {
                log.warn("登录失败：{}", e.message)
            }
            ResponseEntity.status(statusFor(e.code)).body(
                mapOf("success" to false, "code" to e.code, "message" to e.message),
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(
                mapOf("success" to false, "code" to "INVALID_ARGUMENT", "message" to e.message),
            )
        } catch (e: Exception) {
            log.error("账号接口异常", e)
            ResponseEntity.internalServerError().body(
                mapOf("success" to false, "code" to "INTERNAL_ERROR", "message" to "服务暂时不可用，请稍后重试"),
            )
        }

    private fun statusFor(code: String): HttpStatus = when (code) {
        AuthException.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED
        AuthException.BAD_CREDENTIALS -> HttpStatus.UNAUTHORIZED
        AuthException.USERNAME_TAKEN -> HttpStatus.CONFLICT
        AuthException.RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS
        else -> HttpStatus.BAD_REQUEST
    }

    private fun sessionBody(session: UserService.Session) = mapOf(
        "success" to true,
        "data" to mapOf(
            "token" to session.token,
            "user" to userBody(session.user),
        ),
    )

    private fun userBody(user: UserAccount) = mapOf(
        "id" to user.id,
        "username" to user.username,
        "nickname" to user.nickname,
        "pro" to user.pro,
        "proActivatedAt" to user.proActivatedAt,
    )

    /** 兼容 `Bearer xxx` 与裸令牌两种写法 */
    private fun bearerToken(authorization: String?): String? {
        val raw = authorization?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return raw.removePrefix("Bearer ").removePrefix("bearer ").trim().ifEmpty { null }
    }

    /**
     * 取来源 IP。服务在 Nginx 之后，`remoteAddr` 恒为反代地址，
     * 因此优先读 `X-Forwarded-For`（取最左侧的真实客户端），再退到 `X-Real-IP`。
     */
    private fun clientIp(request: HttpServletRequest): String? {
        request.getHeader("X-Forwarded-For")
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        return request.getHeader("X-Real-IP")?.trim()?.takeIf { it.isNotEmpty() }
            ?: request.remoteAddr?.takeIf { it.isNotEmpty() }
    }
}