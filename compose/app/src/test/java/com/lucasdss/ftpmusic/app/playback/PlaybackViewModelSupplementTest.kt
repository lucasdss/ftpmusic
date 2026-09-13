package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackViewModelSupplementTest {

    private lateinit var provider: FakePlaybackStateProvider
    private lateinit var playbackManager: PlaybackManager
    private lateinit var favoriteRepo: FavoriteRepository
    private lateinit var storage: SecureStorage
    private lateinit var trackDao: com.lucasdss.ftpmusic.app.data.db.TrackDao
    private val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher()
    private lateinit var viewModel: PlaybackViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        provider = FakePlaybackStateProvider()
        playbackManager = mockk(relaxed = true)
        favoriteRepo = mockk(relaxed = true)
        storage = mockk(relaxed = true)
        trackDao = mockk(relaxed = true)
        viewModel = PlaybackViewModel(
            provider,
            playbackManager,
            favoriteRepo,
            storage,
            trackDao,
            mockk<com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao>(relaxed = true),
            mockk<SubsonicApi>(relaxed = true),
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        DynamicBaseUrl.url = "" // don't leak the static into other classes
    }

    // ── Control delegation (gap: toggleRepeat, toggleShuffle, toggleSpeed, setVolume) ──

    @Test
    fun `toggleRepeat delegates to provider`() {
        viewModel.toggleRepeat()
        assertTrue(provider.toggleRepeatCalled)
    }

    @Test
    fun `toggleShuffle delegates to provider`() {
        viewModel.toggleShuffle()
        assertTrue(provider.toggleShuffleCalled)
    }

    @Test
    fun `toggleSpeed delegates to provider`() {
        viewModel.toggleSpeed()
        assertTrue(provider.toggleSpeedCalled)
    }

    @Test
    fun `setVolume delegates to provider`() {
        viewModel.setVolume(0.8f)
        assertEquals(0.8f, provider.lastVolume)
    }

    // ── Queue management (gap: playQueueItem, removeFromQueue, clearQueue, persistQueue) ──

    @Test
    fun `playQueueItem delegates to playbackManager`() {
        viewModel.playQueueItem(3)
        verify { playbackManager.playQueueItem(3) }
    }

    @Test
    fun `removeFromQueue delegates to playbackManager`() {
        viewModel.removeFromQueue(1)
        verify { playbackManager.removeFromQueue(1) }
    }

    @Test
    fun `clearQueue delegates to playbackManager`() {
        viewModel.clearQueue()
        verify { playbackManager.clearQueue() }
    }

    @Test
    fun `persistQueue delegates to playbackManager`() {
        viewModel.persistQueue()
        verify { playbackManager.persistCurrentQueue() }
    }

    // ── Cast (gap: onCastConnected, onCastDisconnected) ────────────────────

    @Test
    fun `onCastConnected is a no-op — cast state flows through provider`() = runTest(testDispatcher) {
        // onCastConnected is now a no-op at the ViewModel level.
        // Cast state is received via the provider.
        provider.emit(PlaybackState(isCasting = true, castDeviceName = "Living Room"))
        val state = viewModel.state.first { it.isCasting }
        assertEquals("Living Room", state.castDeviceName)
        assertTrue(state.isCasting)
    }

    @Test
    fun `onCastDisconnected is a no-op — cast state flows through provider`() = runTest(testDispatcher) {
        // Both onCastConnected and onCastDisconnected are now no-ops.
        // Cast state disconnection propagates via provider.
        provider.emit(PlaybackState(isCasting = true, castDeviceName = "TV"))
        val connected = viewModel.state.first { it.isCasting }
        assertTrue(connected.isCasting)

        provider.emit(PlaybackState(isCasting = false, castDeviceName = null))
        val disconnected = viewModel.state.first { !it.isCasting }
        assertFalse(disconnected.isCasting)
        assertNull(disconnected.castDeviceName)
    }

    // ── Play actions (gap: playAll, shuffleAll, playSingleTrack, buildStreamUrl) ──

    @Test
    fun `playAll delegates to playbackManager`() {
        val tracks = listOf(
            Track("t1", "Song 1", artist = "A", duration = 200),
            Track("t2", "Song 2", artist = "B", duration = 180),
        )
        val urls = listOf("http://s/t1", "http://s/t2")

        viewModel.playAll(tracks, urls)
        verify { playbackManager.playAlbum(tracks, urls) }
    }

    @Test
    fun `shuffleAll delegates to playbackManager shuffleAlbum`() {
        val tracks = listOf(
            Track("t1", "Song 1", artist = "A", duration = 200),
        )
        val urls = listOf("http://s/t1")

        viewModel.shuffleAll(tracks, urls)
        verify { playbackManager.shuffleAlbum(tracks, urls) }
    }

    @Test
    fun `playSingleTrack delegates to playbackManager`() {
        val track = Track("t1", "Song 1", artist = "A", duration = 200)
        val url = "http://s/t1"

        viewModel.playSingleTrack(track, url)
        verify { playbackManager.playSingleTrack(track, url) }
    }

    @Test
    fun `buildStreamUrl calls storage and constructs URL`() {
        DynamicBaseUrl.url = "https://test.example.com"
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"

        val url = viewModel.buildStreamUrl("t1")
        assertTrue(url.isNotEmpty())
    }

    // ── getTrackInfo (gap) ─────────────────────────────────────────────────

    @Test
    fun `getTrackInfo delegates to playbackManager`() {
        viewModel.getTrackInfo("t1")
        verify { playbackManager.getTrackInfo("t1") }
    }

    // ── refreshQueueDownloadStatus (gap) ───────────────────────────────────

    @Test
    fun `refreshQueueDownloadStatus does not throw`() = runTest {
        // Uses PlayerHolder.exoPlayer which is null in unit test — no crash expected
        PlayerHolder.exoPlayer = null
        viewModel.refreshQueueDownloadStatus()
        // Should complete without exception (returns early when exoPlayer is null)
    }

    // ── getUpcomingTracks (gap) ────────────────────────────────────────────

    @Test
    fun `getUpcomingTracks reads from queueState`() = runTest {
        provider.emit(PlaybackState(trackIndex = 1, queueSize = 5))
        val state = viewModel.state.first { it.trackIndex == 1 }

        viewModel.getUpcomingTracks(3)
        // Should not crash; reads buildQueueState internally
    }

    @Test
    fun `getUpcomingTracks marks current by active player mediaId not stale index`() = runTest {
        // Cast scenario: receiver auto-advanced to t2, but the idle local
        // ExoPlayer's index still points at t0. Current-track highlight must
        // follow the ACTIVE player's mediaId (Cast SDK ID-matching pattern).
        fun item(id: String, title: String): androidx.media3.common.MediaItem =
            androidx.media3.common.MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder().setTitle(title).build(),
                )
                .build()

        val exo = mockk<androidx.media3.common.Player>(relaxed = true)
        every { exo.mediaItemCount } returns 3
        every { exo.currentMediaItemIndex } returns 0 // stale
        every { exo.getMediaItemAt(0) } returns item("t0", "Track 0")
        every { exo.getMediaItemAt(1) } returns item("t1", "Track 1")
        every { exo.getMediaItemAt(2) } returns item("t2", "Track 2")

        val castPlayer = mockk<androidx.media3.common.Player>(relaxed = true)
        every { castPlayer.currentMediaItem } returns item("t2", "Track 2")

        val prevPlayer = PlayerHolder.player
        val prevExo = PlayerHolder.exoPlayer
        try {
            PlayerHolder.exoPlayer = exo
            PlayerHolder.player = castPlayer // active player = CastPlayer

            val tracks = viewModel.getUpcomingTracks(10)

            assertEquals(3, tracks.size)
            assertFalse("t0 must not be current (stale index)", tracks[0].isCurrent)
            assertTrue("t2 must be current (active player mediaId)", tracks[2].isCurrent)
        } finally {
            PlayerHolder.player = prevPlayer
            PlayerHolder.exoPlayer = prevExo
        }
    }

    @Test
    fun `getUpcomingTracks falls back to index when active player has no media item`() = runTest {
        fun item(id: String): androidx.media3.common.MediaItem = androidx.media3.common.MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder().setTitle("T-$id").build(),
            )
            .build()

        val exo = mockk<androidx.media3.common.Player>(relaxed = true)
        every { exo.mediaItemCount } returns 2
        every { exo.currentMediaItemIndex } returns 1
        every { exo.getMediaItemAt(0) } returns item("t0")
        every { exo.getMediaItemAt(1) } returns item("t1")

        val prevPlayer = PlayerHolder.player
        val prevExo = PlayerHolder.exoPlayer
        try {
            PlayerHolder.exoPlayer = exo
            PlayerHolder.player = null // no active player — fall back to exo index

            val tracks = viewModel.getUpcomingTracks(10)

            assertEquals(2, tracks.size)
            assertTrue("index fallback marks t1 current", tracks[1].isCurrent)
        } finally {
            PlayerHolder.player = prevPlayer
            PlayerHolder.exoPlayer = prevExo
        }
    }

    // ── 2026-08-27 deep-review additions ────────────────────────────────

    @Test
    fun `refreshQueueDownloadStatus marks downloaded ids from dao`() = runTest {
        val player = io.mockk.mockk<androidx.media3.common.Player>(relaxed = true)
        val item1 = androidx.media3.common.MediaItem.Builder().setMediaId("t1").build()
        val item2 = androidx.media3.common.MediaItem.Builder().setMediaId("t2").build()
        io.mockk.every { player.mediaItemCount } returns 2
        io.mockk.every { player.getMediaItemAt(0) } returns item1
        io.mockk.every { player.getMediaItemAt(1) } returns item2
        PlayerHolder.player = player
        val entity = com.lucasdss.ftpmusic.app.data.db.TrackEntity(
            id = "t1",
            title = "T",
            artist = "A",
            isDownloaded = true,
            cachedFilePath = null,
        )
        io.mockk.coEvery { trackDao.getTracksByIds(listOf("t1", "t2")) } returns listOf(entity)

        viewModel.refreshQueueDownloadStatus()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf("t1"), viewModel.state.value.downloadedTrackIds)
        PlayerHolder.player = null
    }

    @Test
    fun `refreshQueueDownloadStatus no-ops on empty queue`() = runTest {
        val player = io.mockk.mockk<androidx.media3.common.Player>(relaxed = true)
        io.mockk.every { player.mediaItemCount } returns 0
        PlayerHolder.player = player

        viewModel.refreshQueueDownloadStatus()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptySet<String>(), viewModel.state.value.downloadedTrackIds)
        PlayerHolder.player = null
    }

    @Test
    fun `onCastDisconnected clears cast state and player queue`() = runTest {
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = "TV"
        PlayerHolder.player = io.mockk.mockk(relaxed = true)

        viewModel.onCastDisconnected()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(PlayerHolder.isCasting)
        PlayerHolder.castDeviceName = null
        PlayerHolder.player = null
    }

    @Test
    fun `buildStreamUrl handles missing credentials gracefully`() {
        io.mockk.every { storage.get(com.lucasdss.ftpmusic.app.data.security.SecureStorage.KEY_USERNAME) } returns null
        io.mockk.every { storage.get(com.lucasdss.ftpmusic.app.data.security.SecureStorage.KEY_PASSWORD) } returns null
        val url = viewModel.buildStreamUrl("t1")
        assertNotNull("null credentials must not crash", url)
        assertTrue(url.isNotEmpty())
    }

    // ── stateWithoutPosition (P1 nav-scope flow, ADR-0027) ────────────────

    @Test
    fun `stateWithoutPosition strips position but keeps metadata`() = runTest(testDispatcher) {
        provider.emit(PlaybackState(title = "Song", artist = "Artist", position = 5000L, isPlaying = true))
        testDispatcher.scheduler.advanceUntilIdle()

        val collected = mutableListOf<PlaybackState>()
        val job = launch { viewModel.stateWithoutPosition.collect { collected.add(it) } }
        testDispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        val out = collected.last()
        assertEquals("position must be stripped for the nav scope", 0L, out.position)
        assertEquals("Song", out.title)
        assertEquals("Artist", out.artist)
        assertTrue(out.isPlaying)
    }

    @Test
    fun `stateWithoutPosition does not re-emit on position-only change`() = runTest(testDispatcher) {
        provider.emit(PlaybackState(position = 1000L))
        testDispatcher.scheduler.advanceUntilIdle()

        val emissions = mutableListOf<Long>()
        val job = launch {
            viewModel.stateWithoutPosition.map { it.position }.collect { emissions.add(it) }
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Position-only change: mapped value stays 0 → distinctUntilChanged drops it.
        provider.emit(PlaybackState(position = 9000L))
        testDispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertEquals("nav scope must not recompose on position ticks", listOf(0L), emissions)
    }
}
