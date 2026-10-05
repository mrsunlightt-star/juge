package com.juge.app

import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 快捷调整面板 UI 基线（配 [UiBaseline]）。
 *
 * 面板的 `setupContent()` 是 1200+ 行的单方法，拆它同样需要像素级安全网。这里走真实启动路径
 * （Intent 带 appWidgetId），配上数据库里的默认配置进入编辑态。
 *
 * 运行：
 *   ./gradlew :app:testDebugUnitTest --tests "com.juge.app.QuickAdjustUiBaselineTest"
 * 基线：
  *   app/src/test/resources/ui-baseline/quick-adjust.tsv + quick-adjust/ 下的可见语义摘要
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuickAdjustUiBaselineTest {

    /** 真实入口是「组件上的调整按钮」：必须带 appWidgetId，否则面板会直接 finish */
    private val launchIntent = Intent(
        ApplicationProvider.getApplicationContext(),
        QuickAdjustActivity::class.java,
    ).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 1)
    }

    private val scenarioRule = ActivityScenarioRule<QuickAdjustActivity>(launchIntent)

    @get:Rule(order = 0)
    val compose = AndroidComposeTestRule(scenarioRule) { rule ->
        lateinit var activity: QuickAdjustActivity
        rule.scenario.onActivity { activity = it }
        activity
    }

    @Test
    fun `快捷调整面板 与基线一致`() {
        compose.waitForIdle()
        val scene = UiSnapshot.capture("quick-adjust", compose.activity, compose)
        UiBaseline("quick-adjust", "QuickAdjustUiBaselineTest").verify(mapOf("quick-adjust" to scene))
    }
}
