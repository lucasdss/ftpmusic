package com.lucasdss.ftpmusic.app.ui.settings

import android.content.Context
import android.content.SharedPreferences
import com.lucasdss.ftpmusic.app.data.cache.AdjustableCacheEvictor
import com.lucasdss.ftpmusic.app.data.cache.CacheService
import com.lucasdss.ftpmusic.app.data.cache.CellularMediaPolicy
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService
import com.lucasdss.ftpmusic.app.data.cache.NetworkPolicyHolder
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.GenreMixDao
import com.lucasdss.ftpmusic.app.data.db.LyricsCacheDao
import com.lucasdss.ftpmusic.app.data.db.MetadataSyncWorker
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.SyncStatus
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.data.update.AppUpdateChecker
import com.lucasdss.ftpmusic.app.data.update.FlexibleInstallEvent
import com.lucasdss.ftpmusic.app.data.update.UpdateCheckResult
import com.lucasdss.ftpmusic.app.playback.CastPreferences
import com.lucasdss.ftpmusic.app.playback.PlaybackManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateSettingsViewModelTest {
    private val cacheService: CacheService = mockk(relaxed = true)
    private val audioCacheEvictor: AdjustableCacheEvictor = mockk(relaxed = true)
    private val castPreferences: CastPreferences = mockk(relaxed = true)
    private val storage: SecureStorage = mockk(relaxed = true)
    private val offlineModeManager: OfflineModeManager = mockk(relaxed = true)
    private val networkPolicyHolder: NetworkPolicyHolder = mockk(relaxed = true)
    private val coverArtFallback: CoverArtFallbackService = mockk(relaxed = true)
    private val playbackManager: PlaybackManager = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val prefs: SharedPreferences = mockk(relaxed = true)
    private val prefsEditor: SharedPreferences.Editor = mockk(relaxed = true)
    private val metadataDao: CachedMetadataDao = mockk(relaxed = true)
    private val lyricsCacheDao: LyricsCacheDao = mockk(relaxed = true)
    private val playlistDao: PlaylistDao = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val genreMixDao: GenreMixDao = mockk(relaxed = true)
    private val metadataSyncWorker: MetadataSyncWorker = mockk(relaxed = true)
    private val serverConfigStore: com.lucasdss.ftpmusic.app.di.ServerConfigStore = mockk(relaxed = true)
    private val serverProbe: com.lucasdss.ftpmusic.app.data.network.ServerProbe = mockk(relaxed = true)
    private val appUpdateChecker: AppUpdateChecker = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { coverArtFallback.maxCacheBytes } returns 300L * 1024 * 1024
        every { context.getSharedPreferences("ftpmusic_sync", any()) } returns prefs
        every { prefs.edit() } returns prefsEditor
        every { prefsEditor.putLong(any(), any()) } returns prefsEditor
        every { prefsEditor.putInt(any(), any()) } returns prefsEditor
        every { prefsEditor.apply() } just Runs
        every { prefs.getLong(any(), any()) } returns 0
        every { metadataSyncWorker.status } returns
            kotlinx.coroutines.flow.MutableStateFlow(SyncStatus())
        every { networkPolicyHolder.cellularMediaPolicy } returns
            kotlinx.coroutines.flow.MutableStateFlow(CellularMediaPolicy.AUTO_CACHE)
        every { networkPolicyHolder.librarySyncWifiOnly } returns
            kotlinx.coroutines.flow.MutableStateFlow(false)
        every { networkPolicyHolder.shouldWarnManualResyncOnCellular() } returns false
        every { appUpdateChecker.applicationId() } returns "com.lucasdss.ftpmusic.app"
        every { appUpdateChecker.observeFlexibleInstall() } returns MutableSharedFlow()
        every { appUpdateChecker.completeUpdate() } returns true
        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SettingsViewModel(
        context,
        cacheService,
        audioCacheEvictor,
        castPreferences,
        storage,
        offlineModeManager,
        networkPolicyHolder,
        coverArtFallback,
        playbackManager,
        metadataDao,
        lyricsCacheDao,
        playlistDao,
        genreMixDao,
        trackDao,
        metadataSyncWorker,
        serverConfigStore,
        serverProbe,
        mockk(relaxed = true),
        appUpdateChecker,
    )

    @Test
    fun `checkForUpdates maps UpToDate`() = runTest(testDispatcher) {
        coEvery { appUpdateChecker.check() } returns UpdateCheckResult.UpToDate

        viewModel.checkForUpdates()
        advanceUntilIdle()

        assertEquals(UpdateCheckUi.UpToDate, viewModel.state.value.updateCheck)
    }

    @Test
    fun `checkForUpdates maps Available`() = runTest(testDispatcher) {
        coEvery { appUpdateChecker.check() } returns
            UpdateCheckResult.UpdateAvailable(availableVersionCode = 20, flexibleAllowed = true)

        viewModel.checkForUpdates()
        advanceUntilIdle()

        val ui = viewModel.state.value.updateCheck
        assertTrue(ui is UpdateCheckUi.Available)
        ui as UpdateCheckUi.Available
        assertEquals(20, ui.availableVersionCode)
        assertTrue(ui.flexibleAllowed)
    }

    @Test
    fun `checkForUpdates maps Unavailable to Error`() = runTest(testDispatcher) {
        coEvery { appUpdateChecker.check() } returns
            UpdateCheckResult.Unavailable("No network")

        viewModel.checkForUpdates()
        advanceUntilIdle()

        assertEquals(UpdateCheckUi.Error("No network"), viewModel.state.value.updateCheck)
    }

    @Test
    fun `checkForUpdates ignores second tap while Checking`() = runTest(testDispatcher) {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { appUpdateChecker.check() } coAnswers {
            gate.await()
            UpdateCheckResult.UpToDate
        }

        viewModel.checkForUpdates()
        testScheduler.runCurrent()
        assertTrue(viewModel.state.value.updateCheck is UpdateCheckUi.Checking)

        viewModel.checkForUpdates()
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { appUpdateChecker.check() }
    }

    @Test
    fun `startUpdate emits StartFlexibleUpdate when flexible allowed`() = runTest(testDispatcher) {
        coEvery { appUpdateChecker.check() } returns
            UpdateCheckResult.UpdateAvailable(14, flexibleAllowed = true)
        viewModel.checkForUpdates()
        advanceUntilIdle()

        val event = async { withTimeout(5_000) { viewModel.appUpdateEvents.first() } }
        viewModel.startUpdate()
        advanceUntilIdle()

        assertEquals(AppUpdateEvent.StartFlexibleUpdate, event.await())
    }

    @Test
    fun `startUpdate emits OpenPlayStore when flexible not allowed`() = runTest(testDispatcher) {
        coEvery { appUpdateChecker.check() } returns
            UpdateCheckResult.UpdateAvailable(14, flexibleAllowed = false)
        viewModel.checkForUpdates()
        advanceUntilIdle()

        val event = async { withTimeout(5_000) { viewModel.appUpdateEvents.first() } }
        viewModel.startUpdate()
        advanceUntilIdle()

        assertEquals(
            AppUpdateEvent.OpenPlayStore("com.lucasdss.ftpmusic.app"),
            event.await(),
        )
    }

    @Test
    fun `startUpdate no-op when not Available`() = runTest(testDispatcher) {
        viewModel.startUpdate()
        advanceUntilIdle()
        verify(exactly = 0) { appUpdateChecker.applicationId() }
    }

    @Test
    fun `launchFlexibleUpdate falls back to Play Store when start fails`() = runTest(testDispatcher) {
        every { appUpdateChecker.startFlexibleUpdate(any(), any()) } returns false

        val event = async { withTimeout(5_000) { viewModel.appUpdateEvents.first() } }
        val started = viewModel.launchFlexibleUpdate(mockk(relaxed = true), mockk(relaxed = true))
        advanceUntilIdle()

        assertEquals(false, started)
        assertEquals(
            AppUpdateEvent.OpenPlayStore("com.lucasdss.ftpmusic.app"),
            event.await(),
        )
    }

    @Test
    fun `openPlayStoreListing emits OpenPlayStore`() = runTest(testDispatcher) {
        val event = async { withTimeout(5_000) { viewModel.appUpdateEvents.first() } }
        viewModel.openPlayStoreListing()
        advanceUntilIdle()

        assertEquals(
            AppUpdateEvent.OpenPlayStore("com.lucasdss.ftpmusic.app"),
            event.await(),
        )
    }

    @Test
    fun `checkForUpdates maps InProgress and observes install`() = runTest(testDispatcher) {
        val installEvents = MutableSharedFlow<FlexibleInstallEvent>(extraBufferCapacity = 1)
        every { appUpdateChecker.observeFlexibleInstall() } returns installEvents
        coEvery { appUpdateChecker.check() } returns UpdateCheckResult.InProgress(25)

        viewModel.checkForUpdates()
        advanceUntilIdle()

        assertEquals(UpdateCheckUi.InProgress(25), viewModel.state.value.updateCheck)

        installEvents.tryEmit(FlexibleInstallEvent.Downloaded)
        advanceUntilIdle()

        assertEquals(UpdateCheckUi.ReadyToInstall, viewModel.state.value.updateCheck)
    }

    @Test
    fun `launchFlexibleUpdate success moves to InProgress`() = runTest(testDispatcher) {
        val installEvents = MutableSharedFlow<FlexibleInstallEvent>(extraBufferCapacity = 1)
        every { appUpdateChecker.observeFlexibleInstall() } returns installEvents
        every { appUpdateChecker.startFlexibleUpdate(any(), any()) } returns true
        coEvery { appUpdateChecker.check() } returns
            UpdateCheckResult.UpdateAvailable(14, flexibleAllowed = true)
        viewModel.checkForUpdates()
        advanceUntilIdle()

        val started = viewModel.launchFlexibleUpdate(mockk(relaxed = true), mockk(relaxed = true))
        advanceUntilIdle()

        assertTrue(started)
        assertEquals(UpdateCheckUi.InProgress(14), viewModel.state.value.updateCheck)
    }

    @Test
    fun `completeFlexibleUpdate calls checker when ReadyToInstall`() = runTest(testDispatcher) {
        val installEvents = MutableSharedFlow<FlexibleInstallEvent>(extraBufferCapacity = 1)
        every { appUpdateChecker.observeFlexibleInstall() } returns installEvents
        coEvery { appUpdateChecker.check() } returns UpdateCheckResult.InProgress(25)
        viewModel.checkForUpdates()
        advanceUntilIdle()
        installEvents.tryEmit(FlexibleInstallEvent.Downloaded)
        advanceUntilIdle()

        viewModel.completeFlexibleUpdate()
        advanceUntilIdle()

        verify { appUpdateChecker.completeUpdate() }
    }

    @Test
    fun `completeFlexibleUpdate no-op when not ReadyToInstall`() = runTest(testDispatcher) {
        viewModel.completeFlexibleUpdate()
        advanceUntilIdle()
        verify(exactly = 0) { appUpdateChecker.completeUpdate() }
    }

    @Test
    fun `install Failed maps to Error`() = runTest(testDispatcher) {
        val installEvents = MutableSharedFlow<FlexibleInstallEvent>(extraBufferCapacity = 1)
        every { appUpdateChecker.observeFlexibleInstall() } returns installEvents
        coEvery { appUpdateChecker.check() } returns UpdateCheckResult.InProgress(25)
        viewModel.checkForUpdates()
        advanceUntilIdle()

        installEvents.tryEmit(FlexibleInstallEvent.Failed("Play update canceled"))
        advanceUntilIdle()

        assertEquals(UpdateCheckUi.Error("Play update canceled"), viewModel.state.value.updateCheck)
    }
}
