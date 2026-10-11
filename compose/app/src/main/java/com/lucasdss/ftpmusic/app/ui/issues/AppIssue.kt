package com.lucasdss.ftpmusic.app.ui.issues

/**
 * Global in-app issue for the AppHeader status tray (ADR-0120).
 * One row in the status sheet; aggregator may emit several at once.
 */
data class AppIssue(
    val id: String,
    val kind: AppIssueKind,
    val title: String,
    val body: String,
    val action: AppIssueAction = AppIssueAction.None,
    /** Lower = higher priority when picking the header icon for a single-kind set. */
    val priority: Int = kind.defaultPriority,
)

enum class AppIssueKind {
    NO_OS_NETWORK,
    SERVER_UNREACHABLE,
    SIMULATE_OFFLINE,
    CONFIG_LOOPBACK,
    CELLULAR_LOCAL_ONLY,
    PLAYBACK_ERROR,
    PLAYLIST_CONFLICT,
    ;

    val defaultPriority: Int
        get() = when (this) {
            NO_OS_NETWORK -> 10
            CONFIG_LOOPBACK -> 20
            SERVER_UNREACHABLE -> 30
            PLAYBACK_ERROR -> 40
            PLAYLIST_CONFLICT -> 50
            CELLULAR_LOCAL_ONLY -> 60
            SIMULATE_OFFLINE -> 70
        }
}

sealed class AppIssueAction {
    data object None : AppIssueAction()
    data object OpenSettings : AppIssueAction()
    data object DismissPlaybackError : AppIssueAction()
    data class OpenPlaylist(val playlistId: String) : AppIssueAction()
}

/** Which glyph the header icon shows (resolved from the active issue set). */
enum class AppIssueIconKind {
    WIFI_OFF,
    CLOUD_OFF,
    CLOUD_OFF_MUTED,
    WARNING,
    ERROR_OUTLINE,
    SYNC_PROBLEM,
}

data class AppIssueHeaderState(
    val issues: List<AppIssue>,
    val iconKind: AppIssueIconKind,
    val showBadge: Boolean,
    val badgeCount: Int,
    val contentDescription: String,
) {
    val visible: Boolean get() = issues.isNotEmpty()
}

fun resolveHeaderIcon(issues: List<AppIssue>): AppIssueIconKind {
    if (issues.isEmpty()) return AppIssueIconKind.WARNING
    val kinds = issues.map { it.kind }.toSet()
    if (kinds.size >= 2) return AppIssueIconKind.WARNING
    return when (kinds.first()) {
        AppIssueKind.NO_OS_NETWORK -> AppIssueIconKind.WIFI_OFF
        AppIssueKind.SERVER_UNREACHABLE -> AppIssueIconKind.CLOUD_OFF
        AppIssueKind.SIMULATE_OFFLINE -> AppIssueIconKind.CLOUD_OFF_MUTED
        AppIssueKind.CONFIG_LOOPBACK -> AppIssueIconKind.WARNING
        AppIssueKind.CELLULAR_LOCAL_ONLY -> AppIssueIconKind.CLOUD_OFF
        AppIssueKind.PLAYBACK_ERROR -> AppIssueIconKind.ERROR_OUTLINE
        AppIssueKind.PLAYLIST_CONFLICT -> AppIssueIconKind.SYNC_PROBLEM
    }
}

data class AppIssueSnapshot(
    val hasOsNetwork: Boolean = true,
    val isReachable: Boolean = true,
    val isOffline: Boolean = false,
    val isLoopback: Boolean = false,
    val loopbackUrlShown: String = "",
    val cellularHardLocal: Boolean = false,
    val playbackError: String? = null,
    val conflictedPlaylists: List<ConflictedPlaylist> = emptyList(),
)

data class ConflictedPlaylist(val id: String, val name: String, val conflictMessage: String? = null)

/**
 * Pure builder for unit tests — no debounce, no Flows.
 * Offline mode suppresses unreachable (ADR-0023 §4 / ADR-0120).
 */
fun buildAppIssues(
    snapshot: AppIssueSnapshot,
    noNetworkTitle: String,
    noNetworkBody: String,
    unreachableTitle: String,
    unreachableBody: String,
    offlineTitle: String,
    offlineBody: String,
    configTitle: String,
    configBody: String,
    cellularTitle: String,
    cellularBody: String,
    playbackTitle: String,
    conflictTitle: String,
    conflictBodyFor: (ConflictedPlaylist) -> String,
    settingsAction: AppIssueAction = AppIssueAction.OpenSettings,
): List<AppIssue> {
    val out = mutableListOf<AppIssue>()
    if (!snapshot.hasOsNetwork) {
        out += AppIssue(
            id = "no_os_network",
            kind = AppIssueKind.NO_OS_NETWORK,
            title = noNetworkTitle,
            body = noNetworkBody,
        )
    }
    if (snapshot.isOffline) {
        out += AppIssue(
            id = "simulate_offline",
            kind = AppIssueKind.SIMULATE_OFFLINE,
            title = offlineTitle,
            body = offlineBody,
            action = settingsAction,
        )
    }
    if (snapshot.isLoopback) {
        out += AppIssue(
            id = "config_loopback",
            kind = AppIssueKind.CONFIG_LOOPBACK,
            title = configTitle,
            body = configBody,
            action = settingsAction,
        )
    }
    // Unreachable only when not intentional offline and OS net present (or we still
    // want unreachable when OS net down? Plan: WifiOff for no OS; CloudOff when OS OK).
    if (!snapshot.isReachable && snapshot.hasOsNetwork && !snapshot.isOffline) {
        out += AppIssue(
            id = "server_unreachable",
            kind = AppIssueKind.SERVER_UNREACHABLE,
            title = unreachableTitle,
            body = unreachableBody,
            action = settingsAction,
        )
    }
    if (snapshot.cellularHardLocal && snapshot.hasOsNetwork && !snapshot.isOffline) {
        out += AppIssue(
            id = "cellular_local_only",
            kind = AppIssueKind.CELLULAR_LOCAL_ONLY,
            title = cellularTitle,
            body = cellularBody,
            action = settingsAction,
        )
    }
    val playback = snapshot.playbackError?.takeIf { it.isNotBlank() }
    if (playback != null) {
        out += AppIssue(
            id = "playback_error",
            kind = AppIssueKind.PLAYBACK_ERROR,
            title = playbackTitle,
            body = playback,
            action = AppIssueAction.DismissPlaybackError,
        )
    }
    for (pl in snapshot.conflictedPlaylists) {
        out += AppIssue(
            id = "playlist_conflict_${pl.id}",
            kind = AppIssueKind.PLAYLIST_CONFLICT,
            title = conflictTitle,
            body = conflictBodyFor(pl),
            action = AppIssueAction.OpenPlaylist(pl.id),
        )
    }
    return out.sortedBy { it.priority }
}
