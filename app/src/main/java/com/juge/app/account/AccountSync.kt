package com.juge.app.account

import android.content.Context

/**
 * 本地账号状态与服务端的对账。
 *
 * 存在的理由：PRO 的权威值在服务端，App 本地那份只是缓存。
 * 用户换手机或重装后本地什么都没有，登录状态下跑一次对账就是「找回 PRO」的全部动作。
 */
object AccountSync {

    /**
     * 用服务端结论校正本地账号状态，并返回服务端是否确认该账号已购 PRO。
     *
     * 令牌已失效时顺手清掉本地账号：否则界面会长期停在「已登录」的假象里，
     * 而实际每次请求都被服务端拒绝。
     *
     * 未登录或网络不可用时返回 false——对账失败不该影响任何本地功能。
     */
    suspend fun refresh(context: Context): Boolean {
        val token = AccountStore.token(context) ?: return false
        return AccountApi.me(token).fold(
            onSuccess = { account ->
                AccountStore.updateAccount(context, account.username, account.nickname, account.pro)
                account.pro
            },
            onFailure = { e ->
                if (e is AccountApi.ApiException && e.code == AccountApi.CODE_UNAUTHORIZED) {
                    AccountStore.clear(context)
                }
                false
            },
        )
    }
}