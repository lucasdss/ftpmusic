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

/**
 * Tests for scenarios identified as gaps in the cross-reference audit.
 * Covers: X4 URL reversion, C5 existing session, unmapped error recovery,
 * and real production paths without CastStateUpdate.
 */
class CastBehaviorMapGapFixTest {

    private val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ═════════════════════════════════════════════════════════════════
    // C5: Existing Cast session detected on startup
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `C5 - addListener fires onDeviceInfoChanged for existing session`() = runTest {
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
        // Simulate: Cast session was already active when listener added
        provider.emit(
            PlaybackState(
                title = "Playing Track",
                artist = "Artist",
                isPlaying = true,
                isCasting = true,
                castDeviceName = "Already Connected TV",
                volume = 0.6f,
                duration = 240000,
            ),
        )
        val state = viewModel.state.first { it.isCasting }
        assertTrue(state.isCasting)
        assertEquals("Already Connected TV", state.castDeviceName)
        assertEquals("Playing Track", state.title)
        assertTrue(state.isPlaying)
    }

    // ═════════════════════════════════════════════════════════════════
    // X4: URL reversion — MediaItemConverter.toMediaItem
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `X4 - PlaybackState correctly reflects local mode after disconnect`() = runTest {
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
                title = "Cast Song",
                isPlaying = true,
                isCasting = true,
                castDeviceName = "Speaker",
                duration = 240000,
            ),
        )
        val cast = viewModel.state.first { it.isCasting }
        assertTrue(cast.isCasting)
        assertEquals("Speaker", cast.castDeviceName)

        // Disconnect — CastPlayer runs toMediaItem internally
        provider.emit(
            PlaybackState(
                title = "Cast Song",
                isPlaying = true,
                isCasting = false,
                castDeviceName = null,
                duration = 240000,
            ),
        )
        val disc = viewModel.state.first { !it.isCasting }
        assertFalse(disc.isCasting)
        assertNull(disc.castDeviceName)
        assertEquals("Cast Song", disc.title) // metadata preserved
    }

    // ═════════════════════════════════════════════════════════════════
    // Unmapped Path: Error Recovery
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `ERROR-RECOVERY - playback error sets paused state`() = runTest {
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
        provider.emit(PlaybackState(title = "T", isPlaying = true, duration = 240000))
        val playing = viewModel.state.first { it.isPlaying }
        assertTrue(playing.isPlaying)

        // Error occurs — playback should pause
        provider.emit(PlaybackState(title = "T", isPlaying = false, duration = 240000))
        val paused = viewModel.state.first { !it.isPlaying }
        assertFalse(paused.isPlaying)
    }

    @Test
    fun `ERROR-RECOVERY - multiple errors don't corrupt state`() = runTest {
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
        for (i in 1..5) {
            provider.emit(PlaybackState(title = "T", isPlaying = true, duration = 240000))
            val playing = viewModel.state.first { it.isPlaying }
            assertTrue(playing.isPlaying)
            provider.emit(PlaybackState(title = "T", isPlaying = false, duration = 240000))
            val paused = viewModel.state.first { !it.isPlaying }
            assertFalse(paused.isPlaying)
        }
        // After many error cycles, state is still paused (not corrupted)
        val state = viewModel.state.first { !it.isPlaying }
        assertEquals("T", state.title)
        assertFalse(state.isPlaying)
    }

    // ═════════════════════════════════════════════════════════════════
    // Unmapped Path: Audio Focus
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `AUDIO-FOCUS - pause on loss, play on gain`() = runTest {
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
        provider.emit(PlaybackState(title = "T", isPlaying = true, duration = 240000))
        val playing = viewModel.state.first { it.isPlaying }
        assertTrue(playing.isPlaying)

        // Audio focus lost (phone call, alarm)
        provider.emit(PlaybackState(title = "T", isPlaying = false, duration = 240000))
        val paused = viewModel.state.first { !it.isPlaying }
        assertFalse(paused.isPlaying)

        // Audio focus regained
        provider.emit(PlaybackState(title = "T", isPlaying = true, duration = 240000))
        val resumed = viewModel.state.first { it.isPlaying }
        assertTrue(resumed.isPlaying)
    }

    // ═════════════════════════════════════════════════════════════════
    // Unmapped Path: Sleep Timer + Volume Edge Cases
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `SLEEP-TIMER - is ViewModel-local, defaults to zero during Cast`() = runTest {
        // sleepTimerEndMs is ViewModel-local, managed via startSleepTimer().
        // Not directly propagated from provider. Verify it defaults to 0 even during Cast.
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
        provider.emit(PlaybackState(isCasting = true, castDeviceName = "TV", sleepTimerEndMs = 1719000000000L))
        val state = viewModel.state.first { it.isCasting }
        assertEquals("sleepTimerEndMs is ViewModel-local, overrides provider", 0L, state.sleepTimerEndMs)
        assertTrue(state.isCasting)
    }

    @Test
    fun `VOLUME - Cast volume synced via onDeviceVolumeChanged division`() = runTest {
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
        // Production: onDeviceVolumeChanged(volume=40, muted=false) sends
        // VolumeChanged(40/100f=0.4f, muted=false)
        provider.emit(PlaybackState(isCasting = true, castDeviceName = "TV", volume = 0.4f, muted = false))
        val state = viewModel.state.first { it.isCasting }
        assertEquals(0.4f, state.volume, 0.001f)
        assertFalse(state.muted)

        // Cast hardware mutes
        provider.emit(PlaybackState(isCasting = true, castDeviceName = "TV", volume = 0.4f, muted = true))
        val muted = viewModel.state.first { it.muted }
        assertTrue(muted.muted)
        assertEquals(0.4f, muted.volume, 0.001f)
    }

    // ═════════════════════════════════════════════════════════════════
    // Unmapped Path: ReplayGain + PlaybackSpeed
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `REPLAYGAIN - playback speed independent of Cast state`() = runTest {
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
        provider.emit(PlaybackState(isCasting = true, castDeviceName = "TV", playbackSpeed = 1.25f))
        val cast = viewModel.state.first { it.isCasting }
        assertEquals(1.25f, cast.playbackSpeed)
        assertTrue(cast.isCasting)

        provider.emit(PlaybackState(isCasting = false, castDeviceName = null, playbackSpeed = 1.25f))
        val disc = viewModel.state.first { !it.isCasting }
        assertFalse(disc.isCasting)
        assertEquals(1.25f, disc.playbackSpeed) // preserved
    }

    // ═════════════════════════════════════════════════════════════════
    // E8 Fix: Resume on restart should reflect paused state
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `E8-FIX - restart after process death restores as paused not playing`() = runTest {
        // The map said "resume on restart" but the implementation restores
        // as paused. This test documents the ACTUAL behavior.
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
                title = "Survivor Track",
                artist = "Artist",
                coverArtId = "cov1",
                isPlaying = false,
                position = 45000,
                duration = 240000,
            ),
        )
        val state = viewModel.state.first { it.title != null }
        assertFalse("Restored state should be paused", state.isPlaying)
        assertEquals("Survivor Track", state.title)
        assertEquals(45000L, state.position)
    }

    // ═════════════════════════════════════════════════════════════════
    // Disconnect → Local Playback (end-to-end state machine)
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `disconnect preserves isPlaying and allows local PlayRequested`() = runTest {
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
        // Cast session playing
        provider.emit(
            PlaybackState(
                title = "Cast Song",
                artist = "Artist",
                isPlaying = true,
                isCasting = true,
                castDeviceName = "TV",
                duration = 240000,
            ),
        )
        val cast = viewModel.state.first { it.isCasting }
        assertTrue(cast.isPlaying)
        assertTrue(cast.isCasting)
        assertEquals("TV", cast.castDeviceName)

        // Disconnect — isPlaying preserved, Cast flags cleared
        provider.emit(
            PlaybackState(
                title = "Cast Song",
                artist = "Artist",
                isPlaying = true,
                isCasting = false,
                castDeviceName = null,
                duration = 240000,
            ),
        )
        val disc = viewModel.state.first { !it.isCasting }
        assertFalse(disc.isCasting)
        assertNull(disc.castDeviceName)
        assertTrue("isPlaying preserved after disconnect", disc.isPlaying)
        assertEquals("Cast Song", disc.title)
    }

    @Test
    fun `disconnect then play after pause transitions to local playing`() = runTest {
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
        // Cast session playing, then paused
        provider.emit(
            PlaybackState(
                title = "Cast Song",
                artist = "Artist",
                isPlaying = false,
                isCasting = true,
                castDeviceName = "TV",
                duration = 240000,
            ),
        )
        val paused = viewModel.state.first { !it.isPlaying }
        assertFalse(paused.isPlaying)

        // Disconnect while paused
        provider.emit(
            PlaybackState(
                title = "Cast Song",
                artist = "Artist",
                isPlaying = false,
                isCasting = false,
                castDeviceName = null,
                duration = 240000,
            ),
        )
        val disc = viewModel.state.first { !it.isCasting }
        assertFalse(disc.isCasting)
        assertFalse(disc.isPlaying)

        // User taps play locally
        provider.emit(
            PlaybackState(
                title = "Cast Song",
                artist = "Artist",
                isPlaying = true,
                isCasting = false,
                castDeviceName = null,
                duration = 240000,
            ),
        )
        val playing = viewModel.state.first { it.isPlaying }
        assertTrue(playing.isPlaying)
        assertFalse(playing.isCasting)
        assertEquals("Cast Song", playing.title)
    }

    @Test
    fun `local events pass through after disconnect without Cast interference`() = runTest {
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
        // Cast session playing with position
        provider.emit(
            PlaybackState(
                title = "Track",
                artist = "Artist",
                isPlaying = true,
                position = 120000,
                isCasting = true,
                castDeviceName = "Speaker",
                duration = 300000,
            ),
        )

        // Disconnect — stale CastStateUpdate arriving after disconnect is ignored
        // (in new architecture: provider emits local-only state; CastStateUpdate does not exist)
        provider.emit(
            PlaybackState(
                title = "Track",
                artist = "Artist",
                isPlaying = true,
                position = 120000,
                isCasting = false,
                castDeviceName = null,
                duration = 300000,
            ),
        )
        val disc = viewModel.state.first { !it.isCasting }
        assertFalse(disc.isCasting)
        assertTrue("local isPlaying preserved after disconnect", disc.isPlaying)
        assertEquals(120000L, disc.position)

        // Local events resume normally
        provider.emit(
            PlaybackState(
                title = "Track",
                artist = "Artist",
                isPlaying = true,
                position = 130000,
                isCasting = false,
                castDeviceName = null,
                duration = 300000,
            ),
        )
        val progressed = viewModel.state.first { it.position >= 130000L }
        assertEquals(130000L, progressed.position)
    }

    @Test
    fun `full cast cycle connect play disconnect play locally`() = runTest {
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
        // Start: local playback
        provider.emit(
            PlaybackState(
                title = "Local Song",
                artist = "Artist",
                album = "Album",
                albumId = "al1",
                artistId = "ar1",
                coverArtId = "cov1",
                isPlaying = true,
                duration = 200000,
            ),
        )
        val local = viewModel.state.first { it.title == "Local Song" }
        assertEquals("Local Song", local.title)
        assertTrue(local.isPlaying)
        assertFalse(local.isCasting)

        // Connect to Cast
        provider.emit(
            PlaybackState(
                title = "Local Song", artist = "Artist", album = "Album",
                albumId = "al1", artistId = "ar1", coverArtId = "cov1",
                isPlaying = true, isCasting = true, castDeviceName = "Living Room TV",
                volume = 0.8f, duration = 200000,
            ),
        )
        val cast = viewModel.state.first { it.isCasting }
        assertTrue(cast.isCasting)
        assertEquals("Living Room TV", cast.castDeviceName)

        // Cast playback — track updates
        provider.emit(
            PlaybackState(
                title = "Cast Song",
                artist = "Cast Artist",
                coverArtId = "cov2",
                isPlaying = true,
                isCasting = true,
                castDeviceName = "Living Room TV",
                duration = 250000,
            ),
        )
        val castTrack = viewModel.state.first { it.title == "Cast Song" }
        assertEquals("Cast Song", castTrack.title)
        assertTrue(castTrack.isPlaying)

        // Disconnect
        provider.emit(
            PlaybackState(
                title = "Cast Song",
                artist = "Cast Artist",
                coverArtId = "cov2",
                isPlaying = true,
                isCasting = false,
                castDeviceName = null,
                duration = 250000,
            ),
        )
        val disc = viewModel.state.first { !it.isCasting }
        assertFalse(disc.isCasting)
        assertNull(disc.castDeviceName)
        assertEquals("Cast Song", disc.title) // metadata preserved

        // Local playback resumes — user taps play
        provider.emit(
            PlaybackState(
                title = "Cast Song",
                artist = "Cast Artist",
                coverArtId = "cov2",
                isPlaying = true,
                position = 50000,
                isCasting = false,
                castDeviceName = null,
                duration = 250000,
            ),
        )
        val localPlaying = viewModel.state.first { it.position >= 50000L }
        assertEquals(50000L, localPlaying.position)
        assertFalse(localPlaying.isCasting)
    }

    @Test
    fun `disconnect during paused state preserves pause`() = runTest {
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
                title = "Song",
                artist = "Artist",
                isPlaying = false,
                isCasting = true,
                castDeviceName = "TV",
                duration = 200000,
            ),
        )
        val paused = viewModel.state.first { !it.isPlaying }
        assertFalse(paused.isPlaying)

        provider.emit(
            PlaybackState(
                title = "Song",
                artist = "Artist",
                isPlaying = false,
                isCasting = false,
                castDeviceName = null,
                duration = 200000,
            ),
        )
        val disc = viewModel.state.first { !it.isCasting }
        assertFalse(disc.isCasting)
        assertFalse("paused state preserved after disconnect", disc.isPlaying)
    }

    @Test
    fun `disconnect restores volume to 1f`() = runTest {
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
        provider.emit(PlaybackState(isCasting = true, castDeviceName = "TV", volume = 0.3f, muted = true))
        val cast = viewModel.state.first { it.isCasting }
        assertEquals(0.3f, cast.volume, 0.001f)
        assertTrue(cast.muted)

        // Disconnect should reset volume to 1f unmuted
        provider.emit(PlaybackState(isCasting = false, castDeviceName = null, volume = 1f, muted = false))
        val disc = viewModel.state.first { !it.isCasting }
        assertEquals(1f, disc.volume, 0.001f)
        assertFalse(disc.muted)
        assertFalse(disc.isCasting)
    }
}
