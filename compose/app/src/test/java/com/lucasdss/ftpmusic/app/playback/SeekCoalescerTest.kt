package com.lucasdss.ftpmusic.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the seek serialization guard — rapid fast-forwards coalesce
 * so overlapping seeks can't interleave with a track transition.
 */
class SeekCoalescerTest {

    @Test
    fun `first request applies immediately`() {
        val c = SeekCoalescer()

        assertEquals(0.5f, c.request(0.5f))
        assertTrue(c.isSeeking)
    }

    @Test
    fun `request while in progress is deferred`() {
        val c = SeekCoalescer()
        c.request(0.5f) // now in progress

        assertNull(c.request(0.6f))
        assertNull(c.request(0.7f))
    }

    @Test
    fun `onSettled returns the latest coalesced request`() {
        val c = SeekCoalescer()
        c.request(0.5f)
        c.request(0.6f)
        c.request(0.9f)

        assertEquals(0.9f, c.onSettled())
        assertFalse(c.isSeeking)
    }

    @Test
    fun `onSettled with no pending returns null`() {
        val c = SeekCoalescer()
        c.request(0.5f)

        assertNull(c.onSettled())
        assertFalse(c.isSeeking)
    }

    @Test
    fun `full cycle serializes multiple seeks`() {
        val c = SeekCoalescer()
        // Seek 1 applies
        assertEquals(0.2f, c.request(0.2f))
        // Fast-forward bursts coalesce into the latest
        c.request(0.3f)
        c.request(0.4f)
        // Seek 1 settles -> apply the latest (0.4)
        assertEquals(0.4f, c.onSettled())
        // Next drag burst
        c.request(0.8f)
        c.request(1.0f)
        // Seek 2 settles -> apply 1.0 (end-of-track skip stays intentional)
        assertEquals(1.0f, c.onSettled())
        assertFalse(c.isSeeking)
    }

    // -- Stuck-seek watchdog --

    @Test
    fun `stale in-flight seek is force-cleared by a new request`() {
        // stuckTimeoutMs=0 ⇒ any later request is already past the window.
        val c = SeekCoalescer(stuckTimeoutMs = 0L)
        assertEquals(0.5f, c.request(0.5f)) // in flight

        assertEquals("stale seek must be force-cleared", 0.8f, c.request(0.8f))
        assertTrue(c.isSeeking)
    }

    @Test
    fun `fresh in-flight seek still coalesces within stuck window`() {
        val c = SeekCoalescer(stuckTimeoutMs = 60_000L)
        assertEquals(0.5f, c.request(0.5f))

        // A request well inside the stuck window is still deferred.
        assertNull(c.request(0.7f))
        assertEquals(0.7f, c.onSettled())
    }

    @Test
    fun `reset abandons in-flight and pending seeks`() {
        val c = SeekCoalescer()
        assertEquals(0.5f, c.request(0.5f))
        assertNull(c.request(0.9f)) // queued while in flight

        c.reset()

        assertFalse(c.isSeeking)
        // The queued 0.9 must NOT survive the reset.
        assertNull(c.onSettled())
        // And the next request applies immediately.
        assertEquals(0.2f, c.request(0.2f))
    }
}
