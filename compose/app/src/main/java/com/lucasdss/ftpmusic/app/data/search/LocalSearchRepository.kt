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
    val usedSoftTypo: Boolean = false,
    val yearConstraint: SearchYearConstraint = SearchYearConstraint(),
)

/**
 * Local-first search: FTS when index populated, else LIKE fallback.
 * Phase-3: year/decade parse + soft edit-distance when FTS empty.
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
        val parsed = SearchYearParser.parse(query)
        // Keep blank text when query was year/decade-only (do not restore raw tokens)
        val textQuery = parsed.text.trim()
        val likeQuery = if (textQuery.isBlank() && parsed.year.isActive) "" else textQuery.ifBlank { query.trim() }
        val likeEscaped = SearchQueryNormalizer.escapeLike(likeQuery)
        val ftsCount = try {
            ftsDao.count()
        } catch (_: Exception) {
            0
        }
        if (ftsCount > 0 && textQuery.isNotBlank()) {
            val match = SearchFtsQuery.toMatchQuery(textQuery)
            val rows = try {
                ftsDao.match(match, limit * 3)
            } catch (_: Exception) {
                emptyList()
            }
            if (rows.isNotEmpty()) {
                return hydrateFts(rows, limit, playableOnly, parsed.year)
            }
            // Soft typo when FTS miss and query long enough
            if (SearchQueryNormalizer.fold(textQuery).length >= 4) {
                softTypo(textQuery, limit, playableOnly, parsed.year)?.let { return it }
            }
        }
        // LIKE fallback (also year-only queries with empty text)
        return likeFallback(likeEscaped, textQuery, limit, playableOnly, parsed.year)
    }

    private suspend fun hydrateFts(
        rows: List<com.lucasdss.ftpmusic.app.data.db.SearchFtsEntity>,
        limit: Int,
        playableOnly: Boolean,
        year: SearchYearConstraint,
    ): LocalSearchHit {
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
        var albums = if (albumIds.isNotEmpty()) {
            metadataDao.getAllAlbums().filter { it.id in albumIds.toSet() }
        } else {
            emptyList()
        }
        if (year.isActive) {
            albums = albums.filter { year.matches(it.year) }
            val albumYears = metadataDao.getAllAlbums().associate { it.id to it.year }
            tracks = tracks.filter { year.matches(albumYears[it.albumId]) }
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
            yearConstraint = year,
        )
    }

    private suspend fun likeFallback(
        like: String,
        textQuery: String,
        limit: Int,
        playableOnly: Boolean,
        year: SearchYearConstraint,
    ): LocalSearchHit {
        var tracks = if (textQuery.isBlank() && year.isActive) {
            emptyList()
        } else if (playableOnly) {
            trackDao.searchPlayableTracks(like)
        } else {
            trackDao.searchAllTracks(like)
        }
        var albums = if (textQuery.isBlank() && year.isActive) {
            metadataDao.getAllAlbums()
        } else if (playableOnly) {
            metadataDao.searchPlayableAlbums(like)
        } else {
            metadataDao.searchAlbums(like)
        }
        if (year.isActive) {
            albums = albums.filter { year.matches(it.year) }.take(limit)
            val allAlbumYears = metadataDao.getAllAlbums().associate { it.id to it.year }
            if (tracks.isEmpty() && textQuery.isBlank()) {
                val matchingAlbumIds = allAlbumYears.filter { year.matches(it.value) }.keys.toList()
                tracks = if (matchingAlbumIds.isNotEmpty()) {
                    trackDao.getTracksByAlbumIds(matchingAlbumIds).let { list ->
                        if (playableOnly) {
                            list.filter { it.isDownloaded || it.cachedFilePath != null }
                        } else {
                            list
                        }
                    }
                } else {
                    emptyList()
                }
            } else {
                tracks = tracks.filter { year.matches(allAlbumYears[it.albumId]) }
            }
        }
        val artists = if (textQuery.isBlank()) {
            emptyList()
        } else if (playableOnly) {
            metadataDao.searchPlayableArtists(like)
        } else {
            metadataDao.searchArtists(like)
        }
        return LocalSearchHit(
            tracks = tracks.take(limit),
            albums = albums.take(limit),
            artists = artists,
            playlists = if (textQuery.isBlank()) emptyList() else playlistDao.searchPlaylists(like),
            genres = if (textQuery.isBlank()) emptyList() else genreDao.searchGenres(like),
            usedFts = false,
            yearConstraint = year,
        )
    }

    private suspend fun softTypo(
        textQuery: String,
        limit: Int,
        playableOnly: Boolean,
        year: SearchYearConstraint,
    ): LocalSearchHit? {
        val folded = SearchQueryNormalizer.fold(textQuery)
        val prefix = SearchQueryNormalizer.escapeLike(folded.take(3))
        val candidates = if (playableOnly) {
            trackDao.searchPlayableTracks(prefix)
        } else {
            trackDao.searchAllTracks(prefix)
        }
        val matched = candidates.filter { t ->
            val title = SearchQueryNormalizer.fold(t.title)
            val artist = SearchQueryNormalizer.fold(t.artist.orEmpty())
            EditDistance.within(title, folded, 1) ||
                EditDistance.within(artist, folded, 1) ||
                title.split(" ").any { EditDistance.within(it, folded, 1) }
        }.take(limit)
        if (matched.isEmpty()) return null
        var tracks = matched
        if (year.isActive) {
            val albumYears = metadataDao.getAllAlbums().associate { it.id to it.year }
            tracks = tracks.filter { year.matches(albumYears[it.albumId]) }
        }
        if (tracks.isEmpty()) return null
        return LocalSearchHit(
            tracks = tracks,
            usedSoftTypo = true,
            yearConstraint = year,
        )
    }
}
