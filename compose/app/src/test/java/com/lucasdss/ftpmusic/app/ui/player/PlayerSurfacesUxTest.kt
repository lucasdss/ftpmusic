package com.lucasdss.ftpmusic.app.ui.player

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lucasdss.ftpmusic.app.playback.PlaybackState
import com.lucasdss.ftpmusic.app.playback.UpcomingTrack
import com.lucasdss.ftpmusic.app.ui.DestructiveRed
import com.lucasdss.ftpmusic.app.ui.SurfaceElevated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** ADR-0070 / ADR-0073: Now Playing · mini · queue sheet UX contracts. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h800dp")
class PlayerSurfacesUxTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `queueRowKey prefers entryId then trackId then index-title`() {
        assertEquals(42, queueRowKey(UpcomingTrack(title = "A", queueIndex = 3, entryId = 42)))
        assertEquals(
            "id-t9",
            queueRowKey(UpcomingTrack(title = "B", trackId = "t9", queueIndex = 3, entryId = 0)),
        )
        assertEquals(
            "qi-3-B",
            queueRowKey(UpcomingTrack(title = "B", queueIndex = 3, entryId = 0)),
        )
    }

    @Test
    fun `idle queue row fill is elevated surface not swipe red`() {
        assertEquals(SurfaceElevated, QueueTrackRowIdleBackground)
        assertNotEquals(Color.Red, QueueTrackRowIdleBackground)
        assertNotEquals(DestructiveRed, QueueTrackRowIdleBackground)
        assertTrue(QueueTrackRowIdleBackground.alpha >= 1f)
    }

    @Test
    fun `mini visibility policy matches PlaybackState isVisible`() {
        assertTrue(PlaybackState(title = "Song").isVisible)
        assertFalse(PlaybackState(title = null).isVisible)
        assertFalse(PlaybackState().isVisible)
    }

    @Test
    fun `default isBuffering is false`() {
        assertFalse(PlaybackState().isBuffering)
        assertTrue(PlaybackState(isBuffering = true).isBuffering)
    }

    @Test
    fun `full player shows sleep timer entry`() {
        var opened = false
        composeRule.setContent {
            PlayerBar(
                state = PlayerBarState(title = "Song", artist = "Artist", expanded = true),
                onSleepTimer = { opened = true },
            )
        }
        composeRule.onNodeWithTag("sleep_timer_entry").assertIsDisplayed().performClick()
        assertTrue(opened)
    }

    @Test
    fun `buffering shows transport indicator not play icon`() {
        composeRule.setContent {
            PlayerBar(
                state = PlayerBarState(
                    title = "Song",
                    artist = "Artist",
                    expanded = true,
                    isBuffering = true,
                    isPlaying = false,
                ),
            )
        }
        composeRule.onNodeWithTag("transport_buffering").assertExists()
        composeRule.onNodeWithContentDescription("Play").assertDoesNotExist()
    }

    @Test
    fun `queue sheet shuffle is playback shuffle not fake queue shuffle`() {
        var shuffled = false
        composeRule.setContent {
            PlayerBar(
                state = PlayerBarState(title = "Song", artist = "Artist", expanded = true),
                onShuffleToggle = { shuffled = true },
            )
        }
        composeRule.onNodeWithTag("queue_peek_strip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_sheet").assertExists()
        composeRule.onNodeWithTag("queue_sheet_shuffle").assertExists().performClick()
        assertTrue(shuffled)
    }

    @Test
    fun `queue sheet uses Next from lexicon for context section`() {
        composeRule.setContent {
            PlayerBar(
                state = PlayerBarState(
                    title = "Current",
                    artist = "Artist",
                    expanded = true,
                    contextSource = "Album X",
                    queueSize = 3,
                    nextTracks = listOf(
                        UpcomingTrack(
                            title = "Current",
                            isCurrent = true,
                            queueIndex = 0,
                            entryId = 1,
                        ),
                        UpcomingTrack(
                            title = "Next Ctx",
                            artist = "A",
                            isCurrent = false,
                            isPriority = false,
                            isAutoplay = false,
                            queueIndex = 1,
                            entryId = 2,
                        ),
                    ),
                ),
            )
        }
        composeRule.onNodeWithTag("queue_peek_strip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_sheet").assertExists()
        composeRule.onNodeWithText("Next from · Album X · 1").assertExists()
    }

    @Test
    fun `queue sheet remove invokes onRemoveFromQueue`() {
        var removed: Int? = null
        composeRule.setContent {
            PlayerBar(
                state = PlayerBarState(
                    title = "Current",
                    artist = "Artist",
                    expanded = true,
                    queueSize = 2,
                    nextTracks = listOf(
                        UpcomingTrack(title = "Current", isCurrent = true, queueIndex = 0, entryId = 1),
                        UpcomingTrack(
                            title = "Queued",
                            isCurrent = false,
                            isPriority = true,
                            queueIndex = 1,
                            entryId = 2,
                        ),
                    ),
                    priorityQueueSize = 1,
                ),
                onRemoveFromQueue = { removed = it },
            )
        }
        composeRule.onNodeWithTag("queue_peek_strip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_sheet").assertExists()
        composeRule.onNodeWithTag("queue_remove").performClick()
        assertEquals(1, removed)
    }

    @Test
    fun `idle queue track row does not paint swipe red`() {
        composeRule.setContent {
            PlayerBar(
                state = PlayerBarState(
                    title = "Current",
                    artist = "Artist",
                    expanded = true,
                    queueSize = 2,
                    nextTracks = listOf(
                        UpcomingTrack(title = "Current", isCurrent = true, queueIndex = 0, entryId = 1),
                        UpcomingTrack(
                            title = "Queued",
                            isCurrent = false,
                            isPriority = true,
                            queueIndex = 1,
                            entryId = 2,
                        ),
                    ),
                    priorityQueueSize = 1,
                ),
            )
        }
        composeRule.onNodeWithTag("queue_peek_strip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_sheet").assertExists()
        // Same setup as remove-callback test: row is composed when queue_remove exists.
        composeRule.onNodeWithTag("queue_remove").assertExists()
        composeRule.onNodeWithTag("queue_track_row", useUnmergedTree = true).assertExists()
        // Token contract: idle fill must stay opaque elevated (ADR-0073).
        assertEquals(SurfaceElevated, QueueTrackRowIdleBackground)
        assertTrue(QueueTrackRowIdleBackground.alpha >= 1f)
        assertNotEquals(DestructiveRed, QueueTrackRowIdleBackground)
    }
}
