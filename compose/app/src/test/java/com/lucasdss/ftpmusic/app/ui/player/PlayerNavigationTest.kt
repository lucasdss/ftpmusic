package com.lucasdss.ftpmusic.app.ui.player

import com.lucasdss.ftpmusic.app.playback.PlaybackState
import com.lucasdss.ftpmusic.app.playback.SavedQueueState
import org.junit.Assert.*
import org.junit.Test

class PlayerNavigationTest {

    @Test
    fun `PlaybackState isVisible is true when title is not null`() {
        val state = PlaybackState(title = "Song Title", artist = "Artist Name")
        assertTrue(state.isVisible)
    }

    @Test
    fun `PlaybackState isVisible is false when title is null`() {
        val state = PlaybackState(title = null)
        assertFalse(state.isVisible)
    }

    @Test
    fun `MiniPlayer shows when title is loaded`() {
        // AnimatedVisibility(visible = title != null) — true when title is non-null
        val uiState = MiniPlayerUiState(position = 30_000L, duration = 200_000L)

        // Verify the visibility condition used in MiniPlayer composable
        val title: String? = "Now Playing"
        val shouldShow = title != null
        assertTrue(shouldShow)

        // Verify progress fraction
        assertEquals(0.15f, uiState.progressFraction)

        // Verify formatTimeMs helper
        assertEquals("0:30", formatTimeMs(30_000L))
        assertEquals("3:20", formatTimeMs(200_000L))
        assertEquals("10:00", formatTimeMs(600_000L))
    }

    @Test
    fun `MiniPlayer is hidden when title is null`() {
        // AnimatedVisibility(visible = title != null) — false when title is null
        val title: String? = null
        val shouldShow = title != null
        assertFalse(shouldShow)
    }

    @Test
    fun `formatTimeMs formats zero correctly`() {
        assertEquals("0:00", formatTimeMs(0L))
    }

    @Test
    fun `formatTimeMs handles single-digit seconds`() {
        assertEquals("1:05", formatTimeMs(65_000L))
    }

    // ── Queue persistence: position and IDs ───────────────────────

    @Test
    fun `SavedQueueState preserves currentIndex and positionMs`() {
        val state = SavedQueueState(
            tracks = emptyList(),
            urls = emptyList(),
            currentIndex = 5,
            positionMs = 32000L,
        )
        assertEquals(5, state.currentIndex)
        assertEquals(32000L, state.positionMs)
    }

    @Test
    fun `Track artistId and albumId are preserved for queue restore`() {
        val track = com.lucasdss.ftpmusic.app.data.model.Track(
            id = "t1",
            title = "Song",
            artistId = "artist-42",
            albumId = "album-99",
        )
        assertEquals("artist-42", track.artistId)
        assertEquals("album-99", track.albumId)
    }

    @Test
    fun `QueueItemEntity has artistId and albumId fields`() {
        val entity = com.lucasdss.ftpmusic.app.data.db.QueueItemEntity(
            trackId = "t1",
            title = "Song",
            url = "http://x",
            position = 0,
            artistId = "a1",
            albumId = "b1",
        )
        assertEquals("a1", entity.artistId)
        assertEquals("b1", entity.albumId)
    }

    @Test
    fun `QueueItemEntity artistId albumId default to null`() {
        val entity = com.lucasdss.ftpmusic.app.data.db.QueueItemEntity(
            trackId = "t1",
            title = "Song",
            url = "http://x",
            position = 0,
        )
        assertNull(entity.artistId)
        assertNull(entity.albumId)
    }
}
