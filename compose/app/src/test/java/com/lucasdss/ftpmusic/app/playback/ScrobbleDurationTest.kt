package com.lucasdss.ftpmusic.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrobbleDurationTest {

    @Test
    fun `blank trackId skips scrobble`() {
        assertNull(computeScrobbleListenedSeconds("", durationMs = 209_000L, lastTrackedPositionMs = 0L))
        assertNull(computeScrobbleListenedSeconds("   ", durationMs = 209_000L, lastTrackedPositionMs = 1000L))
    }

    @Test
    fun `duration extras are milliseconds not seconds`() {
        // Log symptom: secs=125400 from treating 209000ms as seconds * 0.6
        val result = computeScrobbleListenedSeconds(
            trackId = "t1",
            durationMs = 209_000L,
            lastTrackedPositionMs = 0L,
        )!!
        assertEquals(209, result.first)
        assertEquals(125, result.second) // 0.6 * 209
    }

    @Test
    fun `position path uses tracked milliseconds`() {
        val result = computeScrobbleListenedSeconds(
            trackId = "t1",
            durationMs = 209_000L,
            lastTrackedPositionMs = 208_000L,
        )!!
        assertEquals(209, result.first)
        assertEquals(208, result.second)
    }

    @Test
    fun `zero duration falls back to one second`() {
        val result = computeScrobbleListenedSeconds(
            trackId = "t1",
            durationMs = 0L,
            lastTrackedPositionMs = 0L,
        )!!
        assertEquals(0, result.first)
        assertEquals(1, result.second)
    }

    @Test
    fun `listened seconds capped by duration`() {
        val result = computeScrobbleListenedSeconds(
            trackId = "t1",
            durationMs = 10_000L,
            lastTrackedPositionMs = 50_000L,
        )!!
        assertEquals(10, result.first)
        assertEquals(10, result.second)
    }
}
