package com.lucasdss.ftpmusic.app.data.db

/**
 * Single definition of “what belongs on this artist detail page” (ADR-0142).
 *
 * Albums match the three-way merge used by artist detail:
 * album `artist_id`, album artist name, or any track on the album tagged with this artist.
 *
 * Tracks = every row in `tracks` that either:
 * - has `artist_id` = this artist, or
 * - lives on an album in that merged set
 *   (so album-artist / name / OST attribution quirks stay consistent with the Albums tab).
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
