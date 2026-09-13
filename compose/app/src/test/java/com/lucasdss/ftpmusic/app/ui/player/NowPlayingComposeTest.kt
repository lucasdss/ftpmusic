package com.lucasdss.ftpmusic.app.ui.player

import android.app.Application
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.lucasdss.ftpmusic.app.playback.PlaybackState
import com.lucasdss.ftpmusic.app.playback.PlayerHolder
import com.lucasdss.ftpmusic.app.playback.UpcomingTrack
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Compose UI tests for PlayerBar in full Now-Playing mode (expanded = true). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h800dp")
class NowPlayingComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun fullState(
        isPlaying: Boolean = false,
        isCasting: Boolean = false,
        castDeviceName: String? = null,
        isStarred: Boolean = false,
        repeatMode: Int = 0,
        shuffleModeEnabled: Boolean = false,
    ) = PlayerBarState(
        title = "Now Playing Song",
        artist = "Test Artist",
        album = "Test Album",
        isPlaying = isPlaying,
        isCasting = isCasting,
        castDeviceName = castDeviceName,
        isStarred = isStarred,
        repeatMode = repeatMode,
        shuffleModeEnabled = shuffleModeEnabled,
        expanded = true,
        nextTracks = emptyList(),
    )

    /** 3-track queue, current = middle, every track with art. */
    private fun fullStateWithQueue(art: Boolean = true) = fullState().copy(
        coverArtUrl = if (art) "https://example.com/current.jpg" else null,
        queueSize = 3,
        nextTracks = List(3) { i ->
            UpcomingTrack(
                title = "Track $i",
                artist = "Artist $i",
                coverArtUrl = if (art) "https://example.com/art-$i.jpg" else null,
                isCurrent = i == 1,
            )
        },
    )

    @Test
    fun `title artist and album rendered`() {
        composeRule.setContent {
            PlayerBar(state = fullState())
        }
        composeRule.onNodeWithText("Now Playing Song").assertExists()
        // Artist appears both as top-bar chip and under the title in the player tab
        composeRule.onAllNodesWithText("Test Artist").onFirst().assertExists()
        composeRule.onNodeWithText("Test Album").assertExists()
    }

    @Test
    fun `play button when paused`() {
        composeRule.setContent {
            PlayerBar(state = fullState(isPlaying = false))
        }
        composeRule.onNodeWithContentDescription("Play").assertExists()
    }

    @Test
    fun `pause button when playing`() {
        composeRule.setContent {
            PlayerBar(state = fullState(isPlaying = true))
        }
        composeRule.onNodeWithContentDescription("Pause").assertExists()
    }

    @Test
    fun `click play invokes onPlayPause`() {
        var invoked = false
        composeRule.setContent {
            PlayerBar(state = fullState(), onPlayPause = { invoked = true })
        }
        composeRule.onNodeWithContentDescription("Play").performClick()
        assertTrue(invoked)
    }

    @Test
    fun `skip next invokes onSkipNext`() {
        var invoked = false
        composeRule.setContent {
            PlayerBar(state = fullState(), onSkipNext = { invoked = true })
        }
        composeRule.onNodeWithContentDescription("Next").performClick()
        assertTrue(invoked)
    }

    @Test
    fun `skip prev invokes onSkipPrev`() {
        var invoked = false
        composeRule.setContent {
            PlayerBar(state = fullState(), onSkipPrev = { invoked = true })
        }
        composeRule.onNodeWithContentDescription("Previous").performClick()
        assertTrue(invoked)
    }

    @Test
    fun `repeat toggle invokes onRepeatToggle`() {
        var invoked = false
        composeRule.setContent {
            PlayerBar(state = fullState(), onRepeatToggle = { invoked = true })
        }
        composeRule.onNodeWithContentDescription("Repeat").performClick()
        assertTrue(invoked)
    }

    @Test
    fun `shuffle toggle invokes onShuffleToggle`() {
        var invoked = false
        composeRule.setContent {
            PlayerBar(state = fullState(), onShuffleToggle = { invoked = true })
        }
        composeRule.onNodeWithContentDescription("Shuffle").performClick()
        assertTrue(invoked)
    }

    @Test
    fun `like toggle invokes onToggleLike`() {
        var invoked = false
        composeRule.setContent {
            PlayerBar(state = fullState(), onToggleLike = { invoked = true })
        }
        composeRule.onNodeWithContentDescription("Like").performClick()
        assertTrue(invoked)
    }

    @Test
    fun `dislike toggle invokes onToggleDislike`() {
        var invoked = false
        composeRule.setContent {
            PlayerBar(state = fullState(), onToggleDislike = { invoked = true })
        }
        composeRule.onNodeWithContentDescription("Dislike").performClick()
        assertTrue(invoked)
    }

    @Test
    fun `rating tap invokes onRate`() {
        var rated = -1
        composeRule.setContent {
            PlayerBar(state = fullState(), onRate = { rated = it })
        }
        composeRule.onNodeWithContentDescription("Rate 3").performClick()
        assertEquals(3, rated)
    }

    @Test
    fun `filled like when liked`() {
        composeRule.setContent {
            PlayerBar(state = fullState(isStarred = true))
        }
        composeRule.onNodeWithContentDescription("Unlike").assertExists()
    }

    @Test
    fun `volume slider visible when casting`() {
        composeRule.setContent {
            PlayerBar(state = fullState(isCasting = true, castDeviceName = "TV"))
        }
        // Volume control row only present when isCasting=true
        composeRule.onNodeWithContentDescription("Volume").assertExists()
    }

    @Test
    fun `volume slider hidden when not casting`() {
        composeRule.setContent {
            PlayerBar(state = fullState(isCasting = false))
        }
        composeRule.onAllNodesWithContentDescription("Volume").assertCountEquals(0)
    }

    @Test
    fun `cast banner shows device name`() {
        composeRule.setContent {
            PlayerBar(state = fullState(isCasting = true, castDeviceName = "Living Room TV"))
        }
        composeRule.onNodeWithText("Casting to Living Room TV").assertExists()
    }

    @Test
    fun `artist name click invokes onArtistClick`() {
        var invoked = false
        composeRule.setContent {
            PlayerBar(state = fullState(), onArtistClick = { invoked = true })
        }
        // First match is the clickable top-bar artist chip
        composeRule.onAllNodesWithText("Test Artist").onFirst().performClick()
        assertTrue(invoked)
    }

    @Test
    fun `album name click invokes onAlbumClick`() {
        var invoked = false
        composeRule.setContent {
            PlayerBar(state = fullState(), onAlbumClick = { invoked = true })
        }
        composeRule.onNodeWithText("Test Album").performClick()
        assertTrue(invoked)
    }

    @Test
    fun `back button invokes onBack`() {
        var invoked = false
        composeRule.setContent {
            PlayerBar(state = fullState(), onBack = { invoked = true })
        }
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(invoked)
    }

    @Test
    fun `swipe down on now playing minimizes`() {
        var closed = false
        composeRule.setContent {
            PlayerBar(state = fullState(), onBack = { closed = true })
        }
        // Swipe down anywhere on the screen — the drag-to-dismiss wrapper fires
        composeRule.onRoot().performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        assertTrue("Swipe down must minimize", closed)
    }

    @Test
    fun `swipe up on peek strip opens the queue sheet`() {
        composeRule.setContent {
            PlayerBar(state = fullState())
        }
        // Swipe must START inside the strip — its bottom edge is the screen
        // bottom (hit-test exclusive), so a swipe from y=bottom is dead.
        composeRule.onNodeWithTag("queue_peek_strip").performTouchInput {
            swipe(
                start = androidx.compose.ui.geometry.Offset(centerX, bottom - 10f),
                end = androidx.compose.ui.geometry.Offset(centerX, top - 20f),
                durationMillis = 200,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_sheet").assertExists()
    }

    @Test
    fun `swipe down on sheet handle closes the queue sheet`() {
        composeRule.setContent {
            PlayerBar(state = fullState())
        }
        // Open the sheet via the peek strip tap, then swipe down on the handle
        composeRule.onNodeWithTag("queue_peek_strip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_sheet").assertExists()

        composeRule.onNodeWithTag("queue_sheet_handle").performTouchInput {
            // Explicit long swipe down, past the sheet's halfway point and
            // beyond the screen bottom — matches a real user's swipe-to-close
            // motion on the handle (sheet closes once dragged > 50% down).
            swipe(
                start = androidx.compose.ui.geometry.Offset(centerX, top),
                end = androidx.compose.ui.geometry.Offset(centerX, bottom + 500f),
                durationMillis = 300,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_sheet").assertDoesNotExist()
    }

    @Test
    fun `small swipe up on peek strip does not open the queue sheet`() {
        composeRule.setContent {
            PlayerBar(state = fullState())
        }
        // 30px inside the strip on an 800px screen = 3.75% < open threshold
        // (5%) — must spring back closed.
        composeRule.onNodeWithTag("queue_peek_strip").performTouchInput {
            swipe(
                start = androidx.compose.ui.geometry.Offset(centerX, centerY + 15f),
                end = androidx.compose.ui.geometry.Offset(centerX, centerY - 15f),
                durationMillis = 100,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_sheet").assertDoesNotExist()
    }

    @Test
    fun `small swipe down on sheet handle does not close the queue sheet`() {
        composeRule.setContent {
            PlayerBar(state = fullState())
        }
        composeRule.onNodeWithTag("queue_peek_strip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_sheet").assertExists()

        // 20px down = 2.5% of the sheet — must spring back open
        composeRule.onNodeWithTag("queue_sheet_handle").performTouchInput {
            swipe(
                start = androidx.compose.ui.geometry.Offset(centerX, top),
                end = androidx.compose.ui.geometry.Offset(centerX, top + 20f),
                durationMillis = 100,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_sheet").assertExists()
    }

    @Test
    fun `queue content is scrollable with a long queue`() {
        composeRule.setContent {
            PlayerBar(
                state = fullState().copy(
                    queueSize = 40,
                    nextTracks = List(40) { i ->
                        UpcomingTrack(
                            title = "Track $i",
                            artist = "Artist $i",
                            coverArtUrl = "https://example.com/art-$i.jpg",
                            isCurrent = i == 5,
                            queueIndex = i,
                        )
                    },
                ),
            )
        }
        composeRule.onNodeWithTag("queue_peek_strip").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_sheet").assertExists()
        // The track list must expose vertical scroll semantics (regression: the
        // sheet content was a non-scrollable Column overflowing the screen).
        composeRule.onNodeWithTag("queue_scroll").assert(
            androidx.compose.ui.test.SemanticsMatcher("has vertical scroll range") {
                it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)
            },
        )
    }

    @Test
    fun `peek strip shows combined track and artist on one line`() {
        composeRule.setContent {
            PlayerBar(
                state = fullState().copy(
                    nextTrackTitle = "My Next",
                    nextTrackArtist = "Next Artist",
                ),
            )
        }
        composeRule.onNodeWithText("My Next (Next Artist)").assertExists()
    }

    @Test
    fun `peek strip shows title only when artist is null`() {
        composeRule.setContent {
            PlayerBar(state = fullState().copy(nextTrackTitle = "Solo"))
        }
        composeRule.onNodeWithText("Solo").assertExists()
    }

    // ── Art edge-peek cards (YouTube Music style) ───────────────────────────

    @Test
    fun `prev and next peek cards visible at idle with neighbors`() {
        composeRule.setContent {
            PlayerBar(state = fullStateWithQueue())
        }
        composeRule.onNodeWithTag("now_playing_prev_peek").assertIsDisplayed()
        composeRule.onNodeWithTag("now_playing_next_peek").assertIsDisplayed()
        // Placement: prev pinned at the left screen edge, next at the right —
        // never overlapping the centered current card (window 400x800dp @1x).
        val prevCenter = composeRule.onNodeWithTag("now_playing_prev_peek").fetchSemanticsNode().boundsInRoot.center.x
        assertTrue("prev peek must sit left of center, was x=$prevCenter", prevCenter < 200f)
        val nextCenter = composeRule.onNodeWithTag("now_playing_next_peek").fetchSemanticsNode().boundsInRoot.center.x
        assertTrue("next peek must sit right of center, was x=$nextCenter", nextCenter > 200f)
    }

    @Test
    fun `peek cards hidden with single track queue`() {
        composeRule.setContent {
            PlayerBar(
                state = fullState().copy(
                    queueSize = 1,
                    nextTracks = listOf(
                        UpcomingTrack(
                            title = "Only",
                            artist = "Artist",
                            coverArtUrl = "https://example.com/a.jpg",
                            isCurrent = true,
                        ),
                    ),
                ),
            )
        }
        composeRule.onNodeWithTag("now_playing_prev_peek").assertDoesNotExist()
        composeRule.onNodeWithTag("now_playing_next_peek").assertDoesNotExist()
    }

    @Test
    fun `peek cards hidden when neighbors lack art`() {
        composeRule.setContent {
            PlayerBar(state = fullStateWithQueue(art = false))
        }
        composeRule.onNodeWithTag("now_playing_prev_peek").assertDoesNotExist()
        composeRule.onNodeWithTag("now_playing_next_peek").assertDoesNotExist()
    }

    @Test
    fun `peek cards visible while casting`() {
        composeRule.setContent {
            PlayerBar(state = fullStateWithQueue().copy(isCasting = true, castDeviceName = "TV"))
        }
        composeRule.onNodeWithTag("now_playing_prev_peek").assertIsDisplayed()
        composeRule.onNodeWithTag("now_playing_next_peek").assertIsDisplayed()
    }

    @Test
    fun `swipe left on art invokes onSkipNext with peeks present`() {
        var next = false
        composeRule.setContent {
            PlayerBar(state = fullStateWithQueue(), onSkipNext = { next = true })
        }
        composeRule.onNodeWithContentDescription("Cover").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertTrue("Swipe left on art must advance", next)
    }

    @Test
    fun `swipe right on art invokes onSkipPrev with peeks present`() {
        var prev = false
        composeRule.setContent {
            PlayerBar(state = fullStateWithQueue(), onSkipPrev = { prev = true })
        }
        composeRule.onNodeWithContentDescription("Cover").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertTrue("Swipe right on art must go back", prev)
    }

    // ── Volume slider drag-fight prevention ────────────────────────────────

    @Test
    fun `fromPlayer prefers pendingCastVolume over castVolume during Cast`() {
        // The real volume-fight fix: fromPlayer() returns pendingCastVolume
        // until the device confirms it, so stale ramp values (2+ seconds)
        // never overwrite what the user just set.
        PlayerHolder.isCasting = true
        PlayerHolder.pendingCastVolume = 0.73f
        PlayerHolder.castVolume = 0.02f
        try {
            val mockPlayer = mockk<Player>(relaxed = true)
            every { mockPlayer.currentMediaItem } returns null
            every { mockPlayer.mediaItemCount } returns 0
            every { mockPlayer.currentMediaItemIndex } returns 0
            every { mockPlayer.playbackParameters } returns PlaybackParameters.DEFAULT
            val state = PlaybackState.fromPlayer(mockPlayer)
            assertEquals(0.73f, state.volume, 0.01f)
        } finally {
            PlayerHolder.pendingCastVolume = null
            PlayerHolder.isCasting = false
        }
    }

    @Test
    fun `volume change sends value through onVolumeChange`() {
        var sentVolume = -1f
        composeRule.setContent {
            PlayerBar(
                state = fullState(isCasting = true, castDeviceName = "TV")
                    // This test owns volume behavior, not the unsynced Cast
                    // spinner's intentional infinite transition.
                    .copy(volume = 0.5f, isQueueSynced = true),
                onVolumeChange = { sentVolume = it },
            )
        }
        val slider = composeRule.onNode(
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(
                androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
            ),
        )
        slider.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {
            it(1.0f)
        }
        composeRule.waitForIdle()
        // displayToVolume(1.0) = 1.0 — full volume passes through the cube curve
        assertEquals(1.0f, sentVolume, 0.01f)
    }
}
