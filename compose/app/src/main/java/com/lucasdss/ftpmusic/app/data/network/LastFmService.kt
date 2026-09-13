package com.lucasdss.ftpmusic.app.data.network

import android.content.Context
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * Rate limit: 5 req/sec (last.fm policy). Free API key required.
 */
@Singleton
class LastFmService @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        private const val BASE = "https://ws.audioscrobbler.com/2.0/"
    }

    data class SimilarArtist(val name: String, val mbid: String?, val match: Double?)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val apiKey: String by lazy {
        SecureStorage(context.applicationContext).get(SecureStorage.KEY_LASTFM_API_KEY)
            ?.trim()
            .orEmpty()
    }

    /**
     * Fetch similar artists for a given artist name.
     * Returns an empty list if the API key is invalid, the artist is unknown,
     * or the network fails.
     */
    suspend fun fetchSimilarArtists(artistName: String, limit: Int = 8): List<SimilarArtist> =
        withContext(Dispatchers.IO) {
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
