package com.lucasdss.ftpmusic.app.playback

import android.app.Application
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * E2: service-owned sleep timer. Enforcement lives in MediaService (survives
 * recents-swipe + process death via persisted playback_state); the ViewModel
 * only mirrors the deadline and forwards the arm command.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MediaServiceSleepTimerTest {

    private lateinit var service: MediaService

    @Before
    fun setUp() {
        service = spyk(MediaService())
    }

    @After
    fun tearDown() {
        service.cancelSleepTimer()
        PlayerHolder.player = null
        PlayerHolder.sleepTimerEndMs = 0L
        PlayerHolder.isCasting = false
    }

    @Test
    fun `expired deadline clears holder without arming a job`() {
        service.armSleepTimer(System.currentTimeMillis() - 1000)
        assertEquals(0L, PlayerHolder.sleepTimerEndMs)
    }

    @Test
    fun `armed timer pauses the player at the deadline`() {
        val player = mockk<Player>(relaxed = true)
        PlayerHolder.player = player
        service.armSleepTimer(System.currentTimeMillis() + 300)
        assertTrue("holder must carry the future deadline", PlayerHolder.sleepTimerEndMs > 0)

        Thread.sleep(900)
        verify(exactly = 1) { player.pause() }
        assertEquals("deadline must be cleared after expiry", 0L, PlayerHolder.sleepTimerEndMs)
    }

    @Test
    fun `cancel clears the holder immediately`() {
        service.armSleepTimer(System.currentTimeMillis() + 60_000)
        assertTrue(PlayerHolder.sleepTimerEndMs > 0)

        service.cancelSleepTimer()
        assertEquals(0L, PlayerHolder.sleepTimerEndMs)
    }

    @Test
    fun `cancel prevents an armed pause`() {
        val player = mockk<Player>(relaxed = true)
        PlayerHolder.player = player
        service.armSleepTimer(System.currentTimeMillis() + 200)
        service.cancelSleepTimer()

        Thread.sleep(600)
        verify(exactly = 0) { player.pause() }
    }

    @Test
    fun `SLEEP_TIMER_ARM control routes through handlePlaybackControl`() {
        PlayerHolder.player = mockk<Player>(relaxed = true)
        val endMs = System.currentTimeMillis() + 60_000
        service.handlePlaybackControl(PlaybackControl.SLEEP_TIMER_ARM(endMs))
        assertEquals(endMs, PlayerHolder.sleepTimerEndMs)

        service.handlePlaybackControl(PlaybackControl.SLEEP_TIMER_CANCEL)
        assertEquals(0L, PlayerHolder.sleepTimerEndMs)
    }

    @Test
    fun `provider armSleepTimer delegates to the control callback`() {
        val provider = MediaSessionPlaybackProvider(mockk(relaxed = true))
        var received: PlaybackControl? = null
        provider.setControlCallback { received = it }
        val endMs = System.currentTimeMillis() + 60_000
        // Dispatch requires a wired player (controls otherwise queue + re-arm).
        PlayerHolder.player = mockk<Player>(relaxed = true)
        try {
            provider.armSleepTimer(endMs)
            assertEquals(PlaybackControl.SLEEP_TIMER_ARM(endMs), received)
            provider.cancelSleepTimer()
            assertEquals(PlaybackControl.SLEEP_TIMER_CANCEL, received)
        } finally {
            PlayerHolder.player = null
        }
    }
}
