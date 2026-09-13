package com.lucasdss.ftpmusic.app.playback

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lucasdss.ftpmusic.app.data.db.QueueJournalEntity

/**
 * Extracts the weighted track selection algorithm from [MediaService]
 * so it can be unit-tested independently of the Android service lifecycle.
 */
object JournalTrackSelector {

    /**
     * Selects up to [maxTracks] unique track IDs from the journal entries,
     * excluding any IDs already present in [currentTrackIds].
     *
     * Uses reservoir sampling: newer entries (lower index = more recent) are
     * weighted more heavily, with a cap on copies to avoid memory blowup.
     */
    fun select(entries: List<QueueJournalEntity>, currentTrackIds: Set<String>, maxTracks: Int = 10): List<String> {
        if (entries.isEmpty()) return emptyList()

        val gson = Gson()
        val type = object : TypeToken<List<String>>() {}.type

        // Limit effective entries for weighting, regardless of journal cap
        val effectiveEntries = entries.take(50)
        val weightedPool = mutableListOf<String>()
        effectiveEntries.forEachIndexed { index, entry ->
            val ids: List<String> = try {
                gson.fromJson(entry.trackIdsJson, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            val weight = maxOf(1, effectiveEntries.size - index)
            val copies = minOf(weight, 3) // max 3 copies per entry to avoid OOM
            repeat(copies) { weightedPool.addAll(ids.shuffled().take(5)) }
        }

        return weightedPool.shuffled()
            .filter { it !in currentTrackIds }
            .distinct()
            .take(maxTracks)
    }
}
