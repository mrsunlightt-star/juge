package com.juge.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.juge.app.data.DbHelper
import com.juge.app.data.TrialManager
import com.juge.app.pay.ProPurchase
import com.juge.app.ui.main.MainTopBar
import com.juge.app.ui.main.ProActivationDialog
import com.juge.app.ui.main.ProStatusDialog
import com.juge.app.ui.main.formatActivationTime
import com.juge.app.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 会员入口的三个落点：顶栏两态、付费墙、找回入口。
 *
 * 产品规则（2026-11，权益改绑支付宝账号后）：
 * - **未激活**：顶栏是金色「激活PRO ￥3.99」胶囊，点了进付费墙；
 * - **已激活**：胶囊换成静态 PRO 徽章，不再有「我的」页——本机没有账号可看；
 * - **付费墙**：主按钮是「支付宝支付」，次要入口是「已购买过？用支付宝找回」。
 *
 * 只断言语义节点，不做截图基线：整屏截图由 [MainUiBaselineTest] 覆盖主界面本身。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProEntryUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = RuntimeEnvironment.getApplication()

    private fun setTopBar(
        isActivated: Boolean,
        onOpenPro: () -> Unit,
        onOpenStatus: () -> Unit = {},
    ) {
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
                    onOpenStatus = onOpenStatus,
                    isDarkTheme = false,
                    onToggleDarkTheme = {},
                )
            }
        }
    }

    private fun setDialog(
        isActivated: Boolean = false,
        isPaying: Boolean = false,
        isRecovering: Boolean = false,
        onActivate: () -> Unit = {},
        onRecover: () -> Unit = {},
    ) {
        compose.setContent {
            MyApplicationTheme {
                ProActivationDialog(
                    show = true,
                    isActivated = isActivated,
                    isPaying = isPaying,
                    isRecovering = isRecovering,
                    onActivate = onActivate,
                    onRecover = onRecover,
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun `未激活时顶栏显示待激活胶囊并可打开付费墙`() {
        var openedPro = false
        setTopBar(isActivated = false, onOpenPro = { openedPro = true })

        compose.onNodeWithText("激活PRO ${ProPurchase.PRICE_TEXT}").assertIsDisplayed()
        compose.onNodeWithText("PRO").assertDoesNotExist()
        compose.onNodeWithText("激活PRO ${ProPurchase.PRICE_TEXT}").performClick()
        assertTrue(openedPro)
    }

    @Test
    fun `已激活时顶栏换成PRO徽章且可点开会员状态`() {
        var openedStatus = false
        setTopBar(isActivated = true, onOpenPro = {}, onOpenStatus = { openedStatus = true })

        compose.onNodeWithText("PRO").assertIsDisplayed()
        compose.onNodeWithText("激活PRO ${ProPurchase.PRICE_TEXT}").assertDoesNotExist()

        compose.onNodeWithText("PRO").performClick()
        assertTrue("点徽章应该打开会员状态卡", openedStatus)
    }

    @Test
    fun `付费墙同时提供购买与找回两个动作`() {
        var activated = false
        var recovered = false
        setDialog(onActivate = { activated = true }, onRecover = { recovered = true })

        compose.onNodeWithText("支付宝支付 ${ProPurchase.PRICE_TEXT}").assertIsDisplayed()
        compose.onNodeWithText("已购买过？用支付宝找回").assertIsDisplayed()

        compose.onNodeWithText("已购买过？用支付宝找回").performClick()
        assertTrue(recovered)
        assertFalse("点找回不应同时触发购买", activated)
    }

    @Test
    fun `已激活时付费墙只显示会员状态`() {
        setDialog(isActivated = true)

        compose.onNodeWithText("✨ 您已是 PRO 会员，感谢支持！").assertIsDisplayed()
        compose.onNodeWithText("支付宝支付 ${ProPurchase.PRICE_TEXT}").assertDoesNotExist()
        compose.onNodeWithText("已购买过？用支付宝找回").assertDoesNotExist()
    }

    @Test
    fun `找回进行中按钮置忙且不可重复点击`() {
        var recovered = false
        setDialog(isRecovering = true, onRecover = { recovered = true })

        compose.onNodeWithText("正在核验…").assertIsDisplayed()
        compose.onNodeWithText("正在核验…").performClick()
        assertFalse(recovered)
    }

    // ==================== 会员状态卡 ====================

    private fun setStatusDialog(
        record: TrialManager.ActivationRecord?,
        isActivated: Boolean = true,
    ) {
        compose.setContent {
            MyApplicationTheme {
                ProStatusDialog(
                    show = true,
                    isActivated = isActivated,
                    record = record,
                    onOpenPro = {},
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun `会员状态卡展示激活时间购买方式与永久有效`() {
        val activatedAt = 1_767_000_000_000L
        setStatusDialog(TrialManager.ActivationRecord(activatedAt = activatedAt, payMethod = "支付宝"))

        compose.onNodeWithText("已激活 · 永久有效").assertIsDisplayed()
        compose.onNodeWithText("永久，无订阅费用").assertIsDisplayed()
        compose.onNodeWithText("支付宝").assertIsDisplayed()
        compose.onNodeWithText(formatActivationTime(activatedAt)).assertIsDisplayed()
    }

    @Test
    fun `更早版本激活没有记录时给出说明而不是空白`() {
        setStatusDialog(record = null)

        compose.onNodeWithText("已激活 · 永久有效").assertIsDisplayed()
        compose.onNodeWithText("更早版本激活，未记录").assertIsDisplayed()
    }
}
