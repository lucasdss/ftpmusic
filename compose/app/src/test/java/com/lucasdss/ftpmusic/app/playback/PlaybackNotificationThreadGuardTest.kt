package com.lucasdss.ftpmusic.app.playback

import android.app.Application
import android.os.Handler
import android.os.HandlerThread
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Thread-safety regression for the QS notification fix: resolveNotificationState
 * must never access the Media3 player off the main thread (SimpleBasePlayer
 * throws IllegalStateException "Player is accessed on the wrong thread" — the
 * launch crash after the re-derive change). Off-thread callers fall back to the
 * cached StateFlow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlaybackNotificationThreadGuardTest {

    @Before
    fun setUp() {
        PlayerHolder.player = playerWith("New Track", "New Artist")
    }

    @After
    fun tearDown() {
        PlayerHolder.player = null
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null
    }

    private fun playerWith(title: String, artist: String): Player {
        val mediaItem = MediaItem.Builder()
            .setMediaId("new-id")
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).build())
            .build()
        val player = mockk<Player>(relaxed = true)
        every { player.currentMediaItem } returns mediaItem
        every { player.mediaItemCount } returns 1
        every { player.currentMediaItemIndex } returns 0
        every { player.isPlaying } returns true
        every { player.playbackState } returns Player.STATE_READY
        every { player.repeatMode } returns Player.REPEAT_MODE_OFF
        every { player.shuffleModeEnabled } returns false
        every { player.playbackParameters } returns PlaybackParameters.DEFAULT
        every { player.volume } returns 1f
        every { player.currentPosition } returns 0L
        every { player.duration } returns 100_000L
        return player
    }

    @Test
    fun `resolveNotificationState uses cached state on a background thread`() {
        val thread = HandlerThread("bg").apply { start() }
        val latch = CountDownLatch(1)
        var result: PlaybackState? = null
        try {
            Handler(thread.looper).post {
                result = resolveNotificationState(PlaybackState(title = "Cached Track", artist = "Cached Artist"))
                latch.countDown()
            }
            assertTrue("background resolve must complete", latch.await(5, TimeUnit.SECONDS))
        } finally {
            thread.quitSafely()
        }
        // Off-main-thread: no player access — the fresh cached state wins
        assertEquals("Cached Track", result?.title)
        assertEquals("Cached Artist", result?.artist)
    }

    @Test
    fun `resolveNotificationState re-derives on the main thread`() {
        // Robolectric runs test methods on the main thread with a real main looper
        val state = resolveNotificationState(PlaybackState(title = "Stale Track", artist = "Stale Artist"))
        assertEquals("New Track", state.title)
        assertEquals("New Artist", state.artist)
    }
}
