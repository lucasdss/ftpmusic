package com.lucasdss.ftpmusic.app.ui.player

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Compose UI tests for PlayerBar in mini mode (expanded = false). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h800dp")
class PlayerBarComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun miniState(isPlaying: Boolean = false, isCasting: Boolean = false, castDeviceName: String? = null) =
        PlayerBarState(
            title = "Song Title",
            artist = "Artist Name",
            isPlaying = isPlaying,
            isCasting = isCasting,
            castDeviceName = castDeviceName,
            expanded = false,
        )

    private fun expandedState() = PlayerBarState(
        title = "Song Title",
        artist = "Artist Name",
        expanded = true,
    )

    @Test
    fun `title and artist text rendered`() {
        composeRule.setContent {
            PlayerBar(state = miniState())
        }
        composeRule.onNodeWithText("Song Title").assertExists()
        composeRule.onNodeWithText("Artist Name").assertExists()
    }

    @Test
    fun `play icon shown when paused`() {
        composeRule.setContent {
            PlayerBar(state = miniState(isPlaying = false))
        }
        composeRule.onNodeWithContentDescription("Play").assertExists()
        composeRule.onAllNodesWithContentDescription("Pause").assertCountEquals(0)
    }

    @Test
    fun `pause icon shown when playing`() {
        composeRule.setContent {
            PlayerBar(state = miniState(isPlaying = true))
        }
        composeRule.onNodeWithContentDescription("Pause").assertExists()
        composeRule.onAllNodesWithContentDescription("Play").assertCountEquals(0)
    }

    @Test
    fun `click play button invokes onPlayPause`() {
        var invoked = false
        composeRule.setContent {
            PlayerBar(state = miniState(), onPlayPause = { invoked = true })
        }
        composeRule.onNodeWithContentDescription("Play").performClick()
        assertTrue(invoked)
    }

    @Test
    fun `click skip next invokes onSkipNext`() {
        var invoked = false
        composeRule.setContent {
            PlayerBar(state = miniState(), onSkipNext = { invoked = true })
        }
        composeRule.onNodeWithContentDescription("Next").performClick()
        assertTrue(invoked)
    }

    @Test
    fun `click skip prev invokes onSkipPrev`() {
        var invoked = false
        composeRule.setContent {
            PlayerBar(state = miniState(), onSkipPrev = { invoked = true })
        }
        composeRule.onNodeWithContentDescription("Previous").performClick()
        assertTrue(invoked)
    }

    @Test
    fun `cast device name appears in subtitle when casting`() {
        composeRule.setContent {
            PlayerBar(state = miniState(isCasting = true, castDeviceName = "Mini Speaker"))
        }
        composeRule.onNodeWithText("Mini Speaker").assertExists()
    }

    @Test
    fun `no cast device name in subtitle when not casting`() {
        composeRule.setContent {
            PlayerBar(state = miniState(isCasting = false))
        }
        composeRule.onNodeWithText("Artist Name").assertExists()
    }

    @Test
    fun `bar click invokes onClick`() {
        var invoked = false
        composeRule.setContent {
            PlayerBar(state = miniState(), onClick = { invoked = true })
        }
        composeRule.onNodeWithText("Song Title").performClick()
        assertTrue(invoked)
    }
}
