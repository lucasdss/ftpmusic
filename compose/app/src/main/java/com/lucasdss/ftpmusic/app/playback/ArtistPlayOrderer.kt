package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.sort.AlbumTrackOrdering
import kotlin.random.Random

/**
 * Pure artist-catalog ordering for Play All (ADR-0141 / ADR-0145).
 *
 * Album-aware modes group by album, sort tracks within each album by
 * disc → track → title → id, then order (or shuffle) the album blocks.
 * Grouping and within-album ordering live in [AlbumTrackOrdering] so the
 * artist Songs browse sort (ADR-0152) stays aligned.
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
                groups.sortedWith(
                    AlbumTrackOrdering.albumYearComparator(albumYearById, ascending = true),
                )
            }

            ArtistPlayOrder.ALBUM_YEAR_DESC -> orderByAlbumGroups(tracks) { groups ->
                groups.sortedWith(
                    AlbumTrackOrdering.albumYearComparator(albumYearById, ascending = false),
                )
            }

            ArtistPlayOrder.ALBUM_NAME_ASC -> orderByAlbumGroups(tracks) { groups ->
                groups.sortedWith(AlbumTrackOrdering.albumNameComparator(ascending = true))
            }

            ArtistPlayOrder.ALBUM_NAME_DESC -> orderByAlbumGroups(tracks) { groups ->
                groups.sortedWith(AlbumTrackOrdering.albumNameComparator(ascending = false))
            }
        }
    }

    private fun orderByAlbumGroups(
        tracks: List<Track>,
        orderGroups: (List<List<Track>>) -> List<List<Track>>,
    ): List<Track> = orderGroups(AlbumTrackOrdering.groupByAlbum(tracks)).flatten()
}
