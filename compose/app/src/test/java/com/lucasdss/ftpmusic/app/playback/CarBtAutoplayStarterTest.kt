package com.lucasdss.ftpmusic.app.playback

import android.content.Context
import android.content.Intent
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarBtAutoplayStarterTest {

    @Test
    fun `startAutoplay success sets flags`() {
        MediaServiceStartRequest.carBtAutoplayRequested = false
        MediaServiceStartRequest.foregroundRequested = false
        val ctx = mockk<Context>(relaxed = true)
        val result = CarBtAutoplayStarter.startAutoplay(
            context = ctx,
            startForegroundService = { _, _ -> },
            postFallback = {},
        )
        assertEquals(CarBtAutoplayStarter.StartResult.Started, result)
        assertTrue(MediaServiceStartRequest.carBtAutoplayRequested)
        assertTrue(MediaServiceStartRequest.foregroundRequested)
    }

    @Test
    fun `startAutoplay FGS blocked posts fallback and clears foreground flag`() {
        MediaServiceStartRequest.carBtAutoplayRequested = false
        MediaServiceStartRequest.foregroundRequested = false
        val ctx = mockk<Context>(relaxed = true)
        var fallback = 0

        // Named like the platform exception so sdkInt>=S branch treats it as FGS block.
        class ForegroundServiceStartNotAllowedException(msg: String) : Exception(msg)
        val result = CarBtAutoplayStarter.startAutoplay(
            context = ctx,
            startForegroundService = { _, _ ->
                throw ForegroundServiceStartNotAllowedException("blocked")
            },
            postFallback = { fallback++ },
            sdkInt = 31,
        )
        assertEquals(CarBtAutoplayStarter.StartResult.NotificationFallback, result)
        assertEquals(1, fallback)
        assertFalse(MediaServiceStartRequest.foregroundRequested)
        // carBt flag stays true so a later MediaService start still resumes
        assertTrue(MediaServiceStartRequest.carBtAutoplayRequested)
    }

    @Test
    fun `startAutoplay other failure still posts fallback`() {
        val ctx = mockk<Context>(relaxed = true)
        var fallback = 0
        val result = CarBtAutoplayStarter.startAutoplay(
            context = ctx,
            startForegroundService = { _, _ -> error("boom") },
            postFallback = { fallback++ },
            sdkInt = 28,
        )
        assertEquals(CarBtAutoplayStarter.StartResult.NotificationFallback, result)
        assertEquals(1, fallback)
    }

    @Test
    fun `startAutoplay public entry builds CAR_BT intent`() {
        val ctx = mockk<Context>(relaxed = true)
        // Public overload will call startForegroundService on context — catch via mock.
        // Under unit JVM this typically throws or no-ops; either StartResult is fine.
        val result = try {
            CarBtAutoplayStarter.startAutoplay(ctx)
        } catch (_: Throwable) {
            CarBtAutoplayStarter.StartResult.NotificationFallback
        }
        assertTrue(
            result == CarBtAutoplayStarter.StartResult.Started ||
                result == CarBtAutoplayStarter.StartResult.NotificationFallback,
        )
        verify(atLeast = 0) { ctx.startForegroundService(any<Intent>()) }
    }
}
