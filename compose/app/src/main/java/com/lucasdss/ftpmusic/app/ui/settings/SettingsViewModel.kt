package com.lucasdss.ftpmusic.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucasdss.ftpmusic.app.data.cache.CacheService
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService
import com.lucasdss.ftpmusic.app.data.cache.DownloadManager
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.GenreMixDao
import com.lucasdss.ftpmusic.app.data.db.LyricsCacheDao
import com.lucasdss.ftpmusic.app.data.db.MetadataSyncWorker
import com.lucasdss.ftpmusic.app.data.network.CustomHeadersInterceptor
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.playback.CastPreferences
import com.lucasdss.ftpmusic.app.playback.OverwriteBehavior
import com.lucasdss.ftpmusic.app.playback.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val autoCacheBytes: Long = 0,
    val downloadBytes: Long = 0,
    val audioCacheQuotaMb: Int = 2000,
    val coverArtQuotaMb: Int = 300,
    val coverArtCacheBytes: Long = 0,
    val castFromPhone: Boolean = false,
    val useHttpForCast: Boolean = true,
    val downloadMobileData: Boolean = true,
    val autoDownloadPlaylists: Boolean = true,
    val offlineMode: Boolean = false,
    val castDeviceName: String? = null,
    val customHeaders: List<Pair<String, String>> = emptyList(),
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val testResult: String? = null,
    val isTesting: Boolean = false,
    val journalCap: Int = 100,
    val continuousPlayEnabled: Boolean = true,
    // Library Metrics
    val albumCount: Int = 0,
    val artistCount: Int = 0,
    val cachedTrackCount: Int = 0,
    val playlistCount: Int = 0,
    val downloadedTrackCount: Int = 0,
    val lyricsCount: Int = 0,
    val genreCount: Int = 0,
    val lastMetadataSyncMs: Long = 0,
    val metadataSyncDurationMs: Long = 0,
    val lastLyricsFetchMs: Long = 0,
    val isResyncing: Boolean = false,
    val syncIntervalHours: Int = 12,
    val preferItunesArt: Boolean = false,
    val overwriteBehavior: OverwriteBehavior = OverwriteBehavior.ASK,
    // v43: Home & Favorites section visibility (default all ON)
    val showPlaylistsOnHome: Boolean = true,
    val showFavArtistsSection: Boolean = true,
    val showFavAlbumsSection: Boolean = true,
    val showFavRadioSection: Boolean = true,
    // v46: Playback notifications feature toggle (default ON). OFF still posts
    // the FGS-satisfying minimal notification, without media controls/art.
    val playbackNotificationsEnabled: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cacheService: CacheService,
    private val audioCacheEvictor: com.lucasdss.ftpmusic.app.data.cache.AdjustableCacheEvictor,
    private val castPreferences: CastPreferences,
    private val storage: SecureStorage,
    private val offlineModeManager: OfflineModeManager,
    private val coverArtFallback: CoverArtFallbackService,
    private val playbackManager: PlaybackManager,
    private val metadataDao: CachedMetadataDao,
    private val lyricsCacheDao: LyricsCacheDao,
    private val playlistDao: com.lucasdss.ftpmusic.app.data.db.PlaylistDao,
    private val genreMixDao: GenreMixDao,
    private val trackDao: com.lucasdss.ftpmusic.app.data.db.TrackDao,
    private val metadataSyncWorker: MetadataSyncWorker,
    private val serverConfigStore: com.lucasdss.ftpmusic.app.di.ServerConfigStore,
    private val serverProbe: com.lucasdss.ftpmusic.app.data.network.ServerProbe,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            coverArtQuotaMb = (coverArtFallback.maxCacheBytes / (1024 * 1024)).toInt(),
        ),
    )
    val state: StateFlow<SettingsUiState> = _state

    init {
        // Load persisted quotas
        val savedAudioCacheBytes = storage.get(SecureStorage.KEY_AUDIO_CACHE_MAX_BYTES)?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?: com.lucasdss.ftpmusic.app.data.cache.AdjustableCacheEvictor.DEFAULT_MAX_BYTES
        val savedCoverArtQuota = storage.get(SecureStorage.KEY_COVER_ART_QUOTA_MB)?.toIntOrNull() ?: 300
        coverArtFallback.maxCacheBytes = savedCoverArtQuota.toLong() * 1024 * 1024
        refresh()
        val headers = loadCustomHeaders()
        val savedJournalCap = storage.get(SecureStorage.KEY_QUEUE_JOURNAL_CAP)?.toIntOrNull()
        val resolvedJournalCap = savedJournalCap ?: 100
        playbackManager.setJournalCap(resolvedJournalCap)
        val savedContinuousPlay = storage.get(SecureStorage.KEY_CONTINUOUS_PLAY_ENABLED)?.toBooleanStrictOrNull()
        val resolvedContinuousPlay = savedContinuousPlay ?: true
        playbackManager.setContinuousPlayEnabled(resolvedContinuousPlay)
        val savedSyncInterval = storage.get(SecureStorage.KEY_SYNC_INTERVAL_HOURS)?.toIntOrNull()
        val savedPreferItunesArt = storage.get(SecureStorage.KEY_PREFER_ITUNES_ART)?.toBooleanStrictOrNull() ?: false
        val savedShowPlaylists = storage.get(SecureStorage.KEY_HOME_SHOW_PLAYLISTS)?.toBooleanStrictOrNull() ?: true
        val savedShowFavArtists = storage.get(SecureStorage.KEY_HOME_SHOW_FAV_ARTISTS)?.toBooleanStrictOrNull() ?: true
        val savedShowFavAlbums = storage.get(SecureStorage.KEY_HOME_SHOW_FAV_ALBUMS)?.toBooleanStrictOrNull() ?: true
        val savedShowFavRadio = storage.get(SecureStorage.KEY_HOME_SHOW_FAV_RADIO)?.toBooleanStrictOrNull() ?: true
        val savedPlaybackNotifications =
            storage.get(SecureStorage.KEY_PLAYBACK_NOTIFICATIONS)?.toBooleanStrictOrNull() ?: true
        // v47: restore the persisted Wi-Fi-only preference into the download
        // worker so auto-cache respects it after process death.
        val savedDownloadMobileData =
            storage.get(SecureStorage.KEY_DOWNLOAD_MOBILE_DATA)?.toBooleanStrictOrNull() ?: true
        DownloadManager.allowMobileData = savedDownloadMobileData
        _state.value = _state.value.copy(
            audioCacheQuotaMb = (savedAudioCacheBytes / (1024 * 1024)).toInt(),
            coverArtQuotaMb = savedCoverArtQuota,
            castFromPhone = castPreferences.castFromPhone,
            useHttpForCast = castPreferences.useHttpForCast,
            customHeaders = headers,
            autoDownloadPlaylists = storage.get("auto_download_playlists")?.toBooleanStrictOrNull() ?: true,
            downloadMobileData = savedDownloadMobileData,
            offlineMode = storage.get(SecureStorage.KEY_OFFLINE_MODE)?.toBooleanStrictOrNull() ?: false,
            serverUrl = storage.get(SecureStorage.KEY_URL) ?: "",
            username = storage.get(SecureStorage.KEY_USERNAME) ?: "",
            password = storage.get(SecureStorage.KEY_PASSWORD) ?: "",
            journalCap = resolvedJournalCap,
            continuousPlayEnabled = resolvedContinuousPlay,
            syncIntervalHours = savedSyncInterval ?: 12,
            preferItunesArt = savedPreferItunesArt,
            overwriteBehavior = com.lucasdss.ftpmusic.app.playback.OverwriteBehavior.fromKey(
                storage.get(SecureStorage.KEY_QUEUE_OVERWRITE_BEHAVIOR),
            ),
            showPlaylistsOnHome = savedShowPlaylists,
            showFavArtistsSection = savedShowFavArtists,
            showFavAlbumsSection = savedShowFavAlbums,
            showFavRadioSection = savedShowFavRadio,
            playbackNotificationsEnabled = savedPlaybackNotifications,
        )
        viewModelScope.launch {
            metadataSyncWorker.status.collect { s ->
                _state.value = _state.value.copy(isResyncing = s.isRunning)
            }
        }
    }

    private fun loadCustomHeaders(): List<Pair<String, String>> {
        val raw = storage.get("custom_headers") ?: return emptyList()
        val headers = raw.split("||").mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq > 0) part.substring(0, eq) to part.substring(eq + 1) else null
        }.filter { it.first.isNotBlank() && it.second.isNotBlank() }.take(5)
        CustomHeadersInterceptor.updateHeaders(headers)
        return headers
    }

    fun setCastFromPhone(enabled: Boolean) {
        castPreferences.castFromPhone = enabled
        _state.value = _state.value.copy(castFromPhone = enabled)
    }

    fun setUseHttpForCast(enabled: Boolean) {
        castPreferences.useHttpForCast = enabled
        _state.value = _state.value.copy(useHttpForCast = enabled)
    }

    fun setOfflineMode(enabled: Boolean) {
        _state.value = _state.value.copy(offlineMode = enabled)
        if (enabled) offlineModeManager.enable() else offlineModeManager.disable()
    }

    fun setDownloadMobileData(enabled: Boolean) {
        DownloadManager.allowMobileData = enabled
        storage.put(SecureStorage.KEY_DOWNLOAD_MOBILE_DATA, enabled.toString())
        _state.value = _state.value.copy(downloadMobileData = enabled)
    }

    fun setPreferItunesArt(enabled: Boolean) {
        _state.value = _state.value.copy(preferItunesArt = enabled)
        storage.put(SecureStorage.KEY_PREFER_ITUNES_ART, enabled.toString())
    }

    fun setAutoDownloadPlaylists(enabled: Boolean) {
        _state.value = _state.value.copy(autoDownloadPlaylists = enabled)
        storage.put("auto_download_playlists", enabled.toString())
    }

    // v43: Home section visibility toggles — persist to SecureStorage, apply live.
    fun setShowPlaylistsOnHome(enabled: Boolean) {
        _state.value = _state.value.copy(showPlaylistsOnHome = enabled)
        storage.put(SecureStorage.KEY_HOME_SHOW_PLAYLISTS, enabled.toString())
    }

    fun setShowFavArtistsSection(enabled: Boolean) {
        _state.value = _state.value.copy(showFavArtistsSection = enabled)
        storage.put(SecureStorage.KEY_HOME_SHOW_FAV_ARTISTS, enabled.toString())
    }

    fun setShowFavAlbumsSection(enabled: Boolean) {
        _state.value = _state.value.copy(showFavAlbumsSection = enabled)
        storage.put(SecureStorage.KEY_HOME_SHOW_FAV_ALBUMS, enabled.toString())
    }

    fun setShowFavRadioSection(enabled: Boolean) {
        _state.value = _state.value.copy(showFavRadioSection = enabled)
        storage.put(SecureStorage.KEY_HOME_SHOW_FAV_RADIO, enabled.toString())
    }

    // v46: Playback notifications feature toggle. OFF keeps the FGS-satisfying
    // minimal notification (silent, no controls/art) — Android's foreground
    // service contract forbids a completely absent notification while playing.
    fun setPlaybackNotificationsEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(playbackNotificationsEnabled = enabled)
        storage.put(SecureStorage.KEY_PLAYBACK_NOTIFICATIONS, enabled.toString())
    }

    fun setSyncIntervalHours(hours: Int) {
        val clamped = hours.coerceIn(1, 24)
        _state.value = _state.value.copy(syncIntervalHours = clamped)
        storage.put(SecureStorage.KEY_SYNC_INTERVAL_HOURS, clamped.toString())
        // Reschedule WorkManager with new interval
        androidx.work.WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "metadata_sync",
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                androidx.work.PeriodicWorkRequestBuilder<com.lucasdss.ftpmusic.app.data.db.SyncScheduleWorker>(
                    clamped.toLong(),
                    java.util.concurrent.TimeUnit.HOURS,
                )
                    .setConstraints(
                        androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build(),
                    )
                    .addTag("metadata_sync")
                    .build(),
            )
    }

    fun setCustomHeaders(headers: List<Pair<String, String>>) {
        CustomHeadersInterceptor.updateHeaders(headers)
        _state.value = _state.value.copy(customHeaders = headers)
        // Persist to SecureStorage
        val encoded = headers.joinToString("||") { "${it.first}=${it.second}" }
        storage.put("custom_headers", encoded)
    }

    fun refresh() {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences(MetadataSyncWorker.PREFS_NAME, Context.MODE_PRIVATE)
            val totalAlbums = metadataDao.albumCount()
            _state.value = _state.value.copy(
                autoCacheBytes = cacheService.getAutoCacheBytes(),
                downloadBytes = cacheService.getDownloadBytes(),
                coverArtCacheBytes = coverArtFallback.getCacheSizeBytes(),
                lastMetadataSyncMs = prefs.getLong("last_metadata_sync_ms", 0),
                metadataSyncDurationMs = prefs.getLong("metadata_sync_duration_ms", 0),
                lastLyricsFetchMs = prefs.getLong("last_lyrics_fetch_ms", 0),
                albumCount = totalAlbums,
                artistCount = metadataDao.artistCount(),
                cachedTrackCount = metadataDao.cachedTrackCount(),
                playlistCount = playlistDao.count(),
                downloadedTrackCount = trackDao.getDownloadedCount(),
                lyricsCount = lyricsCacheDao.count(),
                genreCount = genreMixDao.genreCount(),
            )
        }
    }

    /** Unified audio cache size (auto-cache + streaming; downloads are pinned and don't count).
     *  Range 500 MB – 10 GB. Applies immediately via the evictor. */
    fun setAudioCacheQuota(mb: Int) {
        val clamped = mb.coerceIn(500, 10_240)
        _state.value = _state.value.copy(audioCacheQuotaMb = clamped)
        storage.put(SecureStorage.KEY_AUDIO_CACHE_MAX_BYTES, (clamped.toLong() * 1024 * 1024).toString())
        // Notify evictor to re-read the limit and evict immediately if needed
        audioCacheEvictor.refresh()
    }

    fun setJournalCap(cap: Int) {
        val clamped = cap.coerceIn(10, 500)
        playbackManager.setJournalCap(clamped)
        _state.value = _state.value.copy(journalCap = clamped)
        storage.put(SecureStorage.KEY_QUEUE_JOURNAL_CAP, clamped.toString())
    }

    fun setContinuousPlayEnabled(enabled: Boolean) {
        playbackManager.setContinuousPlayEnabled(enabled)
        _state.value = _state.value.copy(continuousPlayEnabled = enabled)
        storage.put(SecureStorage.KEY_CONTINUOUS_PLAY_ENABLED, enabled.toString())
    }

    /** User-selected overwrite behavior when starting a context with a non-empty priority queue. */
    fun setOverwriteBehavior(behavior: com.lucasdss.ftpmusic.app.playback.OverwriteBehavior) {
        _state.value = _state.value.copy(overwriteBehavior = behavior)
        storage.put(SecureStorage.KEY_QUEUE_OVERWRITE_BEHAVIOR, behavior.key)
    }

    fun setCoverArtQuota(mb: Int) {
        coverArtFallback.maxCacheBytes = mb.toLong() * 1024 * 1024
        _state.value = _state.value.copy(coverArtQuotaMb = mb)
        coverArtFallback.evictIfNeeded()
        storage.put(SecureStorage.KEY_COVER_ART_QUOTA_MB, mb.toString())
    }

    fun clearCoverArtCache() {
        coverArtFallback.clearCache()
        refresh()
    }

    fun clearAutoCache() {
        viewModelScope.launch {
            cacheService.clearAutoCache()
            refresh()
        }
    }

    fun clearDownloads() {
        viewModelScope.launch {
            cacheService.clearDownloads()
            refresh()
        }
    }

    fun setServerUrl(url: String) {
        _state.value = _state.value.copy(serverUrl = url)
    }

    fun setUsername(username: String) {
        _state.value = _state.value.copy(username = username)
    }

    fun setPassword(password: String) {
        _state.value = _state.value.copy(password = password)
    }

    fun saveServerSettings(): Boolean {
        val s = _state.value
        val input = com.lucasdss.ftpmusic.app.ui.server.validateServerConnectionInput(
            s.serverUrl,
            s.username,
            s.password,
        ).getOrElse {
            _state.value = _state.value.copy(testResult = it.message)
            return false
        }
        // Persist atomically + publish so the UI recomposes with the new config.
        serverConfigStore.set(input.url, input.username, input.password)
        _state.value = _state.value.copy(
            serverUrl = input.url,
            username = input.username,
            testResult = "Settings saved",
        )
        return true
    }

    fun testConnection() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isTesting = true, testResult = null)
            val input = com.lucasdss.ftpmusic.app.ui.server.validateServerConnectionInput(
                _state.value.serverUrl,
                _state.value.username,
                _state.value.password,
            ).getOrElse {
                _state.value = _state.value.copy(testResult = it.message, isTesting = false)
                return@launch
            }
            try {
                // Probe with a throwaway client — never mutates the active config.
                serverProbe.ping(input.url, input.username, input.password)
                _state.value = _state.value.copy(testResult = "✓ Connection successful", isTesting = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    testResult = "✗ ${e.message?.take(80) ?: "Connection failed"}",
                    isTesting = false,
                )
            }
        }
    }
}
