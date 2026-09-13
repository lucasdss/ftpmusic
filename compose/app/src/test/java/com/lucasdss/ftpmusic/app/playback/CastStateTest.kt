package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CastStateTest {

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
    fun `isCasting defaults to false`() {
        val state = PlaybackState()
        assertFalse(state.isCasting)
    }

    @Test
    fun `castDeviceName defaults to null`() {
        val state = PlaybackState()
        assertNull(state.castDeviceName)
    }

    @Test
    fun `copy sets isCasting and castDeviceName correctly`() {
        val state = PlaybackState().copy(
            isCasting = true,
            castDeviceName = "Living Room TV",
        )
        assertTrue(state.isCasting)
        assertEquals("Living Room TV", state.castDeviceName)
    }

    @Test
    fun `copy preserves other fields when setting cast fields`() {
        val state = PlaybackState(
            title = "Test Track",
            artist = "Test Artist",
            isPlaying = true,
            position = 42_000L,
        ).copy(isCasting = true, castDeviceName = "Kitchen Speaker")

        assertEquals("Test Track", state.title)
        assertEquals("Test Artist", state.artist)
        assertTrue(state.isPlaying)
        assertEquals(42_000L, state.position)
        assertTrue(state.isCasting)
        assertEquals("Kitchen Speaker", state.castDeviceName)
    }

    @Test
    fun `copy clearing cast resets to defaults`() {
        val state = PlaybackState(
            isCasting = true,
            castDeviceName = "Living Room TV",
        ).copy(isCasting = false, castDeviceName = null)

        assertFalse(state.isCasting)
        assertNull(state.castDeviceName)
    }

    @Test
    fun `isCasting true with null castDeviceName represents session-init state`() {
        // Auto-heal edge case: session is active but device name hasn't propagated yet.
        // Old code blindly reset to isCasting=false. New code verifies with Cast SDK first.
        val state = PlaybackState(isCasting = true, castDeviceName = null)

        assertTrue("isCasting should be true (session exists, name pending)", state.isCasting)
        assertNull("castDeviceName should be null during initialization window", state.castDeviceName)
    }

    // ── PlaybackViewModel state emission tests (Cast) ────────────────────

    @Test
    fun `CastStateUpdate sets isPlaying and position during Cast`() = runTest {
        val provider = FakePlaybackStateProvider()
        val viewModel =
            PlaybackViewModel(
                provider,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk<com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao>(relaxed = true),
                mockk<SubsonicApi>(relaxed = true),
            )
        provider.emit(
            PlaybackState(
                title = "T",
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = true,
                position = 50000,
                duration = 240000,
            ),
        )
        val state = viewModel.state.first { it.isCasting && it.isPlaying }
        assertTrue(state.isPlaying)
        assertEquals(50000L, state.position)
        assertTrue(state.isCasting)
    }

    @Test
    fun `CastStateUpdate does not affect non-Cast fields`() = runTest {
        val provider = FakePlaybackStateProvider()
        val viewModel =
            PlaybackViewModel(
                provider,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk<com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao>(relaxed = true),
                mockk<SubsonicApi>(relaxed = true),
            )
        provider.emit(
            PlaybackState(
                title = "Track Title",
                artist = "Artist Name",
                coverArtId = "cov1",
                duration = 240000,
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = true,
                position = 30000,
            ),
        )
        val state = viewModel.state.first { it.isCasting }
        assertEquals("Track Title", state.title)
        assertEquals("Artist Name", state.artist)
        assertEquals("cov1", state.coverArtId)
        assertEquals(240000L, state.duration)
    }

    @Test
    fun `CastStateUpdate pause reflects correctly`() = runTest {
        val provider = FakePlaybackStateProvider()
        val viewModel =
            PlaybackViewModel(
                provider,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk<com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao>(relaxed = true),
                mockk<SubsonicApi>(relaxed = true),
            )
        provider.emit(
            PlaybackState(
                title = "T",
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = true,
                position = 10000,
                duration = 240000,
            ),
        )
        val playing = viewModel.state.first { it.isPlaying }
        assertTrue(playing.isPlaying)

        // Cast device pauses
        provider.emit(
            PlaybackState(
                title = "T",
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = false,
                position = 15000,
                duration = 240000,
            ),
        )
        val paused = viewModel.state.first { !it.isPlaying }
        assertFalse(paused.isPlaying)
        assertEquals(15000L, paused.position)
    }

    @Test
    fun `BufferReady is NOT suppressed during Cast with CastPlayer`() = runTest {
        val provider = FakePlaybackStateProvider()
        val viewModel =
            PlaybackViewModel(
                provider,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk<com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao>(relaxed = true),
                mockk<SubsonicApi>(relaxed = true),
            )
        // CastPlayer sends BufferReady through normal Player callbacks — no suppression needed
        provider.emit(
            PlaybackState(title = "T", isCasting = true, castDeviceName = "TV", isPlaying = true, duration = 240000),
        )
        val state = viewModel.state.first { it.isPlaying }
        assertTrue("BufferReady updates isPlaying during Cast (CastPlayer sends accurate events)", state.isPlaying)
    }

    @Test
    fun `PauseRequested is NOT suppressed during Cast with CastPlayer`() = runTest {
        val provider = FakePlaybackStateProvider()
        val viewModel =
            PlaybackViewModel(
                provider,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk<com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao>(relaxed = true),
                mockk<SubsonicApi>(relaxed = true),
            )
        provider.emit(
            PlaybackState(
                title = "T",
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = true,
                position = 5000,
                duration = 240000,
            ),
        )
        val playing = viewModel.state.first { it.isPlaying }
        assertTrue(playing.isPlaying)
        // CastPlayer sends PauseRequested through normal Player callbacks — no suppression needed
        provider.emit(
            PlaybackState(
                title = "T",
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = false,
                position = 5000,
                duration = 240000,
            ),
        )
        val paused = viewModel.state.first { !it.isPlaying }
        assertFalse("PauseRequested updates isPlaying during Cast (CastPlayer sends accurate events)", paused.isPlaying)
    }

    // ── Push-based progress listener tests (1s interval) ─────────────────

    @Test
    fun `rapid CastStateUpdate simulates 1s progress listener`() = runTest {
        val provider = FakePlaybackStateProvider()
        val viewModel =
            PlaybackViewModel(
                provider,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk<com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao>(relaxed = true),
                mockk<SubsonicApi>(relaxed = true),
            )
        provider.emit(
            PlaybackState(
                title = "T",
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = true,
                position = 0,
                duration = 240000,
            ),
        )
        // Simulate 1s progress listener: 5 updates over 5 seconds
        provider.emit(
            PlaybackState(
                title = "T",
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = true,
                position = 1000,
                duration = 240000,
            ),
        )
        provider.emit(
            PlaybackState(
                title = "T",
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = true,
                position = 2000,
                duration = 240000,
            ),
        )
        provider.emit(
            PlaybackState(
                title = "T",
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = true,
                position = 3000,
                duration = 240000,
            ),
        )
        provider.emit(
            PlaybackState(
                title = "T",
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = true,
                position = 5000,
                duration = 240000,
            ),
        )
        val state = viewModel.state.first { it.position >= 5000L }
        assertEquals(5000L, state.position)
        assertTrue(state.isPlaying)
    }

    @Test
    fun `progress listener pauses and resumes correctly`() = runTest {
        val provider = FakePlaybackStateProvider()
        val viewModel =
            PlaybackViewModel(
                provider,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk<com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao>(relaxed = true),
                mockk<SubsonicApi>(relaxed = true),
            )
        provider.emit(
            PlaybackState(
                title = "T",
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = true,
                position = 30000,
                duration = 240000,
            ),
        )
        val playing = viewModel.state.first { it.position >= 30000L }
        assertEquals(30000L, playing.position)
        assertTrue(playing.isPlaying)
        // Cast device pauses — listener reports isPlaying=false
        provider.emit(
            PlaybackState(
                title = "T",
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = false,
                position = 35000,
                duration = 240000,
            ),
        )
        val paused = viewModel.state.first { !it.isPlaying }
        assertFalse(paused.isPlaying)
        assertEquals(35000L, paused.position)
        // Resume
        provider.emit(
            PlaybackState(
                title = "T",
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = true,
                position = 35000,
                duration = 240000,
            ),
        )
        val resumed = viewModel.state.first { it.position == 35000L && it.isPlaying }
        assertTrue(resumed.isPlaying)
        assertEquals(35000L, resumed.position)
    }

    @Test
    fun `progress listener position monotonically advances across track boundary`() = runTest {
        val provider = FakePlaybackStateProvider()
        val viewModel =
            PlaybackViewModel(
                provider,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk<com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao>(relaxed = true),
                mockk<SubsonicApi>(relaxed = true),
            )
        // Track 1 nearing end
        provider.emit(
            PlaybackState(
                title = "Track 1",
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = true,
                position = 235000,
                duration = 240000,
            ),
        )
        val track1 = viewModel.state.first { it.position >= 235000L }
        assertEquals(235000L, track1.position)
        // Track transition: Cast device moves to next track, position resets to 0
        provider.emit(
            PlaybackState(
                title = "Track 2",
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = true,
                position = 0,
                duration = 200000,
            ),
        )
        val track2 = viewModel.state.first { it.title == "Track 2" }
        assertEquals(0L, track2.position)
        assertEquals("Track 2", track2.title)
        // Progress resumes from 0
        provider.emit(
            PlaybackState(
                title = "Track 2",
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = true,
                position = 1500,
                duration = 200000,
            ),
        )
        val progressed = viewModel.state.first { it.position >= 1500L }
        assertEquals(1500L, progressed.position)
    }

    @Test
    fun `progress listener updates do not clobber non-Cast fields`() = runTest {
        val provider = FakePlaybackStateProvider()
        val viewModel =
            PlaybackViewModel(
                provider,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk<com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao>(relaxed = true),
                mockk<SubsonicApi>(relaxed = true),
            )
        provider.emit(
            PlaybackState(
                title = "Immutable",
                artist = "Artist",
                album = "Album",
                artistId = "aId",
                albumId = "arId",
                coverArtId = "cov99",
                duration = 300000,
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = true,
                position = 0,
            ),
        )
        // 10 rapid progress updates — only position/isPlaying should change
        for (i in 1..10) {
            provider.emit(
                PlaybackState(
                    title = "Immutable",
                    artist = "Artist",
                    album = "Album",
                    artistId = "aId",
                    albumId = "arId",
                    coverArtId = "cov99",
                    duration = 300000,
                    isCasting = true,
                    castDeviceName = "TV",
                    isPlaying = true,
                    position =
                        i * 5000L,
                ),
            )
        }
        val state = viewModel.state.first { it.position >= 50000L }
        assertEquals(50000L, state.position)
        assertEquals("Immutable", state.title)
        assertEquals("Artist", state.artist)
        assertEquals("Album", state.album)
        assertEquals("cov99", state.coverArtId)
        assertEquals(300000L, state.duration)
        assertTrue(state.isCasting)
    }
}
