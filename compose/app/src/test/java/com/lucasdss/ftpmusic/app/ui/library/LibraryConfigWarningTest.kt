package com.lucasdss.ftpmusic.app.ui.library

import com.lucasdss.ftpmusic.app.data.cache.DownloadManager
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.GenreMixDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.playback.PlaybackManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Loopback (stale phone-proxy) server config must surface as a warning, and
 * album downloads must never enqueue garbage URLs against a blank base.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryConfigWarningTest {

    private val api: SubsonicApi = mockk()
    private val trackDao: TrackDao = mockk()
    private val genreDao = mockk<com.lucasdss.ftpmusic.app.data.db.GenreDao>()
    private val genreMixDao: GenreMixDao = mockk(relaxed = true)
    private val playlistDao: PlaylistDao = mockk()
    private val metadataDao: CachedMetadataDao = mockk(relaxed = true)
    private val downloadManager: DownloadManager = mockk(relaxed = true)
    private val storage: SecureStorage = mockk(relaxed = true)
    private val playbackManager: PlaybackManager = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { storage.get(SecureStorage.KEY_USERNAME) } returns null
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns null
        DynamicBaseUrl.url = ""
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        DynamicBaseUrl.url = ""
    }

    private fun newViewModel(): LibraryViewModel = LibraryViewModel(
        api, trackDao, genreDao, genreMixDao, playlistDao, mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), metadataDao, mockk(relaxed = true), downloadManager, storage, playbackManager,
        mockk<OfflineModeManager>(relaxed = true), mockk<FavoriteRepository>(relaxed = true),
        mockk<RadioFavoriteDao>(relaxed = true),
        mockk<com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository>(relaxed = true),
        ioDispatcher = testDispatcher,
    )

    @Test
    fun `loopback url sets configWarning`() = runTest(testDispatcher) {
        viewModel = newViewModel()
        DynamicBaseUrl.url = "http://127.0.0.1:9999/"
        viewModel.refreshConfigWarning()
        assertTrue(viewModel.state.value.configWarning)
    }

    @Test
    fun `real server url clears configWarning`() = runTest(testDispatcher) {
        viewModel = newViewModel()
        DynamicBaseUrl.url = "https://music.example.com"
        viewModel.refreshConfigWarning()
        assertFalse(viewModel.state.value.configWarning)
    }

    @Test
    fun `blank url does not warn`() = runTest(testDispatcher) {
        viewModel = newViewModel()
        DynamicBaseUrl.url = ""
        viewModel.refreshConfigWarning()
        assertFalse(viewModel.state.value.configWarning)
    }

    @Test
    fun `download action with unconfigured server enqueues nothing`() = runTest(testDispatcher) {
        viewModel = newViewModel()
        DynamicBaseUrl.url = ""
        coEvery { trackDao.getTracksByAlbumIds(listOf("al1")) } returns listOf(
            TrackEntity(id = "t1", title = "T1", artist = "A", albumId = "al1", durationSeconds = 100),
        )
        viewModel.albumAction("al1", "download")
        advanceUntilIdle()
        coVerify(exactly = 0) { downloadManager.enqueue(any(), any(), any()) }
    }

    @Test
    fun `download action with configured server enqueues real stream urls`() = runTest(testDispatcher) {
        viewModel = newViewModel()
        DynamicBaseUrl.url = "https://music.example.com"
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "lucas"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pw"
        coEvery { trackDao.getTracksByAlbumIds(listOf("al1")) } returns listOf(
            TrackEntity(id = "t1", title = "T1", artist = "A", albumId = "al1", durationSeconds = 100),
        )
        viewModel.albumAction("al1", "download")
        advanceUntilIdle()
        coVerify(exactly = 1) {
            downloadManager.enqueue("t1", match { it.startsWith("https://music.example.com/rest/stream?id=t1") }, 1)
        }
    }
}
