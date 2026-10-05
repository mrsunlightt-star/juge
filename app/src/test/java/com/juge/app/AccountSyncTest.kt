package com.juge.app

import android.content.Context
import com.juge.app.account.AccountStore
import com.juge.app.account.AccountSync
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 账号对账的结论分类。
 *
 * 这个分类是「能否撤销本地激活」的前提：只有 [AccountSync.Outcome.ServerSays] 且 pro=false
 * 才允许撤销。如果把「未登录 / 请求失败」也当成「不是 PRO」，用户一断网就会掉 PRO。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountSyncTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `未登录时返回 NotLoggedIn 而不是不可用或未购`() = runBlocking {
        AccountStore.clear(context)

        assertEquals(AccountSync.Outcome.NotLoggedIn, AccountSync.refresh(context))
    }

    @Test
    fun `退出登录会清空本地账号快照`() {
        AccountStore.saveSession(context, "token-1", "alice", "爱丽丝", pro = true)
        assertEquals("token-1", AccountStore.token(context))

        AccountStore.clear(context)

        assertNull(AccountStore.token(context))
        assertNull(AccountStore.snapshot(context))
    }

    @Test
    fun `账号快照优先显示昵称`() {
        AccountStore.saveSession(context, "token-2", "alice", "爱丽丝", pro = false)
        assertEquals("爱丽丝", AccountStore.snapshot(context)?.displayName)

        AccountStore.saveSession(context, "token-3", "bob", "   ", pro = false)
        assertEquals("bob", AccountStore.snapshot(context)?.displayName)
    }
}
