package com.lucasdss.ftpmusic.app.data.waveform

import java.util.Locale
import kotlin.random.Random

/**
 * Resolves a track's genre string to a bundled waveform asset genre folder.
 *
 * Selection chain (per the product spec):
 *  1. Exact match — normalized (lowercase, alphanumeric only) equality,
 *     e.g. `"Hip-Hop"` ↔ folder `"Hip Hop"`, `"R&B"` ↔ folder `"R&B"`.
 *  2. Curated alias map for common synonyms (EDM → Electronic, …).
 *  3. Fuzzy token-overlap match against the closest genre folder, gated by
 *     [MATCH_THRESHOLD]; ties broken alphabetically for determinism.
 *  4. No match → null; the caller falls back to a random genre folder.
 *
 * Multi-value genres (`"Rock, Pop"`) are tried part by part.
 */
object WaveformGenreMatcher {

    /** Minimum token-overlap similarity for a fuzzy match to be accepted. */
    const val MATCH_THRESHOLD = 0.5f

    /** Curated aliases: normalized track genre → asset folder name. */
    private val ALIASES: Map<String, String> = mapOf(
        "edm" to "Electronic",
        "trance" to "Electronic",
        "techno" to "Electronic",
        "dubstep" to "Electronic",
        "rnb" to "R&B",
        "hiphop" to "Hip Hop",
        "hiphoprap" to "Hip Hop",
        "rocknroll" to "Rock and Roll",
    )

    /** Lowercase + keep only letters/digits: `"Hip-Hop"` → `"hiphop"`, `"R&B"` → `"rb"`. */
    fun normalize(value: String): String = value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    /** Lowercase tokens split on non-alphanumeric runs: `"Rock and Roll"` → [rock, and, roll]. */
    fun tokens(value: String): List<String> =
        value.lowercase(Locale.ROOT).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    /**
     * Best asset genre folder for [genre], or null when nothing matches.
     * [availableGenres] is deduplicated and sorted first so the fuzzy tie-break
     * is deterministic regardless of caller order.
     */
    fun match(genre: String?, availableGenres: List<String>): String? {
        val parts = genre?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() } ?: return null
        if (parts.isEmpty()) return null
        val folders = availableGenres.distinct().sorted()
        if (folders.isEmpty()) return null

        for (part in parts) {
            // 1. Exact (normalized) match.
            val norm = normalize(part)
            folders.firstOrNull { normalize(it) == norm }?.let { return it }

            // 2. Curated alias.
            ALIASES[norm]?.let { alias ->
                folders.firstOrNull { it == alias }?.let { return it }
            }

            // 3. Fuzzy token-overlap match.
            val partTokens = tokens(part)
            var best: String? = null
            var bestScore = 0f
            for (folder in folders) {
                val score = similarity(partTokens, tokens(folder))
                if (score > bestScore) {
                    bestScore = score
                    best = folder
                }
            }
            if (best != null && bestScore >= MATCH_THRESHOLD) return best
        }
        return null
    }

    /** |a ∩ b| / min(|a|, |b|); 0 when either side is empty. */
    fun similarity(a: List<String>, b: List<String>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val inter = a.intersect(b).size
        return inter.toFloat() / minOf(a.size, b.size)
    }

    /** Random folder from [availableGenres]; null when empty. */
    fun pickRandom(availableGenres: List<String>, random: Random = Random.Default): String? {
        val folders = availableGenres.distinct()
        if (folders.isEmpty()) return null
        return folders[random.nextInt(folders.size)]
    }
}
