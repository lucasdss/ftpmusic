package com.lucasdss.ftpmusic.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleDurationTest {

    @Test
    fun `blank trackId skips scrobble`() {
        assertNull(computeScrobbleListenedSeconds("", durationMs = 209_000L, accumulatedHeardMs = 0L))
        assertNull(computeScrobbleListenedSeconds("   ", durationMs = 209_000L, accumulatedHeardMs = 1000L))
    }

    @Test
    fun `zero heard falls back to half duration`() {
        val result = computeScrobbleListenedSeconds(
            trackId = "t1",
            durationMs = 209_000L,
            accumulatedHeardMs = 0L,
        )!!
        assertEquals(209, result.first)
        assertEquals(104, result.second) // half of 209
    }

    @Test
    fun `heard path uses accumulated milliseconds`() {
        val result = computeScrobbleListenedSeconds(
            trackId = "t1",
            durationMs = 209_000L,
            accumulatedHeardMs = 180_000L,
        )!!
        assertEquals(209, result.first)
        assertEquals(180, result.second)
    }

    @Test
    fun `zero duration falls back to one second`() {
        val result = computeScrobbleListenedSeconds(
            trackId = "t1",
            durationMs = 0L,
            accumulatedHeardMs = 0L,
        )!!
        assertEquals(0, result.first)
        assertEquals(1, result.second)
    }

    @Test
    fun `listened seconds capped by duration`() {
        val result = computeScrobbleListenedSeconds(
            trackId = "t1",
            durationMs = 10_000L,
            accumulatedHeardMs = 50_000L,
        )!!
        assertEquals(10, result.first)
        assertEquals(10, result.second)
    }

    @Test
    fun `lastfm short track never meets threshold`() {
        assertNull(scrobbleThresholdMs(30_000L))
        assertFalse(hasMetScrobbleThreshold(25_000L, 25_000L))
    }

    @Test
    fun `lastfm half duration for medium tracks`() {
        // 3 min track → threshold 90s
        assertEquals(90_000L, scrobbleThresholdMs(180_000L))
        assertTrue(hasMetScrobbleThreshold(180_000L, 90_000L))
        assertFalse(hasMetScrobbleThreshold(180_000L, 89_000L))
    }

    @Test
    fun `lastfm caps required heard at 4 minutes`() {
        // 20 min track → half would be 10 min, but Last.fm caps at 4 min
        assertEquals(240_000L, scrobbleThresholdMs(1_200_000L))
        assertTrue(hasMetScrobbleThreshold(1_200_000L, 240_000L))
        assertFalse(hasMetScrobbleThreshold(1_200_000L, 239_000L))
    }
}
