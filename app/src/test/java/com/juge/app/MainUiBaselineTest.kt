package com.juge.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
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
 * 覆盖六个静态场景——「跃然纸上」「个性定制」「明信片行」「App 背景图」
 * 「文本与字号控件」「个性定制 + 教程展开」。
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
        // 新插入的「App 背景图」卡片把后面的卡片推出了 LazyColumn 的预组合范围。
        // performScrollToNode 要求目标已经进入语义树，因此先按固定 item 索引滚到目标卡：
        // 顶部无组件警告是否出现会让索引相差 1。
        val adjustList = compose.onNodeWithTag("adjust_list")
        val hasNoWidgetWarning = compose
            .onAllNodesWithText("未检测到已放置桌面小组件。", substring = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
        val styleIndex = if (hasNoWidgetWarning) 2 else 1
        val appBackgroundIndex = styleIndex + 1
        val textStyleIndex = appBackgroundIndex + 1

        adjustList.performScrollToIndex(styleIndex)
        adjustList.performScrollToNode(hasText("明信片风格"))
        scenes["main-adjust-postcard"] = snapshot("main-adjust-postcard")

        adjustList.performScrollToIndex(appBackgroundIndex)
        compose.waitForIdle()
        scenes["main-adjust-background"] = snapshot("main-adjust-background")

        adjustList.performScrollToIndex(textStyleIndex)
        compose.waitForIdle()

        // 数值步进器必须单独入基线：上面几个场景都停在字号/行距/字间距三行上方。
        // 滚到字间距这一行，让「标签 …… − 当前值 +」整行进画面，防止步进器
        // 被样式改动挤掉、端点禁用态画错或单位文案丢失。
        adjustList.performScrollToNode(hasText("字间距", substring = true))
        scenes["main-adjust-stepper"] = snapshot("main-adjust-stepper")

        // 滚回顶部再展开教程：上一步把列表滚下去了，「桌面组件添加教程」在视口之外点不到
        compose.onNodeWithTag("adjust_list")
            .performScrollToIndex(0)
        compose.onNodeWithText("桌面组件添加教程", substring = true).performClick()
        scenes["main-adjust-tutorial"] = snapshot("main-adjust-tutorial")

        UiBaseline("main", "MainUiBaselineTest").verify(scenes)
    }

    /**
     * 删除金句必须先弹确认，不能像改版前那样点一下就直接删库。
     * 这里只验证交互分支，不纳入截图基线（确认弹窗会多出一个 Compose 根）。
     *
     * 卡面上的编辑/删除按钮已收进长按菜单（iOS 式上下文菜单）：
     * 长按卡片 → 菜单「删除金句」→ 二次确认弹窗。
     */
    @Test
    fun `删除金句先弹二次确认`() {
        // 第三张种子金句的文案全库唯一（第一句与组件预览默认文案相同，选择器会歧义）
        compose.onAllNodesWithText("life is hard，always", substring = true).onFirst()
            .performTouchInput {
                // 手写长按时序（按下→停满长按阈值→抬起），等价于长按手势
                down(center)
                advanceEventTime(1_000)
                up()
            }
        compose.onNodeWithText("删除金句").assertIsDisplayed().performClick()
        compose.onNodeWithText("删除这条金句？").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.onAllNodesWithText("删除这条金句？").assertCountEquals(0)
    }

    /**
     * 分类快速切换后回到「全部」，三张金句都必须直接可见。
     *
     * 回归背景：卡片曾包在逐项的 AnimatedVisibility 里。从「文学」切回「全部」时，
     * LazyColumn 保留旧条目的滚动位置、入场状态又可能停在不可见帧，结果第一张卡片
     * 被推到视口外或中间卡片只剩占位空洞。文案节点是否存在比截图像素更稳定，
     * 因此这里逐个断言三条正文都在屏上。
     */
    @Test
    fun `切回全部后所有金句仍然可见`() {
        categoryChip("文学").performClick()
        categoryChip("诗歌").performClick()
        categoryChip("激励").performClick()
        categoryChip("文学").performClick()
        categoryChip("全部").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("life is hard", substring = true).assertIsDisplayed()
        compose.onNodeWithText("断章", substring = true).assertIsDisplayed()
        compose.onNodeWithText("三体", substring = true).assertIsDisplayed()
    }

    private fun categoryChip(name: String) = compose.onNode(
        hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
    )

    /** 纵向可滚动容器（LazyColumn）。横向的 ScrollRow 也带滚动动作，必须区分开 */
    private val verticalScrollable =
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private fun snapshot(scene: String): UiBaseline.Scene {
        compose.waitForIdle()
        return UiSnapshot.capture(scene, compose.activity, compose)
    }
}
