package com.lucasdss.ftpmusic.app.data.network

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * MusicBrainz Web Service v2 client (https://musicbrainz.org/ws/2/).
 *
 * Capabilities used:
 * - Search artist/release-group/recording by name to resolve MBIDs
 * - Lookup ratings (0–5, vote count) with inc=ratings
 *
 * Constraints honored:
 * - 1 request/second rate limit (MusicBrainz policy)
 * - Meaningful User-Agent header (required)
 *
 * No API key required. Free for non-commercial use.
 */
@Singleton
class MusicBrainzService @Inject constructor() {

    companion object {
        private const val BASE = "https://musicbrainz.org/ws/2"
        private const val APP_NAME = "ftpmusic"
        private const val APP_VERSION = "1.0"

        @Suppress("MayBeConst")
        private val MIN_INTERVAL_MS = 1000L // 1 req/sec
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Serializes requests to honor the 1 req/sec MusicBrainz policy. */
    private val rateLimitMutex = Mutex()
    private var lastRequestMs = 0L

    /** Data class holding a MusicBrainz rating result. */
    data class MusicBrainzRating(val value: Double?, val votes: Int?)

    /**
     * Enforce the 1 request/second rate limit by serializing all calls
     * and spacing them at least [MIN_INTERVAL_MS] apart.
     */
    private suspend fun <T> throttled(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        rateLimitMutex.withLock {
            val now = System.currentTimeMillis()
            val wait = lastRequestMs + MIN_INTERVAL_MS - now
            if (wait > 0) {
                delay(wait)
            }
            lastRequestMs = System.currentTimeMillis()
            block()
        }
    }

    private suspend fun getJson(url: String): JSONObject? = throttled {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "$APP_NAME/$APP_VERSION (music player; contact: local)")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@throttled null
                val body = response.body?.string() ?: return@throttled null
                JSONObject(body)
            }
        } catch (_: Exception) {
            null
        }
    }

    // ── Search (resolve MBID from name) ──────────────────────────────────

    /**
     * Search for an artist by name. Returns the top-scoring MBID or null.
     * Query format: /artist?query=artist:{name}&fmt=json&limit=1
     */
    suspend fun searchArtistMbid(artistName: String): String? {
        val query = encodeQueryParam(artistName)
        if (query.isEmpty()) return null
        val url = "$BASE/artist?query=artist:$query&fmt=json&limit=1"
        val json = getJson(url) ?: return null
        val artists = json.optJSONArray("artists") ?: return null
        if (artists.length() == 0) return null
        return artists.getJSONObject(0).optString("id").takeIf { it.isNotEmpty() }
    }

    /**
     * Search for a release-group (album) by artist + album name.
     * Query format: /release-group?query=artist:{a}+AND+releasegroup:{b}&fmt=json&limit=1
     */
    suspend fun searchAlbumMbid(artistName: String, albumName: String): String? {
        val artist = encodeQueryParam(artistName)
        val album = encodeQueryParam(albumName)
        if (artist.isEmpty() || album.isEmpty()) return null
        val url = "$BASE/release-group?query=artist:$artist+AND+releasegroup:$album&fmt=json&limit=1"
        val json = getJson(url) ?: return null
        val groups = json.optJSONArray("release-groups") ?: return null
        if (groups.length() == 0) return null
        return groups.getJSONObject(0).optString("id").takeIf { it.isNotEmpty() }
    }

    /**
     * Search for a recording (track) by artist + title.
     * Query format: /recording?query=artist:{a}+AND+recording:{t}&fmt=json&limit=1
     */
    suspend fun searchTrackMbid(artistName: String, trackTitle: String): String? {
        val artist = encodeQueryParam(artistName)
        val title = encodeQueryParam(trackTitle)
        if (artist.isEmpty() || title.isEmpty()) return null
        val url = "$BASE/recording?query=artist:$artist+AND+recording:$title&fmt=json&limit=1"
        val json = getJson(url) ?: return null
        val recordings = json.optJSONArray("recordings") ?: return null
        if (recordings.length() == 0) return null
        return recordings.getJSONObject(0).optString("id").takeIf { it.isNotEmpty() }
    }

    /** URL-encode a query term, mapping spaces to + per MusicBrainz Lucene query syntax. */
    private fun encodeQueryParam(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        // URLEncoder encodes space as + already; ensure no double-encoding issues
        return java.net.URLEncoder.encode(trimmed, "UTF-8")
    }

    // ── Rating lookups (inc=ratings) ─────────────────────────────────────

    /** Look up an artist's public rating by MBID. */
    suspend fun getArtistRating(mbid: String): MusicBrainzRating = lookupRating("artist", mbid)

    /** Look up a release-group's public rating by MBID. */
    suspend fun getAlbumRating(mbid: String): MusicBrainzRating = lookupRating("release-group", mbid)

    /** Look up a recording's public rating by MBID. */
    suspend fun getTrackRating(mbid: String): MusicBrainzRating = lookupRating("recording", mbid)

    private suspend fun lookupRating(entity: String, mbid: String): MusicBrainzRating {
        if (mbid.isBlank()) return MusicBrainzRating(null, null)
        val url = "$BASE/$entity/$mbid?inc=ratings&fmt=json"
        val json = getJson(url) ?: return MusicBrainzRating(null, null)
        val rootKey = when (entity) {
            "artist" -> "artist"
            "release-group" -> "release-group"
            "recording" -> "recording"
            else -> return MusicBrainzRating(null, null)
        }
        val entityObj = json.optJSONObject(rootKey) ?: json
        val rating = entityObj.optJSONObject("rating") ?: return MusicBrainzRating(null, null)
        val value = if (rating.has("value") && !rating.isNull("value")) rating.getDouble("value") else null
        val votes = if (rating.has("votes-count")) rating.getInt("votes-count") else null
        return MusicBrainzRating(value, votes)
    }

    // ── Convenience: resolve MBID + rating in one flow ───────────────────

    /** Search artist by name, then fetch its rating. Returns rating + resolved MBID. */
    suspend fun fetchArtistRating(artistName: String): Pair<MusicBrainzRating, String?> {
        val mbid = searchArtistMbid(artistName) ?: return Pair(MusicBrainzRating(null, null), null)
        val rating = getArtistRating(mbid)
        return Pair(rating, mbid)
    }

    /** Search album by artist+name, then fetch its rating. */
    suspend fun fetchAlbumRating(artistName: String, albumName: String): Pair<MusicBrainzRating, String?> {
        val mbid = searchAlbumMbid(artistName, albumName) ?: return Pair(MusicBrainzRating(null, null), null)
        val rating = getAlbumRating(mbid)
        return Pair(rating, mbid)
    }

    /** Search track by artist+title, then fetch its rating. */
    suspend fun fetchTrackRating(artistName: String, trackTitle: String): Pair<MusicBrainzRating, String?> {
        val mbid = searchTrackMbid(artistName, trackTitle) ?: return Pair(MusicBrainzRating(null, null), null)
        val rating = getTrackRating(mbid)
        return Pair(rating, mbid)
    }
}
