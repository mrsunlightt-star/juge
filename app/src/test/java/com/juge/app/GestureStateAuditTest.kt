package com.juge.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.juge.app.ui.horizontalControlUsage
import com.juge.app.ui.library.longPressMenuDragGestures
import com.juge.app.ui.swipeBackToPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GestureStateAuditTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun tapUsesLatestTargetAfterRecomposition() {
        var selected by mutableStateOf("A")
        val received = mutableListOf<String>()
        compose.setContent {
            val target = selected
            Box(Modifier.size(100.dp).testTag("tap").longPressMenuDragGestures(
                key = "same-entry",
                onTap = { received += target },
                onLongPress = {},
                onDragStart = {},
                onDrag = {},
                onDragEnd = {},
                onDragCancel = {},
            ))
        }
        compose.onNodeWithTag("tap").performTouchInput { click() }
        compose.runOnIdle { selected = "B" }
        compose.onNodeWithTag("tap").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("A", "B"), received) }
    }

    @Test
    fun horizontalUsagePreservesPrecedingModifiers() {
        compose.setContent {
            Row(Modifier.size(200.dp, 100.dp)) {
                Box(Modifier.weight(1f).testTag("kept")
                    .horizontalControlUsage(rememberScrollState()))
                Box(Modifier.size(100.dp))
            }
        }
        compose.onNodeWithTag("kept").assertExists().assertWidthIsEqualTo(100.dp)
    }

    @Test
    fun previewSwipeOverHalfPageDoesNotNavigateBack() {
        var backCount = 0
        compose.setContent {
            val pager = rememberPagerState(initialPage = 1, pageCount = { 3 })
            Box(Modifier.size(300.dp, 100.dp).swipeBackToPage(
                pagerState = pager,
                onSwipeBack = { backCount++ },
                requirePagerMovement = false,
            )) {
                HorizontalPager(pager, modifier = Modifier.fillMaxSize().testTag("preview")) { page ->
                    Text("Preview $page")
                }
            }
        }
        swipeRight("preview")
        assertEquals("Swiping through previews must not leave the editor", 0, backCount)
    }

    @Test
    fun contentSwipeOverHalfPageStillNavigatesBack() {
        var backCount = 0
        compose.setContent {
            val pager = rememberPagerState(initialPage = 1, pageCount = { 3 })
            Box(Modifier.size(300.dp, 100.dp).swipeBackToPage(
                pagerState = pager,
                onSwipeBack = { backCount++ },
            )) {
                HorizontalPager(pager, modifier = Modifier.fillMaxSize().testTag("content")) { page ->
                    Text("Page $page")
                }
            }
        }
        swipeRight("content")
        assertEquals(1, backCount)
    }

    @Test
    fun swipeUsesLatestMovementRequirementAndCallback() {
        var requireMovement by mutableStateOf(true)
        val received = mutableListOf<Boolean>()
        compose.setContent {
            val requirement = requireMovement
            val pager = rememberPagerState(pageCount = { 2 })
            Box(Modifier.size(300.dp, 100.dp).swipeBackToPage(
                pagerState = pager,
                onSwipeBack = { received += requirement },
                requirePagerMovement = requirement,
            )) {
                HorizontalPager(pager, modifier = Modifier.fillMaxSize().testTag("pager")) { page ->
                    Text("Page $page")
                }
            }
        }
        compose.onNodeWithTag("pager").performTouchInput { click() }
        compose.runOnIdle { requireMovement = false }
        swipeRight("pager")
        assertEquals(listOf(false), received)
    }

    @Test
    fun longPressAndDragUseLatestCallbacksWithoutRestartingGesture() {
        var target by mutableStateOf("A")
        val received = mutableListOf<String>()
        compose.setContent {
            val current = target
            Box(Modifier.size(200.dp).testTag("drag").longPressMenuDragGestures(
                key = "same-entry",
                onTap = null,
                onLongPress = { received += "press:$current" },
                onDragStart = { received += "start:$current" },
                onDrag = { received += "drag:$current" },
                onDragEnd = { received += "end:$current" },
                onDragCancel = { received += "cancel:$current" },
            ))
        }
        compose.onNodeWithTag("drag").performTouchInput { click() }
        compose.runOnIdle { target = "B" }
        compose.onNodeWithTag("drag").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("drag").performTouchInput {
            advanceEventTime(600)
            moveBy(Offset(100f, 0f))
        }
        compose.runOnIdle { target = "C" }
        compose.onNodeWithTag("drag").performTouchInput {
            moveBy(Offset(30f, 0f))
            up()
        }
        compose.runOnIdle {
            assertEquals("press:B", received.first())
            assertTrue(received.contains("start:B"))
            assertTrue(received.contains("drag:B"))
            assertTrue(received.contains("drag:C"))
            assertEquals("end:C", received.last())
            assertTrue(received.none { it.startsWith("cancel:") })
        }
    }

    @Test
    fun cancelledDragUsesLatestCancelCallback() {
        var target by mutableStateOf("A")
        val received = mutableListOf<String>()
        compose.setContent {
            val current = target
            Box(Modifier.size(200.dp).testTag("drag").longPressMenuDragGestures(
                key = "same-entry",
                onTap = null,
                onLongPress = {},
                onDragStart = {},
                onDrag = {},
                onDragEnd = { received += "end:$current" },
                onDragCancel = { received += "cancel:$current" },
            ))
        }
        compose.onNodeWithTag("drag").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("drag").performTouchInput {
            advanceEventTime(600)
            moveBy(Offset(100f, 0f))
        }
        compose.runOnIdle { target = "B" }
        compose.onNodeWithTag("drag").performTouchInput { cancel() }
        compose.runOnIdle { assertEquals(listOf("cancel:B"), received) }
    }

    private fun swipeRight(tag: String) {
        compose.onNodeWithTag(tag).performTouchInput {
            swipe(Offset(width * 0.1f, height * 0.5f), Offset(width * 0.9f, height * 0.5f), 800)
        }
        compose.waitForIdle()
    }
}
