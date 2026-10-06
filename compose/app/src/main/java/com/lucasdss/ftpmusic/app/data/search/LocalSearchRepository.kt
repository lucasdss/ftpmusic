package com.lucasdss.ftpmusic.app.data.search

import com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity
import com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.GenreDao
import com.lucasdss.ftpmusic.app.data.db.GenreEntity
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntity
import com.lucasdss.ftpmusic.app.data.db.SearchFtsDao
import com.lucasdss.ftpmusic.app.data.db.SearchFtsTypes
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import javax.inject.Inject
import javax.inject.Singleton

data class LocalSearchHit(
    val tracks: List<TrackEntity> = emptyList(),
    val albums: List<CachedAlbumEntity> = emptyList(),
    val artists: List<CachedArtistEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val genres: List<GenreEntity> = emptyList(),
    /** Track ids matched via lyrics FTS (hydrate separately if needed). */
    val lyricTrackIds: List<String> = emptyList(),
    val usedFts: Boolean = false,
)

/**
 * Local-first search: FTS when index populated, else LIKE fallback.
 */
@Singleton
class LocalSearchRepository @Inject constructor(
    private val ftsDao: SearchFtsDao,
    private val trackDao: TrackDao,
    private val metadataDao: CachedMetadataDao,
    private val playlistDao: PlaylistDao,
    private val genreDao: GenreDao,
) {
    suspend fun search(query: String, limit: Int = 100, playableOnly: Boolean = false): LocalSearchHit {
        val like = SearchQueryNormalizer.escapeLike(query)
        val ftsCount = try {
            ftsDao.count()
        } catch (_: Exception) {
            0
        }
        if (ftsCount > 0) {
            val match = SearchFtsQuery.toMatchQuery(query)
            val rows = try {
                ftsDao.match(match, limit * 3)
            } catch (_: Exception) {
                emptyList()
            }
            if (rows.isNotEmpty()) {
                val trackIds = rows.filter { it.entityType == SearchFtsTypes.TRACK }.map { it.entityId }
                val lyricIds = rows.filter { it.entityType == SearchFtsTypes.LYRICS }.map { it.entityId }
                val albumIds = rows.filter { it.entityType == SearchFtsTypes.ALBUM }.map { it.entityId }
                val artistIds = rows.filter { it.entityType == SearchFtsTypes.ARTIST }.map { it.entityId }
                val playlistIds = rows.filter { it.entityType == SearchFtsTypes.PLAYLIST }.map { it.entityId }
                val genreIds = rows.filter { it.entityType == SearchFtsTypes.GENRE }.map { it.entityId }

                var tracks = if (trackIds.isNotEmpty() || lyricIds.isNotEmpty()) {
                    trackDao.getTracksByIds((trackIds + lyricIds).distinct())
                } else {
                    emptyList()
                }
                if (playableOnly) {
                    tracks = tracks.filter { it.isDownloaded || it.cachedFilePath != null }
                }
                val albums = if (albumIds.isNotEmpty()) {
                    metadataDao.getAllAlbums().filter { it.id in albumIds.toSet() }
                } else {
                    emptyList()
                }
                val artists = if (artistIds.isNotEmpty()) {
                    metadataDao.getArtistsByIds(artistIds)
                } else {
                    emptyList()
                }
                val playlists = if (playlistIds.isNotEmpty()) {
                    playlistDao.getAll().filter { it.id in playlistIds.toSet() }
                } else {
                    emptyList()
                }
                val genres = if (genreIds.isNotEmpty()) {
                    genreDao.getAllByPopularity().filter { it.name in genreIds.toSet() }
                } else {
                    emptyList()
                }
                return LocalSearchHit(
                    tracks = tracks.take(limit),
                    albums = albums.take(limit),
                    artists = artists.take(limit),
                    playlists = playlists.take(limit),
                    genres = genres.take(limit),
                    lyricTrackIds = lyricIds,
                    usedFts = true,
                )
            }
        }
        // LIKE fallback
        val tracks = if (playableOnly) {
            trackDao.searchPlayableTracks(like)
        } else {
            trackDao.searchAllTracks(like)
        }
        val albums = if (playableOnly) {
            metadataDao.searchPlayableAlbums(like)
        } else {
            metadataDao.searchAlbums(like)
        }
        val artists = if (playableOnly) {
            metadataDao.searchPlayableArtists(like)
        } else {
            metadataDao.searchArtists(like)
        }
        return LocalSearchHit(
            tracks = tracks,
            albums = albums,
            artists = artists,
            playlists = playlistDao.searchPlaylists(like),
            genres = genreDao.searchGenres(like),
            usedFts = false,
        )
    }
}
