package com.juge.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.juge.app.ui.AppMenuItem
import com.juge.app.ui.library.ContextMenuOverlay
import com.juge.app.ui.main.SubTabSwitcher
import com.juge.app.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainSelectionVisualTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `顶部页签选中块使用全局浅蓝`() {
        compose.setContent {
            MyApplicationTheme {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
                    SubTabSwitcher(pagerState = pagerState, onSelectTab = {})
                }
            }
        }
        compose.onNodeWithText("跃然纸上").assertIsDisplayed()
        compose.waitForIdle()

        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val bandTop = with(compose.density) { 8.dp.roundToPx() }
        val bandBottom = with(compose.density) { 40.dp.roundToPx() }
        val leftSegmentRight = bitmap.width * 0.45f
        var lightBluePixels = 0
        for (y in bandTop until minOf(bandBottom, bitmap.height)) {
            for (x in (bitmap.width * 0.1f).roundToInt() until leftSegmentRight.roundToInt()) {
                val pixel = bitmap.getPixel(x, y)
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                if (abs(red - 0x42) <= 2 && abs(green - 0xB8) <= 2 && abs(blue - 0xEC) <= 2) {
                    lightBluePixels++
                }
            }
        }
        assertTrue(
            "顶部页签选中块应大面积使用 selectBlue #42B8EC，实际仅 $lightBluePixels 个像素",
            lightBluePixels > 5_000,
        )
        bitmap.recycle()
    }

    @Test
    fun `长按菜单不给页面铺灰色遮罩`() {
        compose.setContent {
            MyApplicationTheme {
                Box(Modifier.fillMaxSize().background(Color.Red)) {
                    ContextMenuOverlay(
                        anchorInRoot = Offset.Zero,
                        items = listOf(
                            AppMenuItem(
                                iconRes = R.drawable.ic_symbol_edit_square,
                                label = "编辑金句",
                                iconTint = Color.Blue,
                                onClick = {},
                            )
                        ),
                        onDismiss = {},
                    )
                }
            }
        }
        compose.onNodeWithText("编辑金句").assertIsDisplayed()
        compose.waitForIdle()

        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        // 菜单固定在指尖附近，右下角远离菜单与阴影；页面不压暗时这里必须仍是纯红。
        val pixel = bitmap.getPixel(bitmap.width - 2, bitmap.height - 2)
        assertEquals("长按菜单不应给页面铺灰色遮罩", 0xFFFF0000.toInt(), pixel)
        bitmap.recycle()
    }
}
