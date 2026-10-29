package com.juge.app

import android.content.Context
import com.juge.app.account.AccountApi
import com.juge.app.account.AccountStore
import com.juge.app.account.AccountSync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
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
    fun `同一令牌成功响应更新账号并发布权威结论`() = runBlocking {
        AccountStore.saveSession(context, "current", "alice", null, pro = false)

        val outcome = AccountSync.refresh(context) { token ->
            assertEquals("current", token)
            Result.success(AccountApi.Account("alice", "新昵称", pro = true))
        }

        assertEquals(AccountSync.Outcome.ServerSays(true), outcome)
        assertEquals(AccountStore.Snapshot("current", "alice", "新昵称", true), AccountStore.snapshot(context))
    }

    @Test
    fun `旧令牌成功响应不能覆盖新会话也不能向界面下发旧权益`() = runBlocking {
        // false 不得撤销新账号的 PRO；true 同样不能把旧账号权益授给新会话。
        for (oldPro in listOf(false, true)) {
            AccountStore.saveSession(context, "old", "alice", "旧账号", pro = oldPro)
            val requested = CompletableDeferred<Unit>()
            val response = CompletableDeferred<Result<AccountApi.Account>>()
            val refresh = async {
                AccountSync.refresh(context) { token ->
                    assertEquals("old", token)
                    requested.complete(Unit)
                    response.await()
                }
            }
            requested.await()
            AccountStore.saveSession(context, "new", "bob", "新账号", pro = !oldPro)
            val newSession = AccountStore.snapshot(context)
            response.complete(Result.success(AccountApi.Account("alice", "过期昵称", oldPro)))

            assertEquals(AccountSync.Outcome.Unavailable, refresh.await())
            assertEquals(newSession, AccountStore.snapshot(context))
        }
    }

    @Test
    fun `旧令牌未授权响应不能清空新会话`() = runBlocking {
        AccountStore.saveSession(context, "old", "alice", null, pro = false)
        val requested = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Result<AccountApi.Account>>()
        val refresh = async {
            AccountSync.refresh(context) {
                requested.complete(Unit)
                response.await()
            }
        }
        requested.await()
        AccountStore.saveSession(context, "new", "bob", "新账号", pro = true)
        val newSession = AccountStore.snapshot(context)
        response.complete(unauthorized())

        assertEquals(AccountSync.Outcome.Unavailable, refresh.await())
        assertEquals(newSession, AccountStore.snapshot(context))
    }

    @Test
    fun `退出登录后成功或未授权响应均不可复活账号或下发权益`() = runBlocking {
        val results = listOf(Result.success(AccountApi.Account("alice", null, true)), unauthorized())
        for (result in results) {
            AccountStore.saveSession(context, "old", "alice", null, pro = false)
            val requested = CompletableDeferred<Unit>()
            val response = CompletableDeferred<Result<AccountApi.Account>>()
            val refresh = async {
                AccountSync.refresh(context) {
                    requested.complete(Unit)
                    response.await()
                }
            }
            requested.await()
            AccountStore.clear(context)
            response.complete(result)

            assertEquals(AccountSync.Outcome.Unavailable, refresh.await())
            assertNull(AccountStore.snapshot(context))
            val prefs = context.getSharedPreferences("account_prefs", Context.MODE_PRIVATE)
            assertEquals(emptyMap<String, Any>(), prefs.all)
        }
    }

    @Test
    fun `当前令牌未授权才清理本地会话`() = runBlocking {
        AccountStore.saveSession(context, "current", "alice", null, pro = true)

        assertEquals(AccountSync.Outcome.Unavailable, AccountSync.refresh(context) { unauthorized() })
        assertNull(AccountStore.snapshot(context))
    }

    @Test
    fun `普通网络失败不清账号也不发布权益结论`() = runBlocking {
        AccountStore.saveSession(context, "current", "alice", null, pro = true)
        val session = AccountStore.snapshot(context)

        val outcome = AccountSync.refresh(context) { Result.failure(IllegalStateException("offline")) }

        assertEquals(AccountSync.Outcome.Unavailable, outcome)
        assertEquals(session, AccountStore.snapshot(context))
    }

    private fun unauthorized(): Result<AccountApi.Account> = Result.failure(
        AccountApi.ApiException(AccountApi.CODE_UNAUTHORIZED, "令牌已失效"),
    )

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
