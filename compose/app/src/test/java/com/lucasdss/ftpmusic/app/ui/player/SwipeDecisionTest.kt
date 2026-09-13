package com.lucasdss.ftpmusic.app.ui.player

import org.junit.Assert.*
import org.junit.Test

/**
 * SwipeDecision: pure resolution for the album-art swipe gesture.
 * Negative offset (finger dragged left) = next track, positive = previous.
 * Under the threshold (55dp) = no action (spring back).
 */
class SwipeDecisionTest {

    @Test
    fun `negative offset beyond threshold resolves to next`() {
        assertEquals(SwipeDecision.NEXT, swipeDecision(offsetPx = -120f, thresholdPx = 55f))
    }

    @Test
    fun `positive offset beyond threshold resolves to prev`() {
        assertEquals(SwipeDecision.PREV, swipeDecision(offsetPx = 120f, thresholdPx = 55f))
    }

    @Test
    fun `offset below threshold resolves to none`() {
        assertEquals(SwipeDecision.NONE, swipeDecision(offsetPx = 30f, thresholdPx = 55f))
        assertEquals(SwipeDecision.NONE, swipeDecision(offsetPx = -30f, thresholdPx = 55f))
    }

    @Test
    fun `exact threshold resolves to none`() {
        assertEquals(SwipeDecision.NONE, swipeDecision(offsetPx = 55f, thresholdPx = 55f))
        assertEquals(SwipeDecision.NONE, swipeDecision(offsetPx = -55f, thresholdPx = 55f))
    }

    @Test
    fun `zero offset resolves to none`() {
        assertEquals(SwipeDecision.NONE, swipeDecision(offsetPx = 0f, thresholdPx = 55f))
    }

    @Test
    fun `threshold of zero resolves immediately`() {
        assertEquals(SwipeDecision.NEXT, swipeDecision(offsetPx = -1f, thresholdPx = 0f))
        assertEquals(SwipeDecision.PREV, swipeDecision(offsetPx = 1f, thresholdPx = 0f))
    }
}
