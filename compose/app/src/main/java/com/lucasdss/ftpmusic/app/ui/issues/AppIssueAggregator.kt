package com.lucasdss.ftpmusic.app.ui.issues

import android.content.Context
import com.lucasdss.ftpmusic.app.R
import com.lucasdss.ftpmusic.app.data.cache.CellularMediaPolicy
import com.lucasdss.ftpmusic.app.data.cache.NetworkPolicyHolder
import com.lucasdss.ftpmusic.app.data.cache.NetworkPolicyState
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntity
import com.lucasdss.ftpmusic.app.data.update.AppUpdateAvailabilityHolder
import com.lucasdss.ftpmusic.app.data.update.AvailableUpdate
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.NetworkAvailabilityHolder
import com.lucasdss.ftpmusic.app.di.ReachabilityStateHolder
import com.lucasdss.ftpmusic.app.playback.PlayerHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Combines connectivity / config / playback / playlist-conflict / update signals
 * into a single [issues] flow for the AppHeader status tray (ADR-0120 / ADR-0140).
 *
 * ADR-0122: fail-soft — Room/Flow failures must never kill the process on
 * cold start (1.10.1 upgrade force-close). CEH + [catch] keep the tray empty.
 */
@Singleton
class AppIssueAggregator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val offlineModeManager: OfflineModeManager,
    private val networkPolicyHolder: NetworkPolicyHolder,
    private val playlistDao: PlaylistDao,
    private val updateAvailabilityHolder: AppUpdateAvailabilityHolder,
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, t ->
        android.util.Log.e("ftpmusic-issues", "aggregator failed: ${t.message}", t)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

    private val debouncedOsNetwork: Flow<Boolean> =
        NetworkAvailabilityHolder.hasOsNetwork.debounceAfterFirst(DEFAULT_DEBOUNCE_MS)

    private val debouncedReachable: Flow<Boolean> =
        ReachabilityStateHolder.isReachable.debounceAfterFirst(DEFAULT_DEBOUNCE_MS)

    private val conflictedFlow: Flow<List<ConflictedPlaylist>> =
        mapConflictedFailSoft(playlistDao.observeConflicted())

    private data class ConnSlice(
        val hasOs: Boolean,
        val reachable: Boolean,
        val offline: Boolean,
        val loopback: Boolean,
    )

    private data class RestSlice(
        val cellularPolicy: CellularMediaPolicy,
        val playback: String?,
        val conflicts: List<ConflictedPlaylist>,
        val update: AvailableUpdate?,
    )

    private val connFlow: Flow<ConnSlice> = combine(
        debouncedOsNetwork,
        debouncedReachable,
        offlineModeManager.isOffline,
        DynamicBaseUrl.loopbackFlow,
    ) { hasOs, reachable, offline, loopback ->
        ConnSlice(hasOs, reachable, offline, loopback)
    }

    private val restFlow: Flow<RestSlice> = combine(
        networkPolicyHolder.cellularMediaPolicy,
        PlayerHolder.playbackErrorFlow,
        conflictedFlow,
        updateAvailabilityHolder.available,
    ) { policy, playback, conflicts, update ->
        RestSlice(policy, playback, conflicts, update)
    }

    private val snapshotFlow: Flow<AppIssueSnapshot> = combine(connFlow, restFlow) { conn, rest ->
        networkPolicyHolder.refreshTransport()
        AppIssueSnapshot(
            hasOsNetwork = conn.hasOs,
            isReachable = conn.reachable,
            isOffline = conn.offline,
            isLoopback = conn.loopback,
            loopbackUrlShown = DynamicBaseUrl.url.trimEnd('/'),
            cellularHardLocal = NetworkPolicyState.isCellularHardLocal(),
            playbackError = rest.playback,
            conflictedPlaylists = rest.conflicts,
            updateAvailable = rest.update,
        )
    }

    val issues: StateFlow<List<AppIssue>> = snapshotFlow
        .map { snap -> buildLocalized(snap) }
        .catch { e ->
            android.util.Log.w("ftpmusic-issues", "issues map failed: ${e.message}")
            emit(emptyList())
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val headerState: StateFlow<AppIssueHeaderState> = issues
        .map { list -> toHeaderState(list) }
        .catch { e ->
            android.util.Log.w("ftpmusic-issues", "headerState map failed: ${e.message}")
            emit(
                AppIssueHeaderState(
                    issues = emptyList(),
                    iconKind = AppIssueIconKind.WARNING,
                    showBadge = false,
                    badgeCount = 0,
                    contentDescription = "",
                ),
            )
        }
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            AppIssueHeaderState(
                issues = emptyList(),
                iconKind = AppIssueIconKind.WARNING,
                showBadge = false,
                badgeCount = 0,
                contentDescription = context.getString(R.string.issue_status_cd_none),
            ),
        )

    fun toHeaderState(list: List<AppIssue>): AppIssueHeaderState {
        val icon = resolveHeaderIcon(list)
        val primary = list.firstOrNull()?.title.orEmpty()
        val cd = when {
            list.isEmpty() -> context.getString(R.string.issue_status_cd_none)
            list.size == 1 -> context.getString(R.string.issue_status_cd_one, primary)
            else -> context.getString(R.string.issue_status_cd_many, list.size, primary)
        }
        return AppIssueHeaderState(
            issues = list,
            iconKind = icon,
            showBadge = list.size >= 2,
            badgeCount = list.size,
            contentDescription = cd,
        )
    }

    private fun buildLocalized(snap: AppIssueSnapshot): List<AppIssue> {
        val shown = snap.loopbackUrlShown.ifBlank { null }
        val configBody = if (shown != null) {
            context.getString(R.string.issue_config_body, shown)
        } else {
            context.getString(R.string.issue_config_body_blank)
        }
        return buildAppIssues(
            snapshot = snap,
            noNetworkTitle = context.getString(R.string.issue_no_network_title),
            noNetworkBody = context.getString(R.string.issue_no_network_body),
            unreachableTitle = context.getString(R.string.issue_unreachable_title),
            unreachableBody = context.getString(R.string.issue_unreachable_body),
            offlineTitle = context.getString(R.string.issue_offline_title),
            offlineBody = context.getString(R.string.issue_offline_body),
            configTitle = context.getString(R.string.issue_config_title),
            configBody = configBody,
            cellularTitle = context.getString(R.string.issue_cellular_local_title),
            cellularBody = context.getString(R.string.issue_cellular_local_body),
            playbackTitle = context.getString(R.string.issue_playback_title),
            conflictTitle = context.getString(R.string.issue_playlist_conflict_title),
            conflictBodyFor = { pl ->
                if (pl.name.isNotBlank()) {
                    context.getString(R.string.issue_playlist_conflict_body, pl.name)
                } else {
                    context.getString(R.string.issue_playlist_conflict_body_generic)
                }
            },
            updateTitle = context.getString(R.string.issue_update_available_title),
            updateBody = context.getString(R.string.issue_update_available_body),
        )
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 2_500L
    }
}

/** ADR-0122: Room observeConflicted failures → empty list, never crash. */
internal fun mapConflictedFailSoft(upstream: Flow<List<PlaylistEntity>>): Flow<List<ConflictedPlaylist>> = upstream
    .map { rows ->
        rows.map { ConflictedPlaylist(it.id, it.name, it.conflictMessage) }
    }
    .catch { e ->
        android.util.Log.w(
            "ftpmusic-issues",
            "observeConflicted failed: ${e.message}",
        )
        emit(emptyList())
    }

/**
 * Emit first value immediately; subsequent changes wait [timeoutMs] of quiet.
 * Avoids cold-start delay while still killing connectivity flicker.
 */
internal fun <T> Flow<T>.debounceAfterFirst(timeoutMs: Long): Flow<T> {
    if (timeoutMs <= 0L) return this
    return callbackFlow {
        var first = true
        var job: Job? = null
        val collectJob = launch {
            collect { value ->
                if (first) {
                    first = false
                    send(value)
                } else {
                    job?.cancel()
                    job = launch {
                        delay(timeoutMs)
                        send(value)
                    }
                }
            }
        }
        awaitClose {
            job?.cancel()
            collectJob.cancel()
        }
    }
}
