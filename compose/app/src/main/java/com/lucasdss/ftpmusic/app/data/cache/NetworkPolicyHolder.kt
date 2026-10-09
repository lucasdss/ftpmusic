package com.lucasdss.ftpmusic.app.data.cache

import android.content.Context
import android.net.ConnectivityManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.lucasdss.ftpmusic.app.data.db.SyncScheduleWorker
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live prefs + transport for sync / cellular media policy (ADR-0105).
 * Mirrors [OfflineModeManager] restore-at-boot pattern.
 */
@Singleton
class NetworkPolicyHolder @Inject constructor(
    private val storage: SecureStorage,
    @ApplicationContext private val context: Context,
) {
    private val connectivity by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val _cellularMediaPolicy = MutableStateFlow(CellularMediaPolicy.AUTO_CACHE)
    val cellularMediaPolicy: StateFlow<CellularMediaPolicy> = _cellularMediaPolicy.asStateFlow()

    private val _librarySyncWifiOnly = MutableStateFlow(false)
    val librarySyncWifiOnly: StateFlow<Boolean> = _librarySyncWifiOnly.asStateFlow()

    /** Restore prefs + transport. Call after SecureStorage ready, before workers. */
    fun initialize() {
        val policy = resolveInitialPolicy()
        val syncWifiOnly =
            storage.get(SecureStorage.KEY_LIBRARY_SYNC_WIFI_ONLY)?.toBooleanStrictOrNull() ?: false
        _cellularMediaPolicy.value = policy
        _librarySyncWifiOnly.value = syncWifiOnly
        NetworkPolicyState.cellularMediaPolicy = policy
        NetworkPolicyState.librarySyncWifiOnly = syncWifiOnly
        refreshTransport()
        // Keep DownloadManager.allowMobileData in sync for any residual readers.
        DownloadManager.allowMobileData = policy != CellularMediaPolicy.MINIMAL &&
            policy != CellularMediaPolicy.LOCAL_ONLY
        android.util.Log.i(
            "ftpmusic-netpolicy",
            "Restored cellularMedia=$policy syncWifiOnly=$syncWifiOnly wifiOrEth=${NetworkPolicyState.wifiOrEthernet}",
        )
    }

    fun refreshTransport() {
        try {
            NetworkPolicyState.updateTransport(connectivity)
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-netpolicy", "Transport refresh failed: ${e.message}")
        }
    }

    fun setCellularMediaPolicy(policy: CellularMediaPolicy) {
        _cellularMediaPolicy.value = policy
        NetworkPolicyState.cellularMediaPolicy = policy
        storage.put(SecureStorage.KEY_CELLULAR_MEDIA_POLICY, policy.storageKey)
        DownloadManager.allowMobileData = policy == CellularMediaPolicy.AUTO_CACHE
        android.util.Log.i("ftpmusic-netpolicy", "cellularMediaPolicy=$policy")
    }

    fun setLibrarySyncWifiOnly(enabled: Boolean) {
        _librarySyncWifiOnly.value = enabled
        NetworkPolicyState.librarySyncWifiOnly = enabled
        storage.put(SecureStorage.KEY_LIBRARY_SYNC_WIFI_ONLY, enabled.toString())
        rescheduleMetadataSync()
        android.util.Log.i("ftpmusic-netpolicy", "librarySyncWifiOnly=$enabled")
    }

    fun isCellularActive(): Boolean {
        refreshTransport()
        return NetworkPolicyState.isCellularActive()
    }

    fun shouldWarnManualResyncOnCellular(): Boolean = librarySyncWifiOnly.value && isCellularActive()

    fun shouldWarnFirstLoginOnCellular(): Boolean = isCellularActive()

    fun workManagerNetworkType(): NetworkType =
        if (librarySyncWifiOnly.value) NetworkType.UNMETERED else NetworkType.CONNECTED

    fun rescheduleMetadataSync() {
        try {
            val hours = storage.get(SecureStorage.KEY_SYNC_INTERVAL_HOURS)?.toIntOrNull()?.coerceIn(1, 24) ?: 12
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(workManagerNetworkType())
                .build()
            val periodicWork = PeriodicWorkRequestBuilder<SyncScheduleWorker>(
                hours.toLong(),
                TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .addTag("metadata_sync")
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "metadata_sync",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    periodicWork,
                )
        } catch (e: IllegalStateException) {
            android.util.Log.d("ftpmusic-work", "WorkManager not available: ${e.message}")
        }
    }

    private fun resolveInitialPolicy(): CellularMediaPolicy {
        val stored = CellularMediaPolicy.fromStorage(
            storage.get(SecureStorage.KEY_CELLULAR_MEDIA_POLICY),
        )
        if (stored != null) return stored
        val legacy = storage.get(SecureStorage.KEY_DOWNLOAD_MOBILE_DATA)?.toBooleanStrictOrNull()
        val migrated = CellularMediaPolicy.fromLegacyDownloadMobileData(legacy ?: true)
        storage.put(SecureStorage.KEY_CELLULAR_MEDIA_POLICY, migrated.storageKey)
        // Drop legacy key so a wiped new-key cannot re-migrate from a stale boolean.
        if (legacy != null) {
            storage.remove(SecureStorage.KEY_DOWNLOAD_MOBILE_DATA)
        }
        return migrated
    }
}
