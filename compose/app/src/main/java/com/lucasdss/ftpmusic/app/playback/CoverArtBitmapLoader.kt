package com.lucasdss.ftpmusic.app.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService
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
            loadFromDisk(coverArtId)?.let { return it }
        }
        return loadFromNetwork(uri, coverArtId)
    }

    /** Serve from the local cover-art disk cache (CoverArtFallbackService dir). */
    internal fun loadFromDisk(coverArtId: String): Bitmap? {
        val f = cachedFile(coverArtId)
        if (!f.exists() || f.length() == 0L) return null
        val bitmap = decodeSampled(f.absolutePath)
        if (bitmap == null) {
            // Truncated/corrupt entry — evict so the next load can re-fetch.
            try {
                f.delete()
            } catch (_: Exception) {}
        }
        return bitmap
    }

    /** Fetch the remote artwork (auth params embedded in the URI) and cache it. */
    internal fun loadFromNetwork(uri: Uri, coverArtId: String?): Bitmap? {
        return try {
            val request = Request.Builder().url(uri.toString()).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                val bitmap = decodeSampled(bytes) ?: return null
                if (coverArtId != null) {
                    writeAtomically(cachedFile(coverArtId), bytes)
                }
                bitmap
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Temp-file + rename so a killed process never leaves a partial image. */
    private fun writeAtomically(file: File, bytes: ByteArray) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        try {
            file.parentFile?.mkdirs()
            tmp.outputStream().use { it.write(bytes) }
            if (file.exists()) file.delete()
            if (!tmp.renameTo(file)) tmp.delete()
        } catch (_: Exception) {
            try {
                tmp.delete()
            } catch (_: Exception) {}
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
