package com.lucasdss.ftpmusic.app.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastEndErrorTest {

    @Test
    fun `transient codes 2155 and 2161 reconnect`() {
        val service = MediaService()
        assertTrue(service.isTransientCastEndError(CAST_END_TIMEOUT))
        assertTrue(service.isTransientCastEndError(CAST_END_APP_NOT_RUNNING))
    }

    @Test
    fun `non transient 2055 does not reconnect`() {
        val service = MediaService()
        assertFalse(service.isTransientCastEndError(2055))
        assertFalse(service.isTransientCastEndError(0))
        assertFalse(service.isTransientCastEndError(2000))
    }

    @Test
    fun `connect timeout aborts when session starting for target`() {
        val service = MediaService()
        assertFalse(
            service.shouldAbortConnectTimeout(
                hasLiveSession = false,
                sessionStartingForTarget = true,
            ),
        )
        assertTrue(
            service.shouldAbortConnectTimeout(
                hasLiveSession = false,
                sessionStartingForTarget = false,
            ),
        )
        assertFalse(
            service.shouldAbortConnectTimeout(
                hasLiveSession = true,
                sessionStartingForTarget = false,
            ),
        )
    }
}
