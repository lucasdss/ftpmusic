package com.lucasdss.ftpmusic.app.playback

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RemainingGapsTest {

    @Before
    fun setup() {
    }

    // QueueAutoLoader removed (ADR-0074 PR #2) — Dual + Continuous Play only.

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
