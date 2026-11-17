package com.juge.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.juge.app.data.WidgetStyle
import com.juge.app.ui.SaveOutcome
import com.juge.app.ui.adjust.ShapeBackgroundCard
import com.juge.app.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShapeBackgroundCardTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `已有背景图只保留裁切操作且不展示填充模式`() {
        var croppedPath: String? = null
        compose.setContent {
            MyApplicationTheme {
                ShapeBackgroundCard(
                    isActivated = true,
                    selectedStyle = WidgetStyle.DEFAULT.copy(backgroundImagePath = "/tmp/current.png"),
                    selectedContent = "",
                    selectedWidgetId = 1,
                    selectedReminderId = 1,
                    onStyleStateChange = {},
                    onStyleChange = { _, _, _, _ -> SaveOutcome.SAVED },
                    onPickImage = {},
                    onCropImage = { croppedPath = it },
                )
            }
        }

        compose.onNodeWithText("更换图片").assertIsDisplayed()
        compose.onNodeWithText("调整裁切").performClick()
        assertEquals("/tmp/current.png", croppedPath)

        compose.onNodeWithText("图片填充模式").assertDoesNotExist()
        listOf("平铺", "裁剪", "完整", "拉伸", "铺满").forEach { mode ->
            compose.onNodeWithText(mode).assertDoesNotExist()
        }
    }
}
