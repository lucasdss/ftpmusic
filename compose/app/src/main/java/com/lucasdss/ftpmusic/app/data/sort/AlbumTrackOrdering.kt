package com.lucasdss.ftpmusic.app.data.sort

import com.lucasdss.ftpmusic.app.data.model.Track

/**
 * Shared album-block ordering (ADR-0141 / ADR-0145 / ADR-0152).
 *
 * Album-aware Play All modes and the artist Songs album-name/year browse modes
 * must agree on:
 * - how tracks are grouped by album (orphans become singleton blocks),
 * - how tracks are ordered **within** an album (disc → track → title → id),
 * - how album blocks are keyed by year and catalogue name.
 *
 * Extracting this prevents three divergent comparators from drifting apart.
 * Semantics are intentionally unchanged from the previous
 * `ArtistPlayOrderer` implementation: unknown disc/track numbers sort last and
 * null disc numbers are never coerced to 1.
 */
object AlbumTrackOrdering {

    /** Disc → track → title → id; null disc/track tags sort last. */
    val withinAlbumComparator: Comparator<Track> =
        compareBy<Track> { it.discNumber ?: Int.MAX_VALUE }
            .thenBy { it.trackNumber ?: Int.MAX_VALUE }
            .thenBy { it.title.lowercase() }
            .thenBy { it.id }

    /**
     * Groups tracks by non-blank `albumId`, preserving first-seen album order.
     * Tracks without an album id each get their own singleton block so they are
     * never glued together (Play All parity).
     */
    fun groupByAlbum(tracks: List<Track>): List<List<Track>> {
        val groups = linkedMapOf<String, MutableList<Track>>()
        var orphanSeq = 0
        for (track in tracks) {
            val key = track.albumId?.takeIf { it.isNotBlank() }
                ?: "__orphan_${orphanSeq++}_${track.id}"
            groups.getOrPut(key) { mutableListOf() }.add(track)
        }
        return groups.values.map { group -> group.sortedWith(withinAlbumComparator) }
    }

    fun albumNameComparator(ascending: Boolean): Comparator<List<Track>> = if (ascending) {
        compareBy { albumSortName(it.first()) }
    } else {
        compareByDescending { albumSortName(it.first()) }
    }

    /** Null years always sort after dated albums, in both directions. */
    fun albumYearComparator(albumYearById: Map<String, Int?>, ascending: Boolean): Comparator<List<Track>> {
        val yearCmp = Comparator<List<Track>> { a, b ->
            val ya = resolveYear(a.first(), albumYearById)
            val yb = resolveYear(b.first(), albumYearById)
            when {
                ya == null && yb == null -> 0
                ya == null -> 1
                yb == null -> -1
                ascending -> ya.compareTo(yb)
                else -> yb.compareTo(ya)
            }
        }
        return yearCmp.thenBy { albumSortName(it.first()) }
    }

    fun resolveYear(track: Track, albumYearById: Map<String, Int?>): Int? {
        val id = track.albumId ?: return null
        return albumYearById[id]
    }

    fun albumSortName(track: Track): String = (track.album ?: "").lowercase()
}
