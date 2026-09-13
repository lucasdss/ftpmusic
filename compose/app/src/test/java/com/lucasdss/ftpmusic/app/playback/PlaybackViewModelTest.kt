package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.repository.FavoriteRepository
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackViewModelTest {

    private val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    private val provider = FakePlaybackStateProvider()
    private val playbackManager = mockk<PlaybackManager>(relaxed = true)
    private val favoriteRepo = mockk<FavoriteRepository>(relaxed = true)
    private val viewModel by lazy {
        PlaybackViewModel(
            provider,
            playbackManager,
            favoriteRepo,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk<com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao>(relaxed = true),
            mockk<SubsonicApi>(relaxed = true),
        )
    }

    @Test
    fun `exposes title from provider`() = runTest {
        provider.emit(PlaybackState(title = "Test Song"))
        val state = viewModel.state.first { it.title == "Test Song" }
        assertEquals("Test Song", state.title)
        assertNull(state.artist)
    }

    @Test
    fun `exposes artist and album from provider`() = runTest {
        provider.emit(PlaybackState(title = "S1", artist = "Artist1", album = "Album1"))
        val state = viewModel.state.first { it.title == "S1" }
        assertEquals("Artist1", state.artist)
        assertEquals("Album1", state.album)
    }

    @Test
    fun `exposes isPlaying from provider`() = runTest {
        provider.emit(PlaybackState(isPlaying = true))
        val state = viewModel.state.first { it.isPlaying }
        assertTrue(state.isPlaying)
    }

    @Test
    fun `exposes position and duration from provider`() = runTest {
        provider.emit(PlaybackState(position = 30000L, duration = 180000L))
        val state = viewModel.state.first { it.duration > 0 }
        assertEquals(30000L, state.position)
        assertEquals(180000L, state.duration)
    }

    @Test
    fun `exposes coverArtId from provider`() = runTest {
        provider.emit(PlaybackState(coverArtId = "ca-456"))
        val state = viewModel.state.first { it.coverArtId != null }
        assertEquals("ca-456", state.coverArtId)
    }

    @Test
    fun `isVisible is true when title is non-null`() = runTest {
        provider.emit(PlaybackState(title = "Something"))
        val state = viewModel.state.first { it.isVisible }
        assertTrue(state.isVisible)
    }

    @Test
    fun `isVisible is false when title is null`() = runTest {
        provider.emit(PlaybackState(title = null))
        val state = viewModel.state.first { !it.isVisible }
        assertFalse(state.isVisible)
    }

    @Test
    fun `playPause delegates to provider`() {
        viewModel.playPause()
        assertTrue(provider.playPauseCalled)
    }

    @Test
    fun `skipNext delegates to provider`() {
        viewModel.skipNext()
        assertTrue(provider.skipNextCalled)
    }

    @Test
    fun `skipPrev delegates to provider`() {
        viewModel.skipPrev()
        assertTrue(provider.skipPrevCalled)
    }

    @Test
    fun `seekTo delegates to provider`() {
        viewModel.seekTo(0.5f)
        assertEquals(0.5f, provider.lastSeekFraction, 0.001f)
    }

    @Test
    fun `state combines all fields from provider`() = runTest {
        provider.emit(
            PlaybackState(
                title = "Full Track",
                artist = "Full Artist",
                album = "Full Album",
                coverArtId = "ca-full",
                isPlaying = true,
                position = 60000L,
                duration = 240000L,
            ),
        )
        val state = viewModel.state.first { it.title == "Full Track" }
        assertEquals("Full Artist", state.artist)
        assertEquals("Full Album", state.album)
        assertEquals("ca-full", state.coverArtId)
        assertTrue(state.isPlaying)
        assertEquals(60000L, state.position)
        assertEquals(240000L, state.duration)
        assertTrue(state.isVisible)
    }

    @Test
    fun `syncExtraState pushes isStarred sleepTimer and downloadedTrackIds to provider`() = runTest {
        viewModel.startSleepTimer(30)
        assertTrue(provider.updateExtraStateCalled)

        provider.updateExtraStateCalled = false
        viewModel.cancelSleepTimer()
        assertTrue(provider.updateExtraStateCalled)
    }

    @Test
    fun `toggleLike calls syncExtraState`() = runTest {
        provider.emit(PlaybackState(currentTrackId = "t1", title = "Test"))
        viewModel.toggleLike()
        assertTrue(provider.updateExtraStateCalled)
    }
}
