package com.lucasdss.ftpmusic.app.data.network

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * ListenBrainz popularity / metadata lookup (ADR-0130).
 * Not a general text-search provider — requires MBID.
 * Missing stats → null (callers hide badge; never fake "0").
 * Cancels / transport failures are NOT cached as definitive null.
 */
@Singleton
class ListenBrainzService @Inject constructor() {

    companion object {
        private const val BASE = "https://api.listenbrainz.org/1"
        private const val CACHE_TTL_MS = 30 * 60 * 1000L
        private const val MIN_INTERVAL_MS = 700L

        /** Cap Discover fan-out — serial rate-limited calls otherwise stall UI. */
        const val DISCOVER_FANOUT = 3
    }

    data class CachedCount(val count: Long?, val fetchedAt: Long)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, CachedCount>()
    private val rateMutex = Mutex()
    private var lastRequestMs = 0L

    /** Total listen count for an artist MBID, or null if unknown / error. */
    suspend fun artistListenCount(mbid: String): Long? = withContext(Dispatchers.IO) {
        val key = mbid.trim().lowercase()
        if (key.isEmpty()) return@withContext null
        cachedOrFetch(key) { rateLimitedGet("$BASE/popularity/artist/$key") }
    }

    /** Recording listen count by recording MBID. */
    suspend fun recordingListenCount(mbid: String): Long? = withContext(Dispatchers.IO) {
        val trimmed = mbid.trim().lowercase()
        if (trimmed.isEmpty()) return@withContext null
        cachedOrFetch("rec:$trimmed") {
            rateLimitedGet("$BASE/popularity/recording/$trimmed")
        }
    }

    internal fun parsePopularityTotal(body: String): Long? {
        if (body.isBlank()) return null
        val root = JSONObject(body)
        when {
            root.has("total_listen_count") -> {
                val v = root.optLong("total_listen_count", -1L)
                return v.takeIf { it >= 0 }
            }

            root.has("listen_count") -> {
                val v = root.optLong("listen_count", -1L)
                return v.takeIf { it >= 0 }
            }

            root.has("total") -> {
                val v = root.optLong("total", -1L)
                return v.takeIf { it >= 0 }
            }

            root.has("payload") -> {
                val payload = root.optJSONObject("payload") ?: return null
                val v = payload.optLong("total_listen_count", -1L)
                if (v >= 0) return v
                val v2 = payload.optLong("listen_count", -1L)
                return v2.takeIf { it >= 0 }
            }
        }
        return null
    }

    private suspend fun cachedOrFetch(cacheKey: String, fetch: suspend () -> Long?): Long? {
        val cached = cache[cacheKey]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.fetchedAt < CACHE_TTL_MS) {
            return cached.count
        }
        return try {
            val count = fetch()
            cache[cacheKey] = CachedCount(count, now)
            count
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Leave cache untouched so a later attempt can retry.
            null
        }
    }

    private suspend fun rateLimitedGet(url: String): Long? {
        rateMutex.withLock {
            val now = System.currentTimeMillis()
            val wait = MIN_INTERVAL_MS - (now - lastRequestMs)
            if (wait > 0) kotlinx.coroutines.delay(wait)
            lastRequestMs = System.currentTimeMillis()
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "ftpmusic/1.0 (music player; contact: local)")
            .build()
        val body = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string() ?: return null
        }
        return parsePopularityTotal(body)
    }
}
