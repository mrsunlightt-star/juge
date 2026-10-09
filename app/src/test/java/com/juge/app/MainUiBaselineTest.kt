package com.juge.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
 * 覆盖四个静态场景——「跃然纸上」「个性定制」「个性定制 + 教程展开」「个性定制滚到明信片行」。
 * 比起点击十几个控件，这几个场景已经把主界面所有分支的排版都渲染了一遍
 * （左侧预览卡片、右侧列表、页脚协议行、五行风格预设），搬移代码时任何一处画错都会立刻失败。
 *
 * ⚠️ 第四個场景是补上来的：**首屏只拍得到「经典风格」「萌宠风格」两行标题，明信片行在视口之下**，
 * 于是它的行标题在 2e9f34b 拆分 AdjustTabContent 时被整行删掉，几周都没人发现——
 * 语义摘要只收**可见**节点，看不到的东西等于没有基线。
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

        // 滚到明信片行，把行标题与**行首条目**一起纳入基线。
        // 目标是明信片行**下方**那张卡片的标题：滚到行标题本身只会让标题贴住屏幕下沿，
        // 整行条目仍落在屏幕外——而语义摘要只收完全在屏幕内的节点，等于没覆盖。
        // 必须按**纵向**滚动轴来挑容器：这一页里还嵌着好几条横向 ScrollRow（预览分页器、
        // 经典/萌宠/明信片行本身），用 hasScrollAction() 会先命中那些横向的，滚不动。
        // 还要限定「含『组件风格』那一页」：「跃然纸上」与「个性定制」同在一个横向 pager 里，
        // 两页的 LazyColumn 都在语义树上，只按滚动轴挑会命中屏幕外那一页。
        compose.onNode(verticalScrollable and hasAnyDescendant(hasText("组件风格")))
            .performScrollToNode(hasText("文本内容与字形定制"))
        scenes["main-adjust-postcard"] = snapshot("main-adjust-postcard")

        // 滚回顶部再展开教程：上一步把列表滚下去了，「桌面组件添加教程」在视口之外点不到
        compose.onNode(verticalScrollable and hasAnyDescendant(hasText("组件风格")))
            .performScrollToNode(hasText("桌面组件添加教程", substring = true))
        compose.onNodeWithText("桌面组件添加教程", substring = true).performClick()
        scenes["main-adjust-tutorial"] = snapshot("main-adjust-tutorial")

        UiBaseline("main", "MainUiBaselineTest").verify(scenes)
    }

    /** 纵向可滚动容器（LazyColumn）。横向的 ScrollRow 也带滚动动作，必须区分开 */
    private val verticalScrollable =
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private fun snapshot(scene: String): UiBaseline.Scene {
        compose.waitForIdle()
        return UiSnapshot.capture(scene, compose.activity, compose)
    }
}
