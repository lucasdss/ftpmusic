package com.lucasdss.ftpmusic.app.ui.settings

import com.lucasdss.ftpmusic.app.data.cache.CacheService
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.playback.PlaybackManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var trackDao: TrackDao
    private lateinit var cacheService: CacheService
    private lateinit var playbackManager: PlaybackManager

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        trackDao = mockk(relaxed = true)
        cacheService = mockk(relaxed = true)
        playbackManager = mockk(relaxed = true)
        coEvery { trackDao.getDownloadedPaged(any(), any()) } returns emptyList()
        every { cacheService.isStoredInCache(any()) } returns true
        coEvery { cacheService.healStaleCachePath(any()) } returns false
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun vm() = DownloadsViewModel(
        trackDao = trackDao,
        cacheService = cacheService,
        playbackManager = playbackManager,
        authHelper = SubsonicAuthHelper(),
        storage = mockk(relaxed = true),
    )

    @Test
    fun `refresh loads downloaded page`() = runTest {
        coEvery { trackDao.getDownloadedPaged(50, 0) } returns listOf(
            TrackEntity(id = "d1", title = "Offline", isDownloaded = true, cachedFilePath = "/x"),
        )
        val viewModel = vm()
        assertEquals(1, viewModel.state.value.tracks.size)
        assertEquals("d1", viewModel.state.value.tracks[0].id)
    }

    @Test
    fun `heal marks stale ids`() = runTest {
        coEvery { trackDao.getDownloadedPaged(50, 0) } returns listOf(
            TrackEntity(id = "d1", title = "Gone", isDownloaded = true, cachedFilePath = "/missing"),
        )
        coEvery { cacheService.healStaleCachePath("d1") } returns true
        val viewModel = vm()
        assertTrue(viewModel.state.value.staleIds.contains("d1"))
        assertEquals(null, viewModel.state.value.tracks[0].cachedFilePath)
    }

    @Test
    fun `remove delegates to cacheService`() = runTest {
        coEvery { trackDao.getDownloadedPaged(50, 0) } returns listOf(
            TrackEntity(id = "d1", title = "Offline", isDownloaded = true),
        )
        val viewModel = vm()
        viewModel.remove("d1")
        coVerify { cacheService.removeDownload("d1") }
        assertTrue(viewModel.state.value.tracks.none { it.id == "d1" })
    }

    @Test
    fun `playAt starts album context`() = runTest {
        coEvery { trackDao.getDownloadedPaged(50, 0) } returns listOf(
            TrackEntity(id = "d1", title = "A", isDownloaded = true),
            TrackEntity(id = "d2", title = "B", isDownloaded = true),
        )
        val viewModel = vm()
        viewModel.playAt(1)
        verify {
            playbackManager.playAlbum(
                match { it.size == 2 && it[1].id == "d2" },
                any(),
                startIndex = 1,
            )
        }
    }

    @Test
    fun `clearAll empties state`() = runTest {
        coEvery { trackDao.getDownloadedPaged(50, 0) } returns listOf(
            TrackEntity(id = "d1", title = "A", isDownloaded = true),
        )
        val viewModel = vm()
        viewModel.clearAll()
        coVerify { cacheService.clearDownloads() }
        assertTrue(viewModel.state.value.tracks.isEmpty())
    }

    @Test
    fun `fetch throw clears loading`() = runTest {
        coEvery { trackDao.getDownloadedPaged(any(), any()) } throws RuntimeException("db down")
        val viewModel = vm()
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun `clearAll discards late page`() = runTest {
        val latch = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { trackDao.getDownloadedPaged(50, 0) } coAnswers {
            latch.await()
            listOf(TrackEntity(id = "late", title = "Late", isDownloaded = true))
        }
        // Init refresh will hang on latch — start VM then clearAll first
        val viewModel = DownloadsViewModel(
            trackDao = trackDao,
            cacheService = cacheService,
            playbackManager = playbackManager,
            authHelper = SubsonicAuthHelper(),
            storage = mockk(relaxed = true),
        )
        viewModel.clearAll()
        latch.complete(Unit)
        // Allow hung refresh to finish
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.tracks.isEmpty())
    }

    @Test
    fun `downloadsRowStatus hides glyph when stale`() {
        assertEquals("downloaded", downloadsRowStatus(stale = false))
        assertEquals("none", downloadsRowStatus(stale = true))
    }
}
