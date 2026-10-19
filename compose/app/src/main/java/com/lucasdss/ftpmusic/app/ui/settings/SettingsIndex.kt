package com.lucasdss.ftpmusic.app.ui.settings

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.ui.graphics.vector.ImageVector
import com.lucasdss.ftpmusic.app.R
import com.lucasdss.ftpmusic.app.data.cache.CellularMediaPolicy
import com.lucasdss.ftpmusic.app.playback.OverwriteBehavior
import com.lucasdss.ftpmusic.app.ui.components.formatBytes
import java.net.URI

/**
 * Searchable Settings index. Every hub nav destination must appear here with keywords (D11).
 */
data class SettingNode(
    val id: String,
    val titleRes: Int,
    val groupRes: Int,
    val route: String,
    val icon: ImageVector,
    val keywords: List<String>,
    val testTag: String? = null,
    /** Optional default scroll target when opening this leaf from search. */
    val defaultAnchor: String? = null,
    /** Keyword fragment → scroll anchor (first match wins). */
    val keywordAnchors: Map<String, String> = emptyMap(),
    val summaryProvider: (SettingsUiState, Context) -> String,
)

object SettingsIndex {
    val nodes: List<SettingNode> = listOf(
        SettingNode(
            id = "server",
            titleRes = R.string.settings_row_server,
            groupRes = R.string.settings_group_account,
            route = SettingsRoutes.SERVER,
            icon = Icons.Default.Dns,
            keywords = listOf("server", "url", "password", "credentials", "navidrome", "login"),
            testTag = "settings_nav_server",
            summaryProvider = { state, ctx ->
                hostSummary(state.serverUrl)
                    ?: ctx.getString(R.string.settings_summary_server_empty)
            },
        ),
        SettingNode(
            id = "profile",
            titleRes = R.string.settings_row_profile,
            groupRes = R.string.settings_group_account,
            route = SettingsRoutes.PROFILE,
            icon = Icons.Default.Person,
            keywords = listOf("profile", "stats", "listening", "scrobble", "history"),
            testTag = "settings_nav_profile",
            summaryProvider = { _, ctx -> ctx.getString(R.string.settings_summary_profile) },
        ),
        SettingNode(
            id = "playback",
            titleRes = R.string.settings_row_playback,
            groupRes = R.string.settings_group_playback,
            route = SettingsRoutes.PLAYBACK,
            icon = Icons.Default.QueueMusic,
            keywords = listOf("queue", "autoplay", "continuous", "overwrite", "play all", "journal"),
            testTag = "settings_nav_playback",
            defaultAnchor = SettingsAnchors.CONTINUOUS_PLAY,
            keywordAnchors = mapOf(
                "continuous" to SettingsAnchors.CONTINUOUS_PLAY,
                "overwrite" to SettingsAnchors.CONTINUOUS_PLAY,
            ),
            summaryProvider = { state, ctx ->
                val overwrite = when (state.overwriteBehavior) {
                    OverwriteBehavior.ASK -> ctx.getString(R.string.settings_summary_overwrite_ask)
                    OverwriteBehavior.CLEAN -> ctx.getString(R.string.settings_summary_overwrite_clean)
                    OverwriteBehavior.PUSH -> ctx.getString(R.string.settings_summary_overwrite_ask)
                }
                val continuous = if (state.continuousPlayEnabled) {
                    ctx.getString(R.string.settings_summary_continuous_on)
                } else {
                    ctx.getString(R.string.settings_summary_continuous_off)
                }
                ctx.getString(R.string.settings_summary_playback_combo, overwrite, continuous)
            },
        ),
        SettingNode(
            id = "bluetooth",
            titleRes = R.string.settings_row_bluetooth,
            groupRes = R.string.settings_group_playback,
            route = SettingsRoutes.BLUETOOTH,
            icon = Icons.Default.Bluetooth,
            keywords = listOf("bluetooth", "bt", "a2dp", "car", "headset", "resume", "battery"),
            testTag = "settings_nav_bluetooth",
            defaultAnchor = SettingsAnchors.BT_RESUME,
            summaryProvider = { state, ctx ->
                if (!state.btResumeEnabled) {
                    ctx.getString(R.string.settings_summary_bt_off)
                } else if (state.btResumeMode ==
                    com.lucasdss.ftpmusic.app.playback.BtResumeMode.ANY
                ) {
                    ctx.getString(R.string.settings_summary_bt_any)
                } else {
                    ctx.getString(R.string.settings_summary_bt_devices, state.btSelectedMacs.size)
                }
            },
        ),
        SettingNode(
            id = "cast",
            titleRes = R.string.settings_row_cast,
            groupRes = R.string.settings_group_playback,
            route = SettingsRoutes.CAST,
            icon = Icons.Default.Cast,
            keywords = listOf("cast", "chromecast", "google cast", "http"),
            testTag = "settings_nav_cast",
            defaultAnchor = SettingsAnchors.CAST_FROM_PHONE,
            summaryProvider = { state, ctx ->
                when {
                    !state.castFromPhone -> ctx.getString(R.string.settings_summary_cast_off)
                    state.castDeviceName != null -> state.castDeviceName!!
                    else -> ctx.getString(R.string.settings_summary_cast_from_phone)
                }
            },
        ),
        SettingNode(
            id = "storage",
            titleRes = R.string.settings_row_storage,
            groupRes = R.string.settings_group_storage,
            route = SettingsRoutes.STORAGE,
            icon = Icons.Default.Storage,
            keywords = listOf("storage", "cache", "quota", "clear cache", "cover art", "cache size"),
            testTag = "settings_nav_storage",
            defaultAnchor = SettingsAnchors.CLEAR_CACHE,
            keywordAnchors = mapOf(
                "clear cache" to SettingsAnchors.CLEAR_CACHE,
                "cache size" to SettingsAnchors.CLEAR_CACHE,
            ),
            summaryProvider = { state, _ ->
                formatBytes(state.autoCacheBytes + state.downloadBytes)
            },
        ),
        SettingNode(
            id = "downloads",
            titleRes = R.string.settings_row_downloads,
            groupRes = R.string.settings_group_storage,
            route = SettingsRoutes.DOWNLOADS,
            icon = Icons.Default.DownloadDone,
            keywords = listOf("downloads", "offline", "pinned", "manage downloads"),
            testTag = "settings_downloads_manage",
            summaryProvider = { state, ctx ->
                ctx.getString(
                    R.string.settings_summary_downloads_tracks,
                    state.downloadedTrackCount,
                    formatBytes(state.downloadBytes),
                )
            },
        ),
        SettingNode(
            id = "network",
            titleRes = R.string.settings_row_network,
            groupRes = R.string.settings_group_network,
            route = SettingsRoutes.NETWORK,
            icon = Icons.Default.Cloud,
            keywords = listOf(
                "network", "cellular", "wifi", "wi-fi", "sync", "resync", "delta",
                "auto-download", "ttl", "interval", "mobile data",
            ),
            testTag = "settings_nav_network",
            defaultAnchor = SettingsAnchors.CELLULAR,
            keywordAnchors = mapOf(
                "cellular" to SettingsAnchors.CELLULAR,
                "resync" to SettingsAnchors.CELLULAR,
                "auto-download" to SettingsAnchors.CELLULAR,
            ),
            summaryProvider = { state, ctx ->
                when (state.cellularMediaPolicy) {
                    CellularMediaPolicy.AUTO_CACHE ->
                        ctx.getString(R.string.settings_summary_cellular_auto)

                    CellularMediaPolicy.MINIMAL ->
                        ctx.getString(R.string.settings_summary_cellular_minimal)

                    CellularMediaPolicy.LOCAL_ONLY ->
                        ctx.getString(R.string.settings_summary_cellular_local)
                }
            },
        ),
        SettingNode(
            id = "typography",
            titleRes = R.string.settings_row_typography,
            groupRes = R.string.settings_group_appearance,
            route = SettingsRoutes.TYPOGRAPHY,
            icon = Icons.Default.TextFields,
            keywords = listOf("typography", "font", "density", "text size", "display size"),
            testTag = "settings_typography_nav",
            summaryProvider = { state, ctx ->
                ctx.getString(
                    R.string.settings_summary_typography,
                    state.typographyPrefs.density.label,
                )
            },
        ),
        SettingNode(
            id = "appearance",
            titleRes = R.string.settings_row_appearance,
            groupRes = R.string.settings_group_appearance,
            route = SettingsRoutes.APPEARANCE,
            icon = Icons.Default.ListAlt,
            keywords = listOf(
                "appearance",
                "reactions",
                "duration",
                "track numbers",
                "list",
                "nav labels",
                "itunes",
                "lyrics search",
            ),
            testTag = "settings_nav_appearance",
            summaryProvider = { _, ctx -> ctx.getString(R.string.settings_summary_appearance) },
        ),
        SettingNode(
            id = "notifications",
            titleRes = R.string.settings_row_notifications,
            groupRes = R.string.settings_group_notifications,
            route = SettingsRoutes.NOTIFICATIONS,
            icon = Icons.Default.Notifications,
            keywords = listOf("notifications", "lock screen", "shade", "playback notification"),
            testTag = "settings_nav_notifications",
            defaultAnchor = SettingsAnchors.PLAYBACK_NOTIF,
            summaryProvider = { _, ctx -> ctx.getString(R.string.settings_summary_notifications) },
        ),
        SettingNode(
            id = "home",
            titleRes = R.string.settings_row_home,
            groupRes = R.string.settings_group_home,
            route = SettingsRoutes.HOME,
            icon = Icons.Default.Home,
            keywords = listOf("home", "favorites", "playlists", "artists", "albums", "radio"),
            testTag = "settings_nav_home",
            summaryProvider = { state, ctx ->
                val n = listOf(
                    state.showPlaylistsOnHome,
                    state.showFavArtistsSection,
                    state.showFavAlbumsSection,
                    state.showFavRadioSection,
                ).count { it }
                ctx.getString(R.string.settings_summary_home_sections, n)
            },
        ),
        SettingNode(
            id = "lastfm",
            titleRes = R.string.settings_row_lastfm,
            groupRes = R.string.settings_group_integrations,
            route = SettingsRoutes.LASTFM,
            icon = Icons.Default.Cloud,
            keywords = listOf("last.fm", "lastfm", "api key", "similar artists", "scrobble"),
            testTag = "settings_nav_lastfm",
            summaryProvider = { state, ctx ->
                if (state.lastFmKeySaved) {
                    ctx.getString(R.string.settings_summary_lastfm_saved)
                } else {
                    ctx.getString(R.string.settings_summary_lastfm_empty)
                }
            },
        ),
        SettingNode(
            id = "advanced",
            titleRes = R.string.settings_row_advanced,
            groupRes = R.string.settings_group_integrations,
            route = SettingsRoutes.ADVANCED,
            icon = Icons.Default.Http,
            keywords = listOf("headers", "http", "custom headers", "advanced"),
            testTag = "settings_nav_advanced",
            defaultAnchor = SettingsAnchors.CUSTOM_HEADERS,
            summaryProvider = { state, ctx ->
                ctx.getString(R.string.settings_summary_headers, state.customHeaders.size)
            },
        ),
        SettingNode(
            id = "about",
            titleRes = R.string.settings_row_about,
            groupRes = R.string.settings_group_about,
            route = SettingsRoutes.ABOUT,
            icon = Icons.Default.Info,
            keywords = listOf(
                "about",
                "version",
                "update",
                "diagnostics",
                "metrics",
                "simulate offline",
                "developer",
                "share log",
            ),
            testTag = "settings_nav_about",
            summaryProvider = { _, ctx -> ctx.getString(R.string.settings_summary_about) },
        ),
    )

    /**
     * Case-insensitive match on title (resolved), live summary, and curated keywords.
     */
    fun filter(query: String, state: SettingsUiState, context: Context): List<SettingNode> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return nodes
        return nodes.filter { node ->
            val title = context.getString(node.titleRes).lowercase()
            val summary = node.summaryProvider(state, context).lowercase()
            title.contains(q) ||
                summary.contains(q) ||
                node.keywords.any { it.lowercase().contains(q) || q.contains(it.lowercase()) }
        }
    }

    /** Routes that must be indexed (nested leaves + legacy hub destinations). */
    fun requiredIndexedRoutes(): Set<String> = SettingsRoutes.nestedLeaves.toSet() + setOf(
        SettingsRoutes.DOWNLOADS,
        SettingsRoutes.PROFILE,
    ) - setOf(SettingsRoutes.CUSTOM_MIXES) // customMixes reached from network page, not hub
}

private fun hostSummary(url: String): String? {
    if (url.isBlank()) return null
    return try {
        val host = URI(url.trim()).host
        host?.takeIf { it.isNotBlank() } ?: url.trim()
    } catch (_: Exception) {
        url.trim().take(40)
    }
}
