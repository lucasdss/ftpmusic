package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.Player
import org.junit.Assert.*
import org.junit.Test

class PlaybackStateTest {

    // ── isVisible ──────────────────────────────────────────────────────────

    @Test
    fun `isVisible is true when title is non-null`() {
        val state = PlaybackState(title = "Song Name")
        assertTrue(state.isVisible)
    }

    @Test
    fun `isVisible is false when title is null`() {
        val state = PlaybackState(title = null)
        assertFalse(state.isVisible)
    }

    @Test
    fun `isVisible is false for default state`() {
        val state = PlaybackState()
        assertFalse(state.isVisible)
    }

    // ── Default values ─────────────────────────────────────────────────────

    @Test
    fun `default isPlaying is false`() {
        assertEquals(false, PlaybackState().isPlaying)
    }

    @Test
    fun `default position and duration are zero`() {
        val state = PlaybackState()
        assertEquals(0L, state.position)
        assertEquals(0L, state.duration)
    }

    @Test
    fun `default repeat mode is OFF`() {
        assertEquals(Player.REPEAT_MODE_OFF, PlaybackState().repeatMode)
    }

    @Test
    fun `default volume is 1f and not muted`() {
        val state = PlaybackState()
        assertEquals(1.0f, state.volume)
        assertEquals(false, state.muted)
    }

    @Test
    fun `default playbackSpeed is 1f`() {
        assertEquals(1.0f, PlaybackState().playbackSpeed)
    }

    @Test
    fun `default isCasting is false`() {
        assertEquals(false, PlaybackState().isCasting)
        assertNull(PlaybackState().castDeviceName)
    }

    @Test
    fun `default isStarred is false`() {
        assertEquals(false, PlaybackState().isStarred)
    }

    @Test
    fun `default mediaType is music`() {
        assertEquals("music", PlaybackState().mediaType)
    }

    @Test
    fun `default isQueueSynced is true`() {
        assertEquals(true, PlaybackState().isQueueSynced)
    }

    @Test
    fun `default downloadedTrackIds is empty`() {
        assertEquals(emptySet<String>(), PlaybackState().downloadedTrackIds)
    }

    @Test
    fun `default sleepTimerEndMs is zero`() {
        assertEquals(0L, PlaybackState().sleepTimerEndMs)
    }

    @Test
    fun `default trackIndex and queueSize are zero`() {
        val state = PlaybackState()
        assertEquals(0, state.trackIndex)
        assertEquals(0, state.queueSize)
    }

    // ── Full state construction ────────────────────────────────────────────

    @Test
    fun `all fields are independently settable`() {
        val state = PlaybackState(
            title = "T", artist = "A", album = "AL", coverArtId = "C1",
            isPlaying = true, position = 5000L, duration = 300000L,
            repeatMode = Player.REPEAT_MODE_ALL, shuffleModeEnabled = true,
            nextTrackTitle = "Next", nextTrackArtist = "NextArt",
            isCasting = true, castDeviceName = "TV",
            volume = 0.5f, muted = true, playbackSpeed = 1.5f,
            sleepTimerEndMs = 999L, currentTrackId = "t1",
            isStarred = true, mediaType = "podcast",
            trackIndex = 3, queueSize = 10,
            artistId = "a1", albumId = "al1",
            isOffline = true, isQueueSynced = false,
            downloadedTrackIds = setOf("t1", "t2"),
        )

        assertEquals("T", state.title)
        assertEquals("A", state.artist)
        assertEquals("AL", state.album)
        assertEquals("C1", state.coverArtId)
        assertTrue(state.isPlaying)
        assertEquals(5000L, state.position)
        assertEquals(300000L, state.duration)
        assertEquals(Player.REPEAT_MODE_ALL, state.repeatMode)
        assertTrue(state.shuffleModeEnabled)
        assertEquals("Next", state.nextTrackTitle)
        assertEquals("NextArt", state.nextTrackArtist)
        assertTrue(state.isCasting)
        assertEquals("TV", state.castDeviceName)
        assertEquals(0.5f, state.volume)
        assertTrue(state.muted)
        assertEquals(1.5f, state.playbackSpeed)
        assertEquals(999L, state.sleepTimerEndMs)
        assertEquals("t1", state.currentTrackId)
        assertTrue(state.isStarred)
        assertEquals("podcast", state.mediaType)
        assertEquals(3, state.trackIndex)
        assertEquals(10, state.queueSize)
        assertEquals("a1", state.artistId)
        assertEquals("al1", state.albumId)
        assertTrue(state.isOffline)
        assertFalse(state.isQueueSynced)
        assertEquals(setOf("t1", "t2"), state.downloadedTrackIds)
    }

    @Test
    fun `isQueueSynced is true by default`() {
        assertEquals(true, PlaybackState().isQueueSynced)
    }

    @Test
    fun `fromPlayer computes isQueueSynced from PlayerHolder queueSaveFailed`() {
        val mockPlayer = io.mockk.mockk<androidx.media3.common.Player>(relaxed = true)

        PlayerHolder.queueSaveFailed = false
        assertTrue(PlaybackState.fromPlayer(mockPlayer).isQueueSynced)

        PlayerHolder.queueSaveFailed = true
        assertFalse(PlaybackState.fromPlayer(mockPlayer).isQueueSynced)

        PlayerHolder.queueSaveFailed = false
    }
}
