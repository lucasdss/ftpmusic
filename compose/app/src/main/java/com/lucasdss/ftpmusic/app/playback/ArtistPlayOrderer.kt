package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.model.Track
import kotlin.random.Random

/**
 * Pure artist-catalog ordering for Play All (ADR-0141 / ADR-0145).
 *
 * Album-aware modes group by album, sort tracks within each album by
 * disc → track → title → id, then order (or shuffle) the album blocks.
 */
object ArtistPlayOrderer {

    /**
     * @param albumYearById map of albumId → year (missing / null → unknown year)
     * @param random used for [ArtistPlayOrder.SHUFFLE_ALBUMS] and [ArtistPlayOrder.SHUFFLE_SONGS]
     */
    fun order(
        tracks: List<Track>,
        order: ArtistPlayOrder,
        albumYearById: Map<String, Int?> = emptyMap(),
        random: Random = Random.Default,
    ): List<Track> {
        if (tracks.isEmpty()) return tracks
        return when (order) {
            ArtistPlayOrder.SHUFFLE_SONGS -> tracks.shuffled(random)

            ArtistPlayOrder.SHUFFLE_ALBUMS -> orderByAlbumGroups(tracks) { groups ->
                groups.shuffled(random)
            }

            ArtistPlayOrder.ALBUM_YEAR_ASC -> orderByAlbumGroups(tracks) { groups ->
                groups.sortedWith(albumGroupYearComparator(albumYearById, ascending = true))
            }

            ArtistPlayOrder.ALBUM_YEAR_DESC -> orderByAlbumGroups(tracks) { groups ->
                groups.sortedWith(albumGroupYearComparator(albumYearById, ascending = false))
            }

            ArtistPlayOrder.ALBUM_NAME_ASC -> orderByAlbumGroups(tracks) { groups ->
                groups.sortedWith(compareBy { albumSortName(it.first()) })
            }

            ArtistPlayOrder.ALBUM_NAME_DESC -> orderByAlbumGroups(tracks) { groups ->
                groups.sortedWith(compareByDescending { albumSortName(it.first()) })
            }
        }
    }

    private val withinAlbumComparator: Comparator<Track> =
        compareBy<Track> { it.discNumber ?: Int.MAX_VALUE }
            .thenBy { it.trackNumber ?: Int.MAX_VALUE }
            .thenBy { it.title.lowercase() }
            .thenBy { it.id }

    private fun orderByAlbumGroups(
        tracks: List<Track>,
        orderGroups: (List<List<Track>>) -> List<List<Track>>,
    ): List<Track> {
        // Preserve first-seen album order for stable grouping keys; orphan tracks
        // (no albumId) each get their own group so they are not glued together.
        val groups = linkedMapOf<String, MutableList<Track>>()
        var orphanSeq = 0
        for (track in tracks) {
            val key = track.albumId?.takeIf { it.isNotBlank() } ?: "__orphan_${orphanSeq++}_${track.id}"
            groups.getOrPut(key) { mutableListOf() }.add(track)
        }
        val sortedWithin = groups.values.map { group ->
            group.sortedWith(withinAlbumComparator)
        }
        return orderGroups(sortedWithin).flatten()
    }

    private fun albumGroupYearComparator(
        albumYearById: Map<String, Int?>,
        ascending: Boolean,
    ): Comparator<List<Track>> {
        val yearCmp = Comparator<List<Track>> { a, b ->
            val ya = resolveYear(a.first(), albumYearById)
            val yb = resolveYear(b.first(), albumYearById)
            when {
                ya == null && yb == null -> 0

                ya == null -> 1

                // null years after dated
                yb == null -> -1

                ascending -> ya.compareTo(yb)

                else -> yb.compareTo(ya)
            }
        }
        return yearCmp.thenBy { albumSortName(it.first()) }
    }

    private fun resolveYear(track: Track, albumYearById: Map<String, Int?>): Int? {
        val id = track.albumId ?: return null
        return albumYearById[id]
    }

    private fun albumSortName(track: Track): String = (track.album ?: "").lowercase()
}
