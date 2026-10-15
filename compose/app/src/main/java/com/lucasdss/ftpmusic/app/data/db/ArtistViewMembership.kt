package com.lucasdss.ftpmusic.app.data.db

/**
 * Artist detail membership (ADR-0142 albums / ADR-0145 tracks).
 *
 * **Albums tab** — three-way merge: album `artist_id`, album artist name, or any
 * `cached_album_tracks` row tagged with this artist (so VA/OST albums still appear).
 *
 * **Songs / Play All / Download All / catalog count** — own albums contribute every
 * track; foreign albums contribute only rows attributed to this artist (track
 * `artist_id` or matching `cached_album_tracks.id`). See [TrackDao.getAllTracksForArtistView].
 */
object ArtistViewMembership {

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
}
