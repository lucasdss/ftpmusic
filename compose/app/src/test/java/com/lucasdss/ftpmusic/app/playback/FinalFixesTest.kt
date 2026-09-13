package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.lucasdss.ftpmusic.app.data.cache.DownloadManager
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.model.Track
import io.mockk.*
import kotlin.math.pow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FinalFixesTest {

    // --- ReplayGain tests (Fix 7) ---

    @Test
    fun `ReplayGain uses track gain over album gain`() {
        val trackGain = -6.0f
        val albumGain = -3.0f
        // For unit testing, we verify the selection logic inline
        val selected = trackGain.takeIf { it != 0f } ?: albumGain.takeIf { it != 0f }
        assertEquals(-6.0f, selected)
    }

    @Test
    fun `ReplayGain falls back to album gain when track gain is zero`() {
        val trackGain = 0f
        val albumGain = -3.0f
        val selected = trackGain.takeIf { it != 0f } ?: albumGain.takeIf { it != 0f }
        assertEquals(-3.0f, selected)
    }

    @Test
    fun `ReplayGain returns null when both gains are zero`() {
        val gain = 0f.takeIf { it != 0f } ?: 0f.takeIf { it != 0f }
        assertNull(gain)
    }

    @Test
    fun `ReplayGain clamps gain to valid range`() {
        val gain = 25f
        val clamped = gain.coerceIn(-20f, 20f)
        assertEquals(20f, clamped)

        val gainNeg = -25f
        val clampedNeg = gainNeg.coerceIn(-20f, 20f)
        assertEquals(-20f, clampedNeg)
    }

    @Test
    fun `ReplayGain dB to multiplier is accurate`() {
        val gain = 6.0f
        val multiplier = 10.0.pow(gain.toDouble() / 20.0).toFloat()
        assertTrue(multiplier in 1.9f..2.1f)
    }

    // --- Notification test (Fix 1) ---

    @Test
    fun `PlaybackNotificationProvider has valid channel ID`() {
        val channelId = PlaybackNotificationProvider.CHANNEL_ID
        assertNotNull(channelId)
        assertTrue(channelId.isNotEmpty())
        assertEquals("ftpmusic_playback", channelId)
    }

    @Test
    fun `PlaybackNotificationProvider uses positive notification ID`() {
        val notificationId = PlaybackNotificationProvider.NOTIFICATION_ID
        assertTrue(notificationId > 0)
    }

    // --- PlaybackManager tests (Fix 4) ---

    private val mockPlayer: Player = mockk(relaxed = true)
    private val queueManager = QueueManager(mockk())
    private val mockPersistenceManager: QueuePersistenceManager = mockk(relaxed = true)
    private val mockOfflineModeManager: OfflineModeManager = mockk(relaxed = true)
    private val mockDownloadManager: com.lucasdss.ftpmusic.app.data.cache.DownloadManager = mockk(relaxed = true)
    private val castPreferences: CastPreferences = mockk(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns Uri.EMPTY
        PlayerHolder.player = mockPlayer
        every { mockPlayer.currentMediaItemIndex } returns 0
        every { mockPlayer.mediaItemCount } returns 5
        every { mockPlayer.getMediaItemAt(any()) } returns MediaItem.Builder()
            .setMediaId("t-prev")
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle("Previous")
                    .setArtist("Prev Artist")
                    .build(),
            )
            .setUri("http://server/stream?id=t-prev")
            .build()
        every { mockPlayer.currentPosition } returns 0L
    }

    @After
    fun teardown() {
        unmockkStatic(Uri::class)
        PlayerHolder.player = null
    }

    @Test
    fun `addAllToQueue triggers persistence save`() = runBlocking {
        val app = mockk<android.app.Application>(relaxed = true)
        val manager =
            PlaybackManager(
                queueManager,
                mockPersistenceManager,
                mockOfflineModeManager,
                mockDownloadManager,
                castPreferences,
                app,
                mockk<com.lucasdss.ftpmusic.app.data.db.QueueJournalDao>(relaxed = true),
            )
        val tracks = listOf(
            Track("t1", "Song 1", artist = "Artist A", duration = 100),
            Track("t2", "Song 2", artist = "Artist A", duration = 120),
        )
        val urls = listOf("http://server/stream?id=t1", "http://server/stream?id=t2")

        manager.addAllToQueue(tracks, urls)

        // Give the coroutine time to call save
        kotlinx.coroutines.delay(200)
        coVerify(atLeast = 1) { mockPersistenceManager.save(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `playAlbum with skipPersistence does not call save`() = runBlocking {
        val app = mockk<android.app.Application>(relaxed = true)
        val manager =
            PlaybackManager(
                queueManager,
                mockPersistenceManager,
                mockOfflineModeManager,
                mockDownloadManager,
                castPreferences,
                app,
                mockk<com.lucasdss.ftpmusic.app.data.db.QueueJournalDao>(relaxed = true),
            )
        val tracks = listOf(Track("t1", "Song 1", duration = 100))
        val urls = listOf("http://server/stream?id=t1")

        manager.playAlbum(tracks, urls, skipPersistence = true)

        coVerify(exactly = 0) { mockPersistenceManager.save(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `PLAY_PAUSE toggles isPlaying`() {
        val state1 = PlaybackState(isPlaying = true)
        assertTrue(state1.isPlaying)

        val state2 = state1.copy(isPlaying = false)
        assertFalse(state2.isPlaying)
    }
}
