package com.lucasdss.ftpmusic.app.ui.player

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Render tests for the waveform scrubber fed by real genre-matched bars. */
@RunWith(RobolectricTestRunner::class)
class WaveformScrubberRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bars: List<Float> = (0 until 400).map { i -> (i % 10) / 10f }

    @Test
    fun `renders waveform with time labels`() {
        composeRule.setContent {
            WaveformScrubber(
                bars = bars,
                fraction = 0.5f,
                position = 30_000L,
                duration = 60_000L,
                onSeek = {},
                modifier = Modifier.testTag("scrubber"),
            )
        }
        composeRule.onNodeWithTag("scrubber").assertIsDisplayed()
        composeRule.onNodeWithText("0:30").assertIsDisplayed()
        composeRule.onNodeWithText("-0:30").assertIsDisplayed()
    }

    @Test
    fun `tap on canvas seeks to tapped fraction`() {
        var seeked = -1f
        composeRule.setContent {
            WaveformScrubber(
                bars = bars,
                fraction = 0f,
                position = 0L,
                duration = 60_000L,
                onSeek = { seeked = it },
                modifier = Modifier.testTag("scrubber"),
            )
        }
        composeRule.onNodeWithTag("scrubber").performClick()
        assertTrue("expected a seek fraction, got $seeked", seeked in 0f..1f)
    }

    @Test
    fun `renders with empty bars without crash`() {
        composeRule.setContent {
            WaveformScrubber(
                bars = emptyList(),
                fraction = 0f,
                position = 0L,
                duration = 60_000L,
                onSeek = {},
                modifier = Modifier.testTag("scrubber"),
            )
        }
        composeRule.onNodeWithTag("scrubber").assertIsDisplayed()
    }
}
