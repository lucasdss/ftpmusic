package com.lucasdss.ftpmusic.app.playback

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.spyk
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Regression: the FGS placeholder notification must be buildable pre-init
 *  (MediaService.onCreate calls startForeground with it FIRST to satisfy the
 *  5s deadline — ForegroundServiceDidNotStartInTimeException crash class). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class MediaServiceNotificationTest {

    @Test
    fun `placeholder notification is low priority and ready-to-play`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val service = spyk(MediaService())

        val notification = service.buildPlaceholderNotification(context)

        assertEquals(android.app.Notification.PRIORITY_LOW, notification.priority)
        assertEquals("ftpmusic", notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
        assertEquals(
            "Ready to play",
            notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString(),
        )
    }
}
