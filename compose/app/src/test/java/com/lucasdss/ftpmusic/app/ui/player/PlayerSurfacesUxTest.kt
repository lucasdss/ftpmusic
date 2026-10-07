package com.lucasdss.ftpmusic.app.ui.player

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
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

    @Test
    fun `queue sheet uses Spotify Next in Queue lexicon for priority section`() {
        composeRule.setContent {
            PlayerBar(
                state = PlayerBarState(
                    title = "Current",
                    artist = "Artist",
                    expanded = true,
                    queueSize = 2,
                    priorityQueueSize = 1,
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
                ),
            )
        }
        composeRule.onNodeWithTag("queue_peek_strip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_section_priority").assertExists()
        composeRule.onNodeWithText("Next in Queue · 1").assertExists()
        composeRule.onNodeWithText("Clear Next in Queue").assertExists()
    }

    @Test
    fun `queue sheet Clear queue invokes onClearQueue only`() {
        var cleared = false
        composeRule.setContent {
            PlayerBar(
                state = PlayerBarState(
                    title = "Current",
                    artist = "Artist",
                    expanded = true,
                    queueSize = 2,
                    priorityQueueSize = 1,
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
                ),
                onClearQueue = { cleared = true },
            )
        }
        composeRule.onNodeWithTag("queue_peek_strip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_clear_priority").assertExists().performClick()
        assertTrue(cleared)
    }

    @Test
    fun `queue sheet Autoplay toggle and clear-autoplay wired`() {
        var continuous: Boolean? = null
        var clearedAutoplay = false
        composeRule.setContent {
            PlayerBar(
                state = PlayerBarState(
                    title = "Current",
                    artist = "Artist",
                    expanded = true,
                    continuousPlayEnabled = true,
                    queueSize = 2,
                    nextTracks = listOf(
                        UpcomingTrack(title = "Current", isCurrent = true, queueIndex = 0, entryId = 1),
                        UpcomingTrack(
                            title = "Radio",
                            isCurrent = false,
                            isPriority = false,
                            isAutoplay = true,
                            queueIndex = 1,
                            entryId = 2,
                        ),
                    ),
                ),
                onContinuousPlayChange = { continuous = it },
                onClearAutoplayQueue = { clearedAutoplay = true },
            )
        }
        composeRule.onNodeWithTag("queue_peek_strip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_section_autoplay").assertExists()
        composeRule.onNodeWithTag("queue_continuous_play_switch").assertExists().performClick()
        assertEquals(false, continuous)
        composeRule.onNodeWithTag("queue_clear_autoplay").assertExists().performClick()
        assertTrue(clearedAutoplay)
        composeRule.onNodeWithTag("queue_autoplay_cast_unavailable").assertDoesNotExist()
    }

    @Test
    fun `queue sheet Autoplay switch enabled while casting`() {
        // ADR-0093: Cast CP Step 2 — switch interactive; flatten notice still shown
        var continuous: Boolean? = null
        composeRule.setContent {
            PlayerBar(
                state = PlayerBarState(
                    title = "Current",
                    artist = "Artist",
                    expanded = true,
                    isCasting = true,
                    castDeviceName = "Living Room",
                    continuousPlayEnabled = true,
                    queueSize = 2,
                    nextTracks = listOf(
                        UpcomingTrack(title = "Current", isCurrent = true, queueIndex = 0, entryId = 1),
                        UpcomingTrack(
                            title = "Radio",
                            isCurrent = false,
                            isPriority = false,
                            isAutoplay = true,
                            queueIndex = 1,
                            entryId = 2,
                        ),
                    ),
                ),
                onContinuousPlayChange = { continuous = it },
            )
        }
        composeRule.onNodeWithTag("queue_peek_strip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_autoplay_cast_unavailable").assertDoesNotExist()
        composeRule.onNodeWithTag("queue_cast_flatten_notice").assertExists()
        composeRule.onNodeWithTag("queue_clear_autoplay").assertExists()
        composeRule.onNodeWithTag("queue_continuous_play_switch").assertIsOn()
        composeRule.onNodeWithTag("queue_continuous_play_switch").performClick()
        assertEquals(false, continuous)
    }

    @Test
    fun `queue sheet save tag opens save affordance`() {
        composeRule.setContent {
            PlayerBar(
                state = PlayerBarState(
                    title = "Current",
                    artist = "Artist",
                    expanded = true,
                    queueSize = 2,
                    priorityQueueSize = 1,
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
                ),
            )
        }
        composeRule.onNodeWithTag("queue_peek_strip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_sheet_save").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_save_name_field").assertExists()
    }

    @Test
    fun `queue sheet shows Recently Played when history fed`() {
        composeRule.setContent {
            PlayerBar(
                state = PlayerBarState(
                    title = "Current",
                    artist = "Artist",
                    expanded = true,
                    queueHistory = listOf(
                        com.lucasdss.ftpmusic.app.playback.QueueHistoryTrack(
                            id = "h1",
                            title = "Old Song",
                            artist = "Past Artist",
                        ),
                    ),
                ),
            )
        }
        composeRule.onNodeWithTag("queue_peek_strip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_section_history").assertExists()
        composeRule.onNodeWithTag("queue_history_row").assertExists()
    }

    @Test
    fun `queue sheet selection enter and remove batch`() {
        var removed: Set<Int>? = null
        composeRule.setContent {
            PlayerBar(
                state = PlayerBarState(
                    title = "Current",
                    artist = "Artist",
                    expanded = true,
                    queueSize = 2,
                    priorityQueueSize = 1,
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
                ),
                onRemoveFromQueueBatch = { removed = it },
            )
        }
        composeRule.onNodeWithTag("queue_peek_strip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_select_enter").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_select_checkbox").assertExists().performClick()
        composeRule.onNodeWithTag("queue_select_remove").assertExists().performClick()
        assertEquals(setOf(1), removed)
    }

    @Test
    fun `queue sheet sleep and repeat strip wired`() {
        var sleep = false
        var repeat = false
        composeRule.setContent {
            PlayerBar(
                state = PlayerBarState(
                    title = "Current",
                    artist = "Artist",
                    expanded = true,
                    repeatMode = 1,
                ),
                onSleepTimer = { sleep = true },
                onRepeatToggle = { repeat = true },
            )
        }
        composeRule.onNodeWithTag("queue_peek_strip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_sheet_sleep").assertExists().performClick()
        assertTrue(sleep)
        composeRule.onNodeWithTag("queue_sheet_repeat").assertExists().performClick()
        assertTrue(repeat)
    }

    @Test
    fun `queue sheet selection hides Clear and sleep strip`() {
        composeRule.setContent {
            PlayerBar(
                state = PlayerBarState(
                    title = "Current",
                    artist = "Artist",
                    expanded = true,
                    queueSize = 3,
                    priorityQueueSize = 1,
                    continuousPlayEnabled = true,
                    nextTracks = listOf(
                        UpcomingTrack(title = "Current", isCurrent = true, queueIndex = 0, entryId = 1),
                        UpcomingTrack(
                            title = "Queued",
                            isCurrent = false,
                            isPriority = true,
                            queueIndex = 1,
                            entryId = 2,
                        ),
                        UpcomingTrack(
                            title = "Radio",
                            isCurrent = false,
                            isPriority = false,
                            isAutoplay = true,
                            queueIndex = 2,
                            entryId = 3,
                        ),
                    ),
                ),
            )
        }
        composeRule.onNodeWithTag("queue_peek_strip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_clear_priority").assertExists()
        composeRule.onNodeWithTag("queue_clear_autoplay").assertExists()
        composeRule.onNodeWithTag("queue_sheet_sleep").assertExists()
        composeRule.onNodeWithTag("queue_select_enter").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_clear_priority").assertDoesNotExist()
        composeRule.onNodeWithTag("queue_clear_autoplay").assertDoesNotExist()
        composeRule.onNodeWithTag("queue_sheet_sleep").assertDoesNotExist()
        composeRule.onNodeWithTag("queue_sheet_repeat").assertDoesNotExist()
        composeRule.onNodeWithTag("queue_select_cancel").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_clear_priority").assertExists()
        composeRule.onNodeWithTag("queue_sheet_sleep").assertExists()
    }

    @Test
    fun `queue sheet shows cast flatten notice when casting`() {
        composeRule.setContent {
            PlayerBar(
                state = PlayerBarState(
                    title = "Current",
                    artist = "Artist",
                    expanded = true,
                    isCasting = true,
                    queueSize = 1,
                    nextTracks = listOf(
                        UpcomingTrack(title = "Current", isCurrent = true, queueIndex = 0, entryId = 1),
                    ),
                ),
            )
        }
        composeRule.onNodeWithTag("queue_peek_strip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_cast_flatten_notice").assertExists()
    }
}
