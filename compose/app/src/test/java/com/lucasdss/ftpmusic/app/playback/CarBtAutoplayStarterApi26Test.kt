package com.lucasdss.ftpmusic.app.playback

import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class CarBtAutoplayStarterApi26Test {

    @Test
    fun `postResumeNotification on API 26 uses getService pending intent path`() {
        val ctx = RuntimeEnvironment.getApplication()
        CarBtAutoplayStarter.postResumeNotification(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertNotNull(nm.getNotificationChannel(CarBtAutoplayStarter.RESUME_CHANNEL_ID))
    }
}
