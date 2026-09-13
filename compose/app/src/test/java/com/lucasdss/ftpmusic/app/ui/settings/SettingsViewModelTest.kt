package com.lucasdss.ftpmusic.app.ui.settings

import android.content.Context
import android.content.SharedPreferences
import com.lucasdss.ftpmusic.app.data.cache.AdjustableCacheEvictor
import com.lucasdss.ftpmusic.app.data.cache.CacheService
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService
import com.lucasdss.ftpmusic.app.data.cache.DownloadManager
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.GenreMixDao
import com.lucasdss.ftpmusic.app.data.db.LyricsCacheDao
import com.lucasdss.ftpmusic.app.data.db.MetadataSyncWorker
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.SyncStatus
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.repository.WaveformRepository
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.playback.CastPreferences
import com.lucasdss.ftpmusic.app.playback.PlaybackManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val cacheService: CacheService = mockk(relaxed = true)
    private val audioCacheEvictor: AdjustableCacheEvictor = mockk(relaxed = true)
    private val castPreferences: CastPreferences = mockk(relaxed = true)
    private val storage: SecureStorage = mockk(relaxed = true)
    private val offlineModeManager: OfflineModeManager = mockk(relaxed = true)
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
        every { prefs.getLong("last_metadata_sync_ms", any()) } returns 0
        every { prefs.getLong("metadata_sync_duration_ms", any()) } returns 0
        every { prefs.getLong("last_lyrics_fetch_ms", any()) } returns 0
        coEvery { metadataDao.albumCount() } returns 0
        every { metadataSyncWorker.status } returns
            kotlinx.coroutines.flow.MutableStateFlow(com.lucasdss.ftpmusic.app.data.db.SyncStatus())
        coEvery { metadataDao.artistCount() } returns 0
        coEvery { lyricsCacheDao.count() } returns 0
        // Default SharedPreferences.Editor for context
        every { prefsEditor.putLong(any(), any()) } returns prefsEditor
        every { prefsEditor.apply() } just Runs
        viewModel =
            createViewModel()
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
    )

    @Test
    fun `setOfflineMode true calls enable`() {
        viewModel.setOfflineMode(true)
        verify { offlineModeManager.enable() }
        assertTrue(viewModel.state.value.offlineMode)
    }

    @Test
    fun `setOfflineMode false calls disable`() {
        viewModel.setOfflineMode(false)
        verify { offlineModeManager.disable() }
        assertFalse(viewModel.state.value.offlineMode)
    }

    @Test
    fun `setCastFromPhone updates state and preferences`() {
        viewModel.setCastFromPhone(true)
        verify { castPreferences.castFromPhone = true }
        assertTrue(viewModel.state.value.castFromPhone)
    }

    @Test
    fun `setUseHttpForCast updates state and preferences`() {
        viewModel.setUseHttpForCast(true)
        verify { castPreferences.useHttpForCast = true }
        assertTrue(viewModel.state.value.useHttpForCast)
    }

    @Test
    fun `saveServerSettings normalizes and persists valid input`() {
        viewModel.setServerUrl(" https://music.example.com/navidrome/ ")
        viewModel.setUsername(" user ")
        viewModel.setPassword("secret")

        assertTrue(viewModel.saveServerSettings())

        verify { serverConfigStore.set("https://music.example.com/navidrome", "user", "secret") }
        assertEquals("Settings saved", viewModel.state.value.testResult)
    }

    @Test
    fun `saveServerSettings rejects unsafe public HTTP`() {
        viewModel.setServerUrl("http://example.com")
        viewModel.setUsername("user")
        viewModel.setPassword("secret")

        assertFalse(viewModel.saveServerSettings())

        assertEquals(
            "HTTP is allowed only for private local-network servers",
            viewModel.state.value.testResult,
        )
        verify(exactly = 0) { serverConfigStore.set(any(), any(), any()) }
    }

    @Test
    fun `testConnection rejects invalid input before network setup`() = runTest {
        viewModel.setServerUrl("")
        viewModel.setUsername("user")
        viewModel.setPassword("secret")

        viewModel.testConnection()
        advanceUntilIdle()

        assertEquals("Server URL is required", viewModel.state.value.testResult)
        assertFalse(viewModel.state.value.isTesting)
    }

    // ── v43: Home & Favorites visibility toggles ─────────────────────────

    @Test
    fun `home visibility toggles default to true when storage empty`() {
        assertTrue(viewModel.state.value.showPlaylistsOnHome)
        assertTrue(viewModel.state.value.showFavArtistsSection)
        assertTrue(viewModel.state.value.showFavAlbumsSection)
        assertTrue(viewModel.state.value.showFavRadioSection)
    }

    @Test
    fun `setShowPlaylistsOnHome persists to storage`() {
        viewModel.setShowPlaylistsOnHome(false)
        assertFalse(viewModel.state.value.showPlaylistsOnHome)
        verify { storage.put(SecureStorage.KEY_HOME_SHOW_PLAYLISTS, "false") }
    }

    @Test
    fun `setShowFavArtistsSection persists to storage`() {
        viewModel.setShowFavArtistsSection(false)
        assertFalse(viewModel.state.value.showFavArtistsSection)
        verify { storage.put(SecureStorage.KEY_HOME_SHOW_FAV_ARTISTS, "false") }
    }

    @Test
    fun `setShowFavAlbumsSection persists to storage`() {
        viewModel.setShowFavAlbumsSection(false)
        assertFalse(viewModel.state.value.showFavAlbumsSection)
        verify { storage.put(SecureStorage.KEY_HOME_SHOW_FAV_ALBUMS, "false") }
    }

    @Test
    fun `setShowFavRadioSection persists to storage`() {
        viewModel.setShowFavRadioSection(false)
        assertFalse(viewModel.state.value.showFavRadioSection)
        verify { storage.put(SecureStorage.KEY_HOME_SHOW_FAV_RADIO, "false") }
    }

    @Test
    fun `stored home visibility prefs are restored on init`() {
        every { storage.get(SecureStorage.KEY_HOME_SHOW_PLAYLISTS) } returns "false"
        every { storage.get(SecureStorage.KEY_HOME_SHOW_FAV_ARTISTS) } returns "false"
        every { storage.get(SecureStorage.KEY_HOME_SHOW_FAV_ALBUMS) } returns "false"
        every { storage.get(SecureStorage.KEY_HOME_SHOW_FAV_RADIO) } returns "false"
        val vm =
            createViewModel()
        assertFalse(vm.state.value.showPlaylistsOnHome)
        assertFalse(vm.state.value.showFavArtistsSection)
        assertFalse(vm.state.value.showFavAlbumsSection)
        assertFalse(vm.state.value.showFavRadioSection)
    }

    @Test
    fun `setDownloadMobileData updates state and DownloadManager`() {
        viewModel.setDownloadMobileData(false)
        assertFalse(DownloadManager.allowMobileData)
        assertFalse(viewModel.state.value.downloadMobileData)
    }

    @Test
    fun `setAudioCacheQuota updates state, persists bytes and refreshes evictor`() {
        viewModel.setAudioCacheQuota(1500)
        assertEquals(1500, viewModel.state.value.audioCacheQuotaMb)
        verify { storage.put(SecureStorage.KEY_AUDIO_CACHE_MAX_BYTES, (1500L * 1024 * 1024).toString()) }
        verify { audioCacheEvictor.refresh() }
    }

    @Test
    fun `setAudioCacheQuota clamps to 500MB-10GB range`() {
        viewModel.setAudioCacheQuota(100)
        assertEquals(500, viewModel.state.value.audioCacheQuotaMb)
        viewModel.setAudioCacheQuota(50_000)
        assertEquals(10_240, viewModel.state.value.audioCacheQuotaMb)
    }

    @Test
    fun `clearAutoCache calls cacheService`() = runTest(testDispatcher) {
        viewModel.clearAutoCache()
        advanceUntilIdle()
        coVerify { cacheService.clearAutoCache() }
    }

    @Test
    fun `clearDownloads calls cacheService`() = runTest(testDispatcher) {
        viewModel.clearDownloads()
        advanceUntilIdle()
        coVerify { cacheService.clearDownloads() }
    }

    // ── Cover Art Cache Quota ────────────────────────────────────────────────

    @Test
    fun `setCoverArtQuota updates state and fallback service`() {
        every { coverArtFallback.maxCacheBytes = any() } just Runs

        viewModel.setCoverArtQuota(500)

        assertEquals(500, viewModel.state.value.coverArtQuotaMb)
        verify { coverArtFallback.maxCacheBytes = 500L * 1024 * 1024 }
        verify { coverArtFallback.evictIfNeeded() }
        verify { storage.put(SecureStorage.KEY_COVER_ART_QUOTA_MB, "500") }
    }

    @Test
    fun `refresh loads quota from SecureStorage`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_AUDIO_CACHE_MAX_BYTES) } returns (2000L * 1024 * 1024).toString()
        every { storage.get(SecureStorage.KEY_COVER_ART_QUOTA_MB) } returns "500"
        coEvery { metadataDao.albumCount() } returns 120
        every { metadataSyncWorker.status } returns
            kotlinx.coroutines.flow.MutableStateFlow(com.lucasdss.ftpmusic.app.data.db.SyncStatus())
        coEvery { metadataDao.artistCount() } returns 80
        coEvery { metadataDao.cachedTrackCount() } returns 960
        coEvery { playlistDao.count() } returns 5
        coEvery { trackDao.getDownloadedCount() } returns 42
        coEvery { lyricsCacheDao.count() } returns 15

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(120, state.albumCount)
        assertEquals(80, state.artistCount)
        assertEquals(960, state.cachedTrackCount)
        assertEquals(5, state.playlistCount)
        assertEquals(42, state.downloadedTrackCount)
        assertEquals(15, state.lyricsCount)
    }

    @Test
    fun `clearCoverArtCache delegates to service and refreshes`() = runTest(testDispatcher) {
        every { coverArtFallback.clearCache() } just Runs

        viewModel.clearCoverArtCache()
        advanceUntilIdle()

        verify { coverArtFallback.clearCache() }
        verify { coverArtFallback.getCacheSizeBytes() }
    }

    @Test
    fun `initial state uses fallback service default quota`() {
        every { coverArtFallback.maxCacheBytes } returns 314572800L // 300MB
        val vm =
            createViewModel()

        assertEquals(300, vm.state.value.coverArtQuotaMb)
    }

    // ── Queue Journal Cap ────────────────────────────────────────────────────

    @Test
    fun `initial state uses default journalCap of 100`() {
        assertEquals(100, viewModel.state.value.journalCap)
    }

    @Test
    fun `setJournalCap updates state`() {
        viewModel.setJournalCap(200)
        assertEquals(200, viewModel.state.value.journalCap)
        verify { playbackManager.setJournalCap(200) }
    }

    @Test
    fun `setJournalCap clamps below minimum to 10`() {
        viewModel.setJournalCap(5)
        assertEquals(10, viewModel.state.value.journalCap)
        verify { playbackManager.setJournalCap(10) }
    }

    @Test
    fun `setJournalCap clamps above maximum to 500`() {
        viewModel.setJournalCap(1000)
        assertEquals(500, viewModel.state.value.journalCap)
        verify { playbackManager.setJournalCap(500) }
    }

    @Test
    fun `setJournalCap persists to SecureStorage`() {
        viewModel.setJournalCap(50)
        verify { storage.put(SecureStorage.KEY_QUEUE_JOURNAL_CAP, "50") }
    }

    @Test
    fun `initial journal cap reads from SecureStorage`() {
        every { storage.get(SecureStorage.KEY_QUEUE_JOURNAL_CAP) } returns "200"
        val vm =
            createViewModel()
        assertEquals(200, vm.state.value.journalCap)
        verify { playbackManager.setJournalCap(200) }
    }

    // ── Library Metrics ───────────────────────────────────────────────────────

    @Test
    fun `library metrics show album and artist counts after refresh`() = runTest(testDispatcher) {
        coEvery { metadataDao.albumCount() } returns 1240
        every { metadataSyncWorker.status } returns
            kotlinx.coroutines.flow.MutableStateFlow(com.lucasdss.ftpmusic.app.data.db.SyncStatus())
        coEvery { metadataDao.artistCount() } returns 890

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(1240, viewModel.state.value.albumCount)
        assertEquals(890, viewModel.state.value.artistCount)
    }

    @Test
    fun `library metrics show last sync time from SharedPrefs`() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()
        every { prefs.getLong("last_metadata_sync_ms", any()) } returns now
        every { prefs.getLong("metadata_sync_duration_ms", any()) } returns 45000

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(now, viewModel.state.value.lastMetadataSyncMs)
        assertEquals(45000, viewModel.state.value.metadataSyncDurationMs)
    }

    @Test
    fun `library metrics show cover art cache size`() = runTest(testDispatcher) {
        every { coverArtFallback.getCacheSizeBytes() } returns 45L * 1024 * 1024 // 45 MB

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(45L * 1024 * 1024, viewModel.state.value.coverArtCacheBytes)
    }

    @Test
    fun `library metrics show cached and downloaded music bytes`() = runTest(testDispatcher) {
        every { cacheService.getAutoCacheBytes() } returns 2L * 1024 * 1024 * 1024 + 300L * 1024 * 1024 // ~2.3 GB
        every { cacheService.getDownloadBytes() } returns 500L * 1024 * 1024 // 500 MB

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(2L * 1024 * 1024 * 1024 + 300L * 1024 * 1024, viewModel.state.value.autoCacheBytes)
        assertEquals(500L * 1024 * 1024, viewModel.state.value.downloadBytes)
    }

    @Test
    fun `library metrics show lyrics count`() = runTest(testDispatcher) {
        coEvery { lyricsCacheDao.count() } returns 142

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(142, viewModel.state.value.lyricsCount)
    }

    @Test
    fun `library metrics show genre count after refresh`() = runTest(testDispatcher) {
        coEvery { genreMixDao.genreCount() } returns 25

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(25, viewModel.state.value.genreCount)
    }

    @Test
    fun `library metrics last lyrics fetch time from SharedPrefs`() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()
        every { prefs.getLong("last_lyrics_fetch_ms", any()) } returns now

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(now, viewModel.state.value.lastLyricsFetchMs)
    }

    // ── Utility Functions ─────────────────────────────────────────────────────

    @Test
    fun `formatBytes returns correct values`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("500 B", formatBytes(500))
        assertEquals("1 KB", formatBytes(1024))
        assertEquals("45 MB", formatBytes(45L * 1024 * 1024))
        assertEquals("1 GB", formatBytes(1024L * 1024 * 1024))
        assertEquals("2 GB", formatBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `timeAgo returns correct relative strings`() {
        val now = System.currentTimeMillis()
        assertEquals("0s ago", timeAgo(now))
        assertEquals("45s ago", timeAgo(now - 45_000))
        assertEquals("2m ago", timeAgo(now - 2 * 60_000))
        assertEquals("59m ago", timeAgo(now - 59 * 60_000))
        assertEquals("1h ago", timeAgo(now - 60 * 60_000))
        assertEquals("5h ago", timeAgo(now - 5 * 60 * 60_000))
        assertEquals("2d ago", timeAgo(now - 2 * 24 * 60 * 60_000))
        assertEquals("just now", timeAgo(now + 5000)) // future time
    }

    @Test
    fun `formatDuration returns correct strings`() {
        // Long literals: disambiguate from ProfileScreen's formatDuration(seconds: Int) overload
        assertEquals("0s", formatDuration(0L))
        assertEquals("45s", formatDuration(45_000L))
        assertEquals("1m 30s", formatDuration(90_000L))
        assertEquals("2m", formatDuration(120_000L))
        assertEquals("5m", formatDuration(300_000L))
    }

    // ── v46: Playback notifications feature toggle ─────────────────────

    @Test
    fun `playback notifications default to enabled`() {
        assertTrue(viewModel.state.value.playbackNotificationsEnabled)
    }

    @Test
    fun `setPlaybackNotificationsEnabled persists to storage`() {
        viewModel.setPlaybackNotificationsEnabled(false)
        assertFalse(viewModel.state.value.playbackNotificationsEnabled)
        verify { storage.put(SecureStorage.KEY_PLAYBACK_NOTIFICATIONS, "false") }
    }

    @Test
    fun `stored playback notifications pref is restored on init`() {
        every { storage.get(SecureStorage.KEY_PLAYBACK_NOTIFICATIONS) } returns "false"
        val vm =
            createViewModel()
        assertFalse(vm.state.value.playbackNotificationsEnabled)
    }
}
