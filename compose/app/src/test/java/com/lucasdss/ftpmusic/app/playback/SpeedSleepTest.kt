package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.PlaybackParameters
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeedSleepTest {

    private val mockAppContext: android.app.Application = mockk(relaxed = true)
    private val castPreferences: CastPreferences = mockk(relaxed = true)
    private val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Playback Speed ──

    @Test
    fun `PlaybackState defaults to speed 1_0`() {
        val state = PlaybackState()
        assertEquals(1.0f, state.playbackSpeed)
    }

    @Test
    fun `PlaybackState defaults to sleepTimerEndMs zero`() {
        val state = PlaybackState()
        assertEquals(0L, state.sleepTimerEndMs)
    }

    @Test
    fun `speed toggle delegate works`() {
        val controls = mutableListOf<PlaybackControl>()
        val provider =
            MediaSessionPlaybackProvider(
                com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager(io.mockk.mockk(relaxed = true)),
            )
        provider.setControlCallback { controls.add(it) }

        // Dispatch requires a wired player (controls otherwise queue + re-arm).
        PlayerHolder.player = mockk<androidx.media3.common.Player>(relaxed = true)
        try {
            provider.toggleSpeed()
            assertEquals(1, controls.size)
            assertEquals(PlaybackControl.SPEED_TOGGLE, controls[0])
        } finally {
            PlayerHolder.player = null
        }
    }

    @Test
    fun `speed cycles through all 6 values correctly`() {
        val speeds = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

        // Verify the speed array has all 6 expected values
        assertEquals(6, speeds.size)
        assertEquals(0.5f, speeds[0])
        assertEquals(0.75f, speeds[1])
        assertEquals(1.0f, speeds[2])
        assertEquals(1.25f, speeds[3])
        assertEquals(1.5f, speeds[4])
        assertEquals(2.0f, speeds[5])

        // Verify cycle wrapping: from each speed, nextIndex wraps correctly
        for (i in speeds.indices) {
            val current = speeds[i]
            val nextIndex = (speeds.indexOfFirst { it == current } + 1) % speeds.size
            val expectedNext = speeds[(i + 1) % speeds.size]
            assertEquals(expectedNext, speeds[nextIndex])
        }

        // Verify that from last speed (2.0f) it wraps to first (0.5f)
        val from2x = 2.0f
        val nextFrom2x = speeds[(speeds.indexOfFirst { it == from2x } + 1) % speeds.size]
        assertEquals(0.5f, nextFrom2x)
    }

    @Test
    fun `PlaybackState can carry playbackSpeed`() {
        val state = PlaybackState(playbackSpeed = 1.5f)
        assertEquals(1.5f, state.playbackSpeed)
    }

    @Test
    fun `PlaybackState can carry sleepTimerEndMs`() {
        val endMs = System.currentTimeMillis() + 900_000L
        val state = PlaybackState(sleepTimerEndMs = endMs)
        assertEquals(endMs, state.sleepTimerEndMs)
    }

    // ── Sleep Timer ──

    @Test
    fun `PlaybackViewModel toggleSpeed delegates to provider`() {
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

        viewModel.toggleSpeed()
        assertTrue(provider.toggleSpeedCalled)
    }

    @Test
    fun `PlaybackViewModel startSleepTimer updates sleepTimerEndMs`() = runTest {
        val provider = FakePlaybackStateProvider()
        val manager = mockk<PlaybackManager>(relaxed = true)
        val viewModel =
            PlaybackViewModel(
                provider,
                manager,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk<com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao>(relaxed = true),
                mockk<SubsonicApi>(relaxed = true),
            )

        val beforeMs = System.currentTimeMillis()
        viewModel.startSleepTimer(15)

        // startSleepTimer updates ViewModel state, read from viewModel.state
        val endMs = viewModel.state.value.sleepTimerEndMs
        assertTrue(endMs > beforeMs)
        assertTrue(endMs <= beforeMs + 15 * 60_000L + 1000L)
    }

    @Test
    fun `PlaybackViewModel cancelSleepTimer resets sleepTimerEndMs to zero`() = runTest {
        val provider = FakePlaybackStateProvider()
        val manager = mockk<PlaybackManager>(relaxed = true)
        val viewModel =
            PlaybackViewModel(
                provider,
                manager,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk<com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao>(relaxed = true),
                mockk<SubsonicApi>(relaxed = true),
            )

        viewModel.startSleepTimer(30)
        assertTrue(viewModel.state.value.sleepTimerEndMs > 0L)

        viewModel.cancelSleepTimer()
        assertEquals(0L, viewModel.state.value.sleepTimerEndMs)
    }

    @Test
    fun `startSleepTimer arms the service-side timer through the provider`() = runTest {
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

        val beforeMs = System.currentTimeMillis()
        viewModel.startSleepTimer(15)

        // E2: enforcement lives in the service — the provider must receive the
        // arm command (and the ViewModel must NOT own a timer job anymore).
        val armed = provider.lastArmedSleepEndMs
        assertTrue("provider must be armed", armed >= beforeMs)
        assertTrue(armed <= beforeMs + 15 * 60_000L + 1000L)
        assertTrue(viewModel.state.value.sleepTimerEndMs == armed)
    }

    @Test
    fun `cancelSleepTimer cancels the service-side timer`() = runTest {
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

        viewModel.startSleepTimer(30)
        viewModel.cancelSleepTimer()

        assertTrue(provider.sleepTimerCancelled)
        assertEquals(0L, viewModel.state.value.sleepTimerEndMs)
    }

    @Test
    fun `init re-arms a persisted sleep timer from the dao`() = runTest {
        val provider = FakePlaybackStateProvider()
        val dao = mockk<com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao>(relaxed = true)
        val futureEndMs = System.currentTimeMillis() + 30 * 60_000L
        io.mockk.coEvery { dao.get() } returns
            com.lucasdss.ftpmusic.app.playback.PersistedPlaybackState(sleepTimerEndMs = futureEndMs)

        val viewModel =
            PlaybackViewModel(
                provider,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                dao,
                mockk<SubsonicApi>(relaxed = true),
            )
        testDispatcher.scheduler.advanceUntilIdle()

        // E2: the persisted deadline must be restored into shared state AND
        // re-armed service-side (previously the process-local PlayerHolder read
        // was empty right after process death → timer silently lost).
        assertEquals(futureEndMs, viewModel.state.value.sleepTimerEndMs)
        assertEquals(futureEndMs, provider.lastArmedSleepEndMs)
    }

    @Test
    fun `init does not arm an expired persisted timer`() = runTest {
        val provider = FakePlaybackStateProvider()
        val dao = mockk<com.lucasdss.ftpmusic.app.data.db.PlaybackStateDao>(relaxed = true)
        io.mockk.coEvery { dao.get() } returns
            com.lucasdss.ftpmusic.app.playback.PersistedPlaybackState(
                sleepTimerEndMs =
                    System.currentTimeMillis() - 1000,
            )

        val viewModel =
            PlaybackViewModel(
                provider,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                dao,
                mockk<SubsonicApi>(relaxed = true),
            )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0L, viewModel.state.value.sleepTimerEndMs)
        assertEquals(-1L, provider.lastArmedSleepEndMs)
    }

    // ── Pause ──

    @Test
    fun `pause delegates to PlayerHolder player pause`() {
        val mockPlayer: androidx.media3.common.Player = mockk(relaxed = true)

        try {
            PlayerHolder.player = mockPlayer
            // Create a real PlaybackManager — pause() should call player.pause()
            val queueManager: QueueManager = mockk(relaxed = true)
            val persistenceManager: QueuePersistenceManager = mockk(relaxed = true)
            val manager =
                PlaybackManager(
                    queueManager,
                    persistenceManager,
                    mockk<com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager>(relaxed = true),
                    mockk<com.lucasdss.ftpmusic.app.data.cache.DownloadManager>(relaxed = true),
                    castPreferences,
                    mockAppContext,
                    mockk<com.lucasdss.ftpmusic.app.data.db.QueueJournalDao>(relaxed = true),
                )

            manager.pause()

            verify(exactly = 1) { mockPlayer.pause() }
        } finally {
            PlayerHolder.player = null
        }
    }
}
