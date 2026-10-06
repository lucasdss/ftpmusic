package com.lucasdss.ftpmusic.app.data.network

import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * last.fm API client for similar-artist lookups.
 *
 * endpoint: artist.getSimilar
 *   GET https://ws.audioscrobbler.com/2.0/
 *     ?method=artist.getSimilar&artist={name}&api_key={key}&format=json&limit=10
 *
 * Returns: {"similarartists":{"artist":[{"name":"X","mbid":"...","match":"0.85"}]}}
 *
 * Rate limit: 5 req/sec (last.fm policy). Free API key required (Settings → Last.fm).
 * Key is read per call so a mid-session Settings save takes effect (ADR-0044).
 */
@Singleton
class LastFmService @Inject constructor(private val storage: SecureStorage) {

    companion object {
        private const val BASE = "https://ws.audioscrobbler.com/2.0/"
    }

    data class SimilarArtist(val name: String, val mbid: String?, val match: Double?)

    /** Live Discover / tag browse hit (Phase-5). */
    data class SearchArtistHit(val name: String, val mbid: String?, val listeners: Long? = null)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Current API key from SecureStorage (trimmed). Empty ⇒ fetch short-circuits. */
    fun currentApiKey(): String = storage.get(SecureStorage.KEY_LASTFM_API_KEY)?.trim().orEmpty()

    /**
     * Fetch similar artists for a given artist name.
     * Returns an empty list if the API key is invalid, the artist is unknown,
     * or the network fails.
     */
    suspend fun fetchSimilarArtists(artistName: String, limit: Int = 8): List<SimilarArtist> =
        withContext(Dispatchers.IO) {
            val apiKey = currentApiKey()
            if (apiKey.isEmpty()) return@withContext emptyList()
            try {
                val url = "$BASE?method=artist.getSimilar&artist=" +
                    java.net.URLEncoder.encode(artistName, "UTF-8") +
                    "&api_key=$apiKey&format=json&limit=$limit"
                val request = Request.Builder().url(url).build()
                val body = client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    resp.body?.string() ?: return@withContext emptyList()
                }
                parseSimilarArtists(body)
            } catch (_: Exception) {
                emptyList()
            }
        }

    /**
     * Fetch top tags for an artist (Last.fm artist.getTopTags).
     * Returns empty if no API key or network failure.
     */
    suspend fun fetchArtistTopTags(artistName: String, limit: Int = 8): List<String> = withContext(Dispatchers.IO) {
        val apiKey = currentApiKey()
        if (apiKey.isEmpty() || artistName.isBlank()) return@withContext emptyList()
        try {
            val url = "$BASE?method=artist.getTopTags&artist=" +
                java.net.URLEncoder.encode(artistName, "UTF-8") +
                "&api_key=$apiKey&format=json"
            val request = Request.Builder().url(url).build()
            val body = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: return@withContext emptyList()
            }
            parseTopTags(body, limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Live artist.search for Discover (Phase-5). */
    suspend fun searchArtists(query: String, limit: Int = 8): List<SearchArtistHit> = withContext(Dispatchers.IO) {
        val apiKey = currentApiKey()
        if (apiKey.isEmpty() || query.isBlank()) return@withContext emptyList()
        try {
            val url = "$BASE?method=artist.search&artist=" +
                java.net.URLEncoder.encode(query, "UTF-8") +
                "&api_key=$apiKey&format=json&limit=$limit"
            val request = Request.Builder().url(url).build()
            val body = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: return@withContext emptyList()
            }
            parseArtistSearch(body, limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** tag.getTopArtists — boost Discover when user taps a tag chip (Phase-5). */
    suspend fun fetchTagTopArtists(tag: String, limit: Int = 8): List<SearchArtistHit> = withContext(Dispatchers.IO) {
        val apiKey = currentApiKey()
        if (apiKey.isEmpty() || tag.isBlank()) return@withContext emptyList()
        try {
            val url = "$BASE?method=tag.getTopArtists&tag=" +
                java.net.URLEncoder.encode(tag, "UTF-8") +
                "&api_key=$apiKey&format=json&limit=$limit"
            val request = Request.Builder().url(url).build()
            val body = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: return@withContext emptyList()
            }
            parseTagTopArtists(body, limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun parseArtistSearch(json: String, limit: Int = 8): List<SearchArtistHit> {
        return try {
            val root = JSONObject(json)
            val results = root.optJSONObject("results") ?: return emptyList()
            val matches = results.optJSONObject("artistmatches") ?: return emptyList()
            val artists = matches.optJSONArray("artist") ?: return emptyList()
            (0 until minOf(artists.length(), limit)).mapNotNull { i ->
                val a = artists.getJSONObject(i)
                val name = a.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val mbid = a.optString("mbid").takeIf { it.isNotBlank() }
                val listeners = a.optString("listeners").toLongOrNull()
                SearchArtistHit(name, mbid, listeners)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun parseTagTopArtists(json: String, limit: Int = 8): List<SearchArtistHit> {
        return try {
            val root = JSONObject(json)
            val topartists = root.optJSONObject("topartists") ?: return emptyList()
            val artists = topartists.optJSONArray("artist") ?: return emptyList()
            (0 until minOf(artists.length(), limit)).mapNotNull { i ->
                val a = artists.getJSONObject(i)
                val name = a.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val mbid = a.optString("mbid").takeIf { it.isNotBlank() }
                SearchArtistHit(name, mbid, null)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun parseTopTags(json: String, limit: Int = 8): List<String> {
        return try {
            val root = JSONObject(json)
            val toptags = root.optJSONObject("toptags") ?: return emptyList()
            val tags = toptags.optJSONArray("tag") ?: return emptyList()
            (0 until minOf(tags.length(), limit)).mapNotNull { i ->
                tags.getJSONObject(i).optString("name").takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Parse the last.fm response into SimilarArtist objects. */
    internal fun parseSimilarArtists(json: String): List<SimilarArtist> {
        return try {
            val root = JSONObject(json)
            val similar = root.optJSONObject("similarartists") ?: return emptyList()
            val artists = similar.optJSONArray("artist") ?: return emptyList()
            (0 until artists.length()).mapNotNull { i ->
                val a = artists.getJSONObject(i)
                val name = a.optString("name").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val mbid = a.optString("mbid").takeIf { it.isNotEmpty() }
                val match = if (a.has("match") && !a.isNull("match")) a.getDouble("match") else null
                SimilarArtist(name, mbid, match)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Parse the stored JSON array (produced by [toStoredJson]) back into objects. */
    internal fun parseStoredJson(json: String?): List<SimilarArtist> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val name = o.optString("name").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val mbid = o.optString("mbid").takeIf { it.isNotEmpty() }
                val match = if (o.has("match") && !o.isNull("match")) o.getDouble("match") else null
                SimilarArtist(name, mbid, match)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Serialize similar artists to the JSON format stored in cached_artists. */
    internal fun toStoredJson(artists: List<SimilarArtist>): String {
        val array = JSONArray()
        artists.forEach { sa ->
            val o = JSONObject()
            o.put("name", sa.name)
            sa.mbid?.let { o.put("mbid", it) }
            sa.match?.let { o.put("match", it) }
            array.put(o)
        }
        return array.toString()
    }
}
