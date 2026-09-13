package com.lucasdss.ftpmusic.app.data.cache

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Entry point for resolving the Hilt [CoverArtFallbackService] singleton. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CoverArtFallbackEntryPoint {
    fun coverArtFallbackService(): CoverArtFallbackService
}

/**
 * Fetches cover art from iTunes Search API and MusicBrainz Cover Art Archive
 * when Navidrome has no artwork for a track.
 *
 * Races both services in parallel — first to return wins. If the second service
 * returns a higher-resolution image, it replaces the first. Results are cached
 * to disk.
 *
 * All disk writes are atomic (temp file + validate + rename) so a killed
 * process can never leave a truncated image that is trusted forever.
 */
@Singleton
class CoverArtFallbackService @Inject constructor(@ApplicationContext private val context: Context) {
    companion object {
        @Volatile private var fallbackInstance: CoverArtFallbackService? = null

        /**
         * Process-wide instance. Prefers the Hilt singleton (same instance the
         * rest of the app injects) so cache-version signals are shared; falls
         * back to a locally constructed instance for plain-JVM tests.
         */
        fun getInstance(context: Context): CoverArtFallbackService {
            fallbackInstance?.let { return it }
            val app = context.applicationContext
            val hilt = try {
                EntryPointAccessors.fromApplication(app, CoverArtFallbackEntryPoint::class.java)
                    .coverArtFallbackService()
            } catch (_: Exception) {
                null
            }
            if (hilt != null) {
                fallbackInstance = hilt
                return hilt
            }
            return synchronized(this) {
                fallbackInstance ?: CoverArtFallbackService(app).also { fallbackInstance = it }
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val cacheDirFile: File by lazy { File(context.cacheDir, "covers") }

    internal val cacheDir: File
        get() = cacheDirFile.also { if (!it.isDirectory) it.mkdirs() }

    /** Service-owned scope so background downloads are not leaked per call. */
    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + Dispatchers.IO,
    )

    /**
     * Broad generation counter — bumped only on [clearCache], not on every
     * file write. Prefer [observeVersion] for scroll-sensitive UI so an
     * artist write cannot recompose every album/mix cell on Home.
     */
    val cacheVersionState = mutableIntStateOf(0)

    /**
     * Per disk-cache-key [MutableIntState] map. Each Compose cell reads only
     * its own state's `.intValue`, so writes to other keys never recompose it.
     *
     * Keys match on-disk naming prefixes before hash:
     * - `"artist|name"` / `"artist|album"` lowercase
     * - `"navidrome|<coverArtId>"` (id, not hash)
     */
    private val keyVersionStates = java.util.concurrent.ConcurrentHashMap<String, MutableIntState>()

    private val versionLock = Any()
    private val diskLock = Any()

    /** Snapshot read of one cache key's version (0 if never written). */
    fun observeVersion(cacheKey: String): Int = keyVersionStates.getOrPut(cacheKey) { mutableIntStateOf(0) }.intValue

    /** Stable disk identity for album / artist / navidrome cover files. */
    fun cacheKeyFor(artist: String?, album: String?, coverArtId: String?): String? = when {
        !artist.isNullOrBlank() && !album.isNullOrBlank() -> "$artist|$album".lowercase()
        !artist.isNullOrBlank() -> "artist|$artist".lowercase()
        !coverArtId.isNullOrBlank() -> "navidrome|$coverArtId"
        else -> null
    }

    private fun onFileCached(cacheKey: String) {
        synchronized(versionLock) {
            val state = keyVersionStates.getOrPut(cacheKey) { mutableIntStateOf(0) }
            state.intValue = state.intValue + 1
        }
    }

    /**
     * Validate + atomically publish image bytes to [file]. Rejects non-image
     * content types and undecodable payloads (e.g. Navidrome's HTTP 200 JSON
     * error bodies) so they are never written into the cache.
     */
    private fun writeImageAtomically(file: File, bytes: ByteArray, contentType: String?): Boolean {
        if (contentType != null && !contentType.startsWith("image/", ignoreCase = true)) return false
        if (bytes.isEmpty()) return false
        val tmp = File(file.parentFile, "${file.name}.tmp")
        return try {
            file.parentFile?.mkdirs()
            tmp.outputStream().use { it.write(bytes) }
            if (!CoverArtFiles.isUsableImage(tmp)) {
                tmp.delete()
                return false
            }
            if (file.exists()) file.delete()
            if (tmp.renameTo(file)) {
                true
            } else {
                tmp.delete()
                false
            }
        } catch (_: Exception) {
            tmp.delete()
            false
        }
    }

    /**
     * Cache Navidrome cover art to local disk for offline availability.
     * Called in background — the cover art will be available from disk on next render.
     */
    fun cacheNavidromeArt(coverArtId: String, imageUrl: String) {
        val cachedFile = File(cacheDir, "navidrome|${coverArtId.hashCode()}.jpg")
        if (CoverArtFiles.isUsableImage(cachedFile)) return
        CoverArtFiles.deleteIfUnusable(cachedFile)

        scope.launch {
            try {
                val request = Request.Builder().url(imageUrl).build()
                val response = client.newCall(request).execute()
                val body = response.body
                if (body == null) {
                    diag("cacheNavidromeArt EMPTY BODY id=$coverArtId code=${response.code}")
                    return@launch
                }
                val bytes = body.bytes()
                val ok = writeImageAtomically(cachedFile, bytes, response.header("Content-Type"))
                if (ok) {
                    onFileCached("navidrome|$coverArtId")
                    evictIfNeeded()
                    diag("cacheNavidromeArt OK id=$coverArtId size=${cachedFile.length()}")
                } else {
                    diag("cacheNavidromeArt REJECTED id=$coverArtId contentType=${response.header("Content-Type")}")
                }
            } catch (e: Exception) {
                diag("cacheNavidromeArt FAILED id=$coverArtId: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun diag(message: String) {
        if (com.lucasdss.ftpmusic.app.BuildConfig.IMAGE_DIAGNOSTICS) {
            android.util.Log.w("ftpmusic-images", "[diag] $message")
        }
    }

    /** In-memory LRU-ish cache: artist|album → best URL */
    private val backingCache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 128
    }
    private val urlCache = java.util.Collections.synchronizedMap(backingCache)

    /**
     * Fetches cover art from public services. Emits the first available URL,
     * then a higher-quality one if found by the second service.
     *
     * @return Flow of best cover art URL (or null if none found).
     */
    fun fetchArt(artist: String, album: String): Flow<String?> = flow {
        val cacheKey = "$artist|$album".lowercase()

        // Check in-memory cache first
        urlCache[cacheKey]?.let {
            emit(it)
            return@flow
        }

        // Check disk cache (validated — evict truncated/undecodable files)
        val cachedFile = File(cacheDir, "${cacheKey.hashCode()}.jpg")
        if (CoverArtFiles.isUsableImage(cachedFile)) {
            val url = "file://${cachedFile.absolutePath}"
            urlCache[cacheKey] = url
            emit(url)
            return@flow
        }
        CoverArtFiles.deleteIfUnusable(cachedFile)

        var bestUrl: String? = null
        var bestSize = 0

        // Race iTunes and MusicBrainz in parallel
        coroutineScope {
            val deferredItunes = async(Dispatchers.IO) { fetchITunes(artist, album) }
            val deferredMb = async(Dispatchers.IO) { fetchMusicBrainz(artist, album) }

            // Wait up to 8s for the first result
            val first = withTimeoutOrNull(8000) {
                select {
                    deferredItunes.onAwait { it }
                    deferredMb.onAwait { it }
                }
            }

            if (first != null) {
                bestUrl = first
                bestSize = getImageSize(first)
                urlCache[cacheKey] = bestUrl!!
                emit(bestUrl)
            }

            // Wait for the second service (up to another 8s)
            val second = withTimeoutOrNull(8000) {
                if (deferredItunes.isCompleted) {
                    deferredMb.await()
                } else {
                    deferredItunes.await()
                }
            }

            if (second != null) {
                val secondSize = getImageSize(second)
                if (secondSize > bestSize) {
                    bestUrl = second
                    bestSize = secondSize
                    urlCache[cacheKey] = bestUrl!!
                    emit(bestUrl)
                }
            }
        }

        // Cache best URL to disk for future use
        if (bestUrl != null && !bestUrl.startsWith("file://")) {
            if (downloadToFile(bestUrl, cachedFile)) {
                onFileCached(cacheKey)
            }
        }
        // Run the whole flow (disk-cache reads, iTunes/MusicBrainz HTTP, image
        // download) off the collecting thread — callers collect from Main
        // (LaunchedEffect); on a cold/unreachable server this previously froze
        // the UI thread for seconds per uncached row (09-07 freeze audit).
    }.flowOn(Dispatchers.IO)

    /**
     * Searches iTunes for an album match and returns the 600×600 artwork URL.
     * Returns null if no match found or on any error.
     */
    private fun fetchITunes(artist: String, album: String): String? {
        return try {
            val term = URLEncoder.encode("$artist $album", "UTF-8")
            val url = "https://itunes.apple.com/search?term=$term&entity=album&limit=1"
            val req = Request.Builder().url(url).build()
            val res = client.newCall(req).execute()
            val body = res.body?.string() ?: return null
            val results = JSONObject(body).getJSONArray("results")
            if (results.length() == 0) return null
            val artwork = results.getJSONObject(0).getString("artworkUrl100")
            // Request higher resolution (600×600 instead of 100×100)
            artwork.replace("100x100bb", "600x600bb")
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Searches MusicBrainz for a release, then fetches the front cover from
     * the Cover Art Archive. Returns null if no match found or on any error.
     */
    private fun fetchMusicBrainz(artist: String, album: String): String? {
        return try {
            // Step 1: Search MusicBrainz for release MBID
            val query = URLEncoder.encode("artist:$artist AND release:$album", "UTF-8")
            val searchUrl = "https://musicbrainz.org/ws/2/release?query=$query&fmt=json&limit=1"
            val searchReq = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "ftpmusic/1.0")
                .build()
            val searchRes = client.newCall(searchReq).execute()
            val searchBody = searchRes.body?.string() ?: return null
            val releases = JSONObject(searchBody).getJSONArray("releases")
            if (releases.length() == 0) return null
            val mbid = releases.getJSONObject(0).getString("id")

            // Step 2: Fetch cover art from Cover Art Archive
            val artUrl = "https://coverartarchive.org/release/$mbid/front"
            val artReq = Request.Builder().url(artUrl).build()
            val artRes = client.newCall(artReq).execute()
            if (!artRes.isSuccessful) return null

            // Cover Art Archive returns the image directly for /front endpoint
            // The redirect URL is the actual image URL
            val imageUrl = artRes.request.url.toString()
            if (imageUrl.isBlank()) return null
            imageUrl
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Estimates image dimensions from a URL without downloading the full image.
     * For local file:// URLs, uses BitmapFactory to decode bounds.
     * For remote URLs, returns 0 (caller should prefer local cache sizes).
     */
    private fun getImageSize(url: String): Int {
        if (url.startsWith("file://")) {
            return try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(url.removePrefix("file://"), options)
                options.outWidth * options.outHeight
            } catch (_: Exception) {
                0
            }
        }
        // Rough heuristic: 600×600 iTunes art ≈ 360,000 px
        // Cover Art Archive images are typically 500-1200px
        return if (url.contains("600x600")) 360_000 else 0
    }

    /**
     * Downloads an image from [url] and atomically publishes it to [file].
     * @return true when a valid image was written.
     */
    private fun downloadToFile(url: String, file: File): Boolean = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.instanceFollowRedirects = true
        val bytes = conn.inputStream.use { it.readBytes() }
        val ok = writeImageAtomically(file, bytes, conn.contentType)
        if (ok) evictIfNeeded()
        ok
    } catch (_: Exception) {
        false
    }

    /**
     * Fetches the best available artist image and caches it to the SHARED
     * artist file (`artist|<name>.jpg`) — every surface (Library, Home,
     * Favorites) reads this same file, so the same artist always shows the
     * same art.
     *
     * Source preference:
     * 1. Navidrome artist cover art (when [coverArtId] is available) —
     *    real artist art, downloaded into the shared file.
     * 2. iTunes representative-album artwork (600x600).
     *
     * Checks the disk cache first; emits the cached file URL when present.
     * Concurrent callers for the same artist share one in-flight download
     * so Library Artists fling cannot spawn N parallel network fetches.
     */
    fun fetchArtistArt(artist: String, coverArtId: String? = null): Flow<String?> = flow {
        val cacheKey = "artist|$artist".lowercase()
        val cachedFile = File(cacheDir, "${cacheKey.hashCode()}.jpg")

        // Check disk cache (validated)
        if (CoverArtFiles.isUsableImage(cachedFile)) {
            emit("file://${cachedFile.absolutePath}")
            return@flow
        }
        CoverArtFiles.deleteIfUnusable(cachedFile)

        val created = scope.async {
            resolveArtistArtToFile(cacheKey, artist, cachedFile, coverArtId)
        }
        val winner = artistArtInFlight.putIfAbsent(cacheKey, created)
        val deferred = if (winner != null) {
            created.cancel()
            winner
        } else {
            created
        }
        try {
            emit(deferred.await())
        } finally {
            artistArtInFlight.remove(cacheKey, deferred)
        }
    }.flowOn(Dispatchers.IO)

    /** In-flight artist downloads keyed by `artist|<name>` (lowercase). */
    private val artistArtInFlight = ConcurrentHashMap<String, Deferred<String?>>()

    private fun resolveArtistArtToFile(
        cacheKey: String,
        artistName: String,
        cachedFile: File,
        coverArtId: String?,
    ): String? {
        if (CoverArtFiles.isUsableImage(cachedFile)) {
            return "file://${cachedFile.absolutePath}"
        }
        CoverArtFiles.deleteIfUnusable(cachedFile)

        // Prefer Navidrome artist art (downloaded into the shared file) so
        // artists with real art get their own image.
        if (!coverArtId.isNullOrBlank()) {
            val navidromeUrl = buildNavidromeCoverArtUrl(coverArtId)
            if (navidromeUrl != null && downloadToFile(navidromeUrl, cachedFile)) {
                onFileCached(cacheKey)
                return "file://${cachedFile.absolutePath}"
            }
        }

        // iTunes — artist search doesn't return artwork, so search for a
        // representative album to get a high-res (600x600) artist image
        val itunesUrl: String? = try {
            val term = URLEncoder.encode(artistName, "UTF-8")
            val albumUrl = "https://itunes.apple.com/search?term=$term&entity=album&limit=1"
            val albumReq = Request.Builder().url(albumUrl).build()
            val albumRes = client.newCall(albumReq).execute()
            val body = albumRes.body?.string()
            if (body == null) {
                null
            } else {
                val results = JSONObject(body).getJSONArray("results")
                if (results.length() == 0) {
                    null
                } else {
                    results.getJSONObject(0).getString("artworkUrl100")
                        .replace("100x100bb", "600x600bb")
                }
            }
        } catch (_: Exception) {
            null
        }

        return if (itunesUrl != null) {
            // Only claim a file:// URL when a valid image was actually written.
            if (downloadToFile(itunesUrl, cachedFile)) {
                onFileCached(cacheKey)
                "file://${cachedFile.absolutePath}"
            } else {
                itunesUrl
            }
        } else {
            null
        }
    }

    /** Build an authenticated Navidrome getCoverArt URL for an artist cover id. */
    private fun buildNavidromeCoverArtUrl(coverArtId: String): String? {
        val config = com.lucasdss.ftpmusic.app.di.ServerConfigState.value
        if (!config.isConfigured) return null
        return try {
            val auth = com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper()
                .buildAuthParams(config.username, config.password)
            val query = buildString {
                append("id=").append(URLEncoder.encode(coverArtId, "UTF-8"))
                append("&size=300")
                auth.forEach { (k, v) -> append("&$k=").append(URLEncoder.encode(v, "UTF-8")) }
            }
            "${config.url}/rest/getCoverArt?$query"
        } catch (_: Exception) {
            null
        }
    }

    /** Clears all cached cover art files and the in-memory URL cache. */
    fun clearCache() {
        synchronized(urlCache) { urlCache.clear() }
        synchronized(diskLock) {
            cacheDir.listFiles()?.forEach { it.delete() }
        }
        synchronized(versionLock) {
            keyVersionStates.values.forEach { it.intValue = 0 }
            cacheVersionState.intValue = cacheVersionState.intValue + 1
        }
    }

    // ── Cover Art Cache Quota (LRU eviction) ────────────────────────────────

    /** Default quota in MB. Overridden by user setting. 500MB ≈ 3000 album covers. */
    @Volatile var maxCacheBytes: Long = 500L * 1024 * 1024

    /**
     * Evict oldest files if total cache size exceeds [maxCacheBytes].
     * Called after each new file write. Evicts until under 80% of quota
     * to avoid thrashing on every write near the boundary.
     */
    fun evictIfNeeded() {
        synchronized(diskLock) {
            val files = cacheDir.listFiles() ?: return
            val totalSize = files.sumOf { it.length() }
            if (totalSize <= maxCacheBytes) return

            val targetSize = (maxCacheBytes * 0.8).toLong()
            var currentSize = totalSize
            files.sortedBy { it.lastModified() }.forEach { file ->
                if (currentSize <= targetSize) return
                val fileSize = file.length()
                if (file.delete()) {
                    currentSize -= fileSize
                }
            }
        }
    }

    /** Get current cache size in bytes for settings display. */
    fun getCacheSizeBytes(): Long = cacheDir.listFiles()?.sumOf { it.length() } ?: 0L

    /**
     * Delete orphaned navidrome cover art files that no longer correspond
     * to any active coverArt ID. Called after metadata sync.
     *
     * Safety guards: an empty active set means the caller failed to enumerate
     * the library — sweeping then would wipe every cached cover. Recently
     * written files are also skipped to avoid racing in-flight downloads.
     *
     * @param activeCoverArtIds current set of coverArt IDs from cached_albums.
     */
    fun cleanOrphanedNavidromeArt(activeCoverArtIds: Set<String>) {
        if (activeCoverArtIds.isEmpty()) {
            android.util.Log.w(
                "ftpmusic-cache",
                "[cleanOrphaned] skipped — empty active cover-art set (sync likely incomplete)",
            )
            return
        }
        val activeHashes = activeCoverArtIds.map { "navidrome|${it.hashCode()}.jpg" }.toSet()
        val now = System.currentTimeMillis()
        synchronized(diskLock) {
            cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("navidrome|") && file.name !in activeHashes) {
                    if (now - file.lastModified() < 5 * 60_000L) return@forEach
                    file.delete()
                }
            }
        }
    }
}
