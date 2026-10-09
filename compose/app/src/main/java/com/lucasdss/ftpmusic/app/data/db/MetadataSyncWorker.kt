package com.lucasdss.ftpmusic.app.data.db

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog
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
    private val searchIndexRebuilder: com.lucasdss.ftpmusic.app.data.search.SearchIndexRebuilder? = null,
    private val metadataEnrichRunner: MetadataEnrichRunner? = null,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        private const val TAG = "ftpmusic-metasync"
        private const val PAGE_SIZE = 500

        /** SharedPreferences file name for sync stats/versions. */
        const val PREFS_NAME = "ftpmusic_sync"

        const val PREF_LAST_DELTA_SYNC_MS = "last_delta_sync_ms"
        const val PREF_LAST_FULL_SYNC_MS = "last_full_sync_ms"
        const val PREF_LAST_METADATA_SYNC_MS = "last_metadata_sync_ms"
        const val PREF_METADATA_SYNC_DURATION_MS = "metadata_sync_duration_ms"

        /** Auto FULL catalog heal cadence (ADR-0045). */
        const val FULL_SYNC_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000

        /** Cap newest pages for DELTA (500 × 3 = 1500 albums). */
        private const val DELTA_MAX_PAGES = 3

        /** Bump when track schema changes so existing caches are re-fetched. */
        private const val METADATA_VERSION = 2

        /** Minimum cooldown between auto-triggered sync starts (prevents rapid re-syncs). */
        private const val MIN_SYNC_COOLDOWN_MS = 5 * 60 * 1000L // 5 minutes

        /** Rate-limit spacing between per-genre `getSongsByGenre` calls. */
        private const val GENRE_FETCH_DELAY_MS = 200L

        /** Songs fetched per genre for Daily Mix + search corpus warm. */
        private const val GENRE_SONG_FETCH_COUNT = 200

        /** Top genres by song_count always warmed for local search (beyond mix picks). */
        private const val SEARCH_INDEX_WARM_GENRE_COUNT = 100

        /** Cap pending album-track drain per non-force sync (Phase-7 densify). */
        private const val SEARCH_CORPUS_ALBUM_DRAIN_CAP = 400

        /** Genre offset pages per DELTA orphan densify (ADR-0085). */
        private const val ORPHAN_GENRE_OFFSET_PAGES_DELTA = 3

        /** Genre offset pages per FULL/force orphan densify. */
        private const val ORPHAN_GENRE_OFFSET_PAGES_FULL = 20

        /** getRandomSongs size per round (Subsonic max commonly 500). */
        private const val ORPHAN_RANDOM_SONG_SIZE = 500

        /** Random-song rounds per DELTA. */
        private const val ORPHAN_RANDOM_ROUNDS_DELTA = 2

        /** Random-song rounds per FULL/force. */
        private const val ORPHAN_RANDOM_ROUNDS_FULL = 6

        /**
         * Choose periodic sync mode from watermark.
         * Never synced full (or older than 7d) → FULL; else DELTA.
         */
        fun resolvePeriodicMode(lastFullSyncMs: Long, nowMs: Long = System.currentTimeMillis()): LibrarySyncMode {
            if (lastFullSyncMs <= 0L) return LibrarySyncMode.FULL
            if (nowMs - lastFullSyncMs >= FULL_SYNC_INTERVAL_MS) return LibrarySyncMode.FULL
            return LibrarySyncMode.DELTA
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var syncJob: Job? = null
    private val syncJobs = mutableSetOf<Job>()
    private val isSyncing = AtomicBoolean(false)
    private val trackLimiter = AdaptiveSyncLimiter()

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
                // handles DELTA/FULL at the configured interval (ADR-0045).
                // First-run (no metadata) still triggers immediate sync.
                if (metadataDao.albumCount() == 0) {
                    syncNow(mode = LibrarySyncMode.FULL)
                }
            }
        }
    }

    /** True when cached library metadata already exists. */
    suspend fun hasMetadata(): Boolean = metadataDao.albumCount() > 0

    fun lastFullSyncMs(): Long = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getLong(PREF_LAST_FULL_SYNC_MS, 0L)

    fun lastDeltaSyncMs(): Long = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getLong(PREF_LAST_DELTA_SYNC_MS, 0L)

    fun stop() {
        syncJob?.cancel()
        syncJobs.toList().forEach { it.cancel() }
    }

    /** Kick off a sync. No-ops if already in progress (atomic CAS guard).
     *  @param forceTrackResync re-fetch tracks for ALL albums (Resync Library button)
     *  @param mode DELTA (newest upsert) or FULL (alphabetical replace)
     *  @return true if sync started, false if another sync is already running */
    fun syncNow(
        forceTrackResync: Boolean = false,
        mode: LibrarySyncMode = if (forceTrackResync) LibrarySyncMode.FULL else LibrarySyncMode.DELTA,
        allowCellularOverride: Boolean = false,
    ): Boolean = syncNowAsync(forceTrackResync, mode, allowCellularOverride) != null

    /** Launch the sync. Returns a Job. */
    @VisibleForTesting
    internal fun syncNowAsync(
        forceTrackResync: Boolean = false,
        mode: LibrarySyncMode = if (forceTrackResync) LibrarySyncMode.FULL else LibrarySyncMode.DELTA,
        allowCellularOverride: Boolean = false,
    ): kotlinx.coroutines.Job? {
        // Software offline mode: metadata is already cached locally — nothing to
        // fetch, and the local-first contract forbids the network call.
        if (offlineModeManager.isOfflineEnabled()) {
            android.util.Log.d(TAG, "Skipping sync — offline mode enabled")
            DiagnosticLog.d(TAG, "skip sync — offline")
            return null
        }
        // Reachability gate — avoid burning retries when server is marked down.
        if (!com.lucasdss.ftpmusic.app.di.ReachabilityStateHolder.isReachable.value) {
            android.util.Log.d(TAG, "Skipping sync — server unreachable")
            DiagnosticLog.d(TAG, "skip sync — unreachable")
            return null
        }
        // ADR-0105: cellular local-only or Wi-Fi-only sync (override bypasses Wi-Fi-only only).
        val effectiveCellularOverride =
            allowCellularOverride ||
                com.lucasdss.ftpmusic.app.data.cache.NetworkPolicyState.consumeCellularSyncOverride()
        if (com.lucasdss.ftpmusic.app.data.cache.NetworkPolicyState.shouldSkipMetadataSync(effectiveCellularOverride)) {
            android.util.Log.d(TAG, "Skipping sync — cellular network policy")
            DiagnosticLog.d(TAG, "skip sync — cellular policy override=$effectiveCellularOverride")
            return null
        }
        // Cooldown: skip auto-triggered syncs if the last sync finished recently.
        // Force resyncs (user-triggered) bypass the cooldown.
        if (!forceTrackResync) {
            val elapsed = System.currentTimeMillis() - lastSyncFinishMs
            if (lastSyncFinishMs > 0L && elapsed < MIN_SYNC_COOLDOWN_MS) {
                android.util.Log.d(TAG, "Skipping sync — cooldown active (${elapsed}ms < ${MIN_SYNC_COOLDOWN_MS}ms)")
                DiagnosticLog.d(TAG, "skip sync — cooldown")
                return null
            }
        }
        if (!isSyncing.compareAndSet(false, true)) return null
        val resolvedMode = if (forceTrackResync) LibrarySyncMode.FULL else mode
        val cellularOverride = effectiveCellularOverride
        val job = scope.launch syncJob@{
            syncJobs.add(coroutineContext[kotlinx.coroutines.Job]!!)
            val startMs = System.currentTimeMillis()
            trackLimiter.reset()
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
            fun abortIfCellularPolicyViolated(): Boolean {
                if (!com.lucasdss.ftpmusic.app.data.cache.NetworkPolicyState.shouldSkipMetadataSync(cellularOverride)) {
                    return false
                }
                Log.w(TAG, "Aborting sync — cellular policy mid-run")
                DiagnosticLog.d(TAG, "abort sync — cellular policy")
                _status.value = _status.value.copy(
                    phase = "error",
                    isRunning = false,
                    elapsedMs = System.currentTimeMillis() - startMs,
                )
                return true
            }
            try {
                Log.d(TAG, "Starting metadata sync… mode=$resolvedMode forceTrackResync=$forceTrackResync")
                DiagnosticLog.d(TAG, "sync start mode=$resolvedMode force=$forceTrackResync")
                syncAlbums(resolvedMode)
                if (abortIfCellularPolicyViolated()) return@syncJob
                val albumCount = metadataDao.albumCount()
                DiagnosticLog.d(TAG, "phase=albums albums=$albumCount mode=$resolvedMode")
                _status.value = _status.value.copy(
                    albums = albumCount,
                    albumsTotal = albumCount,
                    phase = "artists",
                    elapsedMs = System.currentTimeMillis() - startMs,
                )

                syncArtists()
                if (abortIfCellularPolicyViolated()) return@syncJob
                val artistCount = metadataDao.artistCount()
                DiagnosticLog.d(TAG, "phase=artists artists=$artistCount")
                _status.value = _status.value.copy(
                    artists = artistCount,
                    artistsTotal = artistCount,
                    phase = "genres",
                    elapsedMs = System.currentTimeMillis() - startMs,
                )

                syncGenres()
                if (abortIfCellularPolicyViolated()) return@syncJob
                val genreCount = genreMixDao.getTopGenres().size
                DiagnosticLog.d(TAG, "phase=genres genres=$genreCount")
                _status.value = _status.value.copy(
                    genres = genreCount,
                    genresTotal = genreCount,
                    phase = "orphans",
                    elapsedMs = System.currentTimeMillis() - startMs,
                )

                // ADR-0085: densify album-less / deep-genre songs into `tracks`.
                syncOrphanSongs(force = forceTrackResync, mode = resolvedMode)
                if (abortIfCellularPolicyViolated()) return@syncJob
                DiagnosticLog.d(TAG, "phase=orphans")

                // Sync stars and ratings from server (mirror Navidrome favorites)
                syncStarredAndRatings()
                if (abortIfCellularPolicyViolated()) return@syncJob
                DiagnosticLog.d(TAG, "phase=stars")
                Log.d(TAG, "Starred/ratings sync complete")

                syncAlbumTracks(forceTrackResync)
                if (abortIfCellularPolicyViolated()) return@syncJob
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
                // Always merge cached album/genre songs into `tracks` so search
                // finds singles + never-played catalog rows (ADR 0078 / phase-2).
                try {
                    trackDao.populateAllTrackGenres()
                    trackDao.populateGenresFromCachedGenreSongs()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "populate tracks for search index: ${e.message}")
                }
                // Search corpus = densified `tracks` (ADR-0085), not only album-tracks.
                val finalTrackCount = try {
                    trackDao.trackCountAll()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    metadataDao.cachedTrackCount()
                }
                DiagnosticLog.d(TAG, "phase=tracks tracks=$finalTrackCount")
                // Phase-4: FTS rebuild after populate (enrich is async — ADR-0085)
                try {
                    searchIndexRebuilder?.rebuildAll()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "FTS rebuild: ${e.message}")
                }
                // Defer MB/Last.fm/getArtistInfo2 — do not block sync UI / watermarks.
                try {
                    metadataEnrichRunner?.enqueue()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "enrich enqueue: ${e.message}")
                }
                val current = _status.value
                // ADR-0068: watermarks only on success — track-phase error must
                // not advance last_full/delta cadence or skip needed heal.
                if (current.phase != "error") {
                    _status.value = current.copy(
                        albums = albumCount, albumsTotal = albumCount,
                        artists = artistCount, artistsTotal = artistCount,
                        trackCount = finalTrackCount,
                        genres = genreCount, genresTotal = genreCount,
                        phase = "complete", isRunning = false,
                        elapsedMs = System.currentTimeMillis() - startMs,
                    )
                    val durationMs = System.currentTimeMillis() - startMs
                    val now = System.currentTimeMillis()
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val editor = prefs.edit()
                        .putLong(PREF_LAST_METADATA_SYNC_MS, now)
                        .putLong(PREF_METADATA_SYNC_DURATION_MS, durationMs)
                        .putInt("metadata_version", METADATA_VERSION)
                    if (resolvedMode == LibrarySyncMode.FULL) {
                        editor.putLong(PREF_LAST_FULL_SYNC_MS, now)
                    } else {
                        editor.putLong(PREF_LAST_DELTA_SYNC_MS, now)
                    }
                    editor.apply()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Metadata sync failed: ${e.message}")
                DiagnosticLog.e(TAG, "sync failed: ${e.message}", e)
                _status.value = _status.value.copy(
                    isRunning = false,
                    phase = "error",
                    elapsedMs = System.currentTimeMillis() - startMs,
                )
            } finally {
                lastSyncFinishMs = System.currentTimeMillis()
                isSyncing.set(false)
                syncJobs.remove(coroutineContext[kotlinx.coroutines.Job]!!)
                val phase = _status.value.phase
                DiagnosticLog.d(
                    TAG,
                    "sync end phase=$phase elapsed=${System.currentTimeMillis() - startMs}ms albums=${_status.value.albums} artists=${_status.value.artists} tracks=${_status.value.trackCount}",
                )
            }
        }
        syncJobs.add(job)
        return job
    }

    @Suppress("ThrowsCount")
    internal suspend fun syncAlbums(mode: LibrarySyncMode = LibrarySyncMode.FULL) {
        val username = SubsonicCredentials.username
        val password = SubsonicCredentials.password
        if (username.isEmpty() || password.isEmpty()) return

        changedAlbumIds.clear()
        val params = authHelper.buildAuthParams(username, password)

        val listType = if (mode == LibrarySyncMode.DELTA) "newest" else "alphabeticalByName"
        val maxPages = if (mode == LibrarySyncMode.DELTA) DELTA_MAX_PAGES else Int.MAX_VALUE

        val allAlbums = mutableListOf<CachedAlbumEntity>()
        var offset = 0
        var page = 0
        var listIncomplete = false
        while (page < maxPages) {
            val response = api.getAlbumList2(
                type = listType,
                size = PAGE_SIZE,
                offset = offset,
                auth = params,
            )
            val sr = response["subsonic-response"] as? Map<*, *>
            if (sr?.get("status") as? String == "failed") {
                // ADR-0068: fail-closed — never replace/upsert from a truncated page walk.
                listIncomplete = true
                break
            }
            val albumList = sr?.get("albumList2") as? Map<*, *>
            val albums = albumList?.get("album") as? List<*>
            if (albums.isNullOrEmpty()) break

            albums.forEach { a ->
                val m = a as? Map<*, *> ?: return@forEach
                parseAlbumMap(m)?.let { allAlbums.add(it) }
            }
            page++
            if (albums.size < PAGE_SIZE) break
            offset += PAGE_SIZE
            delay(100L) // throttle between paginated API calls
        }

        if (listIncomplete) {
            // ADR-0068: abort whole sync so watermarks stay honest (catch → phase=error).
            Log.w(TAG, "Album list incomplete (API failed mid-pagination) — keeping cached albums")
            throw AlbumListIncompleteException()
        }

        // 1A: collapse duplicate Subsonic ids within the fetched batch.
        val uniqueAlbums = allAlbums.distinctBy { it.id }

        if (uniqueAlbums.isNotEmpty()) {
            // Incremental staleness detection: albums whose metadata
            // changed on the server need their tracks re-fetched.
            changedAlbumIds.clear()
            try {
                val cached = metadataDao.getAllAlbums().associateBy { it.id }
                val coverArtsToInvalidate = linkedSetOf<String>()
                for (album in uniqueAlbums) {
                    val old = cached[album.id] ?: continue // new album — uncached path handles it
                    val metaChanged = old.songCount != album.songCount || old.duration != album.duration ||
                        old.name != album.name || old.artist != album.artist ||
                        old.year != album.year || old.genre != album.genre ||
                        old.coverArt != album.coverArt
                    if (metaChanged) {
                        changedAlbumIds.add(album.id)
                        // Cover id changed → drop old slot; any meta churn → revalidate current id
                        // (same-id byte upgrades when other fields moved). ADR-0090.
                        if (old.coverArt != null && old.coverArt != album.coverArt) {
                            coverArtsToInvalidate.add(old.coverArt)
                        }
                        album.coverArt?.let { coverArtsToInvalidate.add(it) }
                    }
                }
                if (coverArtsToInvalidate.isNotEmpty()) {
                    try {
                        coverArtFallback.invalidateNavidromeArts(coverArtsToInvalidate)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Cover art invalidate failed: ${e.message}")
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
        if (uniqueAlbums.isEmpty() && metadataDao.albumCount() > 0) {
            // A failed/partial page must never wipe the cached library or the
            // cover-art cache. Keep what we have; the next sync retries.
            Log.w(TAG, "Album sync returned no rows for a populated library — keeping cached albums")
            return
        }

        if (mode == LibrarySyncMode.DELTA) {
            // Upsert only — do not wipe albums outside the newest window.
            // Preserve enrich columns (notes/MBID) — ADR-0085.
            if (uniqueAlbums.isNotEmpty()) {
                metadataDao.upsertAlbumsPreserveEnrich(uniqueAlbums)
                try {
                    metadataDao.insertNewAlbumsToLedger()
                    metadataDao.refreshAlbumLedgerMetadata()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Album ledger upsert failed: ${e.message}")
                }
            }
            Log.d(TAG, "DELTA upserted ${uniqueAlbums.size} newest albums")
        } else {
            // Diff-replace — delete missing ids, upsert-preserve enrich (ADR-0085).
            // Empty list with empty cache is a legitimate no-op.
            metadataDao.replaceAlbumsDiffPreserveEnrich(uniqueAlbums)
            // Repopulate the albums ledger (favorites table) — ON CONFLICT DO
            // UPDATE preserves starred_at/user_rating/is_disliked/disliked_at across wipes.
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
                coverArtFallback.cleanOrphanedNavidromeArt(uniqueAlbums.mapNotNull { it.coverArt }.toSet())
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            Log.d(TAG, "FULL cached ${uniqueAlbums.size} albums")
        }
        _status.value = _status.value.copy(
            albums = metadataDao.albumCount(),
            albumsTotal = metadataDao.albumCount(),
        )
    }

    private fun parseAlbumMap(m: Map<*, *>): CachedAlbumEntity? {
        val id = m["id"] as? String ?: return null
        return CachedAlbumEntity(
            id = id,
            name = m["name"] as? String ?: "",
            artist = m["artist"] as? String,
            artistId = m["artistId"] as? String,
            year = (m["year"] as? Number)?.toInt(),
            coverArt = m["coverArt"] as? String,
            songCount = (m["songCount"] as? Number)?.toInt(),
            duration = (m["duration"] as? Number)?.toInt(),
            genre = m["genre"] as? String,
        )
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
            // Invalidate cover disk when artist coverArt id changes (ADR-0090).
            try {
                val cached = metadataDao.getAllArtists().associateBy { it.id }
                val coverArtsToInvalidate = linkedSetOf<String>()
                for (artist in allArtists) {
                    val old = cached[artist.id] ?: continue
                    if (old.coverArt != artist.coverArt) {
                        old.coverArt?.let { coverArtsToInvalidate.add(it) }
                        artist.coverArt?.let { coverArtsToInvalidate.add(it) }
                    }
                }
                if (coverArtsToInvalidate.isNotEmpty()) {
                    coverArtFallback.invalidateNavidromeArts(coverArtsToInvalidate)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Artist cover art invalidate failed: ${e.message}")
            }
            // Diff-replace preserves biography/aliases/tags/MBID (ADR-0085).
            metadataDao.replaceArtistsDiffPreserveEnrich(allArtists)
            Log.d(TAG, "Cached ${allArtists.size} artists")
        }
        // Repopulate the artists ledger (favorites table) — ON CONFLICT DO
        // UPDATE preserves starred_at/is_disliked/disliked_at across wipes.
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

        // 2. Fetch songs for Custom Daily Mix genres UNION top-N by song_count
        // for local search corpus density (singles / never-played). Cap at
        // SEARCH_INDEX_WARM_GENRE_COUNT + mix names (deduped).
        val mixGenres = try {
            if (dailyMixRepository.hasMixes()) {
                dailyMixRepository.allMixGenreNames()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
        val warmGenres = genres.sortedByDescending { it.songCount }
            .take(SEARCH_INDEX_WARM_GENRE_COUNT)
            .map { it.name }
        val selectedGenres = (mixGenres + warmGenres).distinct()
        for (genre in selectedGenres) {
            try {
                delay(GENRE_FETCH_DELAY_MS) // rate limit between genre calls
                val songsResp = api.getSongsByGenre(params, genre, GENRE_SONG_FETCH_COUNT)
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
     * Densify album-less / deep-genre songs into `tracks` without requiring
     * `cached_albums` rows (ADR-0085 / plan 2B).
     *
     * 1. Genre offset continuation when server songCount > local cache.
     * 2. getRandomSongs rounds to surface singles / undersampled tracks.
     */
    @VisibleForTesting
    internal suspend fun syncOrphanSongs(force: Boolean = false, mode: LibrarySyncMode = LibrarySyncMode.DELTA) {
        val username = SubsonicCredentials.username
        val password = SubsonicCredentials.password
        if (username.isEmpty()) return
        val params = authHelper.buildAuthParams(username, password)
        val pageBudget = if (force || mode == LibrarySyncMode.FULL) {
            ORPHAN_GENRE_OFFSET_PAGES_FULL
        } else {
            ORPHAN_GENRE_OFFSET_PAGES_DELTA
        }
        val randomRounds = if (force || mode == LibrarySyncMode.FULL) {
            ORPHAN_RANDOM_ROUNDS_FULL
        } else {
            ORPHAN_RANDOM_ROUNDS_DELTA
        }

        var pagesUsed = 0
        val genres = try {
            genreMixDao.getTopGenres()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
        for (genre in genres) {
            if (pagesUsed >= pageBudget) break
            val localCount = try {
                genreMixDao.countSongsForGenre(genre.name)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                0
            }
            if (genre.songCount <= localCount) continue
            var offset = localCount
            while (pagesUsed < pageBudget && offset < genre.songCount) {
                try {
                    delay(GENRE_FETCH_DELAY_MS)
                    val songsResp = api.getSongsByGenre(
                        params,
                        genre.name,
                        GENRE_SONG_FETCH_COUNT,
                        offset,
                    )
                    val songsSr = songsResp["subsonic-response"] as? Map<*, *>
                    val songsData = songsSr?.get("songsByGenre") as? Map<*, *>
                    val songList = songsData?.get("song") as? List<*>
                    pagesUsed++
                    if (songList.isNullOrEmpty()) break
                    val songs = parseGenreSongs(songList, genre.name)
                    if (songs.isNotEmpty()) {
                        genreMixDao.upsertSongs(songs)
                        val trackRows = songs.map { it.toTrackEntity() }.distinctBy { it.id }
                        trackDao.upsertTracksPreserveCache(trackRows)
                    }
                    if (songList.size < GENRE_SONG_FETCH_COUNT) break
                    offset += songList.size
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    break
                }
            }
        }

        val randomBatch = mutableListOf<TrackEntity>()
        repeat(randomRounds) { round ->
            try {
                delay(GENRE_FETCH_DELAY_MS)
                val response = api.getRandomSongs(params, size = ORPHAN_RANDOM_SONG_SIZE)
                val sr = response["subsonic-response"] as? Map<*, *> ?: return@repeat
                if (sr["status"] as? String == "failed") return@repeat
                val randomSongs = sr["randomSongs"] as? Map<*, *> ?: return@repeat
                val songList = randomSongs["song"] as? List<*> ?: return@repeat
                for (s in songList) {
                    val m = s as? Map<*, *> ?: continue
                    parseSongMapToTrack(m)?.let { randomBatch.add(it) }
                }
                Log.d(TAG, "Orphan densify random round=$round size=${songList.size}")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "getRandomSongs round failed: ${e.message}")
            }
        }
        if (randomBatch.isNotEmpty()) {
            trackDao.upsertTracksPreserveCache(randomBatch.distinctBy { it.id })
        }
        try {
            _status.value = _status.value.copy(trackCount = trackDao.trackCountAll())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    private fun parseGenreSongs(songList: List<*>, genre: String): List<CachedGenreSongEntity> =
        songList.mapNotNull { s ->
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

    private fun CachedGenreSongEntity.toTrackEntity(): TrackEntity = TrackEntity(
        id = id,
        title = title,
        artist = artist,
        album = null,
        albumId = albumId,
        artistId = artistId,
        genre = genre,
        durationSeconds = duration,
        trackNumber = trackNumber,
        coverArtUrl = coverArt,
        suffix = suffix,
        contentType = contentType,
    )

    private fun parseSongMapToTrack(m: Map<*, *>): TrackEntity? {
        val id = m["id"] as? String ?: return null
        return TrackEntity(
            id = id,
            title = m["title"] as? String ?: "",
            artist = m["artist"] as? String,
            album = m["album"] as? String,
            albumId = m["albumId"] as? String,
            artistId = m["artistId"] as? String,
            genre = m["genre"] as? String,
            durationSeconds = (m["duration"] as? Number)?.toInt(),
            trackNumber = (m["track"] as? Number)?.toInt(),
            coverArtUrl = m["coverArt"] as? String,
            suffix = m["suffix"] as? String,
            contentType = m["contentType"] as? String,
            bitrate = (m["bitRate"] as? Number)?.toInt(),
        )
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

            // Tracks: upsert metadata into Room (singles / never-played) then star + rating
            val songList = starred["song"] as? List<*> ?: emptyList<Any>()
            val starredTrackIds = mutableListOf<String>()
            val starredEntities = mutableListOf<TrackEntity>()
            for (s in songList) {
                val m = s as? Map<*, *> ?: continue
                val id = m["id"] as? String ?: continue
                starredTrackIds.add(id)
                starredEntities.add(
                    TrackEntity(
                        id = id,
                        title = m["title"] as? String ?: id,
                        artist = m["artist"] as? String,
                        album = m["album"] as? String,
                        albumId = m["albumId"] as? String,
                        artistId = m["artistId"] as? String,
                        genre = m["genre"] as? String,
                        durationSeconds = (m["duration"] as? Number)?.toInt(),
                        trackNumber = (m["track"] as? Number)?.toInt(),
                        coverArtUrl = m["coverArt"] as? String,
                        suffix = m["suffix"] as? String,
                        contentType = m["contentType"] as? String,
                        path = m["path"] as? String,
                    ),
                )
                val rating = (m["userRating"] as? Number)?.toInt() ?: 0
                if (rating > 0) trackDao.setRating(id, rating)
            }
            if (starredEntities.isNotEmpty()) {
                trackDao.upsertAll(starredEntities)
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
            val pendingAlbumsAll = albums.filter { album ->
                if (forceResync) return@filter true
                val existing = metadataDao.getAlbumTracks(album.id)
                !(existing.isNotEmpty() && album.id !in changedAlbumIds)
            }
            // Phase-3: cap non-force drain so delta syncs make steady corpus progress
            val pendingAlbums = if (forceResync) {
                pendingAlbumsAll
            } else {
                pendingAlbumsAll.take(SEARCH_CORPUS_ALBUM_DRAIN_CAP)
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

            // Process in adaptive batches for controlled concurrency + memory; each
            // batch's fetched tracks are written in ONE transaction (ADR-0045).
            var index = 0
            while (index < pendingAlbums.size) {
                if (aborted.get()) break
                val batchSize = trackLimiter.concurrency
                val end = minOf(index + batchSize, pendingAlbums.size)
                val chunk = pendingAlbums.subList(index, end).toList()
                index = end
                val batchFailures = AtomicInteger(0)
                val fetched = coroutineScope {
                    chunk.map { album ->
                        async {
                            val tracks = fetchAlbumTracks(album)
                            if (tracks == null) {
                                consecutiveFailures.incrementAndGet()
                                failedCount.incrementAndGet()
                                batchFailures.incrementAndGet()
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
                if (batchFailures.get() > 0) {
                    trackLimiter.onBatchFailure()
                } else {
                    trackLimiter.onBatchSuccess()
                }
                val allTracks = fetched.flatten()
                if (allTracks.isNotEmpty()) {
                    metadataDao.replaceAlbumTracksBatch(allTracks)
                    // Honest song_count from fetched list (ADR-0085).
                    for ((albumId, tracks) in allTracks.groupBy { it.albumId }) {
                        try {
                            metadataDao.setAlbumSongCount(albumId, tracks.size)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                        }
                    }
                }
                val ok = fetched.count { it.isNotEmpty() }
                count.addAndGet(ok)
                totalTracks.addAndGet(allTracks.size)
                val corpusCount = try {
                    trackDao.trackCountAll()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    totalTracks.get()
                }
                _status.value = _status.value.copy(
                    albumTracksProgress = count.get(),
                    trackCount = corpusCount,
                )
                if (!aborted.get() && index < pendingAlbums.size) {
                    delay(trackLimiter.batchDelayMs)
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
