package com.lucasdss.ftpmusic.app.data.waveform

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Reads the bundled per-genre waveform assets. Each asset is a JSON array of
 * floats in [0,1] — the `generateWaveformAssets` Gradle task emits
 * [ASSET_BAR_COUNT] buckets per track, but older Room caches (100 buckets)
 * remain valid via the [MIN_BARS]..[MAX_BARS] range.
 */
@Singleton
class WaveformAssetLoader @Inject constructor(private val source: WaveformAssetSource) {
    companion object {
        /** Buckets per track emitted by the generateWaveformAssets Gradle task. */
        const val ASSET_BAR_COUNT = 400

        /** Accepted stored-bar range — old 100-value Room caches stay valid. */
        const val MIN_BARS = 20
        const val MAX_BARS = 1000

        private const val ASSET_ROOT = "waveform"
    }

    /** Sorted list of genre folders bundled in assets; empty when none exist. */
    fun availableGenres(): List<String> = source.list(ASSET_ROOT)
        ?.mapNotNull { it.takeIf { name -> name.isNotBlank() } }
        ?.sorted()
        ?: emptyList()

    /**
     * Loads a random track's bars from [genreDir]. Returns null on any failure
     * (missing dir, empty dir, unreadable file, malformed JSON).
     */
    fun loadRandomBars(genreDir: String, random: Random = Random.Default): List<Float>? {
        val files = source.list("$ASSET_ROOT/$genreDir")
            ?.filter { it.endsWith(".json") }
            ?.sorted()
            ?: return null
        if (files.isEmpty()) return null
        val picked = files[random.nextInt(files.size)]
        val json = source.open("$ASSET_ROOT/$genreDir/$picked")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: return null
        return parseBars(json)
    }

    /**
     * Strict parser shared by asset loading and the Room cache: any count in
     * [MIN_BARS]..[MAX_BARS] of finite floats in [0,1], else null (corrupt).
     */
    internal fun parseBars(json: String): List<Float>? {
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return null
        val inner = trimmed.substring(1, trimmed.length - 1)
        if (inner.isBlank()) return null
        val parts = inner.split(",")
        if (parts.size !in MIN_BARS..MAX_BARS) return null
        val bars = ArrayList<Float>(parts.size)
        for (part in parts) {
            val v = part.trim().toFloatOrNull() ?: return null
            if (!v.isFinite() || v < 0f || v > 1f) return null
            bars.add(v)
        }
        return bars
    }
}
