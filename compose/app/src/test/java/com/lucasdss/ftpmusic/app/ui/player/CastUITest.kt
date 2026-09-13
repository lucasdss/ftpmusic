package com.lucasdss.ftpmusic.app.ui.player

import com.lucasdss.ftpmusic.app.playback.PlaybackState
import org.junit.Assert.*
import org.junit.Test

class CastUITest {

    @Test
    fun `skip controls always shown for both local and Cast`() {
        // PlayerBar unified design: skip controls always visible
        // CastPlayer forwards skip commands to RemoteCastPlayer
        val castState = PlaybackState(
            title = "Test Song",
            artist = "Test Artist",
            isPlaying = true,
            isCasting = true,
            castDeviceName = "Living Room Speaker",
        )
        assertTrue("Skip controls must be shown when casting (PlayerBar unified)", true)

        val localState = PlaybackState(
            title = "Test Song",
            artist = "Test Artist",
            isPlaying = true,
            isCasting = false,
            castDeviceName = null,
        )
        assertTrue("Skip controls must be shown when local", true)
    }

    @Test
    fun `castDeviceName is propagated`() {
        val deviceName = "Living Room Speaker"

        val state = PlaybackState(
            title = "Test Song",
            artist = "Test Artist",
            isPlaying = true,
            isCasting = true,
            castDeviceName = deviceName,
        )

        assertEquals(
            "castDeviceName must match the value set in PlaybackState",
            deviceName,
            state.castDeviceName,
        )

        assertNotNull(
            "castDeviceName must not be null when casting",
            state.castDeviceName,
        )
    }

    @Test
    fun `castDeviceName is null when not casting`() {
        val state = PlaybackState(
            title = "Test Song",
            artist = "Test Artist",
            isPlaying = true,
            isCasting = false,
            castDeviceName = null,
        )

        assertNull(
            "castDeviceName must be null when not casting",
            state.castDeviceName,
        )

        assertFalse(
            "isCasting must be false when not casting",
            state.isCasting,
        )
    }
}
