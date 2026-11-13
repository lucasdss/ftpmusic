package com.lucasdss.ftpmusic.app.playback

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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

    @Test
    fun `postNotification returns true when notifications enabled`() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        Shadows.shadowOf(nm).setNotificationsEnabled(true)
        assertTrue(AutoConnectAlertNotifier.postNotification(ctx))
    }

    @Test
    fun `postNotification returns false when notifications disabled`() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        Shadows.shadowOf(nm).setNotificationsEnabled(false)
        assertFalse(AutoConnectAlertNotifier.postNotification(ctx))
    }

    @Test
    fun `maybeNotify does not mark seen when post fails`() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        Shadows.shadowOf(nm).setNotificationsEnabled(false)

        val storage = mockk<SecureStorage>(relaxed = true)
        every { storage.get(SecureStorage.KEY_AA_CONNECT_ALERT_ENABLED) } returns null
        every { storage.get(SecureStorage.KEY_AA_SEEN_CONTROLLERS) } returns null

        val session = mockk<androidx.media3.session.MediaSession>(relaxed = true)
        val controller = mockk<androidx.media3.session.MediaSession.ControllerInfo>(relaxed = true)
        every { session.isAutoCompanionController(controller) } returns true
        every { controller.packageName } returns "com.google.android.projection.gearhead"
        every { controller.uid } returns 1000

        AutoConnectAlertNotifier.maybeNotify(ctx, session, controller, storage)

        verify(exactly = 0) { storage.put(SecureStorage.KEY_AA_SEEN_CONTROLLERS, any()) }
    }

    @Test
    fun `maybeNotify marks seen after successful post`() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        Shadows.shadowOf(nm).setNotificationsEnabled(true)

        val storage = mockk<SecureStorage>(relaxed = true)
        every { storage.get(SecureStorage.KEY_AA_CONNECT_ALERT_ENABLED) } returns null
        every { storage.get(SecureStorage.KEY_AA_SEEN_CONTROLLERS) } returns null

        val session = mockk<androidx.media3.session.MediaSession>(relaxed = true)
        val controller = mockk<androidx.media3.session.MediaSession.ControllerInfo>(relaxed = true)
        every { session.isAutoCompanionController(controller) } returns true
        every { controller.packageName } returns "com.google.android.projection.gearhead"
        every { controller.uid } returns 1000

        AutoConnectAlertNotifier.maybeNotify(ctx, session, controller, storage)

        verify {
            storage.put(
                SecureStorage.KEY_AA_SEEN_CONTROLLERS,
                "com.google.android.projection.gearhead",
            )
        }
    }
}
