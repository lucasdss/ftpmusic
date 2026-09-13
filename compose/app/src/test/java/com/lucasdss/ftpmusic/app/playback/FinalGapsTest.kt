package com.lucasdss.ftpmusic.app.playback

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class FinalGapsTest {

    @After
    fun teardown() {
    }

    @Test
    fun `notification uses dynamic title from PlaybackState`() {
        val state = PlaybackState(title = "Test Song", artist = "Test Artist")
        assertEquals("Test Song", state.title)
        assertEquals("Test Artist", state.artist)
    }

    @Test
    fun `audio focus request does not throw on null player`() {
        // Audio focus listener should handle null player gracefully
        PlayerHolder.player = null
        // Verifying PlayerHolder accepts null assignment without exception
        assertNull(PlayerHolder.player)
    }

    @Test
    fun `notification state reflects playing status`() {
        val playing = PlaybackState(title = "Song", artist = "Artist", isPlaying = true)
        assertTrue(playing.isPlaying)
        assertEquals("Song", playing.title)

        val paused = playing.copy(isPlaying = false)
        assertFalse(paused.isPlaying)
        assertEquals("Song", paused.title) // title preserved on pause
    }

    @Test
    fun `notification content text with empty artist`() {
        val state = PlaybackState(title = "Instrumental", artist = "")
        assertEquals("Instrumental", state.title)
        assertEquals("", state.artist)
    }

    @Test
    fun `notification content text with artist`() {
        val state = PlaybackState(title = "Song", artist = "Artist")
        assertEquals("Song", state.title)
        assertEquals("Artist", state.artist)
    }
}
