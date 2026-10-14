package com.lucasdss.ftpmusic.app.data.sync

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibrarySyncServiceTest {

    @Test
    fun `ensureChannel creates library sync channel`() {
        val context = RuntimeEnvironment.getApplication()
        LibrarySyncService.ensureChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertNotNull(nm.getNotificationChannel(LibrarySyncService.CHANNEL_ID))
    }

    @Test
    fun `start catches FGS blocked and does not throw`() {
        class ForegroundServiceStartNotAllowedException(msg: String) : Exception(msg)

        val context = mockk<Context>(relaxed = true)
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns
            mockk<NotificationManager>(relaxed = true)
        every { context.startForegroundService(any()) } throws
            ForegroundServiceStartNotAllowedException("blocked")

        LibrarySyncService.start(context)

        verify { context.startForegroundService(any()) }
    }

    @Test
    fun `stop sends ACTION_STOP`() {
        val context = mockk<Context>(relaxed = true)
        every { context.startService(any()) } returns null

        LibrarySyncService.stop(context)

        verify {
            context.startService(
                match<Intent> { it.action == LibrarySyncService.ACTION_STOP },
            )
        }
    }

    @Test
    fun `notification ids are stable`() {
        assertEquals(7102, LibrarySyncService.NOTIFICATION_ID)
        assertEquals("library_sync", LibrarySyncService.CHANNEL_ID)
    }
}
