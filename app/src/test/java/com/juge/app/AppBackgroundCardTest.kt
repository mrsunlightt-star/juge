package com.juge.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.juge.app.data.AppBackgroundPreset
import com.juge.app.ui.adjust.AppBackgroundCard
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
class AppBackgroundCardTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `App背景图模块展示两个选项并可切换`() {
        var selected: AppBackgroundPreset? = null
        compose.setContent {
            MyApplicationTheme {
                AppBackgroundCard(
                    selected = AppBackgroundPreset.SUNNY_BEACH,
                    onSelect = { selected = it },
                )
            }
        }

        compose.onNodeWithText("App 背景图").assertIsDisplayed()
        compose.onNodeWithText("阳光海滩").assertIsDisplayed()
        compose.onNodeWithText("大海").assertIsDisplayed()
        compose.onNodeWithContentDescription("大海 App 背景图").performClick()
        assertEquals(AppBackgroundPreset.SEA, selected)
    }
}
