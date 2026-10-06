package com.lucasdss.ftpmusic.app.data.search

import com.lucasdss.ftpmusic.app.data.db.CachedAlbumEntity
import com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.GenreDao
import com.lucasdss.ftpmusic.app.data.db.GenreEntity
import com.lucasdss.ftpmusic.app.data.db.LyricsCacheDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistDao
import com.lucasdss.ftpmusic.app.data.db.PlaylistEntity
import com.lucasdss.ftpmusic.app.data.db.SearchFtsDao
import com.lucasdss.ftpmusic.app.data.db.SearchFtsEntity
import com.lucasdss.ftpmusic.app.data.db.SearchFtsTypes
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackSearchIndexRow
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Rebuilds the unified FTS search_fts table from Room metadata (ADR 0080 / Phase-3).
 * Call after metadata sync / enrichment / debounced upserts — never on keystroke.
 */
@Singleton
class SearchIndexRebuilder @Inject constructor(
    private val ftsDao: SearchFtsDao,
    private val trackDao: TrackDao,
    private val metadataDao: CachedMetadataDao,
    private val playlistDao: PlaylistDao,
    private val genreDao: GenreDao,
    private val lyricsCacheDao: LyricsCacheDao,
    private val storage: SecureStorage,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var debounceJob: Job? = null

    /** Debounced full rebuild — coalesces rapid upserts (search cache, lyrics toggle). */
    fun scheduleRebuild(debounceMs: Long = 2_000L) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceMs)
            rebuildAll()
        }
    }

    suspend fun rebuildAll() = mutex.withLock {
        withContext(Dispatchers.IO) {
            val rows = ArrayList<SearchFtsEntity>(4096)
            for (t in trackDao.getAllTracksForSearchIndex()) {
                rows.add(
                    SearchFtsEntity(
                        entityType = SearchFtsTypes.TRACK,
                        entityId = t.id,
                        body = trackBody(t),
                    ),
                )
            }
            for (a in metadataDao.getAllAlbums()) {
                rows.add(
                    SearchFtsEntity(
                        entityType = SearchFtsTypes.ALBUM,
                        entityId = a.id,
                        body = listOfNotNull(
                            a.name, a.artist, a.genre, a.year?.toString(), a.notes,
                            decadeLabel(a.year),
                        ).joinToString(" "),
                    ),
                )
            }
            for (ar in metadataDao.getAllArtists()) {
                rows.add(
                    SearchFtsEntity(
                        entityType = SearchFtsTypes.ARTIST,
                        entityId = ar.id,
                        body = listOfNotNull(
                            ar.name,
                            ar.similarArtistsJson,
                            ar.biography,
                            ar.searchAliases,
                            ar.searchTags,
                            ar.musicbrainzId,
                        ).joinToString(" "),
                    ),
                )
            }
            for (p in playlistDao.getAll()) {
                rows.add(
                    SearchFtsEntity(
                        entityType = SearchFtsTypes.PLAYLIST,
                        entityId = p.id,
                        body = listOfNotNull(p.name, p.comment).joinToString(" "),
                    ),
                )
            }
            for (g in genreDao.getAllByPopularity()) {
                rows.add(
                    SearchFtsEntity(
                        entityType = SearchFtsTypes.GENRE,
                        entityId = g.name,
                        body = g.name,
                    ),
                )
            }
            val lyricsEnabled = storage.get(SecureStorage.KEY_SEARCH_LYRICS) == "true"
            if (lyricsEnabled) {
                for (l in lyricsCacheDao.getAll()) {
                    val text = l.unstructuredText ?: l.syncedLinesJson ?: continue
                    if (text.isBlank()) continue
                    rows.add(
                        SearchFtsEntity(
                            entityType = SearchFtsTypes.LYRICS,
                            entityId = l.trackId,
                            body = listOfNotNull(l.title, l.artist, text).joinToString(" "),
                        ),
                    )
                }
            }
            ftsDao.clearAll()
            rows.chunked(500).forEach { chunk -> ftsDao.insertAll(chunk) }
        }
    }

    suspend fun ftsCount(): Int = try {
        ftsDao.count()
    } catch (_: Exception) {
        0
    }

    private fun trackBody(t: TrackSearchIndexRow): String =
        listOfNotNull(
            t.title, t.artist, t.album, t.genre, t.path,
            t.year?.toString(), decadeLabel(t.year), t.musicbrainzId,
        ).joinToString(" ")

    private fun decadeLabel(year: Int?): String? {
        if (year == null || year < 1900) return null
        val start = (year / 10) * 10
        return "${start}s ${start % 100}s"
    }
}
