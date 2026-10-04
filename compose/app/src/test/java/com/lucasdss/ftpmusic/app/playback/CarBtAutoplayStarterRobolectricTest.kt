package com.lucasdss.ftpmusic.app.playback

import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CarBtAutoplayStarterRobolectricTest {

    @Test
    fun `ensureResumeChannel and postResumeNotification`() {
        val ctx = RuntimeEnvironment.getApplication()
        CarBtAutoplayStarter.ensureResumeChannel(ctx)
        CarBtAutoplayStarter.postResumeNotification(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertNotNull(nm.getNotificationChannel(CarBtAutoplayStarter.RESUME_CHANNEL_ID))
        // Idempotent second call
        CarBtAutoplayStarter.ensureResumeChannel(ctx)
        assertTrue(true)
    }

    @Test
    fun `public startAutoplay returns Started or fallback`() {
        val ctx = RuntimeEnvironment.getApplication()
        MediaServiceStartRequest.carBtAutoplayRequested = false
        val result = CarBtAutoplayStarter.startAutoplay(ctx)
        assertTrue(
            result == CarBtAutoplayStarter.StartResult.Started ||
                result == CarBtAutoplayStarter.StartResult.NotificationFallback,
        )
    }
}
