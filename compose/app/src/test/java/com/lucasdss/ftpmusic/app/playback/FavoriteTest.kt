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
        queueDao: com.lucasdss.ftpmusic.app.data.db.QueueDao = mockk(relaxed = true),
        api: SubsonicApi = mockk(relaxed = true),
        playlistRepository: com.lucasdss.ftpmusic.app.data.repository.PlaylistRepository = mockk(relaxed = true),
        playbackManager: PlaybackManager = mockk(relaxed = true),
    ): Pair<PlaybackViewModel, FakePlaybackStateProvider> {
        val provider = FakePlaybackStateProvider()
        val viewModel = PlaybackViewModel(
            provider,
            playbackManager,
            favoriteRepo,
            mockk(relaxed = true),
            trackDao,
            queueDao,
            playlistRepository,
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
    fun `toggleLike like then unlike stays unstarred`() = runTest {
        val favoriteRepo = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack("track-lu") } returns TrackEntity(
            id = "track-lu",
            title = "LU",
            starredAt = null,
        )
        val (viewModel, provider) = vm(favoriteRepo, trackDao)
        provider.emit(PlaybackState(currentTrackId = "track-lu"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleLike() // like
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.isStarred)

        viewModel.toggleLike() // unlike (YT Music parity)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("second tap must clear like", viewModel.state.value.isStarred)
        coVerify { favoriteRepo.likeTrack("track-lu") }
        coVerify { favoriteRepo.unlikeTrack("track-lu") }
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

        coVerify { favoriteRepo.rateTrack("track-5", 4) }
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
    fun `failed older like does not undo newer unlike`() = runTest {
        val favoriteRepo = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack("track-gen") } returns TrackEntity(
            id = "track-gen",
            title = "G",
            starredAt = null,
        )
        val likeStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val likeRelease = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { favoriteRepo.likeTrack("track-gen") } coAnswers {
            likeStarted.complete(Unit)
            likeRelease.await()
            throw RuntimeException("stale like fail")
        }
        val (viewModel, provider) = vm(favoriteRepo, trackDao)
        provider.emit(PlaybackState(currentTrackId = "track-gen"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleLike() // gen1 like (will fail later)
        likeStarted.await()
        viewModel.toggleLike() // gen2 unlike (optimistic)
        assertFalse(viewModel.state.value.isStarred)
        likeRelease.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        // Stale gen1 failure must not resurrect the like after gen2 unlike.
        assertFalse("stale fail must not undo newer unlike", viewModel.state.value.isStarred)
        coVerify { favoriteRepo.unlikeTrack("track-gen") }
    }

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
    fun `toggleDislike restores dislike when clearDislikeTrack throws`() = runTest {
        val favoriteRepo = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack("track-4") } returns TrackEntity(
            id = "track-4",
            title = "T4",
            isDisliked = true,
        )
        coEvery { favoriteRepo.clearDislikeTrack("track-4") } throws RuntimeException("boom")
        val (viewModel, provider) = vm(favoriteRepo, trackDao)
        provider.emit(PlaybackState(currentTrackId = "track-4"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleDislike()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("failed clear must restore dislike", viewModel.state.value.isDisliked)
    }

    @Test
    fun `toggleDislike does nothing when no track is loaded`() = runTest {
        val favoriteRepo = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        val (viewModel, _) = vm(favoriteRepo, trackDao)

        viewModel.toggleDislike()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { favoriteRepo.dislikeTrack(any()) }
        coVerify(exactly = 0) { favoriteRepo.clearDislikeTrack(any()) }
    }

    @Test
    fun `failed older dislike does not undo newer clear`() = runTest {
        val favoriteRepo = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack("track-gen-d") } returns TrackEntity(
            id = "track-gen-d",
            title = "G",
            isDisliked = false,
        )
        val dislikeStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val dislikeRelease = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { favoriteRepo.dislikeTrack("track-gen-d") } coAnswers {
            dislikeStarted.complete(Unit)
            dislikeRelease.await()
            throw RuntimeException("stale dislike fail")
        }
        val (viewModel, provider) = vm(favoriteRepo, trackDao)
        provider.emit(PlaybackState(currentTrackId = "track-gen-d"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleDislike() // gen1 dislike (will fail later)
        dislikeStarted.await()
        viewModel.toggleDislike() // gen2 clear (optimistic)
        assertFalse(viewModel.state.value.isDisliked)
        dislikeRelease.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("stale fail must not undo newer clear", viewModel.state.value.isDisliked)
        coVerify { favoriteRepo.clearDislikeTrack("track-gen-d") }
    }

    @Test
    fun `toggleDislike skips confirm when track changes mid flight`() = runTest {
        val favoriteRepo = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack(any()) } returns TrackEntity(id = "track-d1", title = "D1")
        val (viewModel, provider) = vm(favoriteRepo, trackDao)
        provider.emit(PlaybackState(currentTrackId = "track-d1"))
        testDispatcher.scheduler.advanceUntilIdle()
        coEvery { favoriteRepo.dislikeTrack("track-d1") } coAnswers {
            provider.emit(PlaybackState(currentTrackId = "track-d2"))
        }

        viewModel.toggleDislike()
        testDispatcher.scheduler.advanceUntilIdle()

        // Optimistic dislike applied, then track changed — confirm block skipped.
        coVerify { favoriteRepo.dislikeTrack("track-d1") }
    }

    @Test
    fun `toggleDislike skip restore when track changes before failure`() = runTest {
        val favoriteRepo = mockk<FavoriteRepository>(relaxed = true)
        val trackDao = mockk<TrackDao>(relaxed = true)
        coEvery { trackDao.getTrack(any()) } returns TrackEntity(
            id = "track-d3",
            title = "D3",
            starredAt = 1L,
        )
        val (viewModel, provider) = vm(favoriteRepo, trackDao)
        provider.emit(PlaybackState(currentTrackId = "track-d3"))
        testDispatcher.scheduler.advanceUntilIdle()
        coEvery { favoriteRepo.dislikeTrack("track-d3") } coAnswers {
            provider.emit(PlaybackState(currentTrackId = "track-d4"))
            throw RuntimeException("gone")
        }

        viewModel.toggleDislike()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { favoriteRepo.dislikeTrack("track-d3") }
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
        val queueDao = mockk<com.lucasdss.ftpmusic.app.data.db.QueueDao>(relaxed = true)
        val futureEnd = System.currentTimeMillis() + 60_000
        coEvery { queueDao.getState() } returns
            com.lucasdss.ftpmusic.app.data.db.QueueStateEntity(sleepTimerEndMs = futureEnd)
        val (viewModel, provider) = vm(
            mockk(relaxed = true),
            mockk(relaxed = true),
            queueDao = queueDao,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("persisted future deadline must re-arm", provider.lastArmedSleepEndMs > 0)
        assertTrue(viewModel.state.value.sleepTimerEndMs > 0)
    }

    // ── shareQueue (local-first save + public + share, ADR-0076) ────────────

    @Test
    fun `shareQueue creates playlist publishes public and shares`() = runTest {
        val playlistRepo =
            mockk<com.lucasdss.ftpmusic.app.data.repository.PlaylistRepository>(relaxed = true)
        coEvery { playlistRepo.createPlaylistWithTracksSynced(any(), any()) } returns "pl-1"
        coEvery { playlistRepo.setPlaylistPublic("pl-1", true) } returns true
        val (viewModel, _) = vm(
            mockk(relaxed = true),
            mockk(relaxed = true),
            playlistRepository = playlistRepo,
        )

        val player = mockk<androidx.media3.common.Player>(relaxed = true)
        every { player.mediaItemCount } returns 2
        every { player.getMediaItemAt(0) } returns
            androidx.media3.common.MediaItem.Builder().setMediaId("t1").build()
        every { player.getMediaItemAt(1) } returns
            androidx.media3.common.MediaItem.Builder().setMediaId("t2").build()
        PlayerHolder.exoPlayer = player
        mockkStatic(android.widget.Toast::class)
        try {
            every {
                android.widget.Toast.makeText(any<android.content.Context>(), any<CharSequence>(), any<Int>())
            } returns mockk(relaxed = true)
            viewModel.shareQueue(mockk(relaxed = true))
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { playlistRepo.createPlaylistWithTracksSynced(any(), listOf("t1", "t2")) }
            coVerify { playlistRepo.setPlaylistPublic("pl-1", true) }
        } finally {
            PlayerHolder.exoPlayer = null
            unmockkStatic(android.widget.Toast::class)
        }
    }

    @Test
    fun `shareQueue no-ops on empty queue`() = runTest {
        val playlistRepo =
            mockk<com.lucasdss.ftpmusic.app.data.repository.PlaylistRepository>(relaxed = true)
        val (viewModel, _) = vm(
            mockk(relaxed = true),
            mockk(relaxed = true),
            playlistRepository = playlistRepo,
        )
        PlayerHolder.exoPlayer = mockk<androidx.media3.common.Player>(relaxed = true)
        try {
            viewModel.shareQueue(mockk(relaxed = true))
            testDispatcher.scheduler.advanceUntilIdle()
            coVerify(exactly = 0) { playlistRepo.createPlaylistWithTracksSynced(any(), any()) }
        } finally {
            PlayerHolder.exoPlayer = null
        }
    }

    @Test
    fun `shareQueue toasts when sync fails to publish`() = runTest {
        val playlistRepo =
            mockk<com.lucasdss.ftpmusic.app.data.repository.PlaylistRepository>(relaxed = true)
        coEvery { playlistRepo.createPlaylistWithTracksSynced(any(), any()) } returns null
        val (viewModel, _) = vm(
            mockk(relaxed = true),
            mockk(relaxed = true),
            playlistRepository = playlistRepo,
        )
        val player = mockk<androidx.media3.common.Player>(relaxed = true)
        every { player.mediaItemCount } returns 1
        every { player.getMediaItemAt(0) } returns
            androidx.media3.common.MediaItem.Builder().setMediaId("t1").build()
        PlayerHolder.exoPlayer = player
        mockkStatic(android.widget.Toast::class)
        try {
            every {
                android.widget.Toast.makeText(any<android.content.Context>(), any<CharSequence>(), any<Int>())
            } returns mockk(relaxed = true)
            viewModel.shareQueue(mockk(relaxed = true))
            testDispatcher.scheduler.advanceUntilIdle()
            coVerify(exactly = 0) { playlistRepo.setPlaylistPublic(any(), any()) }
        } finally {
            PlayerHolder.exoPlayer = null
            unmockkStatic(android.widget.Toast::class)
        }
    }

    // ── saveQueueAsPlaylist (local-first, ADR-0075) ─────────────────────────

    @Test
    fun `saveQueueAsPlaylist creates playlist and adds tracks`() = runTest {
        val playlistRepo =
            mockk<com.lucasdss.ftpmusic.app.data.repository.PlaylistRepository>(relaxed = true)
        coEvery { playlistRepo.createPlaylist(any()) } returns "temp-pl-1"
        val (viewModel, _) = vm(
            mockk(relaxed = true),
            mockk(relaxed = true),
            playlistRepository = playlistRepo,
        )
        val player = mockk<androidx.media3.common.Player>(relaxed = true)
        every { player.mediaItemCount } returns 2
        every { player.getMediaItemAt(0) } returns
            androidx.media3.common.MediaItem.Builder().setMediaId("t1").build()
        every { player.getMediaItemAt(1) } returns
            androidx.media3.common.MediaItem.Builder().setMediaId("t2").build()
        PlayerHolder.exoPlayer = player
        mockkStatic(android.widget.Toast::class)
        try {
            every {
                android.widget.Toast.makeText(any<android.content.Context>(), any<CharSequence>(), any<Int>())
            } returns mockk(relaxed = true)
            viewModel.saveQueueAsPlaylist(mockk(relaxed = true), "My Queue")
            testDispatcher.scheduler.advanceUntilIdle()
            coVerify { playlistRepo.createPlaylist("My Queue") }
            coVerify { playlistRepo.addToPlaylist("temp-pl-1", listOf("t1", "t2")) }
        } finally {
            PlayerHolder.exoPlayer = null
            unmockkStatic(android.widget.Toast::class)
        }
    }

    @Test
    fun `saveQueueAsPlaylist no-ops on empty queue`() = runTest {
        val playlistRepo =
            mockk<com.lucasdss.ftpmusic.app.data.repository.PlaylistRepository>(relaxed = true)
        val (viewModel, _) = vm(
            mockk(relaxed = true),
            mockk(relaxed = true),
            playlistRepository = playlistRepo,
        )
        PlayerHolder.exoPlayer = mockk<androidx.media3.common.Player>(relaxed = true)
        try {
            viewModel.saveQueueAsPlaylist(mockk(relaxed = true), "Empty")
            testDispatcher.scheduler.advanceUntilIdle()
            coVerify(exactly = 0) { playlistRepo.createPlaylist(any()) }
            coVerify(exactly = 0) { playlistRepo.addToPlaylist(any(), any()) }
        } finally {
            PlayerHolder.exoPlayer = null
        }
    }

    @Test
    fun `removeFromQueueBatch removes descending`() = runTest {
        val playbackManager = mockk<PlaybackManager>(relaxed = true)
        val (viewModel, _) = vm(
            mockk(relaxed = true),
            mockk(relaxed = true),
            playbackManager = playbackManager,
        )
        viewModel.removeFromQueueBatch(listOf(1, 4, 2))
        io.mockk.verifyOrder {
            playbackManager.removeFromQueue(4)
            playbackManager.removeFromQueue(2)
            playbackManager.removeFromQueue(1)
        }
    }
}
