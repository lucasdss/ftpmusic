package com.lucasdss.ftpmusic.app.ui.library

import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.GenreDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.repository.WaveformRepository
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.playback.PlaybackManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LibraryCoverArtTest {

    private val testDispatcher = StandardTestDispatcher()
    private val api: SubsonicApi = mockk()
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val genreDao: GenreDao = mockk(relaxed = true)
    private val playlistDao: PlaylistDao = mockk(relaxed = true)
    private val downloadManager: com.lucasdss.ftpmusic.app.data.cache.DownloadManager = mockk(relaxed = true)
    private val storage: SecureStorage = mockk(relaxed = true)
    private val playbackManager: PlaybackManager = mockk(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        DynamicBaseUrl.url = "" // don't leak the static into other classes
    }

    @Test
    fun `cover art URL uses dynamic base URL`() = runTest {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "testuser"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "testpass"
        coEvery { api.getAlbumList2("newest", any(), any(), any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "albumList2" to mapOf(
                    "album" to listOf(
                        mapOf("id" to "al-1", "name" to "Album", "artist" to "Artist", "coverArt" to "ca-123"),
                    ),
                ),
            ),
        )
        coEvery { api.getArtists(any()) } returns emptyMap()
        coEvery { api.getAlbumList2("random", any(), any(), any()) } returns emptyMap()
        DynamicBaseUrl.url = "https://music.example.com"

        val vm =
            LibraryViewModel(
                api, trackDao, genreDao,
                mockk(
                    relaxed = true,
                ),
                playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(relaxed = true),
                mockk<com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository>(relaxed = true),
            )
        vm.loadAlbums()
        val state = vm.state.first { it.albums.isNotEmpty() }
        assertEquals(1, state.albums.size)
        assertEquals("ca-123", state.albums[0].coverArt)
    }

    @Test
    fun `loadAlbums returns empty on error`() = runTest {
        every { storage.get(any()) } returns "x"
        coEvery { api.getAlbumList2(any(), any(), any(), any()) } throws RuntimeException("fail")
        coEvery { api.getArtists(any()) } returns emptyMap()
        coEvery { api.getAlbumList2("random", any(), any(), any()) } returns emptyMap()

        val vm =
            LibraryViewModel(
                api, trackDao, genreDao,
                mockk(
                    relaxed = true,
                ),
                playlistDao,
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                mockk(
                    relaxed = true,
                ),
                downloadManager, storage, playbackManager,
                mockk<OfflineModeManager>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository>(
                    relaxed = true,
                ),
                mockk<com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao>(relaxed = true),
                mockk<com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository>(relaxed = true),
            )
        vm.loadAlbums()
        val state = vm.state.first { !it.isLoading }
        assertTrue(state.albums.isEmpty())
    }
}
