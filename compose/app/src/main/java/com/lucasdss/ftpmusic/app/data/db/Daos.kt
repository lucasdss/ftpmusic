package com.lucasdss.ftpmusic.app.data.db

import androidx.room.*
import com.lucasdss.ftpmusic.app.playback.PersistedPlaybackState
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE id = :trackId")
    suspend fun getTrack(trackId: String): TrackEntity?

    /** Genre-only projection — waveform scrubber avoids SELECT * on track change. */
    @Query("SELECT genre FROM tracks WHERE id = :trackId")
    suspend fun getGenre(trackId: String): String?

    @Query("SELECT * FROM tracks WHERE cached_file_path IS NOT NULL ORDER BY last_played_at ASC LIMIT :limit")
    suspend fun getRecentlyPlayedCached(limit: Int = 20): List<TrackEntity>

    @Query("SELECT * FROM tracks ORDER BY last_played_at DESC LIMIT :limit")
    suspend fun getRecentlyPlayed(limit: Int = 20): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE starred_at IS NOT NULL ORDER BY starred_at DESC LIMIT :limit")
    suspend fun getStarred(limit: Int = 50): List<TrackEntity>

    /** Reactive variant — Favorites tab observes this so likes update live. */
    @Query("SELECT * FROM tracks WHERE starred_at IS NOT NULL ORDER BY starred_at DESC LIMIT :limit")
    fun getStarredFlow(limit: Int = 50): Flow<List<TrackEntity>>

    /** Lightweight: returns only IDs + rating for starred tracks. Used by buildWeightMap. */
    @Query("SELECT id, 5 AS user_rating FROM tracks WHERE starred_at IS NOT NULL")
    suspend fun getStarredIds(): List<StarredIdProjection>

    /** Filtered: only starred IDs in the candidate pool. */
    @Query("SELECT id, 5 AS user_rating FROM tracks WHERE id IN (:ids) AND starred_at IS NOT NULL")
    suspend fun getStarredIdsByIds(ids: List<String>): List<StarredIdProjection>

    @Query("SELECT * FROM tracks WHERE user_rating IS NOT NULL AND user_rating > 0")
    suspend fun getRatedTracks(): List<TrackEntity>

    /** Filtered: only rated tracks in the candidate pool. Uses lightweight projection. */
    @Query("SELECT id, user_rating FROM tracks WHERE id IN (:ids) AND user_rating IS NOT NULL AND user_rating > 0")
    suspend fun getRatedTracksByIds(ids: List<String>): List<StarredIdProjection>

    @Query("SELECT id, play_count FROM tracks WHERE id IN (:ids) AND play_count > 0")
    suspend fun getTrackPlayCounts(ids: List<String>): List<PlayCountInfo>

    /** Upsert track genre from album sync — preserves play_count, rating, starred_at. */
    @Query(
        "INSERT INTO tracks (id, title, artist, genre, play_count, user_rating, starred_at) VALUES (:id, :title, :artist, :genre, 0, NULL, NULL) ON CONFLICT(id) DO UPDATE SET genre = COALESCE(excluded.genre, tracks.genre), title = CASE WHEN tracks.title = tracks.id THEN excluded.title ELSE tracks.title END, artist = COALESCE(excluded.artist, tracks.artist)",
    )
    suspend fun upsertGenre(id: String, title: String, artist: String?, genre: String?)

    /** Bulk-populate track genres, titles, and artists from cached_genre_songs.
     *  Preserves all cache columns (cached_file_path, is_downloaded, is_auto_cached,
     *  cache_size_bytes, cached_at, last_played_at, bitrate, suffix, content_type,
     *  path, size_bytes, created_at) via LEFT JOIN on the existing tracks row.
     *  Also carries album_id, artist_id, duration, track_number, cover_art from the
     *  genre song source so genre-only tracks remain attributable to artist/album.
     *  COALESCE on album_id/artist_id/artist: the genre-song API may return null
     *  for these; preserve the existing tracks-table values instead of overwriting
     *  valid album/artist associations established by the album-track sync. */
    @Query(
        "INSERT OR REPLACE INTO tracks (id, server_id, title, artist, album_id, artist_id, genre, duration_seconds, track_number, cover_art_url, play_count, user_rating, starred_at, cached_file_path, is_downloaded, is_auto_cached, cache_size_bytes, cached_at, last_played_at, bitrate, suffix, content_type, path, size_bytes, created_at, is_disliked) SELECT cgs.id, '' as server_id, cgs.title, COALESCE(cgs.artist, t.artist), COALESCE(cgs.album_id, t.album_id), COALESCE(cgs.artist_id, t.artist_id), cgs.genre, cgs.duration, cgs.track_number, cgs.cover_art, COALESCE(t.play_count, 0), t.user_rating, t.starred_at, t.cached_file_path, COALESCE(t.is_downloaded, 0), COALESCE(t.is_auto_cached, 0), t.cache_size_bytes, t.cached_at, t.last_played_at, t.bitrate, t.suffix, t.content_type, t.path, t.size_bytes, t.created_at, COALESCE(t.is_disliked, 0) FROM cached_genre_songs cgs LEFT JOIN tracks t ON cgs.id = t.id",
    )
    suspend fun populateGenresFromCachedGenreSongs()

    /** Populate ALL track genres from cached_albums genre + cached_album_tracks.
     *  Preserves all cache columns via LEFT JOIN on the existing tracks row.
     *  COALESCE on artist/artist_id: prefer album-track data but preserve existing
     *  tracks-table values when the album source lacks them (defensive; keeps the
     *  two populate* queries idempotent regardless of execution order). */
    @Query(
        "INSERT OR REPLACE INTO tracks (id, server_id, title, artist, album_id, artist_id, genre, duration_seconds, track_number, disc_number, cover_art_url, play_count, user_rating, starred_at, cached_file_path, is_downloaded, is_auto_cached, cache_size_bytes, cached_at, last_played_at, bitrate, suffix, content_type, path, size_bytes, created_at, is_disliked) SELECT cat.id, '' as server_id, cat.title, COALESCE(cat.artist, t.artist), cat.album_id, COALESCE(cat.artist_id, t.artist_id), COALESCE(ca.genre, ''), cat.duration, cat.track_number, 1 as disc_number, cat.cover_art, COALESCE(t.play_count, 0), t.user_rating, t.starred_at, t.cached_file_path, COALESCE(t.is_downloaded, 0), COALESCE(t.is_auto_cached, 0), t.cache_size_bytes, t.cached_at, t.last_played_at, t.bitrate, t.suffix, t.content_type, t.path, t.size_bytes, t.created_at, COALESCE(t.is_disliked, 0) FROM cached_album_tracks cat JOIN cached_albums ca ON cat.album_id = ca.id LEFT JOIN tracks t ON cat.id = t.id",
    )
    suspend fun populateAllTrackGenres()

    @Query("UPDATE tracks SET starred_at = :starredAt WHERE id = :trackId")
    suspend fun setStarredAt(trackId: String, starredAt: Long?)

    /** Bulk-update starred_at for tracks. Used during star sync from server. */
    @Query("UPDATE tracks SET starred_at = :starredAt WHERE id IN (:ids)")
    suspend fun setStarredAtBulk(ids: List<String>, starredAt: Long)

    /** Clear stars for tracks the server doesn't have — but only stars recorded
     *  BEFORE [beforeMs]. Local-first likes made while offline (starred_at newer
     *  than the sync start) survive until the server confirms them. */
    @Query(
        "UPDATE tracks SET starred_at = NULL WHERE starred_at IS NOT NULL AND id NOT IN (:protectedIds) AND starred_at < :beforeMs",
    )
    suspend fun clearNonStarred(protectedIds: List<String>, beforeMs: Long)

    /** Update user_rating for a single track. */
    @Query("UPDATE tracks SET user_rating = :rating WHERE id = :trackId")
    suspend fun setRating(trackId: String, rating: Int)

    /** v41: set the local dislike flag for a track. */
    @Query("UPDATE tracks SET is_disliked = :disliked WHERE id = :trackId")
    suspend fun setDisliked(trackId: String, disliked: Boolean)

    /** v41: ids of disliked tracks among the given set. */
    @Query("SELECT id FROM tracks WHERE id IN (:ids) AND is_disliked = 1")
    suspend fun getDislikedIdsByIds(ids: List<String>): List<String>

    /** v41: ids of all disliked tracks (for the Favorites/Liked screen). */
    @Query("SELECT id FROM tracks WHERE is_disliked = 1")
    suspend fun getDislikedIds(): List<String>

    /** v44: mark a track's star as locally-removed-pending-server-confirmation. */
    @Query("UPDATE tracks SET pending_unstar_at = :ts WHERE id = :trackId")
    suspend fun setPendingUnstar(trackId: String, ts: Long?)

    /** v44: ids of tracks with a pending local unstar (skip re-star in mirror). */
    @Query("SELECT id FROM tracks WHERE pending_unstar_at IS NOT NULL")
    suspend fun getPendingUnstarIds(): List<String>

    /** v44: clear pending markers for ids the server has confirmed (or dropped). */
    @Query("UPDATE tracks SET pending_unstar_at = NULL WHERE id IN (:ids)")
    suspend fun clearPendingUnstar(ids: List<String>)

    /** Store MusicBrainz public rating + MBID for a track. */
    @Query(
        "UPDATE tracks SET public_rating = :rating, public_rating_votes = :votes, musicbrainz_id = :mbid WHERE id = :trackId",
    )
    suspend fun setPublicRating(trackId: String, rating: Double?, votes: Int?, mbid: String?)

    /** Batch-update user_rating from server sync. */
    @Transaction
    suspend fun syncRatings(ratings: Map<String, Int>) {
        ratings.forEach { (id, rating) -> setRating(id, rating) }
    }

    @Query("SELECT COALESCE(SUM(play_count), 0) FROM tracks")
    suspend fun getTotalPlays(): Int

    @Query("SELECT COALESCE(SUM(duration_seconds * play_count), 0) FROM tracks")
    suspend fun getTotalListeningSeconds(): Int

    @Query("SELECT COUNT(DISTINCT artist_id) FROM tracks WHERE artist_id IS NOT NULL AND play_count > 0")
    suspend fun getArtistCount(): Int

    @Query("SELECT COUNT(*) FROM tracks WHERE play_count > 0")
    suspend fun getTrackCount(): Int

    @Query("SELECT * FROM tracks WHERE cached_file_path IS NOT NULL ORDER BY play_count DESC LIMIT :limit")
    suspend fun getMostPlayedCached(limit: Int = 20): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE cached_file_path IS NOT NULL ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomCached(): TrackEntity?

    @Query("SELECT * FROM tracks WHERE cached_file_path IS NOT NULL ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomCachedTracks(limit: Int = 50): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE title LIKE '%' || :query || '%' AND cached_file_path IS NOT NULL")
    fun searchCached(query: String): Flow<List<TrackEntity>>

    @Query(
        "SELECT * FROM tracks WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' ORDER BY title ASC LIMIT 50",
    )
    suspend fun searchAllTracks(query: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE cached_file_path IS NOT NULL LIMIT :limit OFFSET :offset")
    suspend fun getCachedPaginated(offset: Int, limit: Int): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id IN (:trackIds)")
    suspend fun getTracksByIds(trackIds: List<String>): List<TrackEntity>

    /** Reactive watch over many tracks with ONE query — re-run on any `tracks`
     *  invalidation instead of one flow per track (album detail used to register
     *  2 flows per track: 200 flows for a 100-track album re-ran 200 SELECTs on
     *  every cache progress write). */
    @Query("SELECT * FROM tracks WHERE id IN (:trackIds)")
    fun watchTracksByIds(trackIds: List<String>): Flow<List<TrackEntity>>

    /** Cursor-paginated tracks for an artist. First page — ordered by title for
     *  a stable cursor. Uses the artist_id index. */
    @Query("SELECT * FROM tracks WHERE artist_id = :artistId ORDER BY title ASC, id ASC LIMIT :limit")
    suspend fun getTracksByArtistId(artistId: String, limit: Int): List<TrackEntity>

    /** Cursor-paginated tracks for an artist. Next page — resumes after :cursor
     *  (last title seen). Stable for duplicate titles via id tiebreak. */
    @Query(
        "SELECT * FROM tracks WHERE artist_id = :artistId AND title > :cursor ORDER BY title ASC, id ASC LIMIT :limit",
    )
    suspend fun getTracksByArtistIdAfter(artistId: String, cursor: String, limit: Int): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE album_id IN (:albumIds)")
    suspend fun getTracksByAlbumIds(albumIds: List<String>): List<TrackEntity>

    /** All tracks for a genre — used for local-first Daily Mix generation. */
    @Query("SELECT * FROM tracks WHERE genre = :genre")
    suspend fun getTracksByGenre(genre: String): List<TrackEntity>

    // ── v47/v48: Custom Daily Mix source pools (disliked tracks always
    //    excluded; id-only so composite AND filters can intersect sets) ──

    @Query("SELECT id FROM tracks WHERE genre = :genre AND is_disliked = 0")
    suspend fun getMixTrackIdsByGenre(genre: String): List<String>

    /** Decade pool ids: album-year range. Uses the tracks.album_id index
     *  joined to the albums ledger (PK lookup). */
    @Query(
        "SELECT t.id FROM tracks t JOIN albums a ON t.album_id = a.id " +
            "WHERE a.year BETWEEN :minYear AND :maxYear AND t.is_disliked = 0",
    )
    suspend fun getMixTrackIdsByYearRange(minYear: Int, maxYear: Int): List<String>

    @Query("SELECT id FROM tracks WHERE artist_id IN (:artistIds) AND is_disliked = 0")
    suspend fun getMixTrackIdsByArtistIds(artistIds: List<String>): List<String>

    /** Name fallback for tracks missing an artist_id (legacy rows). */
    @Query("SELECT id FROM tracks WHERE artist_id IS NULL AND artist IN (:artistNames) AND is_disliked = 0")
    suspend fun getMixTrackIdsByArtistNames(artistNames: List<String>): List<String>

    /** Random tracks with NO genre assigned — used as supplemental diversity for
     *  Daily Mix generation (gap filler after the genre-filtered primary pool). */
    @Query("SELECT * FROM tracks WHERE genre IS NULL OR genre = '' ORDER BY RANDOM() LIMIT :limit")
    suspend fun getTracksWithoutGenre(limit: Int = 50): List<TrackEntity>

    @Query("SELECT cached_file_path, is_downloaded FROM tracks WHERE id = :trackId")
    fun watchCacheStatus(trackId: String): Flow<CacheStatusRow>

    @Suppress("ConstructorParameterNaming")
    data class CacheStatusRow(val cached_file_path: String?, val is_downloaded: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Update
    suspend fun update(track: TrackEntity)

    @Query(
        "UPDATE tracks SET play_count = MIN(play_count + 1, 2147483647), last_played_at = :timestamp WHERE id = :trackId",
    )
    suspend fun incrementPlayCount(trackId: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM tracks WHERE is_downloaded = 1")
    suspend fun getDownloadedCount(): Int
}

@Dao
interface SessionStateDao {
    @Query("SELECT * FROM session_state WHERE id = 1")
    suspend fun get(): SessionStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SessionStateEntity)

    @Query("DELETE FROM session_state WHERE id = 1")
    suspend fun clear()
}

@Dao
interface CacheQueueDao {
    @Query(
        "SELECT * FROM cache_queue_items WHERE priority = :priority AND status = 'pending' ORDER BY created_at DESC LIMIT 1",
    )
    suspend fun getNextPendingByPriority(priority: Int): CacheQueueItemEntity?

    @Query("UPDATE cache_queue_items SET downloaded_bytes = :downloaded, total_bytes = :total WHERE id = :id")
    suspend fun updateProgress(id: Int, downloaded: Long, total: Long)

    @Query("UPDATE cache_queue_items SET priority = :priority WHERE id = :id")
    suspend fun updatePriority(id: Int, priority: Int)

    @Query("UPDATE cache_queue_items SET is_download = 1 WHERE id = :id")
    suspend fun markAsDownload(id: Int)

    @Query("SELECT * FROM cache_queue_items WHERE track_id = :trackId ORDER BY id DESC LIMIT 1")
    suspend fun getByTrackId(trackId: String): CacheQueueItemEntity?

    @Query("SELECT * FROM cache_queue_items WHERE track_id = :trackId ORDER BY id DESC LIMIT 1")
    fun watchByTrackId(trackId: String): Flow<List<CacheQueueItemEntity>>

    /** Reactive watch over many queue rows with ONE query (see TrackDao.watchTracksByIds). */
    @Query("SELECT * FROM cache_queue_items WHERE track_id IN (:trackIds)")
    fun watchByTrackIds(trackIds: List<String>): Flow<List<CacheQueueItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: CacheQueueItemEntity)

    /** Atomic enqueue: INSERT OR IGNORE returns -1 when a row for this track
     *  already exists (unique index on track_id), making concurrent enqueues
     *  for the same track safe. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(item: CacheQueueItemEntity): Long

    @Query("UPDATE cache_queue_items SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Int, status: String)

    @Query("SELECT COUNT(*) FROM cache_queue_items WHERE status = 'pending'")
    suspend fun pendingCount(): Int

    @Query("SELECT * FROM cache_queue_items ORDER BY id DESC")
    fun getAllFlow(): Flow<List<CacheQueueItemEntity>>

    @Query("DELETE FROM cache_queue_items WHERE status = :status")
    suspend fun deleteByStatus(status: String)

    @Query("DELETE FROM cache_queue_items")
    suspend fun deleteAll()

    @Query("UPDATE cache_queue_items SET status = 'pending' WHERE status = 'processing'")
    suspend fun resetProcessingToPending()

    @Query("UPDATE cache_queue_items SET retry_count = :count WHERE id = :id")
    suspend fun updateRetryCount(id: Int, count: Int)
}

// ── Genre DAO ──────────────────────────────────────────────────────────────────

@Dao
interface GenreDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(genres: List<GenreEntity>)

    @Query("SELECT * FROM genres ORDER BY song_count DESC")
    suspend fun getAllByPopularity(): List<GenreEntity>

    @Query(
        "SELECT DISTINCT t.genre FROM tracks t WHERE t.genre IS NOT NULL AND t.play_count > 0 ORDER BY t.last_played_at DESC LIMIT 10",
    )
    suspend fun getRecentlyPlayedGenres(): List<String>
}

// --- Queue DAO ---

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_items ORDER BY position ASC")
    fun getAll(): Flow<List<QueueItemEntity>>

    @Query("SELECT * FROM queue_items ORDER BY position ASC")
    suspend fun getAllOnce(): List<QueueItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: QueueItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<QueueItemEntity>)

    @Query("DELETE FROM queue_items WHERE position = :position")
    suspend fun deleteAt(position: Int)

    @Query("UPDATE queue_items SET position = position - 1 WHERE position > :after")
    suspend fun shiftPositionsDown(after: Int)

    @Query("UPDATE queue_items SET position = position + 1 WHERE position >= :from")
    suspend fun shiftPositionsUp(from: Int)

    @Query("UPDATE queue_items SET position = :newPosition WHERE track_id = :trackId AND position = :oldPosition")
    suspend fun movePosition(trackId: String, oldPosition: Int, newPosition: Int)

    @Query("DELETE FROM queue_items")
    suspend fun clear()

    @Query("SELECT * FROM queue_state WHERE id = 1")
    suspend fun getState(): QueueStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveState(state: QueueStateEntity)

    /** Position-only state update in ONE transaction — read-modify-write cannot
     *  interleave with [replaceAllAndState], so a stale index can never be
     *  written over a fresh queue replace. */
    @Transaction
    suspend fun savePositionOnly(index: Int, positionMs: Long) {
        val current = getState() ?: QueueStateEntity()
        saveState(current.copy(currentIndex = index, positionMs = positionMs))
    }

    @Transaction
    suspend fun atomicReplace(items: List<QueueItemEntity>) {
        clear()
        insertAll(items)
    }

    /** Atomically replace queue items AND the state row in ONE transaction.
     *  Previously these were two transactions — a concurrent savePositionOnly
     *  could interleave and persist items from save-A with state from save-B
     *  (restored queue starts at the wrong track). */
    @Transaction
    suspend fun replaceAllAndState(items: List<QueueItemEntity>, state: QueueStateEntity) {
        clear()
        insertAll(items)
        saveState(state)
    }

    @Query("SELECT COUNT(*) FROM queue_items")
    suspend fun count(): Int
}

