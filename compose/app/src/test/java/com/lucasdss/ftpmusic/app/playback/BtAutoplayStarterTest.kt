package com.lucasdss.ftpmusic.app.playback

import android.content.Context
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
        val ctx = mockk<Context>(relaxed = true)
        val result = BtAutoplayStarter.startAutoplay(
            context = ctx,
            startForegroundService = { _, _ -> },
            postFallback = {},
        )
        assertEquals(BtAutoplayStarter.StartResult.Started, result)
        assertTrue(MediaServiceStartRequest.btAutoplayRequested)
        assertTrue(MediaServiceStartRequest.foregroundRequested)
        val snap = com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.snapshot(
            ctx,
            offline = false,
            reachable = true,
        )
        assertTrue(snap.contains("ftpmusic-bt"))
        assertTrue(snap.contains("result=Started"))
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
    fun `postResumeNotification offline body still creates channel`() {
        val ctx = RuntimeEnvironment.getApplication()
        BtAutoplayStarter.postResumeNotification(
            ctx,
            com.lucasdss.ftpmusic.app.R.string.bt_resume_notif_body_offline,
        )
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        org.junit.Assert.assertNotNull(nm.getNotificationChannel(BtAutoplayStarter.RESUME_CHANNEL_ID))
        assertTrue(BtAutoplayStarter.notificationsEnabled(ctx))
    }
}
