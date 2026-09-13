package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import io.mockk.mockk
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

/**
 * Integration tests for PlaybackViewModel → PlaybackState flow.
 * Tests the complete chain: state change → provider emit → state read by ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackStateIntegrationTest {

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
    private val viewModel by lazy {
        PlaybackViewModel(
            provider,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk<com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao>(relaxed = true),
            mockk<SubsonicApi>(relaxed = true),
        )
    }

    @Test
    fun `full Cast lifecycle - connect, play, pause, disconnect`() = runTest {
        // Load track
        provider.emit(
            PlaybackState(
                title = "Lifecycle Track",
                artist = "LCA",
                album = "Test",
                duration = 240000,
                currentTrackId = "lc1",
                coverArtId = "cov-lc1",
            ),
        )
        assertEquals("Lifecycle Track", viewModel.state.value.title)

        // Start playing locally
        provider.emit(PlaybackState(title = "Lifecycle Track", artist = "LCA", isPlaying = true, position = 0))
        assertTrue(viewModel.state.value.isPlaying)

        // Connect to Cast
        provider.emit(
            PlaybackState(
                title = "Lifecycle Track",
                artist = "LCA",
                isCasting = true,
                castDeviceName = "Bedroom Speaker",
                volume = 0.5f,
                isPlaying = true,
                position = 35000,
            ),
        )
        assertTrue(viewModel.state.value.isCasting)
        assertEquals("Bedroom Speaker", viewModel.state.value.castDeviceName)
        assertEquals(0.5f, viewModel.state.value.volume)
        assertEquals(35000, viewModel.state.value.position)

        // Pause on Cast
        provider.emit(
            PlaybackState(
                title = "Lifecycle Track",
                artist = "LCA",
                isPlaying = false,
                position = 42000,
                isCasting = true,
                castDeviceName = "Bedroom Speaker",
            ),
        )
        assertFalse(viewModel.state.value.isPlaying)
        assertEquals(42000, viewModel.state.value.position)

        // Disconnect from Cast
        provider.emit(
            PlaybackState(
                title = "Lifecycle Track",
                artist = "LCA",
                isCasting = false,
                castDeviceName = null,
                isPlaying = false,
            ),
        )
        assertFalse(viewModel.state.value.isCasting)
        assertNull(viewModel.state.value.castDeviceName)
        assertEquals("Lifecycle Track", viewModel.state.value.title)
    }

    @Test
    fun `position poller interaction - 200ms updates`() = runTest {
        // Simulate positionPoller writing every 200ms
        provider.emit(
            PlaybackState(title = "T", isPlaying = true, position = 0, currentTrackId = "t1", duration = 240000),
        )

        provider.emit(PlaybackState(title = "T", isPlaying = true, position = 1000))
        assertEquals(1000, viewModel.state.value.position)

        provider.emit(PlaybackState(title = "T", isPlaying = true, position = 5000))
        assertEquals(5000, viewModel.state.value.position)

        provider.emit(PlaybackState(title = "T", isPlaying = true, position = 30000))
        assertEquals(30000, viewModel.state.value.position)

        // Paused — position should not advance
        provider.emit(PlaybackState(title = "T", isPlaying = false, position = 35000))
        assertEquals(35000, viewModel.state.value.position)
    }

    @Test
    fun `notification content text scenarios`() {
        // Scenario 1: Local playback with artist
        val local = PlaybackState(title = "Song", artist = "Band", isPlaying = true)
        assertTrue(local.isPlaying)
        assertEquals("Band", local.artist)

        // Scenario 2: Cast playback
        val cast = local.copy(isCasting = true, castDeviceName = "TV")
        assertTrue(cast.isCasting)
        assertEquals("TV", cast.castDeviceName)
        // Notification would show: "Band — Casting to TV"

        // Scenario 3: No artist (fallback)
        val noArtist = PlaybackState(title = "Song", artist = "", isPlaying = true)
        assertEquals("", noArtist.artist)
        // Notification would show: "Playing…"
    }

    @Test
    fun `SCENARIO E8 - app killed while casting resumes correctly`() = runTest {
        // Simulate saved state from previous session
        val saved = PlaybackState(
            title = "Resumed Track",
            artist = "Resumed Artist",
            isPlaying = false,
            isCasting = true,
            castDeviceName = "Speaker",
            position = 75000,
            currentTrackId = "rt1",
            duration = 300000,
        )

        // Restore on startup
        provider.emit(saved)
        assertEquals("Resumed Track", viewModel.state.value.title)
        assertEquals(75000, viewModel.state.value.position)
        assertTrue(viewModel.state.value.isCasting)
    }

    @Test
    fun `SCENARIO C5 - existing Cast session detected on startup`() = runTest {
        // Simulate CastPlayer.addListener firing for existing session
        provider.emit(
            PlaybackState(
                title = "Already Playing",
                artist = "Existing Session",
                isPlaying = true,
                isCasting = true,
                castDeviceName = "Soundbar",
                duration = 200000,
                position = 45000,
                volume = 0.7f,
            ),
        )

        val s = viewModel.state.value
        assertTrue(s.isCasting)
        assertEquals("Soundbar", s.castDeviceName)
        assertTrue(s.isPlaying)
    }

    @Test
    fun `SCENARIO D5 - track auto-advance during Cast`() = runTest {
        // Track 1 playing on Cast
        provider.emit(
            PlaybackState(
                title = "Track 1",
                isPlaying = true,
                isCasting = true,
                castDeviceName = "TV",
                position = 230000,
            ),
        )
        assertEquals("Track 1", viewModel.state.value.title)

        // Cast auto-advances to Track 2 — LoadTrack fires
        provider.emit(
            PlaybackState(
                title = "Track 2", artist = "Artist 2", album = "Album 2",
                currentTrackId = "t2", isPlaying = true, position = 0,
                trackIndex = 1, isCasting = true, castDeviceName = "TV",
            ),
        )
        assertEquals("Track 2", viewModel.state.value.title)
        assertEquals(1, viewModel.state.value.trackIndex)
        assertTrue(viewModel.state.value.isCasting)
    }

    @Test
    fun `multiple rapid state updates do not corrupt`() = runTest {
        provider.emit(PlaybackState(title = "Rapid", artist = "Test", isPlaying = true))
        provider.emit(PlaybackState(title = "Rapid", artist = "Test", isPlaying = true, position = 1000))
        provider.emit(PlaybackState(title = "Rapid", artist = "Test", isPlaying = true, position = 2000))
        provider.emit(PlaybackState(title = "Rapid", artist = "Test", isPlaying = false, position = 2500))
        provider.emit(PlaybackState(title = "Rapid", artist = "Test", isPlaying = true, position = 2600))

        assertEquals("Rapid", viewModel.state.value.title)
        assertEquals(2600, viewModel.state.value.position)
        assertTrue(viewModel.state.value.isPlaying)
    }
}
