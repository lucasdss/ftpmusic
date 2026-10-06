package com.lucasdss.ftpmusic.app.data.search

import com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity

/**
 * Curated mood → tag tokens + tag aggregation for Search chips (Phase-5).
 */
object SearchMoodTags {

    data class Mood(val label: String, val queryTokens: List<String>) {
        /** Primary search query used when chip tapped. */
        fun searchQuery(): String = queryTokens.firstOrNull() ?: label.lowercase()
    }

    val MOODS: List<Mood> = listOf(
        Mood("Chill", listOf("chill", "ambient", "acoustic")),
        Mood("Focus", listOf("instrumental", "classical", "ambient")),
        Mood("Workout", listOf("electronic", "dance", "rock")),
        Mood("Party", listOf("dance", "pop", "disco")),
        Mood("Late Night", listOf("soul", "jazz", "ambient")),
        Mood("Morning", listOf("pop", "indie", "folk")),
        Mood("Road Trip", listOf("rock", "indie", "classic")),
        Mood("Throwback", listOf("classic", "90s", "80s")),
    )

    fun extractTags(searchTags: String?): List<String> {
        if (searchTags.isNullOrBlank()) return emptyList()
        return searchTags
            .split(Regex("[\\s,;/|]+"))
            .map { it.trim().lowercase() }
            .filter { it.length >= 2 }
            .distinct()
    }

    /** Frequency-ranked tags from enriched artists. Cap for chip UI. */
    fun aggregateTags(artists: List<CachedArtistEntity>, limit: Int = 24): List<String> {
        val counts = HashMap<String, Int>()
        for (a in artists) {
            for (tag in extractTags(a.searchTags)) {
                counts[tag] = (counts[tag] ?: 0) + 1
            }
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key }
    }

    /** Tags from matched search artists that overlap the query (for result chip strip). */
    fun matchedTagsForQuery(artists: List<CachedArtistEntity>, query: String, limit: Int = 12): List<String> {
        val folded = SearchQueryNormalizer.fold(query)
        if (folded.isBlank()) return emptyList()
        val hits = LinkedHashSet<String>()
        for (a in artists) {
            for (tag in extractTags(a.searchTags)) {
                if (tag.contains(folded) || folded.contains(tag)) hits.add(tag)
            }
        }
        return hits.take(limit)
    }
}
