package com.lucasdss.ftpmusic.app.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastSessionResumePolicyTest {

    @Test
    fun `onSessionStarted clears sticky resume after prior resume`() {
        // EDGE-03: resume → new session must not skip queue reload.
        var flag = CastSessionResumePolicy.onSessionResumed()
        assertTrue(flag)
        flag = CastSessionResumePolicy.onSessionStarted()
        assertFalse(flag)
    }

    @Test
    fun `onSessionEnded and manual disconnect clear sticky resume`() {
        assertFalse(CastSessionResumePolicy.onSessionEnded())
        assertFalse(CastSessionResumePolicy.onManualDisconnect())
    }

    @Test
    fun `onDeviceIdChanged clears only when physical device changes`() {
        assertFalse(
            CastSessionResumePolicy.onDeviceIdChanged(
                previousId = "soundbar",
                newId = "mini",
                current = true,
            ),
        )
        assertTrue(
            CastSessionResumePolicy.onDeviceIdChanged(
                previousId = "soundbar",
                newId = "soundbar",
                current = true,
            ),
        )
        assertTrue(
            CastSessionResumePolicy.onDeviceIdChanged(
                previousId = null,
                newId = "soundbar",
                current = true,
            ),
        )
        assertFalse(
            CastSessionResumePolicy.onDeviceIdChanged(
                previousId = null,
                newId = "soundbar",
                current = false,
            ),
        )
    }
}
