package com.lucasdss.ftpmusic.app.data.sort

import com.lucasdss.ftpmusic.app.data.model.Track

/**
 * Album detail track ordering (ADR-0152).
 *
 * [AlbumTrackSort.DISC_TRACK] is the historical comparator — behavior must stay
 * identical. Play-count modes put the count first and fall through to the same
 * disc → track → title → id tie-breakers, so an album whose counts are all zero
 * stays in normal track order without a special branch.
 */
object AlbumTrackSorter {

    fun sort(tracks: List<Track>, sort: AlbumTrackSort): List<Track> = when (sort) {
        AlbumTrackSort.DISC_TRACK -> tracks.sortedWith(AlbumTrackOrdering.withinAlbumComparator)

        AlbumTrackSort.PLAY_COUNT_DESC -> tracks.sortedWith(
            compareByDescending<Track> { it.playCount }
                .then(AlbumTrackOrdering.withinAlbumComparator),
        )

        AlbumTrackSort.PLAY_COUNT_ASC -> tracks.sortedWith(
            compareBy<Track> { it.playCount }
                .then(AlbumTrackOrdering.withinAlbumComparator),
        )
    }
}
