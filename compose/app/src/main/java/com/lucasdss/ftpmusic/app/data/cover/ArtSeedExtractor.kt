package com.lucasdss.ftpmusic.app.data.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.lucasdss.ftpmusic.app.ui.art.CoverScheme
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Coil-backed art seed extraction. Same decoded source as UI; no raw URL streams.
 * Scores Palette swatches by population × chroma × usable luminance.
 */
object ArtSeedExtractor {

    const val DECODE_SIZE = 112
    private const val FAILURE_TTL_MS = 60_000L
    private const val MEMORY_CAP = 96

    data class SeedResult(
        val seedArgb: Int?,
        val fromCache: Boolean = false,
        /** Transient decode/network failure — do not persist to Room. */
        val softFailure: Boolean = false,
    ) {
        val color: Color?
            get() = seedArgb?.let { Color(it) }
    }

    private data class MemoryEntry(
        val sourceVersion: String,
        val algorithmVersion: Int,
        val seedArgb: Int?,
        val failureUntilMs: Long = 0L,
    )

    private val memory = object : LinkedHashMap<String, MemoryEntry>(MEMORY_CAP, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MemoryEntry>?): Boolean =
            size > MEMORY_CAP
    }
    private val memoryLock = Any()
    private val inFlight = ConcurrentHashMap<String, Mutex>()

    fun clearMemory() {
        synchronized(memoryLock) { memory.clear() }
    }

    fun peekMemory(artKey: String, sourceVersion: String): SeedResult? {
        synchronized(memoryLock) {
            val e = memory[artKey] ?: return null
            if (e.sourceVersion != sourceVersion || e.algorithmVersion != CoverScheme.ALGORITHM_VERSION) {
                return null
            }
            if (e.failureUntilMs > 0L) {
                if (System.currentTimeMillis() < e.failureUntilMs) {
                    return SeedResult(null, fromCache = true, softFailure = true)
                }
                memory.remove(artKey)
                return null
            }
            return SeedResult(e.seedArgb, fromCache = true, softFailure = false)
        }
    }

    fun putMemory(artKey: String, sourceVersion: String, seedArgb: Int?, failed: Boolean = false) {
        synchronized(memoryLock) {
            memory[artKey] = MemoryEntry(
                sourceVersion = sourceVersion,
                algorithmVersion = CoverScheme.ALGORITHM_VERSION,
                seedArgb = seedArgb,
                failureUntilMs = if (failed) System.currentTimeMillis() + FAILURE_TTL_MS else 0L,
            )
        }
    }

    /** Deterministic lettermark seed — no bitmap. */
    fun lettermarkSeedArgb(name: String): Int {
        val c = CoverScheme.lettermarkSeed(name, CollectionCoverResolver::hueFromName)
        return c.toArgbCompat()
    }

    suspend fun extractFromModel(
        context: Context,
        imageModel: Any?,
        artKey: String,
        sourceVersion: String,
        imageLoader: ImageLoader = coil.Coil.imageLoader(context),
    ): SeedResult {
        peekMemory(artKey, sourceVersion)?.let { return it }
        if (imageModel == null) {
            putMemory(artKey, sourceVersion, null, failed = false)
            return SeedResult(null)
        }
        val mutex = inFlight.getOrPut(artKey) { Mutex() }
        return mutex.withLock {
            peekMemory(artKey, sourceVersion)?.let { return@withLock it }
            val result = withContext(Dispatchers.Default) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(imageModel)
                        .size(DECODE_SIZE)
                        .allowHardware(false)
                        .build()
                    val loaded = imageLoader.execute(request)
                    if (loaded !is SuccessResult) {
                        putMemory(artKey, sourceVersion, null, failed = true)
                        return@withContext SeedResult(null, softFailure = true)
                    }
                    val bitmap = (loaded.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap == null) {
                        putMemory(artKey, sourceVersion, null, failed = true)
                        return@withContext SeedResult(null, softFailure = true)
                    }
                    val seed = scoreBitmap(bitmap)
                    putMemory(artKey, sourceVersion, seed)
                    SeedResult(seed)
                } catch (ce: CancellationException) {
                    inFlight.remove(artKey)
                    throw ce
                } catch (_: Exception) {
                    putMemory(artKey, sourceVersion, null, failed = true)
                    SeedResult(null, softFailure = true)
                }
            }
            result
        }.also {
            inFlight.remove(artKey)
        }
    }

    /**
     * Average seeds from montage tile models (detail screens only).
     * Empty / all-null → null seed (neutral scheme).
     */
    suspend fun extractMontageAverage(
        context: Context,
        tileModels: List<Any>,
        artKey: String,
        sourceVersion: String,
        imageLoader: ImageLoader = coil.Coil.imageLoader(context),
    ): SeedResult {
        peekMemory(artKey, sourceVersion)?.let { return it }
        if (tileModels.isEmpty()) {
            putMemory(artKey, sourceVersion, null)
            return SeedResult(null)
        }
        val mutex = inFlight.getOrPut(artKey) { Mutex() }
        return mutex.withLock {
            peekMemory(artKey, sourceVersion)?.let { return@withLock it }
            val seeds = mutableListOf<Color>()
            var anySoftFailure = false
            for ((i, model) in tileModels.withIndex()) {
                val tileKey = "$artKey#tile$i"
                val r = extractFromModel(context, model, tileKey, "$sourceVersion#$i", imageLoader)
                if (r.softFailure) anySoftFailure = true
                r.color?.let { seeds.add(it) }
            }
            // Only cache a complete montage; partial/failed tiles must not pin Room/memory.
            if (anySoftFailure || seeds.size < tileModels.size) {
                putMemory(artKey, sourceVersion, null, failed = true)
                return@withLock SeedResult(null, softFailure = true)
            }
            if (seeds.isEmpty()) {
                putMemory(artKey, sourceVersion, null, failed = true)
                return@withLock SeedResult(null, softFailure = true)
            }
            val avg = Color(
                red = seeds.map { it.red }.average().toFloat(),
                green = seeds.map { it.green }.average().toFloat(),
                blue = seeds.map { it.blue }.average().toFloat(),
                alpha = 1f,
            )
            val argb = if (CoverScheme.isUsableSeed(avg)) avg.toArgbCompat() else null
            putMemory(artKey, sourceVersion, argb)
            SeedResult(argb)
        }.also { inFlight.remove(artKey) }
    }

    fun scoreBitmap(bitmap: Bitmap): Int? {
        val palette = Palette.from(bitmap)
            .resizeBitmapArea(DECODE_SIZE * DECODE_SIZE)
            .clearFilters()
            .generate()
        val candidates = buildList {
            palette.vibrantSwatch?.let { add(it) }
            palette.darkVibrantSwatch?.let { add(it) }
            palette.mutedSwatch?.let { add(it) }
            palette.darkMutedSwatch?.let { add(it) }
            palette.dominantSwatch?.let { add(it) }
            addAll(palette.swatches)
        }.distinctBy { it.rgb }
        var best: Palette.Swatch? = null
        var bestScore = 0f
        for (swatch in candidates) {
            val color = Color(swatch.rgb)
            if (!CoverScheme.isUsableSeed(color)) continue
            val y = color.luminanceCompat()
            val usableY = 1f - kotlin.math.abs(y - 0.35f)
            val maxC = maxOf(color.red, color.green, color.blue)
            val minC = minOf(color.red, color.green, color.blue)
            val chroma = maxC - minC
            val score = swatch.population * chroma * usableY.coerceAtLeast(0.1f)
            if (score > bestScore) {
                bestScore = score
                best = swatch
            }
        }
        return best?.rgb
    }

    private fun Color.toArgbCompat(): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        val r = (red * 255).toInt().coerceIn(0, 255)
        val g = (green * 255).toInt().coerceIn(0, 255)
        val b = (blue * 255).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun Color.luminanceCompat(): Float {
        fun lin(c: Float): Float {
            val s = c.coerceIn(0f, 1f)
            return if (s <= 0.04045f) s / 12.92f else Math.pow(((s + 0.055) / 1.055), 2.4).toFloat()
        }
        return 0.2126f * lin(red) + 0.7152f * lin(green) + 0.0722f * lin(blue)
    }
}
