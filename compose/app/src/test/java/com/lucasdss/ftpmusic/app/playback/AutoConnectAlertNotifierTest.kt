package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoConnectAlertNotifierTest {

    @Test
    fun `alerts enabled by default`() {
        val storage = mockk<SecureStorage>(relaxed = true)
        every { storage.get(SecureStorage.KEY_AA_CONNECT_ALERT_ENABLED) } returns null
        assertTrue(AutoConnectAlertNotifier.alertsEnabled(storage))
    }

    @Test
    fun `setAlertsEnabled writes storage`() {
        val storage = mockk<SecureStorage>(relaxed = true)
        AutoConnectAlertNotifier.setAlertsEnabled(storage, false)
        verify { storage.put(SecureStorage.KEY_AA_CONNECT_ALERT_ENABLED, "false") }
    }

    @Test
    fun `seen fingerprints parse csv`() {
        val storage = mockk<SecureStorage>(relaxed = true)
        every { storage.get(SecureStorage.KEY_AA_SEEN_CONTROLLERS) } returns
            "com.google.android.projection.gearhead, other.pkg"
        assertEquals(
            setOf("com.google.android.projection.gearhead", "other.pkg"),
            AutoConnectAlertNotifier.seenFingerprints(storage),
        )
    }

    @Test
    fun `alerts disabled respects storage`() {
        val storage = mockk<SecureStorage>(relaxed = true)
        every { storage.get(SecureStorage.KEY_AA_CONNECT_ALERT_ENABLED) } returns "false"
        assertFalse(AutoConnectAlertNotifier.alertsEnabled(storage))
    }
}
