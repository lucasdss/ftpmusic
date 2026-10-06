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
 * Phase-4: hydrate-by-id (no getAllAlbums on hot path).
 */
@Singleton
class LocalSearchRepository @Inject constructor(
    private val ftsDao: SearchFtsDao,
    private val trackDao: TrackDao,
    private val metadataDao: CachedMetadataDao,
    private val playlistDao: PlaylistDao,
    private val genreDao: GenreDao,
    private val searchIndexRebuilder: SearchIndexRebuilder,
) {
    suspend fun search(query: String, limit: Int = 100, playableOnly: Boolean = false): LocalSearchHit {
        val parsed = SearchYearParser.parse(query)
        val textQuery = parsed.text.trim()
        val likeQuery = if (textQuery.isBlank() && parsed.year.isActive) "" else textQuery.ifBlank { query.trim() }
        val likeEscaped = SearchQueryNormalizer.escapeLike(likeQuery)
        val ftsCount = try {
            searchIndexRebuilder.ftsCount()
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
            if (SearchQueryNormalizer.fold(textQuery).length >= 4) {
                softTypo(textQuery, limit, playableOnly, parsed.year)?.let { return it }
            }
        }
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
            metadataDao.getAlbumsByIds(albumIds)
        } else {
            emptyList()
        }
        if (year.isActive) {
            albums = albums.filter { year.matches(it.year) }
            val yearIds = (tracks.mapNotNull { it.albumId } + albums.map { it.id }).distinct()
            val albumYears = if (yearIds.isEmpty()) {
                emptyMap()
            } else {
                metadataDao.getAlbumYearRows(yearIds).associate { it.id to it.year }
            }
            tracks = tracks.filter { year.matches(albumYears[it.albumId]) }
        }
        val artists = if (artistIds.isNotEmpty()) {
            metadataDao.getArtistsByIds(artistIds)
        } else {
            emptyList()
        }
        val playlists = if (playlistIds.isNotEmpty()) {
            playlistDao.getPlaylistsByIds(playlistIds)
        } else {
            emptyList()
        }
        val genres = if (genreIds.isNotEmpty()) {
            genreDao.getGenresByNames(genreIds)
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
        var albums = when {
            textQuery.isBlank() && year.isActive -> albumsForYear(year, limit)
            playableOnly -> metadataDao.searchPlayableAlbums(like)
            else -> metadataDao.searchAlbums(like)
        }
        if (year.isActive) {
            albums = albums.filter { year.matches(it.year) }.take(limit)
            if (tracks.isEmpty() && textQuery.isBlank()) {
                val matchingAlbumIds = albums.map { it.id }
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
            } else if (tracks.isNotEmpty()) {
                val yearIds = tracks.mapNotNull { it.albumId }.distinct()
                val albumYears = if (yearIds.isEmpty()) {
                    emptyMap()
                } else {
                    metadataDao.getAlbumYearRows(yearIds).associate { it.id to it.year }
                }
                tracks = tracks.filter { year.matches(albumYears[it.albumId]) }
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

    private suspend fun albumsForYear(year: SearchYearConstraint, limit: Int): List<CachedAlbumEntity> {
        year.exactYear?.let { return metadataDao.searchAlbumsByExactYear(it, limit) }
        val min = year.minYear ?: return emptyList()
        val max = year.maxYear ?: return emptyList()
        return metadataDao.searchAlbumsByYearRange(min, max, limit)
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
            val yearIds = tracks.mapNotNull { it.albumId }.distinct()
            val albumYears = if (yearIds.isEmpty()) {
                emptyMap()
            } else {
                metadataDao.getAlbumYearRows(yearIds).associate { it.id to it.year }
            }
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
