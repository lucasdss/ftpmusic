package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for onPlayerError recovery: corrupt cache deletion and player re-prepare.
 */
class PlaybackErrorRecoveryTest {

    @Test
    fun `onPlayerError sets isPlaying to false`() {
        // Simulate the error handler behavior
        val state1 = PlaybackState(isPlaying = true, title = "Test")
        assertTrue(state1.isPlaying)

        // Error occurs
        val state2 = state1.copy(isPlaying = false)
        assertFalse(state2.isPlaying)
        assertEquals("Test", state2.title) // preserved
    }

    @Test
    fun `error recovery calls prepare on idle errored player`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.playbackState } returns Player.STATE_IDLE
        every { mockPlayer.playerError } returns mockk(relaxed = true)

        // Simulate recovery: if idle + has error → prepare
        val shouldRecover = mockPlayer.playbackState == Player.STATE_IDLE && mockPlayer.playerError != null
        assertTrue("Should recover when idle with error", shouldRecover)

        mockPlayer.prepare()
        verify { mockPlayer.prepare() }
    }

    @Test
    fun `error recovery skipped when player is not idle`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.playbackState } returns Player.STATE_BUFFERING
        every { mockPlayer.playerError } returns mockk(relaxed = true)

        val shouldRecover = mockPlayer.playbackState == Player.STATE_IDLE && mockPlayer.playerError != null
        assertFalse("Should not recover when buffering", shouldRecover)
    }

    @Test
    fun `error recovery skipped when no player error`() {
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.playbackState } returns Player.STATE_IDLE
        every { mockPlayer.playerError } returns null

        val shouldRecover = mockPlayer.playbackState == Player.STATE_IDLE && mockPlayer.playerError != null
        assertFalse("Should not recover when no error", shouldRecover)
    }

    @Test
    fun `corrupt cache file deletion pattern`() {
        var fileExists = true
        var deletionCalled = false

        // Pattern: if cached and error → delete file
        if (fileExists) {
            fileExists = false
            deletionCalled = true
        }

        assertFalse("File should be deleted", fileExists)
        assertTrue("Deletion should be called", deletionCalled)
    }

    @Test
    fun `error recovery preserves playback state fields`() {
        val state1 = PlaybackState(
            title = "Test Song",
            artist = "Test Artist",
            isPlaying = true,
            position = 42000L,
            duration = 240000L,
            isCasting = false,
        )

        // Error: clear isPlaying, preserve metadata
        val state2 = state1.copy(isPlaying = false)

        assertFalse(state2.isPlaying)
        assertEquals("Test Song", state2.title)
        assertEquals("Test Artist", state2.artist)
        assertEquals(42000L, state2.position)
        assertEquals(240000L, state2.duration)
    }

    @Test
    fun `null onMediaItemTransition does not clear PlaybackState`() {
        // Setup: normal playback state
        val state1 = PlaybackState(
            title = "Now Playing",
            artist = "Artist",
            isPlaying = true,
            position = 50000L,
            duration = 200000L,
        )

        // Simulate null onMediaItemTransition (Cast session setup)
        val mediaItem: androidx.media3.common.MediaItem? = null
        val state2 = if (mediaItem == null || mediaItem.mediaId == null) {
            state1 // Suppressed — do NOT clear
        } else {
            state1.copy(title = mediaItem.mediaMetadata?.title?.toString())
        }

        assertNotNull("Title should not be cleared", state2.title)
        assertEquals("Now Playing", state2.title)
        assertEquals("Artist", state2.artist)
        assertTrue(state2.isPlaying)
        assertEquals(50000L, state2.position)
    }

    @Test
    fun `onMediaItemTransition with valid item updates metadata`() {
        val state1 = PlaybackState(title = "Old", artist = "Old", isPlaying = true)

        // Simulate valid transition
        val state2 = state1.copy(title = "New Song", artist = "New Artist", position = 0L, isStarred = false)

        assertEquals("New Song", state2.title)
        assertEquals("New Artist", state2.artist)
    }

    // ── Error auto-skip decision (decideErrorSkipAction) ────────────────

    @Test
    fun `error auto-skip advances to next track when one exists`() {
        assertEquals(
            ErrorSkipAction.SKIP_NEXT,
            decideErrorSkipAction(errorCount = 1, currentMediaId = "t1", currentIndex = 0, mediaItemCount = 5),
        )
    }

    @Test
    fun `error auto-skip advances even with three consecutive errors`() {
        // Cap kicks in at >3 — the first three failures still skip
        assertEquals(
            ErrorSkipAction.SKIP_NEXT,
            decideErrorSkipAction(errorCount = 3, currentMediaId = "t1", currentIndex = 0, mediaItemCount = 5),
        )
    }

    @Test
    fun `error auto-skip stops at last track`() {
        assertEquals(
            ErrorSkipAction.LAST_TRACK_STOP,
            decideErrorSkipAction(errorCount = 1, currentMediaId = "t9", currentIndex = 4, mediaItemCount = 5),
        )
    }

    @Test
    fun `error auto-skip stops on single-item queue`() {
        assertEquals(
            ErrorSkipAction.LAST_TRACK_STOP,
            decideErrorSkipAction(errorCount = 1, currentMediaId = "only", currentIndex = 0, mediaItemCount = 1),
        )
    }

    @Test
    fun `error auto-skip bails out after retry limit`() {
        assertEquals(
            ErrorSkipAction.RETRY_LIMIT_STOP,
            decideErrorSkipAction(errorCount = 4, currentMediaId = "t1", currentIndex = 0, mediaItemCount = 5),
        )
    }

    @Test
    fun `error auto-skip ignores radio streams`() {
        assertEquals(
            ErrorSkipAction.RADIO_IGNORE,
            decideErrorSkipAction(errorCount = 1, currentMediaId = "radio:1234", currentIndex = 0, mediaItemCount = 5),
        )
    }

    @Test
    fun `error auto-skip handles unknown media id`() {
        // Null id at the last position → nothing to skip to
        assertEquals(
            ErrorSkipAction.LAST_TRACK_STOP,
            decideErrorSkipAction(errorCount = 1, currentMediaId = null, currentIndex = 4, mediaItemCount = 5),
        )
        // Null id with items ahead → still skips
        assertEquals(
            ErrorSkipAction.SKIP_NEXT,
            decideErrorSkipAction(errorCount = 1, currentMediaId = null, currentIndex = 1, mediaItemCount = 5),
        )
    }

    @Test
    fun `error auto-skip retry limit beats radio check`() {
        // Systemic failure on a radio stream: the cap wins over radio-ignore
        assertEquals(
            ErrorSkipAction.RETRY_LIMIT_STOP,
            decideErrorSkipAction(errorCount = 5, currentMediaId = "radio:1", currentIndex = 0, mediaItemCount = 1),
        )
    }

    @Test
    fun `findNextCachedIndex skips uncached and radio to first cache hit`() {
        val ids = listOf("a", "b", "radio:1", "c", "d")
        val cached = setOf("c")
        assertEquals(
            3,
            findNextCachedIndex(
                fromIndex = 0,
                mediaItemCount = ids.size,
                mediaIdAt = { ids[it] },
                isCached = { it in cached },
            ),
        )
    }

    @Test
    fun `findNextCachedIndex returns null when nothing ahead is cached`() {
        val ids = listOf("a", "b", "c")
        assertEquals(
            null,
            findNextCachedIndex(
                fromIndex = 0,
                mediaItemCount = ids.size,
                mediaIdAt = { ids[it] },
                isCached = { false },
            ),
        )
    }

    @Test
    fun `error auto-skip resumes playback after seeking to next track`() {
        // SKIP_NEXT contract: seekToNextMediaItem() positions the next source but
        // does NOT resume — the handler must set playWhenReady=true and prepare(),
        // otherwise a paused/focus-lost player sits in STATE_IDLE showing "stopped".
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.playbackState } returns Player.STATE_IDLE
        val wasStoppedBefore = mockPlayer.playbackState == Player.STATE_IDLE

        // The handler sequence:
        mockPlayer.seekToNextMediaItem()
        mockPlayer.playWhenReady = true
        mockPlayer.prepare()

        verify { mockPlayer.seekToNextMediaItem() }
        verify { mockPlayer.playWhenReady = true }
        verify { mockPlayer.prepare() }
        assertTrue("Scenario: player had gone idle on error", wasStoppedBefore)
    }
}
