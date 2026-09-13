package com.lucasdss.ftpmusic.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure decision tests for [MainActivity.onResume]'s playback-service
 * re-initialization guard.
 */
class MainActivityFgsDecisionTest {

    @Test
    fun `no wired player means re-initialize on resume`() {
        assertTrue(shouldReinitializePlaybackServiceOnResume(isPlayerWired = false))
    }

    @Test
    fun `wired player means no re-initialize on resume`() {
        assertFalse(shouldReinitializePlaybackServiceOnResume(isPlayerWired = true))
    }
}
