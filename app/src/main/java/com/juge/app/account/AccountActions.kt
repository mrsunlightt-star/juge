package com.juge.app.account

import android.content.Context

/**
 * 账号状态的破坏性/半破坏性动作的唯一出处。
 *
 * 登录之后，「账号」弹窗（付费墙入口）与「我的」子页面（顶栏入口）都能退出登录或注销账号。
 * 这两处必须在同一份实现上：退出登录要不要动本地 PRO、注销失败时本地会话保不保留，
 * 都是产品规则，复制两份迟早会分叉。
 *
 * 两条规则（与 `AccountStore` 的注释一致）：
 * 1. **退出登录不收回本地已解锁的风格**——用户已经付过款；
 * 2. **注销必须服务端成功后才清本地会话**——失败时保持登录，用户可重试。
 */
object AccountActions {

    /**
     * 退出登录：先尽力作废服务端令牌（失败不影响本地），再清本地会话。
     *
     * 在调用方协程里执行；[AccountApi] 内部已切到 IO。
     */
    suspend fun logout(context: Context) {
        val token = AccountStore.token(context)
        if (token != null) AccountApi.logout(token)
        AccountStore.clear(context)
    }

    /**
     * 注销账号：服务端删除成功后才清本地会话。
     *
     * 本地没有令牌时直接清本地（幂等，避免卡在「未登录但仍有残留」的状态）。
     */
    suspend fun deleteAccount(context: Context): Result<Unit> {
        val token = AccountStore.token(context)
        if (token == null) {
            AccountStore.clear(context)
            return Result.success(Unit)
        }
        return AccountApi.deleteAccount(token).onSuccess { AccountStore.clear(context) }
    }
}
