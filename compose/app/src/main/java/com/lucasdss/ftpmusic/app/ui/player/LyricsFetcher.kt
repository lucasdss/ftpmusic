package com.lucasdss.ftpmusic.app.ui.player

import com.google.gson.Gson
import com.lucasdss.ftpmusic.app.data.cache.LocalOnlyPolicy
import com.lucasdss.ftpmusic.app.data.db.LyricsCacheDao
import com.lucasdss.ftpmusic.app.data.db.LyricsCacheEntity
import com.lucasdss.ftpmusic.app.di.NetworkAvailabilityHolder

/** Display payload for Now Playing lyrics overlay. */
internal data class LyricsDisplay(val lines: List<LyricLine> = emptyList(), val text: String? = null) {
    val isSynced: Boolean get() = lines.isNotEmpty() && lines.any { it.timeMs > 0L }
}

/**
 * Cache / network resolution for a track's lyrics.
 *
 * @property display UI payload (may be empty while loading miss path).
 * @property fromCache true when served from Room without a blocking network call.
 * @property needsBackgroundRefresh true when cache hit is older than [CACHE_TTL_MS].
 */
internal data class LyricsResolveResult(
    val display: LyricsDisplay,
    val fromCache: Boolean,
    val needsBackgroundRefresh: Boolean = false,
)

/**
 * On-demand lyrics fetch + Room cache. Testable without NavHost / Compose.
 * Network still keyed by artist+title (no getLyricsBySongId this pass).
 */
internal object LyricsFetcher {

    const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L

    private val gson = Gson()

