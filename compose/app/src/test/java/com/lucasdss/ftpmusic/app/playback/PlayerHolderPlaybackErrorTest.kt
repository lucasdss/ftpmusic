package com.lucasdss.ftpmusic.app.playback

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayerHolderPlaybackErrorTest {

    @Before
    fun setup() {
        PlayerHolder.dismissPlaybackError()
    }

    @After
    fun teardown() {
        PlayerHolder.dismissPlaybackError()
    }

    @Test
    fun `setPlaybackError sticks through early clear attempt`() {
        PlayerHolder.setPlaybackError("boom", stickyMs = 5_000L)
        assertEquals("boom", PlayerHolder.lastPlaybackError)
        assertFalse(PlayerHolder.clearPlaybackErrorIfSettled(nowMs = System.currentTimeMillis()))
        assertEquals("boom", PlayerHolder.lastPlaybackError)
    }

    @Test
    fun `clearPlaybackErrorIfSettled after sticky window`() {
        PlayerHolder.setPlaybackError("boom", stickyMs = 1_000L)
        val after = PlayerHolder.playbackErrorStickyUntilMs + 1
        assertTrue(PlayerHolder.clearPlaybackErrorIfSettled(nowMs = after))
        assertNull(PlayerHolder.lastPlaybackError)
    }

    @Test
    fun `autoSkip in flight blocks clear`() {
        PlayerHolder.setPlaybackError("boom", stickyMs = 0L)
        PlayerHolder.playbackErrorAutoSkipInFlight = true
        assertFalse(PlayerHolder.clearPlaybackErrorIfSettled(nowMs = System.currentTimeMillis() + 10_000))
        assertEquals("boom", PlayerHolder.lastPlaybackError)
        PlayerHolder.playbackErrorAutoSkipInFlight = false
        assertTrue(PlayerHolder.clearPlaybackErrorIfSettled(nowMs = System.currentTimeMillis() + 10_000))
    }

    @Test
    fun `dismiss clears immediately`() {
        PlayerHolder.setPlaybackError("boom", stickyMs = 60_000L)
        PlayerHolder.playbackErrorAutoSkipInFlight = true
        PlayerHolder.dismissPlaybackError()
        assertNull(PlayerHolder.lastPlaybackError)
        assertFalse(PlayerHolder.playbackErrorAutoSkipInFlight)
    }

    @Test
    fun `sticky scheduler clears after delay without READY`() {
        var scheduled: (() -> Unit)? = null
        PlayerHolder.stickyClearScheduler = { _, action -> scheduled = action }
        var clearedCallback = false
        PlayerHolder.onPlaybackErrorCleared = { clearedCallback = true }
        PlayerHolder.setPlaybackError("boom", stickyMs = 100L)
        assertEquals("boom", PlayerHolder.lastPlaybackError)
        // Simulate sticky window elapsed
        PlayerHolder.playbackErrorStickyUntilMs = 0L
        scheduled!!.invoke()
        assertNull(PlayerHolder.lastPlaybackError)
        assertTrue(clearedCallback)
        PlayerHolder.stickyClearScheduler = null
        PlayerHolder.onPlaybackErrorCleared = null
    }

    @Test
    fun `dismiss invokes onPlaybackErrorCleared for provider patch`() {
        var cleared = false
        PlayerHolder.onPlaybackErrorCleared = { cleared = true }
        PlayerHolder.setPlaybackError("boom", stickyMs = 60_000L)
        PlayerHolder.dismissPlaybackError()
        assertTrue(cleared)
        PlayerHolder.onPlaybackErrorCleared = null
    }
}
