package com.lucasdss.ftpmusic.app.data.db

import com.lucasdss.ftpmusic.app.data.sort.ArtistSongsCursor
import com.lucasdss.ftpmusic.app.data.sort.ArtistSongsMode
import com.lucasdss.ftpmusic.app.data.sort.ArtistSongsSort

/**
 * Artist detail membership (ADR-0142 albums / ADR-0145 tracks / ADR-0152 sort).
 *
 * **Albums tab** — three-way merge: album `artist_id`, album artist name, or any
 * `cached_album_tracks` row tagged with this artist (so VA/OST albums still appear).
 *
 * **Songs / Play All / Download All / catalog count** — own albums contribute every
 * track; foreign albums contribute only rows attributed to this artist (track
 * `artist_id` or matching `cached_album_tracks.id`). See [TrackDao.getAllTracksForArtistView].
 */
object ArtistViewMembership {

    /** `cached_albums` id batch size — stays under the SQLite host-parameter budget. */
    private const val ALBUM_ID_CHUNK = 500

    suspend fun loadAlbums(
        metadataDao: CachedMetadataDao,
        artistId: String,
        artistName: String?,
    ): List<CachedAlbumEntity> {
        val byId = metadataDao.getAlbumsByArtistId(artistId)
        val byName = if (!artistName.isNullOrBlank()) {
            metadataDao.getAlbumsByArtistName(artistName)
        } else {
            emptyList()
        }
        val byTrackArtist = metadataDao.getAlbumsByTrackArtistId(artistId)
        return (byId + byName + byTrackArtist).distinctBy { it.id }
    }

    suspend fun loadAllTracks(trackDao: TrackDao, artistId: String, artistName: String?): List<TrackEntity> =
        trackDao.getAllTracksForArtistView(artistId, artistName.orEmpty())

    suspend fun countTracks(trackDao: TrackDao, artistId: String, artistName: String?): Int =
        trackDao.countTracksForArtistView(artistId, artistName.orEmpty())

    suspend fun loadTracksPage(
        trackDao: TrackDao,
        artistId: String,
        artistName: String?,
        limit: Int,
        titleCursor: String? = null,
        idCursor: String? = null,
    ): List<TrackEntity> {
        val name = artistName.orEmpty()
        return if (titleCursor == null || idCursor == null) {
            trackDao.getTracksForArtistView(artistId, name, limit)
        } else {
            trackDao.getTracksForArtistViewAfter(artistId, name, titleCursor, idCursor, limit)
        }
    }

    /**
     * One SQL-paginated Songs page for a flat sort ([ArtistSongsSort.isKeyset]).
     *
     * Returns an empty list for album-block / random sorts — those modes load the
     * full membership and sort in memory, so they must not silently hit this path.
     */
    suspend fun loadSongsKeysetPage(
        trackDao: TrackDao,
        artistId: String,
        artistName: String?,
        sort: ArtistSongsSort,
        limit: Int,
        cursor: ArtistSongsCursor? = null,
    ): List<TrackEntity> {
        if (!sort.isKeyset) return emptyList()
        val name = artistName.orEmpty()
        return when (sort.mode) {
            ArtistSongsMode.PLAY_COUNT -> {
                val descending = !sort.ascending
                when {
                    cursor == null -> if (descending) {
                        trackDao.getTracksForArtistViewByPlayCountDesc(artistId, name, limit)
                    } else {
                        trackDao.getTracksForArtistViewByPlayCountAsc(artistId, name, limit)
                    }

                    cursor !is ArtistSongsCursor.PlayCount -> emptyList()

                    descending -> trackDao.getTracksForArtistViewAfterByPlayCountDesc(
                        artistId,
                        name,
                        cursor.playCount,
                        cursor.title,
                        cursor.id,
                        limit,
                    )

                    else -> trackDao.getTracksForArtistViewAfterByPlayCountAsc(
                        artistId,
                        name,
                        cursor.playCount,
                        cursor.title,
                        cursor.id,
                        limit,
                    )
                }
            }

            ArtistSongsMode.TRACK_NAME -> {
                val ascending = sort.ascending
                when {
                    cursor == null -> if (ascending) {
                        trackDao.getTracksForArtistView(artistId, name, limit)
                    } else {
                        trackDao.getTracksForArtistViewByTitleDesc(artistId, name, limit)
                    }

                    cursor !is ArtistSongsCursor.Title -> emptyList()

                    ascending -> trackDao.getTracksForArtistViewAfter(
                        artistId,
                        name,
                        cursor.title,
                        cursor.id,
                        limit,
                    )

                    else -> trackDao.getTracksForArtistViewAfterByTitleDesc(
                        artistId,
                        name,
                        cursor.title,
                        cursor.id,
                        limit,
                    )
                }
            }

            ArtistSongsMode.RANDOM,
            ArtistSongsMode.ALBUM_NAME,
            ArtistSongsMode.ALBUM_YEAR,
            -> emptyList()
        }
    }

    /** Batch album-year lookup, chunked to stay under the SQLite bind limit. */
    suspend fun loadAlbumYears(metadataDao: CachedMetadataDao, albumIds: List<String>): Map<String, Int?> {
        if (albumIds.isEmpty()) return emptyMap()
        val years = HashMap<String, Int?>(albumIds.size)
        for (chunk in albumIds.chunked(ALBUM_ID_CHUNK)) {
            metadataDao.getAlbumYearRows(chunk).forEach { row -> years[row.id] = row.year }
        }
        return years
    }
}
