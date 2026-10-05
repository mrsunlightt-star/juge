package com.juge.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.juge.app.data.AppPrefs
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 主界面 UI 基线：拆 MainActivity 的安全网（配 [UiBaseline]）。
 *
 * 覆盖三个静态场景——「跃然纸上」「个性定制」「个性定制 + 教程展开」。比起点击十几个控件，
 * 这三个场景已经把主界面所有分支的排版都渲染了一遍（左侧预览卡片、右侧列表、页脚协议行），
 * 搬移代码时任何一处画错都会立刻失败。
 *
 * 运行：
 *   ./gradlew :app:testDebugUnitTest --tests "com.juge.app.MainUiBaselineTest"
 * 基线：
 *   app/src/test/resources/ui-baseline/main.tsv + main/ 下的可见语义摘要
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainUiBaselineTest {

    /** 隐私弹窗会挡住整个界面，基线要的是主界面本身，所以先落盘「已同意」 */
    @get:Rule(order = 0)
    val acceptPrivacy = object : ExternalResource() {
        override fun before() {
            AppPrefs.setPrivacyAccepted(RuntimeEnvironment.getApplication())
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `主界面各页签 与基线一致`() {
        val scenes = LinkedHashMap<String, UiBaseline.Scene>()

        scenes["main-library"] = snapshot("main-library")

        compose.onNodeWithText("个性定制").performClick()
        scenes["main-adjust"] = snapshot("main-adjust")

        compose.onNodeWithText("桌面组件添加教程", substring = true).performClick()
        scenes["main-adjust-tutorial"] = snapshot("main-adjust-tutorial")

        UiBaseline("main", "MainUiBaselineTest").verify(scenes)
    }

    private fun snapshot(scene: String): UiBaseline.Scene {
        compose.waitForIdle()
        return UiSnapshot.capture(scene, compose.activity, compose)
    }
}