// ── Playback State DAO ─────────────────────────────────────────────────────────

@Dao
interface PlaybackStateDao {
    @Query("SELECT * FROM playback_state WHERE id = 1")
    suspend fun get(): PersistedPlaybackState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(state: PersistedPlaybackState)
}

// ── Lyrics Cache DAO ──────────────────────────────────────────────────────────────

@Dao
interface LyricsCacheDao {
    @Query("SELECT * FROM lyrics_cache WHERE trackId = :trackId")
    suspend fun get(trackId: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: LyricsCacheEntity)

    /** Touch the fetchedAt timestamp on cache hit to track LRU recency. */
    @Query("UPDATE lyrics_cache SET fetchedAt = :now WHERE trackId = :trackId")
    suspend fun touch(trackId: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM lyrics_cache WHERE trackId = :trackId")
    suspend fun delete(trackId: String)

    @Query("SELECT COUNT(*) FROM lyrics_cache")
    suspend fun count(): Int
}

// ── Playlist DAO ────────────────────────────────────────────────────────────────

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(playlists: List<PlaylistEntity>)

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    suspend fun getAll(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntries(entries: List<PlaylistEntryEntity>)

    @Query("SELECT * FROM playlist_entries WHERE playlist_id = :playlistId ORDER BY position ASC")
    suspend fun getEntries(playlistId: String): List<PlaylistEntryEntity>

    @Query("DELETE FROM playlist_entries WHERE playlist_id = :playlistId")
    suspend fun clearEntries(playlistId: String)

    @Transaction
    suspend fun replaceEntries(playlistId: String, entries: List<PlaylistEntryEntity>) {
        clearEntries(playlistId)
        upsertEntries(entries)
    }

    @Transaction
    suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<String>): Int {
        val existingEntries = getEntries(playlistId)
        val existingIds = existingEntries.map { it.trackId }.toSet()
        val newTrackIds = trackIds.filter { it !in existingIds }
        if (newTrackIds.isEmpty()) return existingEntries.size
        val nextPosition = (existingEntries.maxOfOrNull { it.position } ?: -1) + 1
        val newEntries = newTrackIds.mapIndexed { idx, tid ->
            PlaylistEntryEntity(playlistId = playlistId, trackId = tid, position = nextPosition + idx)
        }
        upsertEntries(newEntries)
        val newCount = existingEntries.size + newTrackIds.size
        val meta = getById(playlistId)
        if (meta != null) {
            upsertAll(listOf(meta.copy(trackCount = newCount, updatedAt = System.currentTimeMillis())))
        }
        return newCount
    }

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM playlist_entries WHERE playlist_id = :playlistId AND track_id = :trackId")
    suspend fun removeEntry(playlistId: String, trackId: String)

