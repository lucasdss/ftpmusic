package com.lucasdss.ftpmusic.app.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * ADR-0114 regression: collapsed riding chrome must reclaim the header pad band
 * (brand header only collapses; library nav stays reachable).
 *
 * Also locks Library FAB placement: BottomEnd **inside** riding is clipped at
 * offset=0; BottomEnd as a **sibling overlay** outside riding stays visible.
 *
 * Clip-before-pad order is encoded in [Modifier.appHeaderRidingClipHost] — call sites
 * cannot reorder. Pixel capture is avoided (Robolectric `captureToImage` redraw timeout).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h800dp")
class AppHeaderRidingClipHostComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val chromeColor = Color(0xFFE84040)
    private val fabColor = Color(0xFF14B8A6)

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

    // Inside-riding BottomEnd overhang at offset=0 is locked by
    // AppHeaderContentPaddingTest.`at offset zero painted bottom exceeds parent by full extra`.
    // Robolectric semantics/layout bounds for clipped over-measure children are unreliable.

    @Test
    fun `bottomEnd overlay sibling outside riding is visible and clickable at offset zero`() {
        val headerPad = 72.dp
        var fabClicked = false
        var hostBounds by mutableStateOf<Rect?>(null)
        var fabBounds by mutableStateOf<Rect?>(null)
        composeRule.setContent {
            val headerPadPx = with(LocalDensity.current) { headerPad.toPx() }
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .appHeaderRidingClipHost(headerPad)
                        .testTag("riding_clip_host")
                        .onGloballyPositioned { hostBounds = it.boundsInRoot() },
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .appHeaderRidingContent(
                                offsetPx = { 0f },
                                maxOffsetPx = headerPadPx,
                            )
                            .background(chromeColor),
                    ) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF2244AA)))
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .size(56.dp)
                            .background(fabColor)
                            .testTag("overlay_fab")
                            .onGloballyPositioned { fabBounds = it.boundsInRoot() }
                            .clickable { fabClicked = true },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("overlay_fab").assertIsDisplayed()
        val host = hostBounds
        val fab = fabBounds
        assertNotNull(host)
        assertNotNull(fab)
        assertTrue(
            "Overlay FAB must stay within clipHost at offset=0 " +
                "(fab.bottom=${fab!!.bottom} host.bottom=${host!!.bottom})",
            fab.bottom <= host.bottom + 1f,
        )
        assertTrue("Overlay FAB top must be on-screen", fab.top >= host.top - 1f)

        composeRule.onNodeWithTag("overlay_fab").performClick()
        composeRule.waitForIdle()
        assertTrue("Overlay FAB must remain clickable at offset=0", fabClicked)
    }

    @Test
    fun `overlay fab bounds stay stable when riding offset collapses`() {
        val headerPad = 72.dp
        var offsetPx by mutableFloatStateOf(0f)
        var fabBounds by mutableStateOf<Rect?>(null)
        composeRule.setContent {
            val headerPadPx = with(LocalDensity.current) { headerPad.toPx() }
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .appHeaderRidingClipHost(headerPad)
                        .testTag("riding_clip_host"),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .appHeaderRidingContent(
                                offsetPx = { offsetPx },
                                maxOffsetPx = headerPadPx,
                            )
                            .background(chromeColor),
                    ) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF2244AA)))
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .size(56.dp)
                            .background(fabColor)
                            .testTag("overlay_fab")
                            .onGloballyPositioned { fabBounds = it.boundsInRoot() },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val atRest = fabBounds
        assertNotNull(atRest)
        val headerPadPx = with(composeRule.density) { headerPad.toPx() }
        composeRule.runOnIdle { offsetPx = headerPadPx }
        composeRule.waitForIdle()
        val collapsed = fabBounds
        assertNotNull(collapsed)

        assertEquals(atRest!!.left, collapsed!!.left, 1f)
        assertEquals(atRest.top, collapsed.top, 1f)
        assertEquals(atRest.right, collapsed.right, 1f)
        assertEquals(atRest.bottom, collapsed.bottom, 1f)
        assertTrue(
            "In-riding pad would slide FAB; overlay must not move. deltaY=${abs(collapsed.top - atRest.top)}",
            abs(collapsed.top - atRest.top) < 1f,
        )
    }
}
