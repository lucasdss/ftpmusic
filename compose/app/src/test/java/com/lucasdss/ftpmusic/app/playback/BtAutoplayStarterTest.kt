package com.lucasdss.ftpmusic.app.playback

import android.content.Context
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class BtAutoplayStarterTest {

    @Test
    fun `startAutoplay success sets flags`() {
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.clear()
        MediaServiceStartRequest.btAutoplayRequested = false
        MediaServiceStartRequest.foregroundRequested = false
        MediaServiceStartRequest.userInitiatedResume = true // stale tap must not leak
        val ctx = mockk<Context>(relaxed = true)
        val result = BtAutoplayStarter.startAutoplay(
            context = ctx,
            startForegroundService = { _, _ -> },
            postFallback = {},
        )
        assertEquals(BtAutoplayStarter.StartResult.Started, result)
        assertTrue(MediaServiceStartRequest.btAutoplayRequested)
        assertTrue(MediaServiceStartRequest.foregroundRequested)
        assertFalse(MediaServiceStartRequest.userInitiatedResume)
        val snap = com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.snapshot(
            ctx,
            offline = false,
            reachable = true,
        )
        assertTrue(snap.contains("ftpmusic-bt"))
        assertTrue(snap.contains("result=Started"))
    }

    @Test
    fun `startAutoplay preserves in-flight user tap across reconnect`() {
        MediaServiceStartRequest.btAutoplayRequested = true
        MediaServiceStartRequest.userInitiatedResume = true
        val ctx = mockk<Context>(relaxed = true)
        BtAutoplayStarter.startAutoplay(
            context = ctx,
            startForegroundService = { _, _ -> },
            postFallback = {},
        )
        assertTrue(MediaServiceStartRequest.btAutoplayRequested)
        assertTrue(
            "pending Resume tap must survive A2DP reconnect clobber",
            MediaServiceStartRequest.userInitiatedResume,
        )
    }

    @Test
    fun `startAutoplay failure posts fallback`() {
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.clear()
        val ctx = mockk<Context>(relaxed = true)
        var fallback = 0
        class ForegroundServiceStartNotAllowedException(msg: String) : Exception(msg)
        val result = BtAutoplayStarter.startAutoplay(
            context = ctx,
            startForegroundService = { _, _ ->
                throw ForegroundServiceStartNotAllowedException("blocked")
            },
            postFallback = { fallback++ },
            sdkInt = 31,
        )
        assertEquals(BtAutoplayStarter.StartResult.NotificationFallback, result)
        assertEquals(1, fallback)
        assertFalse(MediaServiceStartRequest.foregroundRequested)
        val snap = com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.snapshot(
            ctx,
            offline = false,
            reachable = true,
        )
        assertTrue(snap.contains("fgsBlocked=true"))
        assertTrue(snap.contains("result=NotificationFallback"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BtAutoplayStarterRobolectricTest {

    @Test
    fun `postResumeNotification creates channel`() {
        val ctx = RuntimeEnvironment.getApplication()
        BtAutoplayStarter.postResumeNotification(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        org.junit.Assert.assertNotNull(nm.getNotificationChannel(BtAutoplayStarter.RESUME_CHANNEL_ID))
    }

    @Test
    fun `postInformationalNotification creates info channel with default priority`() {
        val ctx = RuntimeEnvironment.getApplication()
        BtAutoplayStarter.postInformationalNotification(
            ctx,
            com.lucasdss.ftpmusic.app.R.string.bt_info_notif_title_empty,
            com.lucasdss.ftpmusic.app.R.string.bt_resume_notif_body_empty,
        )
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channel = nm.getNotificationChannel(BtAutoplayStarter.INFO_CHANNEL_ID)
        org.junit.Assert.assertNotNull(channel)
        org.junit.Assert.assertEquals(
            android.app.NotificationManager.IMPORTANCE_DEFAULT,
            channel!!.importance,
        )
    }

    @Test
    fun `postInformationalNotification opens app with no Resume action`() {
        val ctx = RuntimeEnvironment.getApplication()
        BtAutoplayStarter.postInformationalNotification(
            ctx,
            com.lucasdss.ftpmusic.app.R.string.bt_info_notif_title_offline,
            com.lucasdss.ftpmusic.app.R.string.bt_resume_notif_body_offline,
        )
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        @Suppress("DEPRECATION")
        val notif = nm.activeNotifications
            .firstOrNull { it.id == BtAutoplayStarter.INFO_NOTIFICATION_ID }
            ?.notification
        org.junit.Assert.assertNotNull(notif)
        org.junit.Assert.assertEquals(0, notif!!.actions?.size ?: 0)
        val contentIntent = notif.contentIntent
        org.junit.Assert.assertNotNull(contentIntent)
        val intent = org.robolectric.Shadows.shadowOf(contentIntent).savedIntent
        org.junit.Assert.assertNotEquals(MediaServiceStartRequest.ACTION_BT_AUTOPLAY, intent.action)
        org.junit.Assert.assertTrue(intent.component?.className?.endsWith("MainActivity") == true)
    }

    @Test
    fun `selected_null_mac posts actionable Resume via production default`() {
        val ctx = RuntimeEnvironment.getApplication()
        BtConnectionReceiver.debounceMap.clear()
        val storage = mockk<SecureStorage>()
        every { storage.get(SecureStorage.KEY_BT_RESUME_ENABLED) } returns "true"
        every { storage.get(SecureStorage.KEY_BT_RESUME_MODE) } returns "selected"
        every { storage.get(SecureStorage.KEY_BT_DEVICE_MACS) } returns """["11:22:33:44:55:66"]"""
        every { storage.get(SecureStorage.KEY_CAR_BT_RESUME_ENABLED) } returns null
        every { storage.get(SecureStorage.KEY_CAR_BT_DEVICE_MACS) } returns null
        BtConnectionReceiver.dispatchBtConnect(
            context = ctx,
            storage = storage,
            deviceMac = null,
            casting = false,
            nowMs = System.currentTimeMillis(),
            starter = { BtAutoplayStarter.StartResult.Started },
        )
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        @Suppress("DEPRECATION")
        val notif = nm.activeNotifications
            .firstOrNull { it.id == BtAutoplayStarter.RESUME_NOTIFICATION_ID }
            ?.notification
        org.junit.Assert.assertNotNull(notif)
        val contentIntent = notif!!.contentIntent
        val intent = org.robolectric.Shadows.shadowOf(contentIntent).savedIntent
        org.junit.Assert.assertEquals(MediaServiceStartRequest.ACTION_BT_AUTOPLAY, intent.action)
        org.junit.Assert.assertTrue(
            intent.getBooleanExtra(MediaServiceStartRequest.EXTRA_USER_INITIATED_RESUME, false),
        )
    }

    @Test
    fun `postResumeNotification PendingIntent carries USER_INITIATED_RESUME extra`() {
        // ADR-0133: tap must be distinguishable from background A2DP wake.
        val ctx = RuntimeEnvironment.getApplication()
        BtAutoplayStarter.postResumeNotification(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        @Suppress("DEPRECATION")
        val notif = nm.activeNotifications
            .firstOrNull { it.id == BtAutoplayStarter.RESUME_NOTIFICATION_ID }
            ?.notification
        org.junit.Assert.assertNotNull(notif)
        val contentIntent = notif!!.contentIntent
        org.junit.Assert.assertNotNull(contentIntent)
        // Robolectric: unwrap PendingIntent → Intent via shadow or reflection
        val intent = org.robolectric.Shadows.shadowOf(contentIntent).savedIntent
        assertEquals(MediaServiceStartRequest.ACTION_BT_AUTOPLAY, intent.action)
        assertTrue(
            intent.getBooleanExtra(
                MediaServiceStartRequest.EXTRA_USER_INITIATED_RESUME,
                false,
            ),
        )
    }

    @Test
    fun `cancelResumeNotification removes posted Resume notif`() {
        val ctx = RuntimeEnvironment.getApplication()
        BtAutoplayStarter.postResumeNotification(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        assertTrue(
            nm.activeNotifications.any { it.id == BtAutoplayStarter.RESUME_NOTIFICATION_ID },
        )
        BtAutoplayStarter.cancelResumeNotification(ctx)
        assertFalse(
            nm.activeNotifications.any { it.id == BtAutoplayStarter.RESUME_NOTIFICATION_ID },
        )
    }
}