    /** Re-point entries written under a temp local id to the server-assigned id
     *  (create flush remap). Prevents orphaned entries when tracks are added to
     *  a playlist before its create change is flushed. */
    @Query("UPDATE playlist_entries SET playlist_id = :newId WHERE playlist_id = :oldId")
    suspend fun remapEntries(oldId: String, newId: String)

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun count(): Int
}

// ── Pending Playlist Changes DAO ────────────────────────────────────────────────

@Dao
interface PendingPlaylistChangeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(change: PendingPlaylistChangeEntity)

    @Query("SELECT * FROM pending_playlist_changes WHERE flushed = 0 ORDER BY created_at ASC")
    suspend fun getPending(): List<PendingPlaylistChangeEntity>

    @Query("UPDATE pending_playlist_changes SET flushed = 1 WHERE id = :id")
    suspend fun markFlushed(id: Int)

    @Query("DELETE FROM pending_playlist_changes WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT COUNT(*) FROM pending_playlist_changes WHERE flushed = 0")
    suspend fun pendingCount(): Int

    @Query("DELETE FROM pending_playlist_changes WHERE flushed = 1 AND created_at < :cutoff")
    suspend fun deleteFlushedOlderThan(cutoff: Long)

    @Query("SELECT COUNT(*) FROM pending_playlist_changes WHERE flushed = 0 AND playlist_id = :playlistId")
    suspend fun pendingCountForPlaylist(playlistId: String): Int

    @Query("UPDATE pending_playlist_changes SET playlist_id = :newId WHERE playlist_id = :oldId AND flushed = 0")
    suspend fun remapPlaylistId(oldId: String, newId: String)
}

