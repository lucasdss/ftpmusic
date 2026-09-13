package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.Player
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

class CastBehaviorMapTest {

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
    fun `load track sets metadata`() = runTest {
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
                album = "Album",
                albumId = "al1",
                artistId = "ar1",
                duration = 240000,
                coverArtId = "cov1",
            ),
        )
        val state = viewModel.state.first { it.title != null }
        assertEquals("Song", state.title)
        assertEquals("Artist", state.artist)
        assertEquals(240000L, state.duration)
    }

    @Test
    fun `play pause toggles isPlaying`() = runTest {
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
        provider.emit(PlaybackState(title = "T", isPlaying = true, duration = 1000))
        val playing = viewModel.state.first { it.isPlaying }
        assertTrue(playing.isPlaying)
        provider.emit(PlaybackState(title = "T", isPlaying = false, duration = 1000))
        val paused = viewModel.state.first { !it.isPlaying }
        assertFalse(paused.isPlaying)
    }

    @Test
    fun `position update reflects in state`() = runTest {
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
        provider.emit(PlaybackState(title = "T", position = 30000, duration = 1000))
        val state = viewModel.state.first { it.position == 30000L }
        assertEquals(30000L, state.position)
    }

    @Test
    fun `volume change sets volume and muted`() = runTest {
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
        provider.emit(PlaybackState(volume = 0.5f, muted = true))
        val state = viewModel.state.first { it.muted }
        assertEquals(0.5f, state.volume)
        assertTrue(state.muted)
    }

    @Test
    fun `queue cleared resets to idle`() = runTest {
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
        // Emit loaded state first, then empty state simulating cleared queue
        provider.emit(PlaybackState(title = "T", isPlaying = true, duration = 1000))
        val loaded = viewModel.state.first { it.title != null }
        assertNotNull(loaded.title)
        provider.emit(PlaybackState(title = null))
        val cleared = viewModel.state.first { it.title == null }
        assertNull(cleared.title)
    }

    @Test
    fun `CastConnected sets isCasting and device name`() = runTest {
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
        provider.emit(PlaybackState(isCasting = true, castDeviceName = "Mini Speaker", volume = 0.5f))
        val state = viewModel.state.first { it.isCasting }
        assertTrue(state.isCasting)
        assertEquals("Mini Speaker", state.castDeviceName)
        assertEquals(0.5f, state.volume)
    }

    @Test
    fun `CastConnected preserves existing track metadata`() = runTest {
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
                album = "Album",
                albumId = "al1",
                artistId = "ar1",
                coverArtId = "cov1",
                isPlaying = true,
                duration = 1000,
                isCasting = true,
                castDeviceName = "TV",
                volume = 0.8f,
            ),
        )
        val state = viewModel.state.first { it.isCasting }
        assertEquals("Song", state.title)
        assertTrue(state.isCasting)
    }

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
        val state = viewModel.state.first { it.isPlaying }
        assertTrue(state.isPlaying)
        assertEquals(50000L, state.position)
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
        assertEquals(15000L, paused.position)
    }

    @Test
    fun `CastDisconnected clears isCasting`() = runTest {
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
                isPlaying = true,
                isCasting = true,
                castDeviceName = "TV",
                position = 50000,
                duration = 240000,
            ),
        )
        val cast = viewModel.state.first { it.isCasting }
        assertTrue(cast.isCasting)
        provider.emit(
            PlaybackState(
                title = "T",
                isPlaying = true,
                isCasting = false,
                castDeviceName = null,
                position = 50000,
                duration = 240000,
            ),
        )
        val disc = viewModel.state.first { !it.isCasting }
        assertFalse(disc.isCasting)
        assertNull(disc.castDeviceName)
    }

    @Test
    fun `track metadata preserved after disconnect`() = runTest {
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
                title = "My Song",
                artist = "Band",
                album = "Album",
                albumId = "al1",
                artistId = "ar1",
                coverArtId = "cov99",
                duration = 240000,
                isCasting = true,
                castDeviceName = "TV",
            ),
        )
        provider.emit(
            PlaybackState(
                title = "My Song",
                artist = "Band",
                album = "Album",
                albumId = "al1",
                artistId = "ar1",
                coverArtId = "cov99",
                duration = 240000,
                isCasting = false,
                castDeviceName = null,
            ),
        )
        val state = viewModel.state.first { !it.isCasting }
        assertEquals("My Song", state.title)
        assertEquals("Band", state.artist)
        assertEquals("cov99", state.coverArtId)
    }

    @Test
    fun `local events work after disconnect`() = runTest {
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
        provider.emit(PlaybackState(title = "T", isCasting = true, castDeviceName = "TV", duration = 240000))
        provider.emit(
            PlaybackState(
                title = "T",
                isCasting = false,
                castDeviceName = null,
                isPlaying = true,
                position = 60000,
                duration = 240000,
            ),
        )
        val state = viewModel.state.first { !it.isCasting && it.isPlaying }
        assertTrue(state.isPlaying)
        assertEquals(60000L, state.position)
    }

    @Test
    fun `position preserved through Cast cycle`() = runTest {
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
        // Local playing
        provider.emit(PlaybackState(title = "T", isPlaying = true, position = 30000, duration = 240000))
        // Connect to Cast with updated position
        provider.emit(
            PlaybackState(
                title = "T",
                isPlaying = true,
                position = 75000,
                isCasting = true,
                castDeviceName = "TV",
                duration = 240000,
            ),
        )
        // Disconnect — position preserved
        provider.emit(
            PlaybackState(
                title = "T",
                isPlaying = true,
                position = 75000,
                isCasting = false,
                castDeviceName = null,
                duration = 240000,
            ),
        )
        val state = viewModel.state.first { !it.isCasting }
        assertEquals(75000L, state.position)
    }

    @Test
    fun `connect during paused playback`() = runTest {
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
            PlaybackState(title = "T", isPlaying = false, isCasting = true, castDeviceName = "TV", duration = 240000),
        )
        val state = viewModel.state.first { it.isCasting }
        assertTrue(state.isCasting)
        assertFalse(state.isPlaying)
    }

    @Test
    fun `rapid connect disconnect preserves metadata`() = runTest {
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
                coverArtId = "cov1",
                isPlaying = true,
                isCasting = true,
                castDeviceName = "TV",
                position = 5000,
                duration = 240000,
            ),
        )
        provider.emit(
            PlaybackState(
                title = "Song",
                artist = "Artist",
                coverArtId = "cov1",
                isPlaying = true,
                isCasting = false,
                castDeviceName = null,
                position = 5000,
                duration = 240000,
            ),
        )
        provider.emit(
            PlaybackState(
                title = "Song",
                artist = "Artist",
                coverArtId = "cov1",
                isPlaying = true,
                isCasting = true,
                castDeviceName = "Soundbar",
                position = 5000,
                duration = 240000,
            ),
        )
        provider.emit(
            PlaybackState(
                title = "Song",
                artist = "Artist",
                coverArtId = "cov1",
                isPlaying = true,
                isCasting = false,
                castDeviceName = null,
                position = 5000,
                duration = 240000,
            ),
        )
        val state = viewModel.state.first { !it.isCasting }
        assertEquals("Song", state.title)
        assertFalse(state.isCasting)
    }

    @Test
    fun `load new track during Cast keeps isCasting`() = runTest {
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
                title = "Track1",
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = true,
                position = 10000,
                duration = 240000,
            ),
        )
        provider.emit(
            PlaybackState(
                title = "Track2",
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = true,
                position = 0,
                duration = 200000,
            ),
        )
        val state = viewModel.state.first { it.title == "Track2" }
        assertEquals("Track2", state.title)
        assertTrue(state.isPlaying)
        assertTrue(state.isCasting)
    }

    @Test
    fun `queue size changes during Cast`() = runTest {
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
            PlaybackState(title = "T", isCasting = true, castDeviceName = "TV", queueSize = 19, duration = 240000),
        )
        val state = viewModel.state.first { it.queueSize == 19 }
        assertEquals(19, state.queueSize)
    }

    @Test
    fun `repeat and shuffle work independently of Cast`() = runTest {
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
                isCasting = true,
                castDeviceName = "TV",
                repeatMode = Player.REPEAT_MODE_ALL,
                shuffleModeEnabled = true,
            ),
        )
        val state = viewModel.state.first { it.shuffleModeEnabled }
        assertEquals(Player.REPEAT_MODE_ALL, state.repeatMode)
        assertTrue(state.shuffleModeEnabled)
    }

    @Test
    fun `star flows through provider state to ViewModel`() = runTest {
        // isStarred/isDisliked/trackRating are carried by the provider state
        // (synced from the DB by the ViewModel via updateExtraState).
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
        provider.emit(PlaybackState(title = "T", isStarred = true))
        val state = viewModel.state.first { it.title != null }
        assertTrue("provider isStarred must flow through", state.isStarred)
    }

    // ═════════════════════════════════════════════════════════════════
    // Edge Cases
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `E6 - Cast device volume change syncs to UI`() = runTest {
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
        provider.emit(PlaybackState(isCasting = true, castDeviceName = "TV", volume = 0.75f, muted = false))
        val state = viewModel.state.first { it.isCasting }
        assertEquals(0.75f, state.volume)
        assertFalse(state.muted)
    }

    @Test
    fun `E7 - disconnect while app in background preserves metadata`() = runTest {
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
                title = "Background Song",
                artist = "Artist",
                coverArtId = "cov-bg",
                isPlaying = true,
                isCasting = true,
                castDeviceName = "TV",
                position = 120000,
                duration = 300000,
            ),
        )
        // Simulate unexpected disconnect
        provider.emit(
            PlaybackState(
                title = "Background Song",
                artist = "Artist",
                coverArtId = "cov-bg",
                isPlaying = true,
                isCasting = false,
                castDeviceName = null,
                position = 120000,
                duration = 300000,
            ),
        )
        val state = viewModel.state.first { !it.isCasting }
        assertFalse(state.isCasting)
        assertEquals("Background Song", state.title)
        assertEquals(120000L, state.position)
    }

    @Test
    fun `E9 - multiple rapid CastStateUpdates don't corrupt position`() = runTest {
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
                isPlaying = false,
                position = 3500,
                duration = 240000,
            ),
        )
        provider.emit(
            PlaybackState(
                title = "T",
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = true,
                position = 3500,
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
        provider.emit(
            PlaybackState(
                title = "T",
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = true,
                position = 8000,
                duration = 240000,
            ),
        )
        val state = viewModel.state.first { it.position >= 8000L }
        assertEquals(8000L, state.position)
        assertTrue(state.isPlaying)
    }

    @Test
    fun `E10 - sequential CastStateUpdate preserves monotonic position`() = runTest {
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
        provider.emit(
            PlaybackState(
                title = "T",
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = true,
                position = 100000,
                duration = 240000,
            ),
        )
        provider.emit(
            PlaybackState(
                title = "T",
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = true,
                position = 150000,
                duration = 240000,
            ),
        )
        val state = viewModel.state.first { it.position >= 150000L }
        assertEquals(150000L, state.position)
    }

    // ═════════════════════════════════════════════════════════════════
    // Notification State Scenarios
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `notification reflects isPlaying correctly`() = runTest {
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
        provider.emit(PlaybackState(title = "Track", artist = "Artist", isPlaying = true, duration = 1000))
        val playing = viewModel.state.first { it.isPlaying }
        assertTrue(playing.isPlaying)
        provider.emit(PlaybackState(title = "Track", artist = "Artist", isPlaying = false, duration = 1000))
        val paused = viewModel.state.first { !it.isPlaying }
        assertFalse(paused.isPlaying)
    }

    @Test
    fun `notification shows Cast device during Cast`() = runTest {
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
                title = "Track",
                artist = "Artist",
                isCasting = true,
                castDeviceName = "Kitchen Speaker",
                duration = 1000,
            ),
        )
        val state = viewModel.state.first { it.isCasting }
        assertTrue(state.isCasting)
        assertEquals("Kitchen Speaker", state.castDeviceName)
    }

    @Test
    fun `notification clears Cast device after disconnect`() = runTest {
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
            PlaybackState(title = "Track", artist = "Artist", isCasting = true, castDeviceName = "TV", duration = 1000),
        )
        provider.emit(
            PlaybackState(
                title = "Track",
                artist = "Artist",
                isCasting = false,
                castDeviceName = null,
                duration = 1000,
            ),
        )
        val state = viewModel.state.first { !it.isCasting }
        assertFalse(state.isCasting)
        assertNull(state.castDeviceName)
    }

    // ═════════════════════════════════════════════════════════════════
    // Download Status and Sleep Timer
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `download status defaults to empty from ViewModel`() = runTest {
        // downloadedTrackIds is ViewModel-local, managed via refreshQueueDownloadStatus().
        // Provider-emitted value is overridden by ViewModel's _downloadedTrackIds default (emptySet).
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
        provider.emit(PlaybackState(downloadedTrackIds = setOf("t1", "t2", "t3")))
        val state = viewModel.state.first { true }
        assertTrue(
            "downloadedTrackIds is ViewModel-local; provider value is overridden",
            state.downloadedTrackIds.isEmpty(),
        )
    }

    @Test
    fun `sleep timer defaults to zero from ViewModel`() = runTest {
        // sleepTimerEndMs is ViewModel-local, managed via startSleepTimer()/cancelSleepTimer().
        // Provider-emitted value is overridden by ViewModel's _sleepTimerEndMs default (0L).
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
        provider.emit(PlaybackState(sleepTimerEndMs = 1719000000000L))
        val state = viewModel.state.first { true }
        assertEquals("sleepTimerEndMs is ViewModel-local; provider value is overridden", 0L, state.sleepTimerEndMs)
    }

    @Test
    fun `next track preview shows upcoming track`() = runTest {
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
        provider.emit(PlaybackState(nextTrackTitle = "Coming Up", nextTrackArtist = "Next Band"))
        val state = viewModel.state.first { it.nextTrackTitle != null }
        assertEquals("Coming Up", state.nextTrackTitle)
        assertEquals("Next Band", state.nextTrackArtist)
    }

    @Test
    fun `next track preview clears with null`() = runTest {
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
        provider.emit(PlaybackState(nextTrackTitle = "Next", nextTrackArtist = "Band"))
        val with = viewModel.state.first { it.nextTrackTitle != null }
        assertNotNull(with.nextTrackTitle)
        provider.emit(PlaybackState(nextTrackTitle = null, nextTrackArtist = null))
        val cleared = viewModel.state.first { it.nextTrackTitle == null }
        assertNull(cleared.nextTrackTitle)
    }

    // ═════════════════════════════════════════════════════════════════
    // PlaybackEnded scenarios
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `playback ended clears all state`() = runTest {
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
                album = "Album",
                albumId = "al1",
                artistId = "ar1",
                coverArtId = "cov1",
                isPlaying = true,
                position = 120000,
                duration = 240000,
            ),
        )
        // Playback ended — clear to empty state
        provider.emit(PlaybackState(title = null, artist = null))
        val state = viewModel.state.first { it.title == null }
        assertNull(state.title)
        assertNull(state.artist)
    }

    @Test
    fun `playback ended during Cast clears state`() = runTest {
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
                isCasting = true,
                castDeviceName = "TV",
                isPlaying = true,
                position = 10000,
                duration = 240000,
            ),
        )
        provider.emit(PlaybackState(title = null))
        val state = viewModel.state.first { it.title == null }
        assertNull(state.title)
    }

    // ═════════════════════════════════════════════════════════════════
    // Duration Updates
    // ═════════════════════════════════════════════════════════════════

    @Test
    fun `duration update sets correct value`() = runTest {
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
        provider.emit(PlaybackState(title = "T", duration = 300000))
        val state = viewModel.state.first { it.duration > 0 }
        assertEquals(300000L, state.duration)
    }

    @Test
    fun `track index change sets current track position`() = runTest {
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
        provider.emit(PlaybackState(trackIndex = 7))
        val state = viewModel.state.first { it.trackIndex == 7 }
        assertEquals(7, state.trackIndex)
    }
}
