package com.juge.app.account

import android.content.Context
import android.content.SharedPreferences

/**
 * 账号本地存储。
 *
 * 只存令牌与展示所需的账号快照，**不存密码**。
 * 这里的 PRO 标记是服务端权威值的缓存：登录后用 `/api/auth/me` 的结果回灌，
 * 本地 [com.juge.app.data.TrialManager] 那份才是界面真正读的开关。
 */
object AccountStore {

    private const val PREFS = "account_prefs"
    private const val KEY_TOKEN = "token"
    private const val KEY_USERNAME = "username"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_PRO = "pro"

    data class Snapshot(
        val token: String,
        val username: String,
        val nickname: String?,
        val pro: Boolean,
    ) {
        /** 界面上优先显示昵称，没设才退回登录名 */
        val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: username
    }

    /** 未登录返回 null——不登录是正常状态，不是异常 */
    fun snapshot(context: Context): Snapshot? {
        val prefs = prefs(context)
        val token = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        return Snapshot(
            token = token,
            username = prefs.getString(KEY_USERNAME, "").orEmpty(),
            nickname = prefs.getString(KEY_NICKNAME, null),
            pro = prefs.getBoolean(KEY_PRO, false),
        )
    }

    fun token(context: Context): String? = snapshot(context)?.token

    fun saveSession(context: Context, token: String, username: String, nickname: String?, pro: Boolean) {
        prefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USERNAME, username)
            .putString(KEY_NICKNAME, nickname)
            .putBoolean(KEY_PRO, pro)
            .apply()
    }

    /** 刷新账号快照，令牌保持不变 */
    fun updateAccount(context: Context, username: String, nickname: String?, pro: Boolean) {
        prefs(context).edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_NICKNAME, nickname)
            .putBoolean(KEY_PRO, pro)
            .apply()
    }

    /**
     * 退出登录。只清账号，**不动本地的 PRO 激活状态**——
     * 用户已经付过款，退出登录不该把已解锁的风格收回去。
     */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}