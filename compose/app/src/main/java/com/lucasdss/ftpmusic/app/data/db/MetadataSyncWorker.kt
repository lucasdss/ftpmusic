package com.lucasdss.ftpmusic.app.data.db

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.lucasdss.ftpmusic.app.data.model.Album
import com.lucasdss.ftpmusic.app.data.model.Artist
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SyncStatus(
    val albums: Int = 0,
    val albumsTotal: Int = 0,
    val artists: Int = 0,
    val artistsTotal: Int = 0,
    val albumTracksProgress: Int = 0,
    val albumTracksProgressTotal: Int = 0,
    val genres: Int = 0,
    val genresTotal: Int = 0,
    val trackCount: Int = 0,
    // Daily Mix generation (runs after sync)
    val dailyMixProgress: Int = 0,
    val dailyMixTotal: Int = 0,
    val phase: String = "",
    val isRunning: Boolean = false,
    val elapsedMs: Long = 0L,
) {
    /** Per-row progress row for the six-row SyncingScreen layout. */
    data class Row(
        val label: String,
        val icon: String, // "✓" "⟳" "◦" ""
        val progress: Int, // current count
        val total: Int, // total (0 = no denominator, show as plain counter)
        val showProgress: Boolean = false, // true = show progress bar under label
    )

    fun toRows(): List<Row> {
        val albumsDone = albums >= albumsTotal && albumsTotal > 0
        val albumTracksDone = albumTracksProgress >= albumTracksProgressTotal && albumTracksProgressTotal > 0
        val artistsDone = artists >= artistsTotal && artistsTotal > 0
        val genresDone = genres >= genresTotal && genresTotal > 0
        val dailyMixDone = dailyMixProgress >= dailyMixTotal && dailyMixTotal > 0

        return listOf(
            Row(
                "Albums",
                icon(albumsDone, isRunning && phase == "albums"),
                albums,
                albumsTotal,
                showProgress =
                    isRunning && phase == "albums",
            ),
            Row(
                "  Album tracks",
                icon(albumTracksDone, isRunning && phase == "tracks"),
                albumTracksProgress,
                albumTracksProgressTotal,
                showProgress =
                    isRunning && phase == "tracks",
            ),
            Row(
                "Artists",
                icon(artistsDone, isRunning && phase == "artists"),
                artists,
                artistsTotal,
                showProgress =
                    isRunning && phase == "artists",
            ),
            Row(
                "Genres",
                icon(genresDone, isRunning && phase == "genres"),
                genres,
                genresTotal,
                showProgress =
                    isRunning && phase == "genres",
            ),
            Row(
                "Tracks",
                if (isRunning &&
                    phase == "tracks"
                ) {
                    "⟳"
                } else if (!isRunning &&
                    trackCount > 0
                ) {
                    "✓"
                } else {
                    "◦"
                },
                trackCount,
                0,
                showProgress = false,
            ),
            Row(
                "Daily Mix",
                icon(dailyMixDone, isRunning && phase == "dailyMix"),
                dailyMixProgress,
                dailyMixTotal,
                showProgress =
                    isRunning && phase == "dailyMix",
            ),
        )
    }

    companion object {
        private fun icon(done: Boolean, inProgress: Boolean) = when {
            done -> "✓"
            inProgress -> "⟳"
            else -> "◦"
        }
    }
}

/**
 * Background worker that fetches and caches library metadata (albums, artists)
 * for offline availability and fast startup. Runs on app start and periodically.
 *
 * Mirrors Substreamer's SyncWorker pattern — caches metadata locally so the
 * library is available without a live API call.
 */
