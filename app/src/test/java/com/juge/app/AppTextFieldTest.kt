package com.juge.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.height
import com.juge.app.ui.AppTextField
import com.juge.app.ui.StreamingGlyphRenderer
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [AppTextField] 的自定义渲染层回归。
 *
 * 输入框现在「BasicTextField 管输入/光标/语义 + Canvas 自绘文字 + 逐字 blur 动画」，
 * 关键风险都压在这一层：
 * - 透明文字不能把输入链路 / 语义树弄坏（onValueChange、EditableText 必须照旧）；
 * - placeholder 是独立的 Text，空值时要在、有内容时要消失；
 * - 逐字动画不能吞掉输入，也不能让逐帧重绘停不下来；
 * - 文字必须真的由自绘层画出来（BasicTextField 的文字是全透明的，画面上出现字 = 自绘成功，
 *   这一条用截图差异钉住，比只看语义树可靠）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppTextFieldTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun inputReportsValueAndKeepsEditableTextSemantics() {
        var value by mutableStateOf("")
        var received: String? = null
        compose.setContent {
            MyApplicationTheme {
                AppTextField(
                    value = value,
                    onValueChange = {
                        received = it
                        value = it
                    },
                    placeholder = "说点什么",
                )
            }
        }

        // 自绘文字不影响 SetText 语义：输入照旧上报、语义树里的可编辑文本照旧是真实值
        compose.onNode(hasSetTextAction()).performTextReplacement("今天也要好好写字")
        compose.waitUntil(5_000L) { value == "今天也要好好写字" }

        assertEquals("今天也要好好写字", received)
        compose.onNode(hasSetTextAction()).assertTextContains("今天也要好好写字")
    }

    @Test
    fun placeholderShowsOnlyWhileEmpty() {
        var value by mutableStateOf("")
        compose.setContent {
            MyApplicationTheme {
                AppTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = "输入桌面小组件上显示的金句",
                    minLines = 3,
                )
            }
        }

        compose.onNodeWithText("输入桌面小组件上显示的金句").assertIsDisplayed()

        compose.onNode(hasSetTextAction()).performTextReplacement("换一句新的")
        compose.waitUntil(5_000L) { value == "换一句新的" }
        compose.onNodeWithText("输入桌面小组件上显示的金句").assertDoesNotExist()
    }

    @Test(timeout = 60_000)
    fun typingStartsStreamingAnimationAndSettlesBackToCleanState() {
        var value by mutableStateOf("")
        compose.setContent {
            MyApplicationTheme {
                AppTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = false,
                    minLines = 3,
                    maxLines = Int.MAX_VALUE,
                    streaming = true,
                )
            }
        }

        // 焦点 → IME 输入，走真实键入路径（逐字 diff + 每字一段晕开动画）
        compose.onNode(hasSetTextAction()).performTouchInput { click() }
        compose.onNode(hasSetTextAction()).performTextInput("句阁")
        compose.waitUntil(5_000L) { value == "句阁" }

        // 动画是 ~320ms 的时钟：推进足够长的时间，逐帧重绘要能自己停下来
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        assertEquals("句阁", value)
        compose.onNode(hasSetTextAction()).assertTextContains("句阁")
    }

    @Test
    fun passwordFieldKeepsTransformationOnInputPath() {
        var value by mutableStateOf("")
        compose.setContent {
            MyApplicationTheme {
                AppTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = "密码",
                )
            }
        }

        compose.onNode(hasSetTextAction()).performTextReplacement("hunter2")
        compose.waitUntil(5_000L) { value == "hunter2" }
        // 密码框绘制的是 visualTransformation 的输出（圆点串），明文只存在于输入链路与语义树
        compose.onNode(hasSetTextAction()).assertTextContains("hunter2")
    }

    @Test(timeout = 60_000)
    fun textIsPaintedByTheCustomCanvasLayer() {
        // 初始内容走「静止」路径：没有动画、没有焦点、没有 placeholder / label / 图标，
        // 画面上唯一可能出现的深色像素就是自绘的字形（BasicTextField 的文字是全透明的）
        compose.setContent {
            MyApplicationTheme {
                AppTextField(value = "句阁", onValueChange = {})
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()

        val inkPixels = countInkPixels(bitmap)
        assertTrue(
            "BasicTextField 的文字是全透明的，画面上出现字形墨色像素只能来自 Canvas 自绘层；" +
                "实测墨色像素数 = $inkPixels",
            inkPixels > 200,
        )
    }

    @Test
    fun multilineFieldGrowsWithContentInsteadOfClipping() {
        var value by mutableStateOf("")
        compose.setContent {
            MyApplicationTheme {
                AppTextField(
                    value = value,
                    onValueChange = { value = it },
                    minLines = 3,
                    maxLines = Int.MAX_VALUE,
                )
            }
        }

        val initialHeight = compose.onNode(hasSetTextAction()).getUnclippedBoundsInRoot().height
        compose.onNode(hasSetTextAction())
            .performTextReplacement("第一行\n第二行\n第三行\n第四行\n第五行")
        compose.waitUntil(5_000L) { value.contains("第五行") }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()

        // 金句框的硬要求：内容多了框要长高（外层卡片有 animateContentSize），不能在框内滚动藏文本
        val grownHeight = compose.onNode(hasSetTextAction()).getUnclippedBoundsInRoot().height
        assertTrue("多行输入后输入框应当长高：$initialHeight → $grownHeight", grownHeight > initialHeight)
    }


    @Test
    fun trailingIconRendersBesideInputAndDoesNotBreakTyping() {
        var value by mutableStateOf("")
        compose.setContent {
            MyApplicationTheme {
                AppTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    label = "密码",
                    trailingIcon = { Text(text = "显示", style = AppType.label) },
                )
            }
        }

        compose.onNodeWithText("显示").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextReplacement("secret")
        compose.waitUntil(5_000L) { value == "secret" }
        compose.onNodeWithText("显示").assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun blurredGlyphIsSofterThanCrispGlyph() {
        // 动画的「晕开」靠软件位图 + BlurMaskFilter 渲染：如果掩码滤镜不生效（老 API / 某些实现
        // 会静默忽略），模糊字形会和清晰字形一样硬，这里用半透明边缘像素的数量把这条钉住。
        val renderer = StreamingGlyphRenderer(Typeface.DEFAULT, textSizePx = 48f, colorArgb = 0xFF000000.toInt())

        val blurred = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        renderer.drawBlurred(
            canvas = Canvas(blurred),
            glyph = "W",
            left = 40f,
            lineTop = 30f,
            lineHeight = 90f,
            baselineInLine = 70f,
            advance = 48f,
            radiusPx = 24f,
            bucket = 8,
            alpha = 1f,
        )

        val crisp = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        renderer.drawCrisp(Canvas(crisp), "W", 40f, 100f, 1f)

        val softBlurred = countPartialAlphaPixels(blurred)
        val softCrisp = countPartialAlphaPixels(crisp)
        assertTrue(
            "模糊字形的半透明边缘应当远多于清晰字形（blurred=$softBlurred, crisp=$softCrisp）",
            softBlurred > softCrisp * 2,
        )
    }

    @Test(timeout = 60_000)
    fun passwordFieldPaintsBulletsInsteadOfPlaintext() {
        var value by mutableStateOf("abc1234")
        var masked by mutableStateOf(true)
        compose.setContent {
            MyApplicationTheme {
                AppTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    visualTransformation = if (masked) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                )
            }
        }

        val maskedFirst = captureSettled()
        // 等长、不同内容的明文：画圆点的话两帧必须像素级一致（画布拿不到明文）
        compose.runOnIdle { value = "zzz9999" }
        compose.mainClock.advanceTimeBy(600)
        val maskedSecond = captureSettled()
        assertEquals(
            "密码框绘制的是 visualTransformation 的输出：等长的不同明文必须像素级一致",
            0,
            countDifferingPixels(maskedFirst, maskedSecond),
        )

        // 关掉变换后画的是明文，画面必须变
        compose.runOnIdle {
            value = "abc1234"
            masked = false
        }
        compose.mainClock.advanceTimeBy(600)
        val plain = captureSettled()
        assertTrue(
            "关掉密码变换后画面应当变成明文，与圆点渲染不同",
            countDifferingPixels(maskedFirst, plain) > 100,
        )
    }

    private fun captureSettled(): Bitmap {
        compose.waitForIdle()
        return compose.onRoot().captureToImage().asAndroidBitmap()
    }

    /** 统计 alpha 在 (0, 255) 之间的像素：抗锯齿 / 模糊都会产生，模糊越多越多 */
    private fun countPartialAlphaPixels(bitmap: Bitmap): Int {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var partial = 0
        for (pixel in pixels) {
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha in 1..254) partial++
        }
        return partial
    }

    private fun countDifferingPixels(before: Bitmap, after: Bitmap): Int {
        if (before.width != after.width || before.height != after.height) return Int.MAX_VALUE
        val width = before.width
        val height = before.height
        val first = IntArray(width * height)
        val second = IntArray(width * height)
        before.getPixels(first, 0, width, 0, 0, width, height)
        after.getPixels(second, 0, width, 0, 0, width, height)
        var different = 0
        for (index in first.indices) {
            if (first[index] != second[index]) different++
        }
        return different
    }

    /** 统计「不透明且偏墨色」的像素：根视图透明区域与浅色底都不算，只剩字形笔画的像素 */
    private fun countInkPixels(bitmap: Bitmap): Int {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var ink = 0
        for (pixel in pixels) {
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < 200) continue
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            // 近似亮度：正文字色 #0F172A 约 22，浅色底（fieldBg #F1F4F9）约 244
            val luma = (red * 299 + green * 587 + blue * 114) / 1000
            if (luma < 90) ink++
        }
        return ink
    }
}
