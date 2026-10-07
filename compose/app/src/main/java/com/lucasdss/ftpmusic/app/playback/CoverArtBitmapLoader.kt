package com.lucasdss.ftpmusic.app.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFiles
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * MediaSession artwork loader for the system QuickSettings player and lock
 * screen. Android's system-side `MediaDataLoader` rejects remote http(s)
 * artwork URIs ("Invalid album art uri"), so media3's legacy session loads the
 * artwork through this [androidx.media3.common.util.BitmapLoader] and exposes
 * it as an in-memory bitmap that the system renders directly.
 *
 * Strategy: serve from the local cover-art disk cache first (instant, works
 * offline), otherwise fetch the remote URI (which already carries Subsonic
 * auth params) and cache the bytes for the next load.
 */
@Singleton
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CoverArtBitmapLoader @Inject constructor(@ApplicationContext private val context: Context) :
    androidx.media3.common.util.BitmapLoader {

    companion object {
        private const val MAX_DIMENSION = 512
        private const val MIME_JPEG = "image/jpeg"
        private const val MIME_PNG = "image/png"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Swappable for tests — production uses [client]. */
    internal var httpClient: OkHttpClient = client

    override fun supportsMimeType(mimeType: String): Boolean = mimeType == MIME_JPEG || mimeType == MIME_PNG

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = loadFromDiskOrNetwork(uri)
                if (bitmap != null) {
                    future.set(bitmap)
                } else {
                    future.setException(IllegalStateException("No cover art for $uri"))
                }
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    override fun decodeBitmap(bytes: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = decodeSampled(bytes)
                if (bitmap != null) {
                    future.set(bitmap)
                } else {
                    future.setException(IllegalStateException("Undecodable artwork bytes"))
                }
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    private fun loadFromDiskOrNetwork(uri: Uri): Bitmap? {
        val coverArtId = coverArtIdFromUri(uri)
        if (coverArtId != null) {
            val fallback = CoverArtFallbackService.getInstance(context)
            val f = cachedFile(coverArtId)
            if (fallback.isFreshOnDisk(f)) {
                loadFromDisk(coverArtId)?.let { return it }
            } else {
                // Stale/soft/legacy: prefer network revalidate; fall back to disk offline.
                loadFromNetwork(uri, coverArtId)?.let { return it }
                loadFromDisk(coverArtId)?.let { return it }
            }
        }
        return loadFromNetwork(uri, coverArtId)
    }

    /** Serve from the local cover-art disk cache (CoverArtFallbackService dir). */
    internal fun loadFromDisk(coverArtId: String): Bitmap? {
        val f = cachedFile(coverArtId)
        if (!CoverArtFiles.looksLikeImage(f)) {
            CoverArtFiles.deleteIfNotImage(f)
            return null
        }
        val bitmap = decodeSampled(f.absolutePath)
        if (bitmap == null) {
            try {
                com.lucasdss.ftpmusic.app.data.cache.CoverArtCacheMeta.delete(f)
                f.delete()
            } catch (_: Exception) {}
            return null
        }
        return bitmap
    }

    /** Fetch the remote artwork (auth params embedded in the URI) and cache it. */
    internal fun loadFromNetwork(uri: Uri, coverArtId: String?): Bitmap? {
        return try {
            val existingMeta = coverArtId?.let {
                com.lucasdss.ftpmusic.app.data.cache.CoverArtCacheMeta.read(cachedFile(it))
            }
            val requestBuilder = Request.Builder().url(uri.toString())
            existingMeta?.etag?.let { requestBuilder.header("If-None-Match", it) }
            existingMeta?.lastModified?.let { requestBuilder.header("If-Modified-Since", it) }
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (response.code == 304 && coverArtId != null) {
                    com.lucasdss.ftpmusic.app.data.cache.CoverArtCacheMeta.touchFetchedAt(cachedFile(coverArtId))
                    return loadFromDisk(coverArtId)
                }
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                val bitmap = decodeSampled(bytes) ?: return null
                if (coverArtId != null) {
                    CoverArtFallbackService.getInstance(context).publishNavidromeBytes(
                        coverArtId = coverArtId,
                        bytes = bytes,
                        contentType = response.header("Content-Type") ?: "image/jpeg",
                        etag = response.header("ETag"),
                        lastModified = response.header("Last-Modified"),
                    )
                }
                bitmap
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun decodeSampled(filePath: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeFile(filePath, sampleOptions(bounds))
    }

    internal fun decodeSampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, sampleOptions(bounds))
    }

    /** Extract the cover art id from an artwork URI (see [coverArtIdFromUri]). */
    internal fun coverArtIdFromUri(uri: Uri): String? = com.lucasdss.ftpmusic.app.playback.coverArtIdFromUri(uri)

    private fun cachedFile(coverArtId: String): File =
        File(CoverArtFallbackService.getInstance(context).cacheDir, "navidrome|${coverArtId.hashCode()}.jpg")

    private fun sampleOptions(bounds: BitmapFactory.Options): BitmapFactory.Options {
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_DIMENSION ||
            bounds.outHeight / (sample * 2) >= MAX_DIMENSION
        ) {
            sample *= 2
        }
        return BitmapFactory.Options().apply { inSampleSize = sample }
    }
}
