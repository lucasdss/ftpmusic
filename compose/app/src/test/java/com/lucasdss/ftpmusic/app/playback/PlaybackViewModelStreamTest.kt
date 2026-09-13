package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackViewModelStreamTest {

    private val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `buildStreamUrl should return direct URL`() {
        val provider: PlaybackStateProvider = mockk(relaxed = true)
        val playbackManager: PlaybackManager = mockk(relaxed = true)
        val favoriteRepo: FavoriteRepository = mockk(relaxed = true)
        val storage: SecureStorage = mockk(relaxed = true)

        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"

        val viewModel =
            PlaybackViewModel(
                provider,
                playbackManager,
                favoriteRepo,
                storage,
                mockk(relaxed = true),
                mockk<com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao>(relaxed = true),
                mockk<SubsonicApi>(relaxed = true),
            )
        val url = viewModel.buildStreamUrl("track123")

        assertTrue(url.isNotEmpty())
    }

    @Test
    fun `playSingleTrack should delegate to playbackManager`() {
        val provider: PlaybackStateProvider = mockk(relaxed = true)
        val playbackManager: PlaybackManager = mockk(relaxed = true)
        val favoriteRepo: FavoriteRepository = mockk(relaxed = true)
        val storage: SecureStorage = mockk(relaxed = true)

        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        every { playbackManager.playSingleTrack(any(), any()) } just Runs

        val viewModel =
            PlaybackViewModel(
                provider,
                playbackManager,
                favoriteRepo,
                storage,
                mockk(relaxed = true),
                mockk<com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao>(relaxed = true),
                mockk<SubsonicApi>(relaxed = true),
            )
        val track = Track(id = "t1", title = "Test", artist = "Artist")
        viewModel.playSingleTrack(track, "http://example.com/stream")

        verify { playbackManager.playSingleTrack(track, "http://example.com/stream") }
    }

    @Test
    fun `buildStreamUrl should handle empty credentials gracefully`() {
        val provider: PlaybackStateProvider = mockk(relaxed = true)
        val playbackManager: PlaybackManager = mockk(relaxed = true)
        val favoriteRepo: FavoriteRepository = mockk(relaxed = true)
        val storage: SecureStorage = mockk(relaxed = true)

        every { storage.get(SecureStorage.KEY_USERNAME) } returns ""
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns ""

        val viewModel =
            PlaybackViewModel(
                provider,
                playbackManager,
                favoriteRepo,
                storage,
                mockk(relaxed = true),
                mockk<com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao>(relaxed = true),
                mockk<SubsonicApi>(relaxed = true),
            )
        val url = viewModel.buildStreamUrl("t1")

        assertNotNull(url)
        assertTrue(url.isNotEmpty())
    }
}