// ── Cached Metadata DAO ────────────────────────────────────────────────────────

@Dao
interface CachedMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlbums(albums: List<CachedAlbumEntity>)

    @Query("DELETE FROM cached_albums")
    suspend fun clearAlbums()

    /** Atomically clear and replace all albums — no empty window for concurrent queries. */
    @Transaction
    suspend fun replaceAlbums(albums: List<CachedAlbumEntity>) {
        clearAlbums()
        upsertAlbums(albums)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtists(artists: List<CachedArtistEntity>)

    @Query("DELETE FROM cached_artists")
    suspend fun clearArtists()

    @Transaction
    suspend fun replaceArtists(artists: List<CachedArtistEntity>) {
        clearArtists()
        upsertArtists(artists)
    }

    @Query("SELECT COUNT(*) FROM cached_albums")
    suspend fun albumCount(): Int

    @Query("SELECT COUNT(*) FROM cached_album_tracks")
    suspend fun cachedTrackCount(): Int

    /**
     * Count albums with zero cached tracks — single SQL query instead of
     * O(n) individual reads in the sync loop. Used to set tracksTotal for progress.
     */
    @Query(
        """
        SELECT COUNT(*) FROM cached_albums a
        WHERE NOT EXISTS (SELECT 1 FROM cached_album_tracks t WHERE t.album_id = a.id)
    """,
    )
    suspend fun countUncachedAlbums(): Int

    @Query("SELECT * FROM cached_albums ORDER BY name ASC")
    suspend fun getAllAlbums(): List<CachedAlbumEntity>

    @Query("SELECT cover_art FROM cached_albums WHERE id = :albumId")
    suspend fun getAlbumCoverArt(albumId: String): String?

    @Query("SELECT * FROM cached_albums WHERE id = :albumId LIMIT 1")
    suspend fun getAlbumById(albumId: String): CachedAlbumEntity?

    @Query(
        "SELECT * FROM cached_albums WHERE name LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' ORDER BY name ASC",
    )
    suspend fun searchAlbums(query: String): List<CachedAlbumEntity>

    @Query("SELECT COUNT(*) FROM cached_artists")
    suspend fun artistCount(): Int

    @Query("SELECT * FROM cached_artists ORDER BY name ASC")
    suspend fun getAllArtists(): List<CachedArtistEntity>

    @Query("SELECT * FROM cached_artists WHERE id = :artistId LIMIT 1")
    suspend fun getArtistById(artistId: String): CachedArtistEntity?

    @Query("SELECT * FROM cached_albums WHERE artist_id = :artistId ORDER BY name ASC")
    suspend fun getAlbumsByArtistId(artistId: String): List<CachedAlbumEntity>

    @Query("SELECT * FROM cached_albums WHERE artist = :artistName ORDER BY name ASC")
    suspend fun getAlbumsByArtistName(artistName: String): List<CachedAlbumEntity>

    /** Albums whose TRACKS are attributed to this artist_id, even if the album's
     *  artist_id points elsewhere (e.g., Navidrome returns album artist as
     *  "Original Soundtrack" while tracks are by the actual artist). */
    @Query(
        "SELECT DISTINCT ca.* FROM cached_albums ca INNER JOIN cached_album_tracks cat ON ca.id = cat.album_id WHERE cat.artist_id = :artistId ORDER BY ca.name ASC",
    )
    suspend fun getAlbumsByTrackArtistId(artistId: String): List<CachedAlbumEntity>

    /** Recompute artist album counts from the actual cached_albums rows.
     *  Counts albums where the artist is the album artist (by id or name) OR
     *  a track artist (Navidrome may attribute the album to "Original
     *  Soundtrack" while tracks are by the actual artist). Mirrors the artist
     *  detail page's merged queries. Must run AFTER cached_album_tracks is
     *  populated (post tracks-phase) so track attribution is included. */
    @Query(
        "UPDATE cached_artists SET album_count = (SELECT COUNT(DISTINCT ca.id) FROM cached_albums ca LEFT JOIN cached_album_tracks cat ON ca.id = cat.album_id WHERE ca.artist_id = cached_artists.id OR ca.artist = cached_artists.name OR cat.artist_id = cached_artists.id)",
    )
    suspend fun updateArtistAlbumCounts()

    /** Store MusicBrainz public rating + MBID for an album. */
    @Query(
        "UPDATE cached_albums SET public_rating = :rating, public_rating_votes = :votes, musicbrainz_id = :mbid WHERE id = :albumId",
    )
    suspend fun setAlbumPublicRating(albumId: String, rating: Double?, votes: Int?, mbid: String?)

    /** Store MusicBrainz public rating + MBID for an artist. */
    @Query(
        "UPDATE cached_artists SET public_rating = :rating, public_rating_votes = :votes, musicbrainz_id = :mbid WHERE id = :artistId",
    )
    suspend fun setArtistPublicRating(artistId: String, rating: Double?, votes: Int?, mbid: String?)

    /** Store similar artists JSON for an artist (last.fm). */
    @Query("UPDATE cached_artists SET similar_artists_json = :json WHERE id = :artistId")
    suspend fun setArtistSimilarArtists(artistId: String, json: String?)

    @Query("SELECT * FROM cached_artists WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchArtists(query: String): List<CachedArtistEntity>

    /** Paged variant for the Custom Daily Mix artist picker ("Load more"). */
    @Query(
        "SELECT * FROM cached_artists WHERE name LIKE '%' || :query || '%' " +
            "ORDER BY name ASC LIMIT :limit OFFSET :offset",
    )
    suspend fun searchArtistsPaged(query: String, limit: Int, offset: Int): List<CachedArtistEntity>

    /** Resolve selected artist ids to display names (mix editor chips). */
    @Query("SELECT * FROM cached_artists WHERE id IN (:ids)")
    suspend fun getArtistsByIds(ids: List<String>): List<CachedArtistEntity>

    @Query(
        "SELECT DISTINCT a.* FROM cached_albums a JOIN tracks t ON t.album_id = a.id WHERE t.cached_file_path IS NOT NULL OR t.is_downloaded = 1 ORDER BY a.name ASC",
    )
    suspend fun getOfflineAlbums(): List<CachedAlbumEntity>

    @Query(
        "SELECT DISTINCT ar.* FROM cached_artists ar JOIN tracks t ON (t.artist_id = ar.id OR (t.artist_id IS NULL AND t.artist = ar.name)) WHERE t.cached_file_path IS NOT NULL OR t.is_downloaded = 1 ORDER BY ar.name ASC",
    )
    suspend fun getOfflineArtists(): List<CachedArtistEntity>

    // ── Album/Artist star sync ───────────────────────────────────────────────
    // Star/dislike state lives in the `albums`/`artists` ledger tables, which
    // are repopulated every metadata sync (see syncAlbumLedger/syncArtistLedger)
    // with ON CONFLICT DO UPDATE — stars and dislikes survive full wipes of the
    // cached_albums/cached_artists tables.

    @Query("UPDATE albums SET starred_at = :starredAt WHERE id = :albumId")
    suspend fun setAlbumStarredAt(albumId: String, starredAt: Long?)

    @Query("UPDATE albums SET user_rating = :rating WHERE id = :albumId")
    suspend fun setAlbumRating(albumId: String, rating: Int)

    @Query("UPDATE artists SET starred_at = :starredAt WHERE id = :artistId")
    suspend fun setArtistStarredAt(artistId: String, starredAt: Long?)

    /** Bulk-update starred_at for albums. Used during star sync from server. */
    @Query("UPDATE albums SET starred_at = :starredAt WHERE id IN (:ids)")
    suspend fun setAlbumStarredAtBulk(ids: List<String>, starredAt: Long)

    /** Bulk-update starred_at for artists. Used during star sync from server. */
    @Query("UPDATE artists SET starred_at = :starredAt WHERE id IN (:ids)")
    suspend fun setArtistStarredAtBulk(ids: List<String>, starredAt: Long)

    /** Clear album stars the server doesn't have — but only stars recorded
     *  BEFORE [beforeMs] (local-first offline likes survive until confirmed). */
    @Query(
        "UPDATE albums SET starred_at = NULL WHERE starred_at IS NOT NULL AND id NOT IN (:protectedIds) AND starred_at < :beforeMs",
    )
    suspend fun clearNonStarredAlbums(protectedIds: List<String>, beforeMs: Long)

    /** Clear artist stars the server doesn't have — but only stars recorded
     *  BEFORE [beforeMs] (local-first offline likes survive until confirmed). */
    @Query(
        "UPDATE artists SET starred_at = NULL WHERE starred_at IS NOT NULL AND id NOT IN (:protectedIds) AND starred_at < :beforeMs",
    )
    suspend fun clearNonStarredArtists(protectedIds: List<String>, beforeMs: Long)

    /** v43: ensure an album exists in the favorites ledger (search-upserted
     *  albums live in cached_albums only). INSERT OR IGNORE never touches
     *  existing favorite state. */
    @Query(
        "INSERT OR IGNORE INTO albums (id, server_id, artist_id, name, artist, year, cover_art_url, genre, created_at) SELECT id, '', artist_id, name, artist, year, cover_art, genre, cached_at FROM cached_albums WHERE id = :albumId",
    )
    suspend fun ensureAlbumLedgerRow(albumId: String)

    /** v43: ensure an artist exists in the favorites ledger. */
    @Query(
        "INSERT OR IGNORE INTO artists (id, server_id, name, cover_art_url) SELECT id, '', name, cover_art FROM cached_artists WHERE id = :artistId",
    )
    suspend fun ensureArtistLedgerRow(artistId: String)

    @Query("SELECT COUNT(*) FROM albums")
    suspend fun ledgerAlbumCount(): Int

    @Query("SELECT COUNT(*) FROM artists")
    suspend fun ledgerArtistCount(): Int

    // ── Starred/disliked reads (ledger tables) ───────────────────────────────

    @Query("SELECT * FROM albums WHERE starred_at IS NOT NULL ORDER BY starred_at DESC LIMIT :limit")
    suspend fun getStarredAlbums(limit: Int = 50): List<AlbumEntity>

    /** Reactive variant — Home/Favorites observe this so sections update live. */
    @Query("SELECT * FROM albums WHERE starred_at IS NOT NULL ORDER BY starred_at DESC LIMIT :limit")
    fun getStarredAlbumsFlow(limit: Int = 50): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM artists WHERE starred_at IS NOT NULL ORDER BY starred_at DESC LIMIT :limit")
    suspend fun getStarredArtists(limit: Int = 50): List<ArtistEntity>

    /** v47: ALL liked artists (no cap) — Favorite Artists mix source pool. */
    @Query("SELECT * FROM artists WHERE starred_at IS NOT NULL ORDER BY starred_at DESC")
    suspend fun getAllStarredArtists(): List<ArtistEntity>

    /** Reactive variant — Home/Favorites observe this so sections update live. */
    @Query("SELECT * FROM artists WHERE starred_at IS NOT NULL ORDER BY starred_at DESC LIMIT :limit")
    fun getStarredArtistsFlow(limit: Int = 50): Flow<List<ArtistEntity>>

    @Query("SELECT id FROM albums WHERE starred_at IS NOT NULL")
    suspend fun getStarredAlbumIds(): List<String>

    /** Reactive variant — screens observe this so album like state updates live. */
    @Query("SELECT id FROM albums WHERE starred_at IS NOT NULL")
    fun getStarredAlbumIdsFlow(): Flow<List<String>>

    @Query("SELECT id FROM artists WHERE starred_at IS NOT NULL")
    suspend fun getStarredArtistIds(): List<String>

    /** v43: local dislike for an album. Clears any like (= star) first. */
    @Query("UPDATE albums SET is_disliked = :disliked WHERE id = :albumId")
    suspend fun setAlbumDisliked(albumId: String, disliked: Boolean)

    /** v43: local dislike for an artist. Clears any like (= star) first. */
    @Query("UPDATE artists SET is_disliked = :disliked WHERE id = :artistId")
    suspend fun setArtistDisliked(artistId: String, disliked: Boolean)

    @Query("SELECT id FROM albums WHERE is_disliked = 1")
    suspend fun getDislikedAlbumIds(): List<String>

    /** Reactive variant — Home observes this so dislike state updates live. */
    @Query("SELECT id FROM albums WHERE is_disliked = 1")
    fun getDislikedAlbumIdsFlow(): Flow<List<String>>

    @Query("SELECT id FROM artists WHERE is_disliked = 1")
    suspend fun getDislikedArtistIds(): List<String>

    /** Reactive variant — Home observes this so dislike state updates live. */
    @Query("SELECT id FROM artists WHERE is_disliked = 1")
    fun getDislikedArtistIdsFlow(): Flow<List<String>>

    /** v44: mark an album's star as locally-removed-pending-server-confirmation. */
    @Query("UPDATE albums SET pending_unstar_at = :ts WHERE id = :albumId")
    suspend fun setAlbumPendingUnstar(albumId: String, ts: Long?)

    /** v44: ids of albums with a pending local unstar (skip re-star in mirror). */
    @Query("SELECT id FROM albums WHERE pending_unstar_at IS NOT NULL")
    suspend fun getPendingUnstarAlbumIds(): List<String>

    /** v44: mark an artist's star as locally-removed-pending-server-confirmation. */
    @Query("UPDATE artists SET pending_unstar_at = :ts WHERE id = :artistId")
    suspend fun setArtistPendingUnstar(artistId: String, ts: Long?)

    /** v44: ids of artists with a pending local unstar (skip re-star in mirror). */
    @Query("SELECT id FROM artists WHERE pending_unstar_at IS NOT NULL")
    suspend fun getPendingUnstarArtistIds(): List<String>

    /** v44: clear pending markers for ids the server has confirmed (or dropped). */
    @Query("UPDATE albums SET pending_unstar_at = NULL WHERE id IN (:ids)")
    suspend fun clearPendingUnstarAlbums(ids: List<String>)

    /** v44: clear pending markers for ids the server has confirmed (or dropped). */
    @Query("UPDATE artists SET pending_unstar_at = NULL WHERE id IN (:ids)")
    suspend fun clearPendingUnstarArtists(ids: List<String>)

    // ── Ledger population (runs at the end of syncAlbums/syncArtists) ────────
    // Copies metadata into albums/artists without touching the favorite state:
    // INSERT OR IGNORE adds new rows, then a correlated UPDATE refreshes
    // metadata on existing rows — starred_at, user_rating, is_disliked survive.

    @Query(
        "INSERT OR IGNORE INTO albums (id, server_id, artist_id, name, artist, year, cover_art_url, genre, created_at) SELECT id, '', artist_id, name, artist, year, cover_art, genre, cached_at FROM cached_albums",
    )
    suspend fun insertNewAlbumsToLedger()

    @Query(
        "UPDATE albums SET artist_id = (SELECT artist_id FROM cached_albums WHERE cached_albums.id = albums.id), name = (SELECT name FROM cached_albums WHERE cached_albums.id = albums.id), artist = (SELECT artist FROM cached_albums WHERE cached_albums.id = albums.id), year = (SELECT year FROM cached_albums WHERE cached_albums.id = albums.id), cover_art_url = (SELECT cover_art FROM cached_albums WHERE cached_albums.id = albums.id), genre = (SELECT genre FROM cached_albums WHERE cached_albums.id = albums.id) WHERE EXISTS (SELECT 1 FROM cached_albums WHERE cached_albums.id = albums.id)",
    )
    suspend fun refreshAlbumLedgerMetadata()

    @Transaction
    suspend fun syncAlbumLedger() {
        insertNewAlbumsToLedger()
        refreshAlbumLedgerMetadata()
    }

    /** Remove ledger rows for albums no longer on the server — but keep rows
     *  that are starred or disliked (favorites survive temporary gaps). */
    @Query(
        "DELETE FROM albums WHERE id NOT IN (SELECT id FROM cached_albums) AND starred_at IS NULL AND is_disliked = 0",
    )
    suspend fun pruneAlbumLedger()

    @Query(
        "INSERT OR IGNORE INTO artists (id, server_id, name, cover_art_url) SELECT id, '', name, cover_art FROM cached_artists",
    )
    suspend fun insertNewArtistsToLedger()

    @Query(
        "UPDATE artists SET name = (SELECT name FROM cached_artists WHERE cached_artists.id = artists.id), cover_art_url = (SELECT cover_art FROM cached_artists WHERE cached_artists.id = artists.id) WHERE EXISTS (SELECT 1 FROM cached_artists WHERE cached_artists.id = artists.id)",
    )
    suspend fun refreshArtistLedgerMetadata()

    @Transaction
    suspend fun syncArtistLedger() {
        insertNewArtistsToLedger()
        refreshArtistLedgerMetadata()
    }

    @Query(
        "DELETE FROM artists WHERE id NOT IN (SELECT id FROM cached_artists) AND starred_at IS NULL AND is_disliked = 0",
    )
    suspend fun pruneArtistLedger()

    /** Up to 4 distinct album cover art ids for a playlist's tracks (montage). */
    @Query(
        "SELECT DISTINCT ca.cover_art FROM tracks t JOIN cached_albums ca ON ca.id = t.album_id WHERE t.id IN (:trackIds) AND ca.cover_art IS NOT NULL LIMIT 4",
    )
    suspend fun getPlaylistMontageCovers(trackIds: List<String>): List<String>

    // ── Album tracks lazy cache ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlbumTracks(tracks: List<CachedAlbumTrackEntity>)

    @Query("SELECT * FROM cached_album_tracks WHERE album_id = :albumId ORDER BY track_number ASC")
    suspend fun getAlbumTracks(albumId: String): List<CachedAlbumTrackEntity>

    @Query("DELETE FROM cached_album_tracks WHERE album_id = :albumId")
    suspend fun clearAlbumTracks(albumId: String)

    /** Atomically replace all tracks for an album — prevents partial data on process death. */
    @Transaction
    suspend fun replaceAlbumTracks(albumId: String, tracks: List<CachedAlbumTrackEntity>) {
        clearAlbumTracks(albumId)
        upsertAlbumTracks(tracks)
    }

    /** Replace tracks for MANY albums in one transaction (metadata-sync track
     *  phase). Fewer, shorter DB locks than per-album transactions, so
     *  concurrent Room reads are not starved during a full resync. */
    @Transaction
    suspend fun replaceAlbumTracksBatch(entries: List<CachedAlbumTrackEntity>) {
        entries.groupBy { it.albumId }.forEach { (albumId, tracks) ->
            clearAlbumTracks(albumId)
            upsertAlbumTracks(tracks)
        }
    }

    /** Remove tracks whose album no longer exists on the server (deleted albums). */
    @Query("DELETE FROM cached_album_tracks WHERE album_id NOT IN (SELECT id FROM cached_albums)")
    suspend fun deleteOrphanedAlbumTracks(): Int

    /** Get album name from cached_albums (needed for MediaSessionCallback album title). */
    @Query("SELECT name FROM cached_albums WHERE id = :albumId LIMIT 1")
    suspend fun getAlbumName(albumId: String): String?
}

// ── Radio Favorites DAO ────────────────────────────────────────────────────────
// v43: Bookmarked internet radio stations (local-only favorites).

@Dao
interface RadioFavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(favorite: RadioFavoriteEntity)

    @Query("DELETE FROM radio_favorites WHERE station_id = :stationId")
    suspend fun delete(stationId: String)

    @Query("SELECT * FROM radio_favorites ORDER BY bookmarked_at DESC")
    suspend fun getAll(): List<RadioFavoriteEntity>

    /** Reactive variant — Home/Favorites observe this so bookmarks update live. */
    @Query("SELECT * FROM radio_favorites ORDER BY bookmarked_at DESC")
    fun getAllFlow(): Flow<List<RadioFavoriteEntity>>

    @Query("SELECT COUNT(*) FROM radio_favorites")
    suspend fun count(): Int
}

