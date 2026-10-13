package com.lucasdss.ftpmusic.app.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ADR-0114 regression: collapsed riding chrome must reclaim the header pad band
 * (brand header only collapses; library nav stays reachable).
 *
 * Clip-before-pad order is encoded in [Modifier.appHeaderRidingClipHost] — call sites
 * cannot reorder. This test asserts reclaim geometry + interactivity. Pixel capture
 * is avoided (Robolectric `captureToImage` redraw timeout).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h800dp")
class AppHeaderRidingClipHostComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val chromeColor = Color(0xFFE84040)

    @Test
    fun `full collapse chrome reclaims pad band and stays clickable`() {
        val headerPad = 72.dp
        var chromeClicked = false
        composeRule.setContent {
            val headerPadPx = with(LocalDensity.current) { headerPad.toPx() }
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .appHeaderRidingClipHost(headerPad)
                        .testTag("riding_clip_host"),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .appHeaderRidingContent(
                                offsetPx = { headerPadPx },
                                maxOffsetPx = headerPadPx,
                            )
                            .background(chromeColor),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(headerPad)
                                .testTag("chrome_label")
                                .clickable { chromeClicked = true },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text("Albums")
                        }
                        Box(Modifier.fillMaxSize().background(Color(0xFF2244AA)))
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Albums").assertIsDisplayed()
        composeRule.onNodeWithTag("chrome_label").assertIsDisplayed()

        val headerPadPx = with(composeRule.density) { headerPad.toPx() }
        val bounds = composeRule.onNodeWithTag("chrome_label").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Collapsed chrome top must enter header pad band (near y=0), was ${bounds.top}",
            bounds.top < 1f,
        )
        assertTrue(
            "Collapsed chrome must cover mid pad band, bottom=${bounds.bottom} pad=$headerPadPx",
            bounds.bottom >= headerPadPx - 1f,
        )

        composeRule.onNodeWithTag("chrome_label").performClick()
        composeRule.waitForIdle()
        assertTrue("Collapsed chrome must remain clickable", chromeClicked)
    }
}
