package com.lucasdss.ftpmusic.app.playback

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RemainingGapsTest {

    @Before
    fun setup() {
    }

    // QueueAutoLoader tests

    @Test
    fun `QUEUELOADER shouldLoadMore when near end`() {
        // currentIndex at position 98, totalLoaded = 100, threshold = 2
        // should return true because currentIndex >= totalLoaded - threshold (98 >= 98)
        assertTrue(MediaService.QueueAutoLoader.shouldLoadMore(currentIndex = 98, totalLoaded = 100))
    }

    @Test
    fun `QUEUELOADER shouldLoadMore when not near end`() {
        // currentIndex at position 10, totalLoaded = 100, threshold = 2
        // should return false because currentIndex < totalLoaded - threshold (10 < 90)
        assertFalse(MediaService.QueueAutoLoader.shouldLoadMore(currentIndex = 10, totalLoaded = 100))
    }

    @Test
    fun `QUEUELOADER shouldLoadMore when queue too small`() {
        // totalLoaded = 1, below threshold of 2
        // should return false regardless of currentIndex
        assertFalse(MediaService.QueueAutoLoader.shouldLoadMore(currentIndex = 0, totalLoaded = 1))
    }

    @Test
    fun `QUEUELOADER shouldLoadMore at exact threshold boundary`() {
        // totalLoaded = 5, threshold = 2 → boundary at index 3 (5-2)
        assertFalse(MediaService.QueueAutoLoader.shouldLoadMore(currentIndex = 2, totalLoaded = 5))
        assertTrue(MediaService.QueueAutoLoader.shouldLoadMore(currentIndex = 3, totalLoaded = 5))
    }

    // mediaType tests

    @Test
    fun `mediaType defaults to music in PlaybackState`() {
        val state = PlaybackState()
        assertEquals("music", state.mediaType)
    }

    @Test
    fun `mediaType preserves custom value in PlaybackState`() {
        val state = PlaybackState(mediaType = "podcast")
        assertEquals("podcast", state.mediaType)
    }
}
