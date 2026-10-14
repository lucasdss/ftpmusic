package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.model.Track
import kotlin.random.Random

/**
 * Pure artist-catalog ordering for Play All (ADR-0141).
 *
 * Chronological: year → album name → disc → track → title → id.
 * Null years sort after dated albums; among nulls, album name A–Z.
 */
object ArtistPlayOrderer {

    /**
     * @param albumYearById map of albumId → year (missing / null → unknown year)
     * @param random used only for [ArtistPlayOrder.SHUFFLE] (inject for tests)
     */
    fun order(
        tracks: List<Track>,
        order: ArtistPlayOrder,
        albumYearById: Map<String, Int?> = emptyMap(),
        random: Random = Random.Default,
    ): List<Track> {
        if (tracks.isEmpty()) return tracks
        return when (order) {
            ArtistPlayOrder.SHUFFLE -> tracks.shuffled(random)
            ArtistPlayOrder.TITLE_ASC -> tracks.sortedWith(titleComparator)
            ArtistPlayOrder.ALBUM_YEAR_ASC -> tracks.sortedWith(albumYearComparator(albumYearById, ascending = true))
            ArtistPlayOrder.ALBUM_YEAR_DESC -> tracks.sortedWith(albumYearComparator(albumYearById, ascending = false))
        }
    }

    private val titleComparator: Comparator<Track> =
        compareBy<Track> { it.title.lowercase() }.thenBy { it.id }

    private fun albumYearComparator(albumYearById: Map<String, Int?>, ascending: Boolean): Comparator<Track> {
        val yearCmp = Comparator<Track> { a, b ->
            val ya = resolveYear(a, albumYearById)
            val yb = resolveYear(b, albumYearById)
            when {
                ya == null && yb == null -> 0

                ya == null -> 1

                // null years after dated
                yb == null -> -1

                ascending -> ya.compareTo(yb)

                else -> yb.compareTo(ya)
            }
        }
        return yearCmp
            .thenBy { albumSortName(it) }
            .thenBy { it.discNumber ?: Int.MAX_VALUE }
            .thenBy { it.trackNumber ?: Int.MAX_VALUE }
            .thenBy { it.title.lowercase() }
            .thenBy { it.id }
    }

    private fun resolveYear(track: Track, albumYearById: Map<String, Int?>): Int? {
        val id = track.albumId ?: return null
        return albumYearById[id]
    }

    private fun albumSortName(track: Track): String = (track.album ?: "").lowercase()
}
