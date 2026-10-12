package com.lucasdss.ftpmusic.app.data.network

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Deezer public Search API (ADR-0130).
 *
 * No API key for catalog search. ~50 req/5s per IP — we budget ~40/5s.
 * Quota may arrive as HTTP 200 + `{"error":{"code":4}}` — treat as backoff, not empty.
 */
@Singleton
class DeezerService @Inject constructor() {

    companion object {
        private const val BASE = "https://api.deezer.com"
        private const val WINDOW_MS = 5_000L
        private const val MAX_IN_WINDOW = 40
        private const val QUOTA_BACKOFF_MS = 2_000L
    }

    data class ArtistHit(val id: String, val name: String)
    data class AlbumHit(val id: String, val title: String, val artistName: String?)
    data class TrackHit(val id: String, val title: String, val artistName: String?, val albumName: String?)

    class QuotaException(message: String) : Exception(message)

    private val rateMutex = Mutex()
    private val requestTimestamps = ArrayDeque<Long>()

    private val rateLimiter = Interceptor { chain ->
        // Timestamps updated in acquireSlot before call; interceptor is passthrough marker.
        chain.proceed(chain.request())
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(rateLimiter)
        .build()

    suspend fun searchArtists(query: String, limit: Int = 8): List<ArtistHit> = search("artist", query, limit) { obj ->
        ArtistHit(
            id = obj.optLong("id").toString(),
            name = obj.optString("name"),
        ).takeIf { it.name.isNotBlank() && it.id != "0" }
    }

    suspend fun searchAlbums(query: String, limit: Int = 8): List<AlbumHit> = search("album", query, limit) { obj ->
        val artist = obj.optJSONObject("artist")?.optString("name")?.takeIf { it.isNotBlank() }
        AlbumHit(
            id = obj.optLong("id").toString(),
            title = obj.optString("title"),
            artistName = artist,
        ).takeIf { it.title.isNotBlank() && it.id != "0" }
    }

    suspend fun searchTracks(query: String, limit: Int = 8): List<TrackHit> = search("track", query, limit) { obj ->
        val artist = obj.optJSONObject("artist")?.optString("name")?.takeIf { it.isNotBlank() }
        val album = obj.optJSONObject("album")?.optString("title")?.takeIf { it.isNotBlank() }
        TrackHit(
            id = obj.optLong("id").toString(),
            title = obj.optString("title"),
            artistName = artist,
            albumName = album,
        ).takeIf { it.title.isNotBlank() && it.id != "0" }
    }

    private suspend fun <T> search(type: String, query: String, limit: Int, map: (JSONObject) -> T?): List<T> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            try {
                acquireSlot()
                val url = "$BASE/search/$type?q=" +
                    java.net.URLEncoder.encode(query, "UTF-8") +
                    "&limit=$limit"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "ftpmusic/1.0 (music player; contact: local)")
                    .build()
                val body = client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        if (resp.code == 429) {
                            delay(QUOTA_BACKOFF_MS)
                            throw QuotaException("HTTP 429")
                        }
                        return@withContext emptyList()
                    }
                    resp.body?.string() ?: return@withContext emptyList()
                }
                parseHits(body, map)
            } catch (e: QuotaException) {
                emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }

    internal fun <T> parseHits(body: String, map: (JSONObject) -> T?): List<T> {
        val root = JSONObject(body)
        val err = root.optJSONObject("error")
        if (err != null) {
            val code = err.optInt("code", -1)
            if (code == 4) throw QuotaException("Deezer quota code=4")
            return emptyList()
        }
        val data = root.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<T>(data.length())
        for (i in 0 until data.length()) {
            val obj = data.optJSONObject(i) ?: continue
            map(obj)?.let { out.add(it) }
        }
        return out
    }

    private suspend fun acquireSlot() {
        rateMutex.withLock {
            val now = System.currentTimeMillis()
            while (requestTimestamps.isNotEmpty() && now - requestTimestamps.first() > WINDOW_MS) {
                requestTimestamps.removeFirst()
            }
            if (requestTimestamps.size >= MAX_IN_WINDOW) {
                val wait = WINDOW_MS - (now - requestTimestamps.first()) + 50
                if (wait > 0) delay(wait)
                val after = System.currentTimeMillis()
                while (requestTimestamps.isNotEmpty() && after - requestTimestamps.first() > WINDOW_MS) {
                    requestTimestamps.removeFirst()
                }
            }
            requestTimestamps.addLast(System.currentTimeMillis())
        }
    }
}
