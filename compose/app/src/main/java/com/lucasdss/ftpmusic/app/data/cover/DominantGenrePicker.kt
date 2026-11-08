package com.lucasdss.ftpmusic.app.data.cover

/**
 * Picks a deterministic majority genre label for pack-cover fallback (ADR 0151).
 * Ties break lexicographically on the normalized key (stable, testable).
 */
object DominantGenrePicker {
    fun majority(genres: List<String>): String? {
        val counts = linkedMapOf<String, Pair<String, Int>>()
        for (raw in genres) {
            val display = raw.trim()
            if (display.isEmpty()) continue
            val key = display.lowercase().replace(Regex("\\s+"), " ")
            val prev = counts[key]
            if (prev == null) {
                counts[key] = display to 1
            } else {
                counts[key] = prev.first to (prev.second + 1)
            }
        }
        if (counts.isEmpty()) return null
        val max = counts.values.maxOf { it.second }
        return counts.entries
            .filter { it.value.second == max }
            .minBy { it.key }
            .value
            .first
    }

    /** Recipe genres win when non-empty; else track/majority list. */
    fun preferRecipeThenTracks(recipeGenres: List<String>, trackGenres: List<String>): String? {
        val firstRecipe = recipeGenres.firstOrNull { it.isNotBlank() }?.trim()
        if (firstRecipe != null) return firstRecipe
        return majority(trackGenres)
    }
}
