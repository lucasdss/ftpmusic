package com.lucasdss.ftpmusic.app

import android.app.Application
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.lucasdss.ftpmusic.app.data.cache.CacheService
import com.lucasdss.ftpmusic.app.data.cache.DownloadManager
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.MetadataSyncWorker
import com.lucasdss.ftpmusic.app.data.db.PlaylistSyncWorker
import com.lucasdss.ftpmusic.app.data.db.SyncScheduleWorker
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class FtpmusicApp : Application() {

    @Inject lateinit var downloadManager: DownloadManager

    @Inject lateinit var cacheService: CacheService

    @Inject lateinit var playlistSyncWorker: PlaylistSyncWorker

    @Inject lateinit var metadataSyncWorker: MetadataSyncWorker

    @Inject lateinit var storage: SecureStorage

    @Inject lateinit var offlineModeManager: OfflineModeManager

    @Inject lateinit var serverConfigStore: com.lucasdss.ftpmusic.app.di.ServerConfigStore

    override fun onCreate() {
        super.onCreate()
        // Single authoritative server-config restore — runs before any worker,
        // ViewModel, playback or UI composition. A transient read failure keeps
        // the store uninitialized so splash/MediaService/UI can retry.
        try {
            serverConfigStore.initialize()
        } catch (e: Exception) {
            Log.w("ftpmusic-network", "Server config restore failed: ${e.message}")
        }
        // Restore the persisted offline toggle BEFORE any worker starts, so the
        // download manager, sync workers and scrobble service all respect it
        // from the first network call. (SettingsViewModel also re-applies it on
        // settings open — this is the process-death safety net.)
        try {
            offlineModeManager.initialize()
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-offline", "Offline restore failed: ${e.message}")
        }
        // v47: restore "Download on Wi-Fi only" so auto-cache and downloads
        // keep the user's data preference after process death.
        try {
            DownloadManager.allowMobileData =
                storage.get(SecureStorage.KEY_DOWNLOAD_MOBILE_DATA)?.toBooleanStrictOrNull() ?: true
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-download", "Mobile-data preference restore failed: ${e.message}")
        }
        // v1.0.0: Remote Library Management (yt-dlp) removed. Purge any
        // credentials stored by pre-release builds of that feature — the
        // feature is unreachable now, so stale secrets must not linger.
        try {
            storage.removeRemoteSourceKeys()
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-secure", "Remote-source key cleanup failed: ${e.message}")
        }
        // Configure Coil with a dedicated disk cache for cover art.
        // 500MB ≈ 3000 album covers at 600px — prevents thrashing on large libraries.
        try {
            coil.ImageLoader.Builder(this)
                .diskCache {
                    coil.disk.DiskCache.Builder()
                        .directory(cacheDir.resolve("coil_cover_cache"))
                        .maxSizeBytes(500L * 1024 * 1024)
                        .build()
                }
                .crossfade(true)
                .apply {
                    if (BuildConfig.IMAGE_DIAGNOSTICS) {
                        logger(coil.util.DebugLogger())
                    }
                }
                .build()
                .let { coil.Coil.setImageLoader(it) }
            if (BuildConfig.IMAGE_DIAGNOSTICS) {
                Log.w("ftpmusic-images", "[diag] Coil ImageLoader installed (DebugLogger on)")
            }
        } catch (e: Exception) {
            if (BuildConfig.IMAGE_DIAGNOSTICS) {
                Log.w(
                    "ftpmusic-images",
                    "[diag] Coil ImageLoader setup FAILED: ${e.javaClass.simpleName}: ${e.message}",
                )
            }
        }
        // Start download worker — processes cache-warming, play-queue pre-fetch, and user downloads
        downloadManager.start()
        // Start playlist sync worker — flushes pending changes to server
        playlistSyncWorker.start()
        // Start metadata sync — initial run, then periodic via WorkManager
        metadataSyncWorker.start()
        schedulePeriodicMetadataSync()
        // Initialize auto-cache byte counters for LRU eviction
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                cacheService.initialize()
                Log.d("ftpmusic-cache", "[App] CacheService initialized")
            } catch (e: Exception) {
                Log.w("ftpmusic-cache", "[App] CacheService init failed: ${e.message}")
            }
        }
    }

    private fun schedulePeriodicMetadataSync() {
        try {
            val hours = storage.get(SecureStorage.KEY_SYNC_INTERVAL_HOURS)?.toIntOrNull()?.coerceIn(1, 24) ?: 12
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicWork = PeriodicWorkRequestBuilder<SyncScheduleWorker>(
                hours.toLong(),
                TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .addTag("metadata_sync")
                .build()

            WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                    "metadata_sync",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    periodicWork,
                )
        } catch (e: IllegalStateException) {
            // WorkManager not available (unit test environment)
            Log.d("ftpmusic-work", "WorkManager not available: ${e.message}")
        }
    }
}
