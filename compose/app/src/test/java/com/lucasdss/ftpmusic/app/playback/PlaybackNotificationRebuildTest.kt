package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the QS/notification stale-metadata fix:
 * resolveNotificationState must re-derive the track info from the live player
 * (not the cached StateFlow) so a gapless auto-advance can never freeze the
 * notification on the previous track while the MediaSession (BT) and Now
 * Playing stay correct.
 */
class PlaybackNotificationRebuildTest {

    @After
    fun tearDown() {
        PlayerHolder.player = null
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null
        PlayerHolder.castVolume = 0f
        PlayerHolder.castDeviceVolume = 0.5f
        PlayerHolder.pendingCastVolume = null
        PlayerHolder.pendingCastVolumeTimestamp = 0L
    }

    /** Mock player with a single media item carrying the given title/artist. */
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
    fun `resolveNotificationState prefers live player metadata over stale cached state`() {
        PlayerHolder.player = playerWith("New Track", "New Artist")
        val cached = PlaybackState(title = "Stale Track", artist = "Stale Artist")

        val state = resolveNotificationState(cached)

        assertEquals("New Track", state.title)
        assertEquals("New Artist", state.artist)
    }

    @Test
    fun `resolveNotificationState falls back to cached state when no player attached`() {
        PlayerHolder.player = null
        val cached = PlaybackState(title = "Cached Track", artist = "Cached Artist")

        val state = resolveNotificationState(cached)

        assertEquals("Cached Track", state.title)
        assertEquals("Cached Artist", state.artist)
    }

    @Test
    fun `resolveNotificationState preserves cast device when player present`() {
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = "Bedroom"
        PlayerHolder.player = playerWith("Cast Track", "Cast Artist")

        val state = resolveNotificationState(PlaybackState())

        assertEquals("Cast Track", state.title)
        assertEquals("Cast Artist", state.artist)
        assertTrue(state.isCasting)
        assertEquals("Bedroom", state.castDeviceName)
    }

    @Test
    fun `resolveNotificationState preserves non-player fields from cached state`() {
        PlayerHolder.player = playerWith("New Track", "New Artist")

        val state = resolveNotificationState(
            PlaybackState(isStarred = true, isDisliked = false, trackRating = 4, sleepTimerEndMs = 1234L),
        )

        assertEquals("New Track", state.title)
        assertTrue(state.isStarred)
        assertFalse(state.isDisliked)
        assertEquals(4, state.trackRating)
        assertEquals(1234L, state.sleepTimerEndMs)
    }

    @Test
    fun `isOnMainThread returns true when no looper is prepared`() {
        // Plain-JVM test environment: getMainLooper()/myLooper() both return
        // null (returnDefaultValues) — the guard treats this as main so the
        // re-derive logic remains testable.
        assertTrue(isOnMainThread())
    }
}