    fun isCacheStale(fetchedAt: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs - fetchedAt > CACHE_TTL_MS

    /**
     * Resolve lyrics for a track: version eviction, cache hit (+ optional reparse),
     * or miss marker. Does not perform network I/O.
     *
     * Reparse only for legacy raw-only rows (`syncedLinesJson` and `unstructuredText`
     * both null). Negative / unstructured hits skip reparse to avoid Gson+put every open.
     */
    suspend fun resolveFromCache(
        trackId: String,
        dao: LyricsCacheDao,
        nowMs: Long = System.currentTimeMillis(),
    ): LyricsResolveResult? {
        val cached = dao.get(trackId) ?: return null
        if (cached.cacheVersion < LyricsCacheEntity.CURRENT_CACHE_VERSION) {
            dao.delete(trackId)
            return null
        }
        var display = displayFromEntity(cached)
        val needsReparse = cached.syncedLinesJson == null &&
            cached.unstructuredText == null &&
            cached.rawJson != null
        if (needsReparse) {
            val upgraded = reparseFromRaw(cached.rawJson!!, trackId, dao)
            if (upgraded != null) {
                display = upgraded
            }
        }
        return LyricsResolveResult(
            display = display,
            fromCache = true,
            needsBackgroundRefresh = isCacheStale(cached.fetchedAt, nowMs),
        )
    }

    /** Network fetch allowed only when not Simulate Offline and OS has INTERNET. */
    fun shouldFetchLyricsOverNetwork(
        isOffline: Boolean,
        hasOsNetwork: Boolean = NetworkAvailabilityHolder.hasOsNetwork.value,
    ): Boolean = !LocalOnlyPolicy.isLocalOnly(isOffline, hasOsNetwork)

    fun displayFromEntity(cached: LyricsCacheEntity): LyricsDisplay {
        if (cached.syncedLinesJson != null) {
            val lines = try {
                gson.fromJson(cached.syncedLinesJson, Array<LyricLine>::class.java)?.toList().orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
            if (lines.isNotEmpty() && isTrulySynced(lines)) {
                return LyricsDisplay(lines = lines, text = null)
            }
            // Stale fake-sync (all timeMs==0) → prefer unstructured if present
            if (!cached.unstructuredText.isNullOrEmpty()) {
                return LyricsDisplay(lines = emptyList(), text = cached.unstructuredText)
            }
            if (lines.isNotEmpty()) {
                val joined = lines.joinToString("\n") { it.text }
                return LyricsDisplay(lines = emptyList(), text = joined.ifBlank { null })
            }
        }
        return LyricsDisplay(lines = emptyList(), text = cached.unstructuredText)
    }

    /**
     * Fetch from Subsonic getLyrics, parse, cache, return display.
     * @param getLyrics injectible network call for unit tests.
     * @param markFetched optional prefs side-effect (last_lyrics_fetch_ms).
     */
    suspend fun fetchAndCache(
        artist: String,
        title: String,
        trackId: String?,
        dao: LyricsCacheDao,
        getLyrics: suspend (artist: String, title: String) -> Map<String, Any>,
        markFetched: (() -> Unit)? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): LyricsDisplay {
        val response = getLyrics(artist, title)
        val display = parseResponse(response)
        putCache(trackId, artist, title, response, display, dao, nowMs)
        markFetched?.invoke()
        return display
    }

    /** Pure parse of a Subsonic getLyrics response map. */
    fun parseResponse(response: Map<String, Any>): LyricsDisplay {
        val sr = response["subsonic-response"] as? Map<*, *>
        val lyricsData = sr?.get("lyrics") as? Map<*, *>
        return parseLyricsData(lyricsData)
    }

    fun parseLyricsData(lyricsData: Map<*, *>?): LyricsDisplay {
        val structuredLines = lyricsData?.get("line") as? List<*>
        if (structuredLines != null && structuredLines.isNotEmpty()) {
            val rawLines = structuredLines.mapNotNull { line ->
                val m = line as? Map<*, *> ?: return@mapNotNull null
                val start = (m["start"] as? Number)?.toLong() ?: 0L
                val value = m["value"] as? String ?: return@mapNotNull null
                Pair(start, value)
            }
            if (rawLines.isEmpty()) {
                return unstructuredFromMap(lyricsData)
            }
            var lyricLines = rawLines.map { (start, value) ->
                LyricLine(start, cleanLyricText(value))
            }
            if (lyricLines.all { it.timeMs == 0L }) {
                val combinedRaw = rawLines.joinToString("\n") { it.second }
                val lrcParsed = parseLrcText(combinedRaw)
                if (lrcParsed.isNotEmpty() && isTrulySynced(lrcParsed)) {
                    lyricLines = lrcParsed
                } else {
                    // No real timing — fall through to unstructured (no fake sync)
                    val plain = rawLines.joinToString("\n") { cleanLyricText(it.second) }
                    return LyricsDisplay(lines = emptyList(), text = plain.ifBlank { null })
                }
            }
            return LyricsDisplay(lines = lyricLines, text = null)
        }
        return unstructuredFromMap(lyricsData)
    }

    private fun unstructuredFromMap(lyricsData: Map<*, *>?): LyricsDisplay {
        val text = lyricsData?.get("value") as? String
            ?: (lyricsData?.get("text") as? String)
        if (text == null) {
            return LyricsDisplay(lines = emptyList(), text = null)
        }
        val lrcParsed = parseLrcText(text)
        return when {
            lrcParsed.isNotEmpty() && isTrulySynced(lrcParsed) ->
                LyricsDisplay(lines = lrcParsed, text = null)

            else ->
                LyricsDisplay(lines = emptyList(), text = text)
        }
    }

    private fun isTrulySynced(lines: List<LyricLine>): Boolean = lines.any { it.timeMs > 0L }

    private suspend fun putCache(
        trackId: String?,
        artist: String,
        title: String,
        response: Map<String, Any>,
        display: LyricsDisplay,
        dao: LyricsCacheDao,
        nowMs: Long,
    ) {
        if (trackId == null) return
        val rawJson = gson.toJson(response)
        val entity = when {
            display.lines.isNotEmpty() && isTrulySynced(display.lines) -> LyricsCacheEntity(
                trackId = trackId,
                artist = artist,
                title = title,
                rawJson = rawJson,
                syncedLinesJson = gson.toJson(display.lines),
                unstructuredText = null,
                fetchedAt = nowMs,
            )

            else -> LyricsCacheEntity(
                trackId = trackId,
                artist = artist,
                title = title,
                rawJson = rawJson,
                syncedLinesJson = null,
                // Negative cache: empty string when no lyrics so offline remiss stops
                unstructuredText = display.text ?: "",
                fetchedAt = nowMs,
            )
        }
        dao.put(entity)
    }

    /**
     * Re-parse saved raw API JSON when parsing logic improved.
     * Returns upgraded display when synced lines found; null if nothing to write.
     */
    suspend fun reparseFromRaw(rawJson: String, trackId: String, dao: LyricsCacheDao): LyricsDisplay? {
        return try {
            @Suppress("UNCHECKED_CAST")
            val response = gson.fromJson(rawJson, Map::class.java) as? Map<String, Any> ?: return null
            val display = parseResponse(response)
            if (display.lines.isNotEmpty() && isTrulySynced(display.lines)) {
                val existing = dao.get(trackId)
                dao.put(
                    LyricsCacheEntity(
                        trackId = trackId,
                        artist = existing?.artist,
                        title = existing?.title,
                        rawJson = rawJson,
                        syncedLinesJson = gson.toJson(display.lines),
                        unstructuredText = null,
                        fetchedAt = existing?.fetchedAt ?: System.currentTimeMillis(),
                    ),
                )
                display
            } else if (display.text != null) {
                val existing = dao.get(trackId)
                dao.put(
                    LyricsCacheEntity(
                        trackId = trackId,
                        artist = existing?.artist,
                        title = existing?.title,
                        rawJson = rawJson,
                        syncedLinesJson = null,
                        unstructuredText = display.text,
                        fetchedAt = existing?.fetchedAt ?: System.currentTimeMillis(),
                    ),
                )
                display
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
