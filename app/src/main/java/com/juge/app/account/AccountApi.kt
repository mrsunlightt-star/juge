package com.juge.app.account

import com.juge.app.net.ServerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 账号服务端 API 封装。
 *
 * 账号是可选能力，因此所有方法都返回 [Result]：调用失败只影响「找回 PRO」，
 * 由调用方决定是否提示，不向上抛异常打断别的流程。
 */
object AccountApi {

    data class Account(
        val username: String,
        val nickname: String?,
        val pro: Boolean,
    )

    data class Session(
        val token: String,
        val account: Account,
    )

    /** 服务端返回的预期内失败，[code] 用于区分「登录失效」等需要特殊处理的场景 */
    class ApiException(
        val code: String,
        override val message: String,
    ) : RuntimeException(message)

    const val CODE_UNAUTHORIZED = "UNAUTHORIZED"

    suspend fun register(username: String, password: String, nickname: String?): Result<Session> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                    if (!nickname.isNullOrBlank()) put("nickname", nickname)
                }
                parseSession(ServerClient.post("/api/auth/register", body))
            }
        }

    suspend fun login(username: String, password: String): Result<Session> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                }
                parseSession(ServerClient.post("/api/auth/login", body))
            }
        }

    /** 当前账号信息。App 启动时用它回灌 PRO 状态 */
    suspend fun me(token: String): Result<Account> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = ServerClient.get("/api/auth/me", token)
                ensureSuccess(resp)
                parseAccount(resp.getJSONObject("data"))
            }
        }

    /** 登出：作废服务端令牌。失败也不影响本地清理，因此不返回 Result */
    suspend fun logout(token: String) {
        withContext(Dispatchers.IO) {
            runCatching { ServerClient.post("/api/auth/logout", JSONObject(), token) }
        }
    }

    /**
     * 注销账号：删除服务端账号、全部令牌与订单绑定（订单本体服务端留档对账）。
     * 成功后才由调用方清理本地登录态；失败时本地保持登录，用户可重试。
     */
    suspend fun deleteAccount(token: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureSuccess(ServerClient.post("/api/auth/delete-account", JSONObject(), token))
            }
        }

    private fun parseSession(resp: JSONObject): Session {
        ensureSuccess(resp)
        val data = resp.getJSONObject("data")
        return Session(
            token = data.getString("token"),
            account = parseAccount(data.getJSONObject("user")),
        )
    }

    private fun parseAccount(json: JSONObject) = Account(
        username = json.optString("username"),
        // JSON null 经 optString 会变成字符串 "null"，所以先判 isNull
        nickname = if (json.isNull("nickname")) null else json.optString("nickname").takeIf { it.isNotBlank() },
        pro = json.optBoolean("pro"),
    )

    private fun ensureSuccess(resp: JSONObject) {
        if (resp.optBoolean("success")) return
        throw ApiException(
            code = resp.optString("code").ifBlank { "UNKNOWN" },
            message = resp.optString("message").ifBlank { "操作失败，请稍后重试" },
        )
    }
}