// ── Genre Mix DAO ────────────────────────────────────────────────────────────────

@Dao
interface GenreMixDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGenres(genres: List<CachedGenreEntity>)

    @Query("SELECT * FROM cached_genres ORDER BY song_count DESC")
    suspend fun getTopGenres(): List<CachedGenreEntity>

    @Query("DELETE FROM cached_genres")
    suspend fun clearGenres()

    @Transaction
    suspend fun replaceGenres(genres: List<CachedGenreEntity>) {
        clearGenres()
        upsertGenres(genres)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSongs(songs: List<CachedGenreSongEntity>)

    @Query("SELECT * FROM cached_genre_songs WHERE genre = :genre ORDER BY title ASC LIMIT 120")
    suspend fun getSongsForGenre(genre: String): List<CachedGenreSongEntity>

    @Query("DELETE FROM cached_genre_songs WHERE genre = :genre")
    suspend fun clearSongsForGenre(genre: String)

    @Transaction
    suspend fun replaceSongs(genre: String, songs: List<CachedGenreSongEntity>) {
        clearSongsForGenre(genre)
        upsertSongs(songs)
    }

    @Query("SELECT COUNT(*) FROM cached_genres")
    suspend fun genreCount(): Int

    @Query("SELECT COUNT(*) FROM cached_genre_songs")
    suspend fun genreSongCount(): Int

    @Query("SELECT COUNT(*) FROM cached_genre_songs WHERE genre = :genre")
    suspend fun countSongsForGenre(genre: String): Int

    @Query("SELECT * FROM daily_mix WHERE date = :date AND mix_id = :mixId LIMIT 1")
    suspend fun getDailyMix(date: String, mixId: Long): DailyMixEntity?

    /** Batch read for Home cards: today's + yesterday's rows in one query. */
    @Query("SELECT * FROM daily_mix WHERE date IN (:dates)")
    suspend fun getDailyMixesForDates(dates: List<String>): List<DailyMixEntity>

    /** Track counts for many daily mixes in one query (Home card batch). */
    @Query(
        "SELECT mix_id, COUNT(*) AS track_count FROM daily_mix_tracks " +
            "WHERE mix_id IN (:dailyMixIds) GROUP BY mix_id",
    )
    suspend fun getDailyMixTrackCounts(dailyMixIds: List<Int>): List<DailyMixTrackCount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyMix(mix: DailyMixEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyMixTracks(tracks: List<DailyMixTrackEntity>)

    @Query("DELETE FROM daily_mix WHERE date = :date AND mix_id = :mixId")
    suspend fun deleteDailyMix(date: String, mixId: Long)

    @Transaction
    suspend fun replaceDailyMix(date: String, mixId: Long, tracks: List<DailyMixTrackEntity>) {
        deleteDailyMix(date, mixId) // cascade deletes old tracks
        val dailyMixId = upsertDailyMix(DailyMixEntity(date = date, mixId = mixId))
        insertDailyMixTracks(tracks.map { it.copy(mixId = dailyMixId) })
    }

    @Query("SELECT track_id FROM daily_mix_tracks WHERE mix_id = :mixId ORDER BY position ASC")
    suspend fun getDailyMixTrackIds(mixId: Int): List<String>

    @Query("SELECT COUNT(*) FROM daily_mix WHERE date = :date")
    suspend fun countDailyMixesForDate(date: String): Int

    /** First 4 unique cover URLs of a generated mix, in track order — Home cards. */
    @Query(
        "SELECT DISTINCT t.cover_art_url FROM daily_mix_tracks dmt " +
            "JOIN tracks t ON dmt.track_id = t.id " +
            "WHERE dmt.mix_id = :dailyMixId AND t.cover_art_url IS NOT NULL " +
            "ORDER BY dmt.position ASC LIMIT 4",
    )
    suspend fun getDailyMixCovers(dailyMixId: Int): List<CoverArtProjection>

    /** Fetch ALL songs for a genre (no LIMIT) — used during manual refresh. */
    @Query("SELECT * FROM cached_genre_songs WHERE genre = :genre ORDER BY title ASC")
    suspend fun getSongsForGenreUnlimited(genre: String): List<CachedGenreSongEntity>

    /** Look up cached_genre_songs by track IDs across ALL genres. */
    @Query("SELECT * FROM cached_genre_songs WHERE id IN (:ids)")
    suspend fun getSongsByIds(ids: List<String>): List<CachedGenreSongEntity>
}

// ── Custom Daily Mix DAO (v47) ───────────────────────────────────────────────

@Dao
interface CustomMixDao {
    @Query("SELECT * FROM custom_mixes ORDER BY id ASC")
    suspend fun getAll(): List<CustomMixEntity>

    @Query("SELECT * FROM custom_mixes ORDER BY id ASC")
    fun getAllFlow(): Flow<List<CustomMixEntity>>

    @Query("SELECT * FROM custom_mixes WHERE id = :id")
    suspend fun getById(id: Long): CustomMixEntity?

    @Query("SELECT COUNT(*) FROM custom_mixes")
    suspend fun count(): Int

    @Insert
    suspend fun insert(mix: CustomMixEntity): Long

    @Insert
    suspend fun insertAll(mixes: List<CustomMixEntity>)

    @Update
    suspend fun update(mix: CustomMixEntity)

    @Query("DELETE FROM custom_mixes WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Atomic first-run seed: re-checks the marker and fills only the free
     *  slots under [maxMixes], then marks seeded — one transaction, so
     *  concurrent callers cannot double-seed or overshoot the cap. */
    @Transaction
    suspend fun seedOnce(mixes: List<CustomMixEntity>, maxMixes: Int): Boolean {
        if (getState()?.seededAt != null) return false
        val slots = (maxMixes - count()).coerceAtLeast(0)
        if (slots > 0) insertAll(mixes.take(slots))
        upsertState(CustomMixStateEntity(seededAt = System.currentTimeMillis()))
        return true
    }

    /** Atomic cap-checked insert: -1 when the 20-mix cap is already reached. */
    @Transaction
    suspend fun insertIfUnderCap(mix: CustomMixEntity, maxMixes: Int): Long =
        if (count() >= maxMixes) -1L else insert(mix)

    @Query("SELECT * FROM custom_mix_state WHERE id = 1")
    suspend fun getState(): CustomMixStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: CustomMixStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCacheTracks(tracks: List<CustomMixCacheTrackEntity>)

    @Query("SELECT track_id FROM custom_mix_cache_tracks WHERE custom_mix_id = :mixId")
    suspend fun getOwnedTrackIds(mixId: Long): List<String>

    @Query("DELETE FROM custom_mix_cache_tracks WHERE custom_mix_id = :mixId AND track_id IN (:trackIds)")
    suspend fun deleteCacheTracks(mixId: Long, trackIds: List<String>)

    @Query("DELETE FROM custom_mix_cache_tracks WHERE custom_mix_id = :mixId")
    suspend fun deleteAllCacheTracks(mixId: Long)

    @Query("SELECT DISTINCT track_id FROM custom_mix_cache_tracks WHERE custom_mix_id != :mixId")
    suspend fun getTrackIdsOwnedByOtherMixes(mixId: Long): List<String>
}

// ── Queue Journal DAO ───────────────────────────────────────────────────────────

@Dao
interface QueueJournalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: QueueJournalEntity)

    @Query("SELECT * FROM queue_journal ORDER BY updated_at DESC")
    suspend fun getAllRecent(): List<QueueJournalEntity>

    @Query("SELECT * FROM queue_journal ORDER BY updated_at ASC LIMIT 1")
    suspend fun getOldest(): QueueJournalEntity?

    @Delete
    suspend fun delete(entry: QueueJournalEntity)

    @Query("SELECT COUNT(*) FROM queue_journal")
    suspend fun count(): Int

    /** Evict oldest entries until count <= cap. */
    @Transaction
    suspend fun evictIfNeeded(cap: Int) {
        while (count() > cap) {
            val oldest = getOldest() ?: break
            delete(oldest)
        }
    }
}

// ── Track Waveform DAO ──────────────────────────────────────────────────────

@Dao
interface TrackWaveformDao {
    @Query("SELECT * FROM track_waveforms WHERE track_id = :trackId")
    suspend fun get(trackId: String): TrackWaveformEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrackWaveformEntity)

    @Query("DELETE FROM track_waveforms WHERE track_id IN (:trackIds)")
    suspend fun deleteByTrackIds(trackIds: List<String>)
}
