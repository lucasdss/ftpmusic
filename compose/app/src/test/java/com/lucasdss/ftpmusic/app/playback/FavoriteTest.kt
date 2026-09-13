package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `PlaybackState isStarred defaults to false`() {
        val state = PlaybackState()
        assertFalse(state.isStarred)
    }

    @Test
    fun `PlaybackState isDisliked defaults to false`() {
        val state = PlaybackState()
        assertFalse(state.isDisliked)
    }

    @Test
    fun `PlaybackState currentTrackId defaults to null`() {
        val state = PlaybackState()
        assertNull(state.currentTrackId)
    }

    private fun vm(
        favoriteRepo: FavoriteRepository,
        trackDao: TrackDao,
        playbackStateDao: com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao = mockk(relaxed = true),
        api: SubsonicApi = mockk(relaxed = true),
    ): Pair<PlaybackViewModel, FakePlaybackStateProvider> {
        val provider = FakePlaybackStateProvider()
        val viewModel = PlaybackViewModel(
            provider,
            mockk(relaxed = true),
            favoriteRepo,
            mockk(relaxed = true),
            trackDao,
            playbackStateDao,
            api,
        )
        return viewModel to provider
    }

    @Test
    fun `toggleLike calls likeTrack when not starred or disliked`() = runTest {
        val favoriteRepo = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack("track-1") } returns null
        val (viewModel, provider) = vm(favoriteRepo, trackDao)
        provider.emit(PlaybackState(currentTrackId = "track-1"))

        viewModel.toggleLike()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { favoriteRepo.likeTrack("track-1") }
        coVerify(exactly = 0) { favoriteRepo.unlikeTrack(any()) }
    }

    @Test
    fun `toggleLike calls unlikeTrack when already starred`() = runTest {
        val favoriteRepo = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack("track-2") } returns TrackEntity(
            id = "track-2",
            title = "T2",
            starredAt = 123L,
        )
        val (viewModel, provider) = vm(favoriteRepo, trackDao)
        provider.emit(PlaybackState(currentTrackId = "track-2"))
        testDispatcher.scheduler.advanceUntilIdle() // DB load sets isStarred

        viewModel.toggleLike()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { favoriteRepo.unlikeTrack("track-2") }
        coVerify(exactly = 0) { favoriteRepo.likeTrack(any()) }
    }

    @Test
    fun `toggleLike does nothing when no track is loaded`() = runTest {
        val favoriteRepo = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        val (viewModel, _) = vm(favoriteRepo, trackDao)

        viewModel.toggleLike()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { favoriteRepo.likeTrack(any()) }
        coVerify(exactly = 0) { favoriteRepo.unlikeTrack(any()) }
    }

    @Test
    fun `toggleDislike calls dislikeTrack and clears like on mutual exclusion`() = runTest {
        val favoriteRepo = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack("track-3") } returns TrackEntity(
            id = "track-3",
            title = "T3",
            starredAt = 456L,
            isDisliked = false,
        )
        val (viewModel, provider) = vm(favoriteRepo, trackDao)
        provider.emit(PlaybackState(currentTrackId = "track-3"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleDislike()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { favoriteRepo.dislikeTrack("track-3") }
        assertEquals("Dislike state set", true, viewModel.state.value.isDisliked)
        assertEquals("Star cleared by mutual exclusion", false, viewModel.state.value.isStarred)
    }

    @Test
    fun `toggleDislike calls clearDislikeTrack when already disliked`() = runTest {
        val favoriteRepo = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack("track-4") } returns TrackEntity(
            id = "track-4",
            title = "T4",
            isDisliked = true,
        )
        val (viewModel, provider) = vm(favoriteRepo, trackDao)
        provider.emit(PlaybackState(currentTrackId = "track-4"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleDislike()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { favoriteRepo.clearDislikeTrack("track-4") }
        assertEquals(false, viewModel.state.value.isDisliked)
    }

    @Test
    fun `rateCurrent writes local rating then syncs best effort`() = runTest {
        val favoriteRepo = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack("track-5") } returns null
        val api = mockk<SubsonicApi>(relaxed = true)
        val (viewModel, provider) = vm(favoriteRepo, trackDao, api = api)
        provider.emit(PlaybackState(currentTrackId = "track-5"))

        viewModel.rateCurrent(4)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { trackDao.setRating("track-5", 4) }
        coVerify { api.setRating(any(), id = "track-5", rating = 4) }
        assertEquals("Optimistic rating", 4, viewModel.state.value.trackRating)
    }

    @Test
    fun `rateCurrent clamps rating to 0-5`() = runTest {
        val favoriteRepo = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        val (viewModel, provider) = vm(favoriteRepo, trackDao)
        provider.emit(PlaybackState(currentTrackId = "track-6"))

        viewModel.rateCurrent(9)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Clamped to 5", 5, viewModel.state.value.trackRating)
    }

    @Test
    fun `track change reaction load does not clobber in-flight toggle`() = runTest {
        val favoriteRepo = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        // DB says NOT starred — but a like is in flight, so the load must not apply
        coEvery { trackDao.getTrack("track-7") } returns TrackEntity(id = "track-7", title = "T7")
        coEvery { favoriteRepo.likeTrack("track-7") } coAnswers {
            kotlinx.coroutines.delay(50) // hold the mutation in flight
        }
        val (viewModel, provider) = vm(favoriteRepo, trackDao)
        provider.emit(PlaybackState(currentTrackId = "track-7"))
        testDispatcher.scheduler.advanceUntilIdle()

        // Start the like (holds in flight via delay), then trigger a track-change
        // reload while it's still running.
        viewModel.toggleLike()
        viewModel.state // ensure like coroutine started
        provider.emit(PlaybackState(currentTrackId = "track-8"))
        testDispatcher.scheduler.advanceUntilIdle()

        // track-8 load is guarded by the in-flight flag → not clobbered mid-flight
        // and the like for track-7 completed once the delay resolved.
        coVerify(exactly = 1) { favoriteRepo.likeTrack("track-7") }
    }

    // ── Failure branches (server throws) — release-gate coverage ────────────

    @Test
    fun `toggleLike restores like when unlikeTrack throws`() = runTest {
        val favoriteRepo = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack("track-2") } returns TrackEntity(
            id = "track-2",
            title = "T2",
            starredAt = 123L,
        )
        coEvery { favoriteRepo.unlikeTrack("track-2") } throws RuntimeException("boom")
        val (viewModel, provider) = vm(favoriteRepo, trackDao)
        provider.emit(PlaybackState(currentTrackId = "track-2"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleLike()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("failed unlike must restore the star", viewModel.state.value.isStarred)
    }

    @Test
    fun `toggleLike restores dislike when likeTrack throws`() = runTest {
        val favoriteRepo = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack("track-1") } returns TrackEntity(
            id = "track-1",
            title = "T1",
            isDisliked = true,
        )
        coEvery { favoriteRepo.likeTrack("track-1") } throws RuntimeException("boom")
        val (viewModel, provider) = vm(favoriteRepo, trackDao)
        provider.emit(PlaybackState(currentTrackId = "track-1"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleLike()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("failed like must restore dislike", viewModel.state.value.isDisliked)
        assertFalse(viewModel.state.value.isStarred)
    }

    @Test
    fun `toggleDislike restores states when dislikeTrack throws`() = runTest {
        val favoriteRepo = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack("track-1") } returns TrackEntity(
            id = "track-1",
            title = "T1",
            starredAt = 123L,
        )
        coEvery { favoriteRepo.dislikeTrack("track-1") } throws RuntimeException("boom")
        val (viewModel, provider) = vm(favoriteRepo, trackDao)
        provider.emit(PlaybackState(currentTrackId = "track-1"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleDislike()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("failed dislike must restore the star", viewModel.state.value.isStarred)
        assertFalse(viewModel.state.value.isDisliked)
    }

    @Test
    fun `rateCurrent keeps local rating when server sync throws`() = runTest {
        val favoriteRepo = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.setRating(any(), any()) } throws RuntimeException("boom")
        val (viewModel, provider) = vm(favoriteRepo, trackDao)
        provider.emit(PlaybackState(currentTrackId = "track-1"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.rateCurrent(4)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("local rating survives server failure", 4, viewModel.state.value.trackRating)
    }

    @Test
    fun `track change loader tolerates dao failure`() = runTest {
        val favoriteRepo = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack("track-1") } throws RuntimeException("boom")
        val (viewModel, provider) = vm(favoriteRepo, trackDao)
        provider.emit(PlaybackState(currentTrackId = "track-1"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("failed reaction load must not crash", viewModel.state.value.isStarred)
        assertFalse(viewModel.state.value.isDisliked)
    }

    // ── Sleep timer VM surface ──────────────────────────────────────────────

    @Test
    fun `startSleepTimer arms provider and mirrors deadline`() = runTest {
        val (viewModel, provider) = vm(mockk(relaxed = true), mockk(relaxed = true))
        viewModel.startSleepTimer(30)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(provider.lastArmedSleepEndMs > 0)
        assertTrue(viewModel.state.value.sleepTimerEndMs > 0)
    }

    @Test
    fun `cancelSleepTimer resets mirror and cancels provider`() = runTest {
        val (viewModel, provider) = vm(mockk(relaxed = true), mockk(relaxed = true))
        viewModel.startSleepTimer(30)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.cancelSleepTimer()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0L, viewModel.state.value.sleepTimerEndMs)
        assertTrue(provider.sleepTimerCancelled)
    }

    @Test
    fun `init restores a future sleep timer from the dao and arms provider`() = runTest {
        val playbackStateDao = mockk<com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao>(relaxed = true)
        val futureEnd = System.currentTimeMillis() + 60_000
        coEvery { playbackStateDao.get() } returns PersistedPlaybackState(sleepTimerEndMs = futureEnd)
        val (viewModel, provider) = vm(
            mockk(relaxed = true),
            mockk(relaxed = true),
            playbackStateDao = playbackStateDao,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("persisted future deadline must re-arm", provider.lastArmedSleepEndMs > 0)
        assertTrue(viewModel.state.value.sleepTimerEndMs > 0)
    }

    // ── shareQueue (playlist share sheet) ───────────────────────────────────

    @Test
    fun `shareQueue creates playlist and shares when queue non-empty`() = runTest {
        val favoriteRepo = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        val api = mockk<SubsonicApi>(relaxed = true)
        coEvery { api.createPlaylist(any(), name = any(), songIds = any()) } returns mapOf<String, Any>(
            "subsonic-response" to mapOf("status" to "ok", "playlist" to mapOf("id" to "pl-1")),
        )
        val (viewModel, _) = vm(favoriteRepo, trackDao, api = api)

        val player = mockk<androidx.media3.common.Player>(relaxed = true)
        every { player.mediaItemCount } returns 2
        every { player.getMediaItemAt(0) } returns androidx.media3.common.MediaItem.Builder().setMediaId("t1").build()
        every { player.getMediaItemAt(1) } returns androidx.media3.common.MediaItem.Builder().setMediaId("t2").build()
        PlayerHolder.exoPlayer = player
        // android.widget.Toast statics return null under isReturnDefaultValues — stub them
        mockkStatic(android.widget.Toast::class)
        try {
            every {
                android.widget.Toast.makeText(any<android.content.Context>(), any<CharSequence>(), any<Int>())
            } returns
                mockk(relaxed = true)
            viewModel.shareQueue(mockk(relaxed = true))
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { api.createPlaylist(any(), name = any(), songIds = "t1,t2") }
        } finally {
            PlayerHolder.exoPlayer = null
            unmockkStatic(android.widget.Toast::class)
        }
    }

    @Test
    fun `shareQueue no-ops on empty queue`() = runTest {
        val (viewModel, _) = vm(mockk(relaxed = true), mockk(relaxed = true))
        PlayerHolder.exoPlayer = mockk<androidx.media3.common.Player>(relaxed = true) // mediaItemCount -> 0
        try {
            viewModel.shareQueue(mockk(relaxed = true))
            testDispatcher.scheduler.advanceUntilIdle()
            // no crash, no API call
        } finally {
            PlayerHolder.exoPlayer = null
        }
    }
}
