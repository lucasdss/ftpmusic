package com.lucasdss.ftpmusic.app.data.sort

import com.lucasdss.ftpmusic.app.data.model.Track
import kotlin.random.Random

/**
 * Pure ordering for artist-detail **browse** modes (ADR-0152).
 *
 * Only album-block and random modes are actually served from this object at
 * runtime; play-count and track-name browse pages come from SQL keyset queries.
 * [playCountComparator] / [trackNameComparator] exist as a test oracle and are
 * documented to diverge from SQL on case-sensitive titles (SQLite BINARY vs
 * Kotlin `lowercase()`).
 */
object ArtistSongsSorter {

    fun order(
        tracks: List<Track>,
        sort: ArtistSongsSort,
        albumYearById: Map<String, Int?> = emptyMap(),
        random: Random = Random.Default,
    ): List<Track> {
        if (tracks.isEmpty()) return tracks
        return when (sort.mode) {
            ArtistSongsMode.RANDOM -> tracks.shuffled(random)

            ArtistSongsMode.ALBUM_NAME -> AlbumTrackOrdering.groupByAlbum(tracks)
                .sortedWith(AlbumTrackOrdering.albumNameComparator(sort.ascending))
                .flatten()

            ArtistSongsMode.ALBUM_YEAR -> AlbumTrackOrdering.groupByAlbum(tracks)
                .sortedWith(AlbumTrackOrdering.albumYearComparator(albumYearById, sort.ascending))
                .flatten()

            ArtistSongsMode.PLAY_COUNT -> tracks.sortedWith(playCountComparator(sort.ascending))

            ArtistSongsMode.TRACK_NAME -> tracks.sortedWith(trackNameComparator(sort.ascending))
        }
    }

    /** Most/least played first, then case-insensitive title, then id (test oracle). */
    fun playCountComparator(ascending: Boolean): Comparator<Track> {
        val byCount = if (ascending) {
            compareBy<Track> { it.playCount }
        } else {
            compareByDescending<Track> { it.playCount }
        }
        return byCount
            .thenBy { it.title.lowercase() }
            .thenBy { it.id }
    }

    /** Case-insensitive title, then id (test oracle). */
    fun trackNameComparator(ascending: Boolean): Comparator<Track> {
        val byTitle = if (ascending) {
            compareBy<Track> { it.title.lowercase() }
        } else {
            compareByDescending<Track> { it.title.lowercase() }
        }
        return byTitle.thenBy { it.id }
    }
}
