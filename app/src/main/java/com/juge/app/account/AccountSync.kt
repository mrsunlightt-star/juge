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
     * 对账结论。
     *
     * 刻意区分「服务端明确说这个账号不是 PRO」与「这次没问成」：
     * 只有前者才允许撤销本地激活；网络抖动、未登录都必须保持本地状态原样。
     */
    sealed interface Outcome {
        /** 未登录——不登录是正常状态 */
        data object NotLoggedIn : Outcome

        /** 请求失败（网络不可用、服务端异常等）：本次结论不可用 */
        data object Unavailable : Outcome

        /** 服务端给出的权威结论 */
        data class ServerSays(val pro: Boolean) : Outcome
    }

    /**
     * 用服务端结论校正本地账号状态。
     *
     * 令牌已失效时顺手清掉本地账号：否则界面会长期停在「已登录」的假象里，
     * 而实际每次请求都被服务端拒绝。
     *
     * 对账失败不影响任何本地功能。
     */
    suspend fun refresh(context: Context): Outcome {
        val token = AccountStore.token(context) ?: return Outcome.NotLoggedIn
        return AccountApi.me(token).fold(
            onSuccess = { account ->
                AccountStore.updateAccount(context, account.username, account.nickname, account.pro)
                Outcome.ServerSays(account.pro)
            },
            onFailure = { e ->
                if (e is AccountApi.ApiException && e.code == AccountApi.CODE_UNAUTHORIZED) {
                    AccountStore.clear(context)
                }
                Outcome.Unavailable
            },
        )
    }
}