class MetadataSyncWorker(
    private val context: Context,
    private val api: SubsonicApi,
    private val authHelper: SubsonicAuthHelper,
    private val metadataDao: CachedMetadataDao,
    private val trackDao: com.lucasdss.ftpmusic.app.data.db.TrackDao,
    private val genreMixDao: GenreMixDao,
    private val coverArtFallback: com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService,
    private val offlineModeManager: com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager,
    private val dailyMixRepository: com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        private const val TAG = "ftpmusic-metasync"
        private const val SYNC_INTERVAL_MS = 30 * 60 * 1000L // 30 minutes
        private const val PAGE_SIZE = 500

        /** SharedPreferences file name for sync stats/versions. */
        const val PREFS_NAME = "ftpmusic_sync"

        /** Bump when track schema changes so existing caches are re-fetched. */
        private const val METADATA_VERSION = 2

        /** Minimum cooldown between auto-triggered sync starts (prevents rapid re-syncs). */
        private const val MIN_SYNC_COOLDOWN_MS = 5 * 60 * 1000L // 5 minutes

        /** Max concurrent album track API fetches per batch. */
        private const val MAX_CONCURRENT_TRACK_FETCHES = 5

        /** Delay between track sync batches (reduces server load). */
        private const val TRACK_BATCH_DELAY_MS = 200L

        /** Rate-limit spacing between per-genre `getSongsByGenre` calls. */
        private const val GENRE_FETCH_DELAY_MS = 200L
    }

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var syncJob: Job? = null
    private val syncJobs = mutableSetOf<Job>()
    private val isSyncing = AtomicBoolean(false)

    @Volatile private var lastSyncFinishMs: Long = 0L

    /** Album IDs whose metadata (songCount/duration) changed since last sync —
     *  their tracks are re-fetched during the tracks phase (incremental staleness). */
    private val changedAlbumIds = mutableSetOf<String>()

    /** Observable sync status — SyncingScreen observes for progress display */
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    fun start(initialDelayMs: Long = 2000L) {
        syncJob = scope.launch {
            delay(initialDelayMs)
            if (SubsonicCredentials.username.isNotEmpty()) {
                // Skip startup sync if metadata already exists — periodic WorkManager
                // handles resyncs at the configured interval (default 12h).
                // First-run (no metadata) still triggers immediate sync.
                if (metadataDao.albumCount() == 0) {
                    syncNow()
                }
            }
            // Periodic resync is now handled by WorkManager (FtpmusicApp)
        }
    }

    /** True when cached library metadata already exists — used by the
     *  periodic WorkManager worker to skip the post-install/update full sync
     *  on populated libraries (avoids the DB write storm that starved the
     *  Home loaders' Room reads). */
    suspend fun hasMetadata(): Boolean = metadataDao.albumCount() > 0

    fun stop() {
        syncJob?.cancel()
        syncJobs.toList().forEach { it.cancel() }
    }

    /** Kick off a sync. No-ops if already in progress (atomic CAS guard).
     *  @param forceTrackResync re-fetch tracks for ALL albums (Resync Library button)
     *  @return true if sync started, false if another sync is already running */
    fun syncNow(forceTrackResync: Boolean = false): Boolean = syncNowAsync(forceTrackResync) != null

    /** Launch the sync. Returns a Job. */
    @VisibleForTesting
    internal fun syncNowAsync(forceTrackResync: Boolean = false): kotlinx.coroutines.Job? {
        // Software offline mode: metadata is already cached locally — nothing to
        // fetch, and the local-first contract forbids the network call.
        if (offlineModeManager.isOfflineEnabled()) {
            android.util.Log.d(TAG, "Skipping sync — offline mode enabled")
            return null
        }
        // Cooldown: skip auto-triggered syncs if the last sync finished recently.
        // Force resyncs (user-triggered) bypass the cooldown.
        if (!forceTrackResync) {
            val elapsed = System.currentTimeMillis() - lastSyncFinishMs
            if (lastSyncFinishMs > 0L && elapsed < MIN_SYNC_COOLDOWN_MS) {
                android.util.Log.d(TAG, "Skipping sync — cooldown active (${elapsed}ms < ${MIN_SYNC_COOLDOWN_MS}ms)")
                return null
            }
        }
        if (!isSyncing.compareAndSet(false, true)) return null
        val job = scope.launch syncJob@{
            syncJobs.add(coroutineContext[kotlinx.coroutines.Job]!!)
            val startMs = System.currentTimeMillis()
            // Emit initial status with all rows visible, totals set to what we know
            _status.value = SyncStatus(
                albumsTotal = 1,
                artistsTotal = 1,
                genresTotal = 1,
                albumTracksProgressTotal = 1,
                phase = "albums",
                isRunning = true,
                elapsedMs = 0L,
            )
            try {
                Log.d(TAG, "Starting metadata sync… (forceTrackResync=$forceTrackResync)")
                syncAlbums()
                val albumCount = metadataDao.albumCount()
                _status.value = _status.value.copy(
                    albums = albumCount,
                    albumsTotal = albumCount,
                    phase = "artists",
                    elapsedMs = System.currentTimeMillis() - startMs,
                )

                syncArtists()
                val artistCount = metadataDao.artistCount()
                _status.value = _status.value.copy(
                    artists = artistCount,
                    artistsTotal = artistCount,
                    phase = "genres",
                    elapsedMs = System.currentTimeMillis() - startMs,
                )

                syncGenres()
                val genreCount = genreMixDao.getTopGenres().size
                _status.value = _status.value.copy(
                    genres = genreCount,
                    genresTotal = genreCount,
                    phase = "genres",
                    elapsedMs = System.currentTimeMillis() - startMs,
                )

                // Sync stars and ratings from server (mirror Navidrome favorites)
                syncStarredAndRatings()
                Log.d(TAG, "Starred/ratings sync complete")

                syncAlbumTracks(forceTrackResync)
                // Recompute artist album counts AFTER tracks are cached so
                // track-artist attribution is included (Navidrome attributes
                // some albums to "Original Soundtrack" while their tracks are
                // by the real artist). Without this ordering, artists like
                // Aerosmith/2Cellos would show album_count=0. Mirrors the
                // artist detail page's merged queries (by id, by name, by
                // track artist).
                try {
                    metadataDao.updateArtistAlbumCounts()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
                // First-run Custom Daily Mix seeding: materialize the top-20
                // genres as default mixes ONCE (marker in custom_mix_state).
                // Runs AFTER tracks are cached and BEFORE the Daily Mix phase.
                try {
                    dailyMixRepository.seedIfNeeded()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
                val finalTrackCount = metadataDao.cachedTrackCount()
                if (_status.value.albumTracksProgress > 0) {
                    trackDao.populateAllTrackGenres()
                    trackDao.populateGenresFromCachedGenreSongs()
                }
                val current = _status.value
                if (current.phase != "error") {
                    _status.value = current.copy(
                        albums = albumCount, albumsTotal = albumCount,
                        artists = artistCount, artistsTotal = artistCount,
                        trackCount = finalTrackCount,
                        genres = genreCount, genresTotal = genreCount,
                        phase = "complete", isRunning = false,
                        elapsedMs = System.currentTimeMillis() - startMs,
                    )
                }
                val durationMs = System.currentTimeMillis() - startMs
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putLong("last_metadata_sync_ms", System.currentTimeMillis())
                    .putLong("metadata_sync_duration_ms", durationMs)
                    .putInt("metadata_version", METADATA_VERSION)
                    .apply()
            } catch (e: Exception) {
                Log.w(TAG, "Metadata sync failed: ${e.message}")
                _status.value = _status.value.copy(
                    isRunning = false,
                    phase = "error",
                    elapsedMs = System.currentTimeMillis() - startMs,
                )
            } finally {
                lastSyncFinishMs = System.currentTimeMillis()
                isSyncing.set(false)
                syncJobs.remove(coroutineContext[kotlinx.coroutines.Job]!!)
            }
        }
        syncJobs.add(job)
        return job
    }

    @Suppress("ThrowsCount")
    internal suspend fun syncAlbums() {
        val username = SubsonicCredentials.username
        val password = SubsonicCredentials.password
        if (username.isEmpty() || password.isEmpty()) return

        changedAlbumIds.clear()
        val params = authHelper.buildAuthParams(username, password)
        val base = DynamicBaseUrl.url.trimEnd('/')

        val allAlbums = mutableListOf<CachedAlbumEntity>()
        var offset = 0
        while (true) {
            val response = api.getAlbumList2(
                type = "alphabeticalByName",
                size = PAGE_SIZE,
                offset = offset,
                auth = params,
            )
            val sr = response["subsonic-response"] as? Map<*, *>
            if (sr?.get("status") as? String == "failed") break
            val albumList = sr?.get("albumList2") as? Map<*, *>
            val albums = albumList?.get("album") as? List<*>
            if (albums.isNullOrEmpty()) break

            albums.forEach { a ->
                val m = a as? Map<*, *> ?: return@forEach
                val id = m["id"] as? String ?: return@forEach
                allAlbums.add(
                    CachedAlbumEntity(
                        id = id,
                        name = m["name"] as? String ?: "",
                        artist = m["artist"] as? String,
                        artistId = m["artistId"] as? String,
                        year = (m["year"] as? Number)?.toInt(),
                        coverArt = m["coverArt"] as? String,
                        songCount = (m["songCount"] as? Number)?.toInt(),
                        duration = (m["duration"] as? Number)?.toInt(),
                        genre = m["genre"] as? String,
                    ),
                )
            }
            if (albums.size < PAGE_SIZE) break
            offset += PAGE_SIZE
            delay(100L) // throttle between paginated API calls
        }

        if (allAlbums.isNotEmpty()) {
            // Incremental staleness detection: albums whose metadata
            // changed on the server need their tracks re-fetched.
            changedAlbumIds.clear()
            try {
                val cached = metadataDao.getAllAlbums().associateBy { it.id }
                for (album in allAlbums) {
                    val old = cached[album.id] ?: continue // new album — uncached path handles it
                    if (old.songCount != album.songCount || old.duration != album.duration ||
                        old.name != album.name || old.artist != album.artist ||
                        old.year != album.year || old.genre != album.genre ||
                        old.coverArt != album.coverArt
                    ) {
                        changedAlbumIds.add(album.id)
                    }
                }
                if (changedAlbumIds.isNotEmpty()) {
                    Log.d(TAG, "Detected ${changedAlbumIds.size} albums with changed metadata")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Change detection failed: ${e.message}")
            }
        }
        if (allAlbums.isEmpty() && metadataDao.albumCount() > 0) {
            // A failed/partial page must never wipe the cached library or the
            // cover-art cache. Keep what we have; the next sync retries.
            Log.w(TAG, "Album sync returned no rows for a populated library — keeping cached albums")
            return
        }
        // Replace — an empty list with an empty cache is a legitimate no-op
        // (fresh install / genuinely empty server).
        metadataDao.replaceAlbums(allAlbums)
        // Repopulate the albums ledger (favorites table) — ON CONFLICT DO
        // UPDATE preserves starred_at/user_rating/is_disliked across wipes.
        try {
            metadataDao.syncAlbumLedger()
            metadataDao.pruneAlbumLedger()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Album ledger sync failed: ${e.message}")
        }
        // Clean up tracks for albums deleted from the server
        try {
            val orphans = metadataDao.deleteOrphanedAlbumTracks()
            if (orphans > 0) Log.d(TAG, "Removed $orphans orphaned album tracks")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
        // Clean up orphaned navidrome cover art files for deleted albums
        try {
            coverArtFallback.cleanOrphanedNavidromeArt(allAlbums.mapNotNull { it.coverArt }.toSet())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
        _status.value = _status.value.copy(albums = allAlbums.size, albumsTotal = allAlbums.size)
        Log.d(TAG, "Cached ${allAlbums.size} albums")
    }

    internal suspend fun syncArtists() {
        val username = SubsonicCredentials.username
        val password = SubsonicCredentials.password
        if (username.isEmpty() || password.isEmpty()) return

        val params = authHelper.buildAuthParams(username, password)
        val response = api.getArtists(params)
        val sr = response["subsonic-response"] as? Map<*, *>
        if (sr?.get("status") as? String == "failed") return
        val artistsData = sr?.get("artists") as? Map<*, *>
        val indexList = artistsData?.get("index") as? List<*>

        val allArtists = mutableListOf<CachedArtistEntity>()
        indexList?.forEach { idx ->
            val m = idx as? Map<*, *> ?: return@forEach
            val artistList = m["artist"] as? List<*>
            artistList?.forEach { a ->
                val am = a as? Map<*, *> ?: return@forEach
                val id = am["id"] as? String ?: return@forEach
                allArtists.add(
                    CachedArtistEntity(
                        id = id,
                        name = am["name"] as? String ?: "",
                        coverArt = am["coverArt"] as? String,
                        albumCount = (am["albumCount"] as? Number)?.toInt(),
                    ),
                )
            }
        }

        if (allArtists.isNotEmpty()) {
            metadataDao.replaceArtists(allArtists)
            Log.d(TAG, "Cached ${allArtists.size} artists")
        }
        // Repopulate the artists ledger (favorites table) — ON CONFLICT DO
        // UPDATE preserves starred_at/is_disliked across wipes.
        try {
            metadataDao.syncArtistLedger()
            metadataDao.pruneArtistLedger()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Artist ledger sync failed: ${e.message}")
        }
        // NOTE: updateArtistAlbumCounts() is deliberately NOT called here — it
        // must run AFTER the tracks phase (cached_album_tracks populated) so
        // track-artist attribution is included. See syncNowAsync.
    }

    internal suspend fun syncGenres() {
        val username = SubsonicCredentials.username
        val password = SubsonicCredentials.password
        if (username.isEmpty()) return
        val params = authHelper.buildAuthParams(username, password)

        // 1. Fetch all genres
        val response = api.getGenres(params)
        val sr = response["subsonic-response"] as? Map<*, *> ?: return
        val genresData = sr["genres"] as? Map<*, *> ?: return
        val genreList = genresData["genre"] as? List<*> ?: return

        val genres = genreList.mapNotNull { g ->
            val m = g as? Map<*, *> ?: return@mapNotNull null
            CachedGenreEntity(
                name = (m["value"] ?: m["name"]) as? String ?: return@mapNotNull null,
                songCount = (m["songCount"] as? Number)?.toInt() ?: 0,
                albumCount = (m["albumCount"] as? Number)?.toInt() ?: 0,
            )
        }
        genreMixDao.replaceGenres(genres)

        // 2. Fetch songs (120 each) for every genre referenced by a Custom
        // Daily Mix. Before first-run seeding there are no mixes at all, so
        // fall back to the CURRENT top 20 (the seeding phase later materializes
        // those as default mixes). Users with only decade/artist mixes need no
        // genre song fetch — skip it instead of re-downloading the top 20.
        val selectedGenres = try {
            if (dailyMixRepository.hasMixes()) {
                dailyMixRepository.allMixGenreNames()
            } else {
                genres.sortedByDescending { it.songCount }.take(20).map { it.name }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            genres.sortedByDescending { it.songCount }.take(20).map { it.name }
        }
        for (genre in selectedGenres) {
            try {
                delay(GENRE_FETCH_DELAY_MS) // rate limit between genre calls
                val songsResp = api.getSongsByGenre(params, genre, 120)
                val songsSr = songsResp["subsonic-response"] as? Map<*, *>
                val songsData = songsSr?.get("songsByGenre") as? Map<*, *>
                val songList = songsData?.get("song") as? List<*>
                if (songList != null) {
                    val songs = songList.mapNotNull { s ->
                        val sm = s as? Map<*, *> ?: return@mapNotNull null
                        CachedGenreSongEntity(
                            id = sm["id"] as? String ?: return@mapNotNull null,
                            genre = genre,
                            title = sm["title"] as? String ?: "",
                            artist = sm["artist"] as? String,
                            albumId = sm["albumId"] as? String,
                            artistId = sm["artistId"] as? String,
                            duration = (sm["duration"] as? Number)?.toInt(),
                            trackNumber = (sm["track"] as? Number)?.toInt(),
                            coverArt = sm["coverArt"] as? String,
                            suffix = sm["suffix"] as? String,
                            contentType = sm["contentType"] as? String,
                        )
                    }
                    if (songs.isNotEmpty() || genreMixDao.countSongsForGenre(genre) == 0) {
                        genreMixDao.replaceSongs(genre, songs)
                    } else {
                        // Empty response for a genre that has cached songs: keep
                        // the cache (wiping it leaves Daily Mix covers blank).
                        Log.w(TAG, "Genre '$genre' returned no songs — keeping cached rows")
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                /* skip failed genre */
            }
        }
    }

    /**
     * Sync starred tracks, albums, artists and ratings from the Navidrome server.
     * Calls getStarred2, then mirrors server state to the local tables:
     * - tracks: star + rating (track-level)
     * - albums ledger: star + rating
     * - artists ledger: star
     * The ledger tables are populated by syncAlbumLedger/syncArtistLedger during
     * the albums/artists phases, which run BEFORE this phase in syncNowAsync.
     *
     * Local-first intent (ADR 0020):
     * - Stars set locally while offline (newer than [syncStartMs]) are pushed
     *   back to the server best-effort (chunked, comma-joined ids) and excluded
     *   from the clear-non-starred sweep.
     * - Stars the user REMOVED locally (disliked, or pending_unstar_at set)
     *   are never re-starred from the server, and the unstar is pushed back for
     *   rows the server still lists. Markers clear once the server confirms.
     */
    internal suspend fun syncStarredAndRatings() {
        try {
            val username = SubsonicCredentials.username
            val password = SubsonicCredentials.password
            if (username.isEmpty() || password.isEmpty()) return

            val params = authHelper.buildAuthParams(username, password)
            val syncStartMs = System.currentTimeMillis()
            val response = api.getStarred2(params)
            val sr = response["subsonic-response"] as? Map<*, *> ?: return
            val starred = sr["starred2"] as? Map<*, *> ?: return

            val now = System.currentTimeMillis()

            // Tracks: star + rating (collect first, batch update)
            val songList = starred["song"] as? List<*> ?: emptyList<Any>()
            val starredTrackIds = mutableListOf<String>()
            for (s in songList) {
                val m = s as? Map<*, *> ?: continue
                val id = m["id"] as? String ?: continue
                starredTrackIds.add(id)
                val rating = (m["userRating"] as? Number)?.toInt() ?: 0
                if (rating > 0) trackDao.setRating(id, rating)
            }
            val localTrackIds = trackDao.getStarredIds().map { it.id }.toSet()
            val pendingUnstarTracks = trackDao.getPendingUnstarIds().toSet()
            val dislikedTracks = trackDao.getDislikedIds().toSet()
            // Server stars pushed for local-only likes; never re-star rows the
            // user removed locally.
            val pushTrackIds = (localTrackIds - starredTrackIds.toSet()).toList()
            pushIdsBestEffort(params, pushTrackIds, StarPushKind.STAR, idKind = "id")
            val skipRestarTracks = pendingUnstarTracks + dislikedTracks
            val restarTrackIds = starredTrackIds.filter { it !in skipRestarTracks }
            if (restarTrackIds.isNotEmpty()) trackDao.setStarredAtBulk(restarTrackIds, now)
            // Unstar push-back for locally-removed rows the server still lists.
            val unstarPushTracks = (pendingUnstarTracks + dislikedTracks).filter { it in starredTrackIds }
            pushIdsBestEffort(params, unstarPushTracks, StarPushKind.UNSTAR, idKind = "id")
            // Marker lifecycle: cleared when the server confirms (absent next
            // mirror) or the unstar push succeeded this run.
            val confirmedUnstarTracks = pendingUnstarTracks.filter { it !in starredTrackIds }
            if (confirmedUnstarTracks.isNotEmpty()) trackDao.clearPendingUnstar(confirmedUnstarTracks)
            val protectedTracks = starredTrackIds + pushTrackIds
            if (protectedTracks.isNotEmpty()) trackDao.clearNonStarred(protectedTracks, syncStartMs)

            // Albums: star + rating
            val albumList = starred["album"] as? List<*> ?: emptyList<Any>()
            val starredAlbumIds = mutableListOf<String>()
            for (a in albumList) {
                val m = a as? Map<*, *> ?: continue
                val id = m["id"] as? String ?: continue
                starredAlbumIds.add(id)
                val rating = (m["userRating"] as? Number)?.toInt() ?: 0
                if (rating > 0) metadataDao.setAlbumRating(id, rating)
            }
            val localAlbumIds = metadataDao.getStarredAlbumIds().toSet()
            val pendingUnstarAlbums = metadataDao.getPendingUnstarAlbumIds().toSet()
            val dislikedAlbums = metadataDao.getDislikedAlbumIds().toSet()
            val pushAlbumIds = (localAlbumIds - starredAlbumIds.toSet()).toList()
            pushIdsBestEffort(params, pushAlbumIds, StarPushKind.STAR, idKind = "albumId")
            val skipRestarAlbums = pendingUnstarAlbums + dislikedAlbums
            val restarAlbumIds = starredAlbumIds.filter { it !in skipRestarAlbums }
            if (restarAlbumIds.isNotEmpty()) metadataDao.setAlbumStarredAtBulk(restarAlbumIds, now)
            val unstarPushAlbums = (pendingUnstarAlbums + dislikedAlbums).filter { it in starredAlbumIds }
            pushIdsBestEffort(params, unstarPushAlbums, StarPushKind.UNSTAR, idKind = "albumId")
            val confirmedUnstarAlbums = pendingUnstarAlbums.filter { it !in starredAlbumIds }
            if (confirmedUnstarAlbums.isNotEmpty()) metadataDao.clearPendingUnstarAlbums(confirmedUnstarAlbums)
            val protectedAlbums = starredAlbumIds + pushAlbumIds
            if (protectedAlbums.isNotEmpty()) metadataDao.clearNonStarredAlbums(protectedAlbums, syncStartMs)

            // Artists: star
            val artistList = starred["artist"] as? List<*> ?: emptyList<Any>()
            val starredArtistIds = mutableListOf<String>()
            for (ar in artistList) {
                val m = ar as? Map<*, *> ?: continue
                val id = m["id"] as? String ?: continue
                starredArtistIds.add(id)
            }
            val localArtistIds = metadataDao.getStarredArtistIds().toSet()
            val pendingUnstarArtists = metadataDao.getPendingUnstarArtistIds().toSet()
            val dislikedArtists = metadataDao.getDislikedArtistIds().toSet()
            val pushArtistIds = (localArtistIds - starredArtistIds.toSet()).toList()
            pushIdsBestEffort(params, pushArtistIds, StarPushKind.STAR, idKind = "artistId")
            val skipRestarArtists = pendingUnstarArtists + dislikedArtists
            val restarArtistIds = starredArtistIds.filter { it !in skipRestarArtists }
            if (restarArtistIds.isNotEmpty()) metadataDao.setArtistStarredAtBulk(restarArtistIds, now)
            val unstarPushArtists = (pendingUnstarArtists + dislikedArtists).filter { it in starredArtistIds }
            pushIdsBestEffort(params, unstarPushArtists, StarPushKind.UNSTAR, idKind = "artistId")
            val confirmedUnstarArtists = pendingUnstarArtists.filter { it !in starredArtistIds }
            if (confirmedUnstarArtists.isNotEmpty()) metadataDao.clearPendingUnstarArtists(confirmedUnstarArtists)
            val protectedArtists = starredArtistIds + pushArtistIds
            if (protectedArtists.isNotEmpty()) metadataDao.clearNonStarredArtists(protectedArtists, syncStartMs)

            Log.d(
                TAG,
                "Starred sync: ${starredTrackIds.size} tracks, " +
                    "${starredAlbumIds.size} albums, ${starredArtistIds.size} artists " +
                    "(pushed ${pushTrackIds.size}/${pushAlbumIds.size}/${pushArtistIds.size} local-only stars, " +
                    "${unstarPushTracks.size}/${unstarPushAlbums.size}/${unstarPushArtists.size} unstars)",
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Starred/ratings sync failed: ${e.message}")
        }
    }

    private enum class StarPushKind { STAR, UNSTAR }

    /** Best-effort star/unstar push in chunks (URL-length safety), one call per
     *  chunk with comma-joined ids — Subsonic accepts comma-separated ids.
     *  Never throws; failures are retried on the next sync. */
    private suspend fun pushIdsBestEffort(
        params: Map<String, String>,
        ids: List<String>,
        kind: StarPushKind,
        idKind: String,
    ) {
        if (ids.isEmpty()) return
        ids.chunked(50).forEach { chunk ->
            val joined = chunk.joinToString(",")
            try {
                when (kind) {
                    StarPushKind.STAR -> when (idKind) {
                        "albumId" -> api.star(params, albumId = joined)
                        "artistId" -> api.star(params, artistId = joined)
                        else -> api.star(params, id = joined)
                    }

                    StarPushKind.UNSTAR -> when (idKind) {
                        "albumId" -> api.unstar(params, albumId = joined)
                        "artistId" -> api.unstar(params, artistId = joined)
                        else -> api.unstar(params, id = joined)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Star ${kind.name.lowercase()} push-back deferred (retried next sync): ${e.message}")
            }
        }
    }

    /**
     * Background-sync album tracks with parallel batching (up to
     * [MAX_CONCURRENT_TRACK_FETCHES] concurrent API calls per batch).
     * Skips albums that already have cached tracks, EXCEPT:
     * - albums whose metadata changed on the server (incremental staleness)
     * - all albums when [force] is set (Resync Library button)
     * - all albums when METADATA_VERSION was bumped (schema change)
     * Falls back to lazy caching if the user opens an album before
     * the background sync reaches it.
     */
    internal suspend fun syncAlbumTracks(force: Boolean = false) {
        val username = SubsonicCredentials.username
        val password = SubsonicCredentials.password
        if (username.isEmpty() || password.isEmpty()) return

        val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "ftpmusic:tracksync")
        lock?.acquire()

        try {
            val params = authHelper.buildAuthParams(username, password)
            val albums = metadataDao.getAllAlbums()
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val storedVersion = prefs.getInt("metadata_version", 0)
            val forceResync = force || storedVersion < METADATA_VERSION

            // Pre-filter: only albums that actually need syncing
            val pendingAlbums = albums.filter { album ->
                if (forceResync) return@filter true
                val existing = metadataDao.getAlbumTracks(album.id)
                !(existing.isNotEmpty() && album.id !in changedAlbumIds)
            }
            val pendingTotal = pendingAlbums.size
            _status.value = _status.value.copy(
                phase = "tracks",
                albumTracksProgressTotal = pendingTotal,
                isRunning = true,
            )

            if (pendingAlbums.isEmpty()) return

            val count = AtomicInteger(0)
            val totalTracks = AtomicInteger(0)
            val consecutiveFailures = AtomicInteger(0)
            val failedCount = AtomicInteger(0)
            val aborted = AtomicBoolean(false)

            // Helper: fetch one album's tracks (called from parallel coroutines).
            // Pure network — DB writes are batched per chunk below so a full
            // resync issues a few large transactions instead of thousands of
            // tiny ones (Room readers stay responsive).
            suspend fun fetchAlbumTracks(album: CachedAlbumEntity): List<CachedAlbumTrackEntity>? {
                try {
                    val response = api.getAlbum(id = album.id, auth = params)
                    if (!authHelper.checkResponseStatus(response)) return null
                    val sr = response["subsonic-response"] as? Map<*, *> ?: run {
                        Log.w(TAG, "Malformed getAlbum response for ${album.id}")
                        return null
                    }
                    val albumData = sr["album"] as? Map<*, *>
                    val songs = albumData?.get("song") as? List<*>
                    if (songs == null) return null

                    return songs.mapNotNull { s ->
                        val m = s as? Map<*, *> ?: return@mapNotNull null
                        val tid = m["id"] as? String ?: return@mapNotNull null
                        CachedAlbumTrackEntity(
                            id = tid, albumId = album.id,
                            title = m["title"] as? String ?: "",
                            artist = m["artist"] as? String,
                            genre = m["genre"] as? String,
                            year = (m["year"] as? Number)?.toInt(),
                            artistId = m["artistId"] as? String,
                            discNumber = (m["discNumber"] as? Number)?.toInt(),
                            duration = (m["duration"] as? Number)?.toInt(),
                            trackNumber = (m["track"] as? Number)?.toInt(),
                            bitrate = (m["bitRate"] as? Number)?.toInt(),
                            size = (m["size"] as? Number)?.toLong(),
                            coverArt = m["coverArt"] as? String,
                            suffix = m["suffix"] as? String,
                            contentType = m["contentType"] as? String,
                        )
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Failed to sync tracks for ${album.id}: ${e.message}")
                    return null
                }
            }

            // Process in batches for controlled concurrency + memory; each
            // batch's fetched tracks are written in ONE transaction.
            pendingAlbums.chunked(MAX_CONCURRENT_TRACK_FETCHES).forEach { chunk ->
                if (aborted.get()) return@forEach
                val fetched = coroutineScope {
                    chunk.map { album ->
                        async {
                            val tracks = fetchAlbumTracks(album)
                            if (tracks == null) {
                                consecutiveFailures.incrementAndGet()
                                failedCount.incrementAndGet()
                                // Abort on 3 consecutive failures (network gone)
                                if (consecutiveFailures.get() >= 3) {
                                    aborted.set(true)
                                    Log.w(
                                        TAG,
                                        "Aborting track sync after ${consecutiveFailures.get()} consecutive failures",
                                    )
                                }
                                emptyList<CachedAlbumTrackEntity>()
                            } else {
                                consecutiveFailures.set(0)
                                tracks
                            }
                        }
                    }.awaitAll()
                }
                val allTracks = fetched.flatten()
                if (allTracks.isNotEmpty()) {
                    metadataDao.replaceAlbumTracksBatch(allTracks)
                }
                val ok = fetched.count { it.isNotEmpty() }
                count.addAndGet(ok)
                totalTracks.addAndGet(allTracks.size)
                _status.value = _status.value.copy(
                    albumTracksProgress = count.get(),
                    trackCount = totalTracks.get(),
                )
                if (!aborted.get() && pendingAlbums.size > MAX_CONCURRENT_TRACK_FETCHES) {
                    delay(TRACK_BATCH_DELAY_MS)
                }
            }

            // Failure threshold check
            val f = failedCount.get()
            if (f > 0 && pendingTotal > 0 && f.toDouble() / pendingTotal > 0.05) {
                _status.value = _status.value.copy(
                    isRunning = false,
                    phase = "error",
                    trackCount = totalTracks.get(),
                    albumTracksProgress = count.get(),
                )
                Log.w(TAG, "Album track sync degraded — $f failures out of $pendingTotal pending")
                return
            }
            _status.value = _status.value.copy(albumTracksProgress = count.get(), trackCount = totalTracks.get())
            if (count.get() > 0) Log.d(TAG, "Background-synced tracks for ${count.get()} albums")
            changedAlbumIds.clear()
        } finally {
            lock?.release()
        }
    }
}
