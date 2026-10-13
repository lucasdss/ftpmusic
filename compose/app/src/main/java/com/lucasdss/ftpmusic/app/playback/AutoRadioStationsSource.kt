package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.cache.LocalOnlyPolicy
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.RadioFavoriteDao
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Station row for Android Auto Live Radio (ADR-0131). */
data class AutoRadioStation(val id: String, val name: String, val streamUrl: String, val homePageUrl: String? = null)

/**
 * Network-first internet radio list with [RadioFavoriteDao] offline fallback.
 */
fun interface AutoRadioStationsSource {
    suspend fun loadStations(): List<AutoRadioStation>
}

class DefaultAutoRadioStationsSource(
    private val api: SubsonicApi,
    private val authHelper: SubsonicAuthHelper,
    private val radioFavoriteDao: RadioFavoriteDao,
    private val offlineModeManager: OfflineModeManager? = null,
    private val cacheTtlMs: Long = 60_000L,
    private val isLocalOnly: () -> Boolean = {
        LocalOnlyPolicy.isLocalOnly(offlineModeManager?.isOfflineEnabled() == true)
    },
) : AutoRadioStationsSource {
    @Volatile
    private var cachedAtMs: Long = 0L

    @Volatile
    private var cachedStations: List<AutoRadioStation> = emptyList()

    private val loadMutex = Mutex()

    override suspend fun loadStations(): List<AutoRadioStation> = loadMutex.withLock {
        val now = System.currentTimeMillis()
        // ADR-0132: local-only / simulate-offline → bookmarks only (no network, no
        // stale network-station cache from before the policy flip).
        if (isLocalOnly()) {
            return loadBookmarks(now)
        }
        if (cachedStations.isNotEmpty() && now - cachedAtMs < cacheTtlMs) {
            return cachedStations
        }
        val username = SubsonicCredentials.username
        val password = SubsonicCredentials.password
        if (username.isNotEmpty() &&
            password.isNotEmpty() &&
            DynamicBaseUrl.isConfigured()
        ) {
            try {
                val auth = authHelper.buildAuthParams(username, password)
                val response = api.getInternetRadioStations(auth)
                val parsed = parseStations(response)
                if (parsed.isNotEmpty()) {
                    cachedStations = parsed
                    cachedAtMs = now
                    return parsed
                }
            } catch (e: Exception) {
                android.util.Log.w("ftpmusic", "[AutoRadio] network load failed: ${e.message}")
            }
        }
        return loadBookmarks(now)
    }

    private suspend fun loadBookmarks(now: Long): List<AutoRadioStation> {
        val bookmarks = radioFavoriteDao.getAll().map {
            AutoRadioStation(
                id = it.stationId,
                name = it.name,
                streamUrl = it.streamUrl,
                homePageUrl = it.homePageUrl,
            )
        }.filter { it.streamUrl.isNotBlank() }
        if (bookmarks.isNotEmpty()) {
            cachedStations = bookmarks
            cachedAtMs = now
        }
        return bookmarks
    }

    companion object {
        fun parseStations(response: Map<String, Any>): List<AutoRadioStation> {
            val sr = response["subsonic-response"] as? Map<*, *> ?: return emptyList()
            val radioData = sr["internetRadioStations"] as? Map<*, *> ?: return emptyList()
            val raw = when (val stations = radioData["internetRadioStation"]) {
                is List<*> -> stations
                is Map<*, *> -> listOf(stations)
                else -> emptyList<Any>()
            }
            return raw.mapNotNull { s ->
                val m = s as? Map<*, *> ?: return@mapNotNull null
                val streamUrl = m["streamUrl"] as? String ?: return@mapNotNull null
                if (streamUrl.isBlank()) return@mapNotNull null
                AutoRadioStation(
                    id = stationIdOf(m["id"]) ?: streamUrl,
                    name = m["name"] as? String ?: "Unknown Station",
                    streamUrl = streamUrl,
                    homePageUrl = m["homePageUrl"] as? String,
                )
            }
        }

        /** Coerce JSON String / Number ids (Navidrome often emits numeric ids). */
        fun stationIdOf(raw: Any?): String? = when (raw) {
            is String -> raw.takeIf { it.isNotBlank() }
            is Number -> raw.toString()
            else -> null
        }
    }
}
