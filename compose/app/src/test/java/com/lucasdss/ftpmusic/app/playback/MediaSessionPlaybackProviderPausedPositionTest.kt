package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/** ADR-0117: paused seat must mirror Exo position into UI flows. */
class MediaSessionPlaybackProviderPausedPositionTest {

    @Test
    fun `mirrorPausedPosition updates positionMs when paused at saved offset`() {
        val provider = MediaSessionPlaybackProvider(
            com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager(mockk(relaxed = true)),
        )
        val player = mockk<Player>(relaxed = true)
        every { player.currentPosition } returns 106_679L
        every { player.duration } returns 240_000L
        every { player.currentMediaItem } returns MediaItem.Builder().setMediaId("t1").build()
        every { player.isPlaying } returns false

        provider.mirrorPausedPosition(player)

        assertEquals(106_679L, provider.positionMs.value)
        assertEquals(106_679L, provider.playbackState.value.position)
        assertEquals(240_000L, provider.playbackState.value.duration)
    }

    @Test
    fun `mirrorPausedPosition no-op when already mirrored`() {
        val provider = MediaSessionPlaybackProvider(
            com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager(mockk(relaxed = true)),
        )
        val player = mockk<Player>(relaxed = true)
        every { player.currentPosition } returns 5_000L
        every { player.duration } returns 10_000L
        every { player.currentMediaItem } returns MediaItem.Builder().setMediaId("t1").build()

        provider.mirrorPausedPosition(player)
        val first = provider.playbackState.value
        provider.mirrorPausedPosition(player)
        assertEquals(first, provider.playbackState.value)
        assertEquals(5_000L, provider.positionMs.value)
    }
}
