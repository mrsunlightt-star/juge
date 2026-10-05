package com.juge.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 首启隐私弹窗 UI 基线。
 *
 * 这是每个用户看到的第一屏，也是合规检查的固定靶子：文案漏掉一个信息处理场景、
 * 按钮被长文案挤出屏幕，都属于「必须人工看出」的问题。与主界面基线不同，这里
 * 刻意**不**预先落盘同意状态，让弹窗真实出现。
 *
 * 运行：
 *   ./gradlew :app:testDebugUnitTest --tests "com.juge.app.PrivacyDialogUiBaselineTest"
 * 基线：
 *   app/src/test/resources/ui-baseline/privacy.tsv + privacy/ 下的可见语义摘要
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrivacyDialogUiBaselineTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `首启隐私弹窗 与基线一致`() {
        val scenes = LinkedHashMap<String, UiBaseline.Scene>()
        compose.waitForIdle()
        scenes["privacy-consent"] = UiSnapshot.capture("privacy-consent", compose.activity, compose)
        UiBaseline("privacy", "PrivacyDialogUiBaselineTest").verify(scenes)
    }
}
