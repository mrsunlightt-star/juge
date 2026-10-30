package com.juge.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.juge.app.account.AccountStore
import com.juge.app.data.DbHelper
import com.juge.app.data.TrialManager
import com.juge.app.pay.ProPurchase
import com.juge.app.ui.main.MainTopBar
import com.juge.app.ui.main.MinePage
import com.juge.app.ui.main.formatActivationTime
import com.juge.app.ui.theme.MyApplicationTheme
import java.io.File
import java.math.BigDecimal
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 会员入口的两个状态 + 「我的」子页面。
 *
 * 产品规则（2026-10）：
 * - **未激活**：顶栏保持金色「激活PRO ￥3.99」胶囊，点了进付费墙；
 * - **已激活**：胶囊换成「我的」图标，点开账户中心（会员状态 / 会员账户昵称 / 退出登录 / 注销账号）。
 *
 * 这里只断言语义节点，不做截图基线：页面是弹窗形态，整屏截图基线由 [MainUiBaselineTest] 覆盖主界面本身。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProEntryUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = RuntimeEnvironment.getApplication()

    private fun setTopBar(isActivated: Boolean, onOpenPro: () -> Unit, onOpenMine: () -> Unit) {
        compose.setContent {
            MyApplicationTheme {
                MainTopBar(
                    isActivated = isActivated,
                    dbHelper = DbHelper.getInstance(context),
                    trialManager = TrialManager.getInstance(context),
                    scope = CoroutineScope(Dispatchers.Main),
                    onReloaded = { _, _, _ -> },
                    onActivatedChange = {},
                    onOpenPro = onOpenPro,
                    onOpenMine = onOpenMine,
                    isDarkTheme = false,
                    onToggleDarkTheme = {},
                )
            }
        }
    }

    @Test
    fun `未激活时顶栏保持金色待激活胶囊且没有我的入口`() {
        var openedPro = false
        var openedMine = false
        setTopBar(isActivated = false, onOpenPro = { openedPro = true }, onOpenMine = { openedMine = true })

        compose.onNodeWithText("激活PRO ${ProPurchase.PRICE_TEXT}").assertIsDisplayed()
        compose.onNodeWithContentDescription("我的").assertDoesNotExist()

        compose.onNodeWithText("激活PRO ${ProPurchase.PRICE_TEXT}").performClick()
        assertTrue("点待激活胶囊应打开付费墙", openedPro)
        assertTrue("未激活时不应打开「我的」页", !openedMine)
    }

    @Test
    fun `已激活时顶栏换成我的图标且不再出现待激活胶囊`() {
        var openedPro = false
        var openedMine = false
        setTopBar(isActivated = true, onOpenPro = { openedPro = true }, onOpenMine = { openedMine = true })

        compose.onNodeWithContentDescription("我的").assertIsDisplayed()
        compose.onNodeWithText("激活PRO ${ProPurchase.PRICE_TEXT}").assertDoesNotExist()

        compose.onNodeWithContentDescription("我的").performClick()
        assertTrue("点「我的」图标应打开账户中心", openedMine)
        assertTrue("已激活时不应再弹付费墙", !openedPro)
    }

    private fun setMinePage(
        isActivated: Boolean,
        account: AccountStore.Snapshot?,
        onOpenPro: () -> Unit = {},
        onOpenLogin: () -> Unit = {},
    ) {
        compose.setContent {
            MyApplicationTheme {
                MinePageHost(isActivated, account, onOpenPro, onOpenLogin)
            }
        }
    }

    @Composable
    private fun MinePageHost(
        isActivated: Boolean,
        account: AccountStore.Snapshot?,
        onOpenPro: () -> Unit,
        onOpenLogin: () -> Unit,
    ) {
        MinePage(
            show = true,
            isActivated = isActivated,
            account = account,
            trialManager = TrialManager.getInstance(context),
            onOpenLogin = onOpenLogin,
            onOpenPro = onOpenPro,
            onSessionChanged = {},
            onDismiss = {},
        )
    }

    @Test
    fun `已激活且有账号时我的页展示会员状态昵称与账号操作`() {
        setMinePage(
            isActivated = true,
            account = AccountStore.Snapshot(
                token = "token",
                username = "alice",
                nickname = "阿丽",
                pro = true,
            ),
        )

        compose.onNodeWithText("我的").assertIsDisplayed()
        // 会员账户昵称优先展示昵称，并把登录名标成「会员账户」
        compose.onNodeWithText("阿丽").assertIsDisplayed()
        compose.onNodeWithText("会员账户 · @alice").assertIsDisplayed()
        // 会员状态
        compose.onNodeWithText("PRO 永久会员").assertIsDisplayed()
        compose.onNodeWithText("已激活").assertIsDisplayed()
        compose.onNodeWithText("永久有效 · 一次买断").assertIsDisplayed()
        // 常规账户操作
        compose.onNodeWithText("退出登录").assertIsDisplayed()
        compose.onNodeWithText("注销账号").assertIsDisplayed()
        // 协议入口不在这里重复：App 各页底部的 LegalFooter 已经是协议的唯一入口，
        // 「我的」页再放一份就是重复入口（用户明确要求去掉）
        compose.onNodeWithText("用户协议").assertDoesNotExist()
        compose.onNodeWithText("隐私政策").assertDoesNotExist()
        compose.onNodeWithText("开源许可").assertDoesNotExist()
    }

    @Test
    fun `注销账号是独立按钮 点击后才弹确认弹窗`() {
        setMinePage(
            isActivated = true,
            account = AccountStore.Snapshot(
                token = "token",
                username = "alice",
                nickname = "阿丽",
                pro = true,
            ),
        )

        // 页面本身不内联展开确认区：只有一枚「注销账号」按钮
        compose.onNodeWithText("注销账号").assertIsDisplayed()
        compose.onNodeWithText("确认注销").assertDoesNotExist()

        compose.onNodeWithText("注销账号").performClick()
        compose.onNodeWithText("确认注销").assertIsDisplayed()
        compose.onNodeWithText("注销后将永久删除服务端账号与订单绑定信息", substring = true)
            .assertIsDisplayed()

        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("确认注销").assertDoesNotExist()
        // 取消后仍停留在「我的」页，按钮还在
        compose.onNodeWithText("注销账号").assertIsDisplayed()
    }

    @Test
    fun `未登录时我的页给出登录入口且不显示退出登录`() {
        setMinePage(isActivated = true, account = null)

        compose.onNodeWithText("未登录").assertIsDisplayed()
        compose.onNodeWithText("登录 / 注册").assertIsDisplayed()
        compose.onNodeWithText("退出登录").assertDoesNotExist()
        compose.onNodeWithText("注销账号").assertDoesNotExist()
    }

    @Test
    fun `会员状态失效时我的页就地给出激活入口`() {
        var openedPro = false
        setMinePage(isActivated = false, account = null, onOpenPro = { openedPro = true })

        compose.onNodeWithText("尚未激活 PRO").assertIsDisplayed()
        compose.onNodeWithText("支付宝支付 ${ProPurchase.PRICE_TEXT}").assertIsDisplayed()
        compose.onNodeWithText("支付宝支付 ${ProPurchase.PRICE_TEXT}").performClick()
        assertTrue(openedPro)
    }

    @Test
    fun `展示价格必须与服务端商品目录一致`() {
        val serverConfig = File("../lkl-pay-server/src/main/resources/application.yml")
        // 只装了 App 模块时跳过：这条一致性校验依赖同仓库的服务端商品目录
        org.junit.Assume.assumeTrue("未找到服务端配置：${serverConfig.absolutePath}", serverConfig.exists())

        val amountFen = Regex("amount-fen:\\s*(\\d+)")
            .find(serverConfig.readText())
            ?.groupValues
            ?.get(1)
            ?.toLong()
        assertTrue("服务端商品目录里找不到 amount-fen", amountFen != null)

        val serverPrice = "￥" + BigDecimal(amountFen!!).movePointLeft(2).toPlainString()
        assertEquals(
            "App 展示价必须等于服务端实际扣款金额（否则构成虚假定价）",
            serverPrice,
            ProPurchase.PRICE_TEXT,
        )
    }

    @Test
    fun `激活时间按本地时区格式化到分钟`() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 30, 21, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals("2026年10月30日 21:30", formatActivationTime(calendar.timeInMillis))
    }
}
