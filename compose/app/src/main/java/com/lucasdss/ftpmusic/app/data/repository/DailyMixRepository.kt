package com.lucasdss.ftpmusic.app.data.repository

import androidx.compose.runtime.Immutable
import com.lucasdss.ftpmusic.app.data.cache.MixCacheCoordinator
import com.lucasdss.ftpmusic.app.data.cover.CollectionCoverKind
import com.lucasdss.ftpmusic.app.data.cover.CollectionCoverStore
import com.lucasdss.ftpmusic.app.data.cover.UserCoverLibrary
import com.lucasdss.ftpmusic.app.data.db.ArtistEntity
import com.lucasdss.ftpmusic.app.data.db.CachedGenreEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.CustomMixDao
import com.lucasdss.ftpmusic.app.data.db.CustomMixEntity
import com.lucasdss.ftpmusic.app.data.db.CustomMixStateEntity
import com.lucasdss.ftpmusic.app.data.db.DailyMixEntity
import com.lucasdss.ftpmusic.app.data.db.DailyMixStatus
import com.lucasdss.ftpmusic.app.data.db.DailyMixTrackEntity
import com.lucasdss.ftpmusic.app.data.db.GenreMixDao
import com.lucasdss.ftpmusic.app.data.db.ListenEventDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.playback.DailyMixGenerator
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Composite Custom Daily Mix filters (v48). Every non-empty dimension narrows
 * the pool; dimensions are ANDed (Rock ∩ 90s ∩ selected artists) while picks
 * inside one dimension are ORed (Rock OR Jazz). An empty dimension is a
 * wildcard. `includeFavoriteArtists` keeps the legacy dynamic "all liked
 * artists" behavior alongside explicit artist ids.
 */
@Immutable
data class MixFilters(
    val genres: List<String> = emptyList(),
    val decades: List<String> = emptyList(),
    val artistIds: List<String> = emptyList(),
    val includeFavoriteArtists: Boolean = false,
)

/** Domain model for a Custom Daily Mix recipe. */
@Immutable
data class CustomMix(
    val id: Long,
    val name: String,
    val filters: MixFilters,
    val autoCache: Boolean,
    val isDefault: Boolean,
    /** When false (default), short pools shrink — no Phase-3 fill from other mixes. */
    val allowCrossMixFill: Boolean = false,
    /** Local fixed cover: [CollectionCoverKind.NAVIDROME] | [CollectionCoverKind.LOCAL]. */
    val fixedCoverKind: String? = null,
    val fixedCoverValue: String? = null,
)

/**
 * Central service for Custom Daily Mixes: recipe CRUD, first-run seeding,
 * composite source-pool resolution, tracklist generation and auto-cache
 * hand-off. All generation paths (Home lazy load, sync screen, detail
 * refresh) go through here so the rules live in one place.
 */
@Singleton
class DailyMixRepository @Inject constructor(
    private val customMixDao: CustomMixDao,
    private val genreMixDao: GenreMixDao,
    private val trackDao: TrackDao,
    private val metadataDao: CachedMetadataDao,
    private val listenEventDao: ListenEventDao,
    private val mixCacheCoordinator: MixCacheCoordinator,
    private val coverStore: CollectionCoverStore,
    private val userCoverLibrary: UserCoverLibrary,
    private val ioDispatcher: CoroutineDispatcher,
) {
    companion object {
        const val MAX_MIXES = 20
        const val NAME_MAX = 32
        const val COVER_PREFIX = "mix"

        const val KIND_GENRES = "genres"
        const val KIND_DECADES = "decades"
        const val KIND_FAVORITE_ARTISTS = "favoriteArtists"
        const val KIND_ARTISTS = "artists"
        const val KIND_MIXED = "mixed"

        /** SQLite bind-variable guard for `IN (:ids)` queries (older Android
         *  builds cap at 999; uncapped selections can exceed it). */
        const val SQL_CHUNK = 900

        /** UI order + year ranges for the decade picker. */
        val DECADE_RANGES: Map<String, IntRange> = linkedMapOf(
            "60s" to 1960..1969,
            "70s" to 1970..1979,
            "80s" to 1980..1989,
            "90s" to 1990..1999,
            "2000s" to 2000..2009,
            "2010s" to 2010..2019,
            "2020s" to 2020..2029,
        )

        fun encodeNames(names: List<String>): String = names.joinToString("\n")

        fun decodeNames(raw: String): List<String> = raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Shared per-run generation context: one weight-map query for all mixes. */
    data class GenerationContext(
        val date: String,
        val yesterday: String,
        val mixes: List<CustomMixEntity>,
        val weightMap: Map<String, Int>,
        val allSongs: List<DailyMixGenerator.SongInfo>,
        val pools: Map<Long, List<DailyMixGenerator.SongInfo>>,
    )

    /** Why a single mix's generation attempt ended the way it did. */
    enum class MixGenerationStatus {
        /** A tracklist was generated and persisted. */
        PRODUCED,

        /** The persisted list is still fresh; nothing was written. */
        SKIPPED_FRESH,

        /** The recipe was eligible but yielded nothing (empty pool / no tracks). */
        EMPTY_ELIGIBLE,
    }

    /** Result of one [generateForMix] attempt. */
    data class MixGenerationOutcome(val trackIds: List<String>, val status: MixGenerationStatus)

    /**
     * Aggregate result of one [generateAllOutcome] run.
     *
     * [skippedFresh] is kept separate from [emptyEligible] because a run where
     * every mix was merely still fresh must not be recorded as an empty
     * outcome: the list can cross the age/listen threshold later the same day
     * (DM-14).
     */
    data class GenerateAllResult(val produced: Int, val skippedFresh: Int, val emptyEligible: Int) {
        /** Mixes that actually reached generation or were eligible but empty. */
        val attempted: Int get() = produced + emptyEligible

        /** True only when every attempted mix was eligible but produced nothing. */
        val isEmptyOutcome: Boolean
            get() = produced == 0 && skippedFresh == 0 && emptyEligible > 0
    }

    /** Per-run dimension caches: a genre/decade shared by several mixes is
     *  resolved once. Favorites and disliked artist names (name-only tracks)
     *  are fetched once too. Album/artist-id dislike is applied in SQL JOINs. */
    private class PoolCaches(val favoriteArtists: List<ArtistEntity>, val dislikedArtistNames: Set<String>) {
        val genreIds = mutableMapOf<String, Set<String>>()
        val decadeIds = mutableMapOf<String, Set<String>>()
    }

    /** Serializes generation runs (Home lazy, sync screen, detail refresh,
     *  source edits) so they cannot interleave or double-seed. */
    private val generationMutex = Mutex()

    suspend fun getAll(): List<CustomMix> = customMixDao.getAll().map { it.toDomain() }

    suspend fun getMix(id: Long): CustomMix? = customMixDao.getById(id)?.toDomain()

    suspend fun allGenres(): List<CachedGenreEntity> = genreMixDao.getTopGenres()

    /** True when at least one recipe exists (used by the sync song fetch). */
    suspend fun hasMixes(): Boolean = customMixDao.count() > 0

    /** Genre names referenced by any mix — the sync's per-genre song fetch list. */
    suspend fun allMixGenreNames(): List<String> = customMixDao.getAll()
        .flatMap { decodeNames(it.genresJson) }
        .distinct()

    /** Genre names referenced by mixes that no longer exist in the library. */
    suspend fun missingSourceGenres(): Map<Long, List<String>> {
        val known = genreMixDao.getTopGenres().map { it.name }.toSet()
        return customMixDao.getAll().mapNotNull { mix ->
            val missing = decodeNames(mix.genresJson).filter { it !in known }
            if (missing.isEmpty()) null else mix.id to missing
        }.toMap()
    }

    /**
     * First-run seeding: materialize the top-20 genres as default mixes ONCE.
     * `seedOnce` re-checks the marker and fills only free slots inside a single
     * transaction, so concurrent callers cannot double-seed and a user who
     * already added mixes pre-seed cannot exceed the 20-mix cap.
     */
    suspend fun seedIfNeeded() = withContext(ioDispatcher) {
        if (customMixDao.getState()?.seededAt != null) return@withContext
        val top = genreMixDao.getTopGenres().take(MAX_MIXES)
        if (top.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        customMixDao.seedOnce(
            top.map { genre ->
                CustomMixEntity(
                    name = "${genre.name} Mix".take(NAME_MAX),
                    sourceKind = KIND_GENRES,
                    genresJson = genre.name,
                    isDefault = true,
                    createdAt = now,
                )
            },
            MAX_MIXES,
        )
    }

    /** @return inserted id, or null when at the 20-mix cap (atomic check). */
    suspend fun addMix(
        name: String,
        filters: MixFilters,
        autoCache: Boolean,
        allowCrossMixFill: Boolean = false,
    ): Long? = withContext(ioDispatcher) {
        val normalized = normalize(name, filters) ?: return@withContext null
        val id = customMixDao.insertIfUnderCap(
            normalized.toEntity(
                autoCache = autoCache,
                allowCrossMixFill = allowCrossMixFill,
                isDefault = false,
            ),
            MAX_MIXES,
        )
        if (id == -1L) null else id
    }

    /**
     * Persist an edit. Clears the `is_default` badge (any edit makes a mix
     * user-owned). Filter / fill-mode edits regenerate immediately and
     * reconcile the auto-cache ownership; auto-cache toggles cache/evict
     * immediately.
     */
    suspend fun updateMix(
        id: Long,
        name: String,
        filters: MixFilters,
        autoCache: Boolean,
        allowCrossMixFill: Boolean = false,
    ) = withContext(ioDispatcher) {
        val old = customMixDao.getById(id) ?: return@withContext
        val normalized = normalize(name, filters) ?: return@withContext
        val filtersChanged = old.sourceKind != normalized.sourceKind ||
            old.genresJson != normalized.genresJson ||
            old.decadesJson != normalized.decadesJson ||
            old.artistsJson != normalized.artistsJson ||
            old.includeFavoriteArtists != normalized.includeFavoriteArtists
        val fillChanged = old.allowCrossMixFill != allowCrossMixFill
        val autoCacheChanged = old.autoCache != autoCache
        customMixDao.update(
            normalized.toEntity(
                autoCache = autoCache,
                allowCrossMixFill = allowCrossMixFill,
                isDefault = false,
                id = old.id,
                createdAt = old.createdAt,
                fixedCoverKind = old.fixedCoverKind,
                fixedCoverValue = old.fixedCoverValue,
            ),
        )
        when {
            filtersChanged || fillChanged -> {
                // Explicit editor action: build + activate (DM-16).
                generateOne(id, manual = true, activate = true)
            }

            autoCacheChanged -> {
                if (autoCache) {
                    val activeIds = activeTrackIds(id)
                    val ids = activeIds.ifEmpty {
                        generateOne(id, manual = true, activate = true)
                    }
                    if (ids.isNotEmpty()) {
                        mixCacheCoordinator.onSourceChanged(id, ids)
                    }
                } else {
                    mixCacheCoordinator.onDisabled(id)
                }
            }
        }
    }

    suspend fun deleteMix(id: Long) = withContext(ioDispatcher) {
        val old = customMixDao.getById(id)
        if (old?.fixedCoverKind == CollectionCoverKind.LOCAL) {
            coverStore.deleteRelative(old.fixedCoverValue)
        }
        coverStore.deleteForPrefix(COVER_PREFIX, id.toString())
        mixCacheCoordinator.onDeleted(id)
        customMixDao.deleteById(id)
    }

    /**
     * Pin a fixed cover for a mix recipe (local only). [kind] is
     * [CollectionCoverKind.NAVIDROME] or [CollectionCoverKind.LOCAL].
     */
    /**
     * Pin a fixed cover. LOCAL values are library-relative paths (ADR 0151).
     * Switching covers does **not** delete prior library files.
     */
    suspend fun setFixedCover(id: Long, kind: String, value: String) = withContext(ioDispatcher) {
        if (!CollectionCoverKind.isValid(kind) || value.isBlank()) return@withContext
        val old = customMixDao.getById(id) ?: return@withContext
        customMixDao.update(
            old.copy(fixedCoverKind = kind, fixedCoverValue = value),
        )
    }

    /** Clear assignment only — retained library files are kept (ADR 0151). */
    suspend fun clearFixedCover(id: Long) = withContext(ioDispatcher) {
        val old = customMixDao.getById(id) ?: return@withContext
        customMixDao.update(old.copy(fixedCoverKind = null, fixedCoverValue = null))
    }

    /**
     * Persist a cropped bitmap into the personal cover library and pin it.
     * Recycles [bitmap]. @return library relative path or null on failure.
     */
    suspend fun setFixedCoverFromCroppedBitmap(id: Long, bitmap: android.graphics.Bitmap): String? =
        withContext(ioDispatcher) {
            val entry = userCoverLibrary.importCroppedBitmap(bitmap) ?: return@withContext null
            setFixedCover(id, CollectionCoverKind.LOCAL, entry.relativePath)
            entry.relativePath
        }

    /** Build pools + one weight map for the whole run. Run on IO by callers. */
    suspend fun prepare(date: String): GenerationContext {
        seedIfNeeded()
        trackDao.populateAllTrackGenres()
        trackDao.populateGenresFromCachedGenreSongs()
        val mixes = customMixDao.getAll()
        // Only pay for the favorites query when a recipe actually uses it.
        val needsFavorites = mixes.any {
            it.includeFavoriteArtists ||
                (it.sourceKind == KIND_FAVORITE_ARTISTS && decodeNames(it.artistsJson).isEmpty())
        }
        val caches = PoolCaches(
            favoriteArtists = if (needsFavorites) metadataDao.getAllStarredArtists() else emptyList(),
            dislikedArtistNames = metadataDao.getDislikedArtistNames().toSet(),
        )
        val pools = mixes.associate { it.id to resolvePool(it, caches) }
        val allSongs = pools.values.flatten().distinctBy { it.id }
        val weightMap = DailyMixGenerator.buildWeightMap(allSongs.map { it.id }, trackDao)
        return GenerationContext(
            date = date,
            yesterday = LocalDate.parse(date).minusDays(1).toString(),
            mixes = mixes,
            weightMap = weightMap,
            allSongs = allSongs,
            pools = pools,
        )
    }

    /**
     * Generate/refresh one mix's tracklist for [context.date] (DM-16).
     *
     * Builds a new version off to the side. Never mutates the active list in
     * place. Never deletes the active row on empty pool.
     *
     * When [activate] is true (rebuild / Detail Update / filter edit), the new
     * ready version becomes active. When false (Home/sync lazy), only
     * [CustomMixEntity.pendingDailyMixId] is updated — sticky cards stay.
     * First successful generation (`active IS NULL`) always activates.
     */
    suspend fun generateForMix(
        context: GenerationContext,
        mix: CustomMixEntity,
        manual: Boolean,
        activate: Boolean = manual,
    ): MixGenerationOutcome {
        val recipe = customMixDao.getById(mix.id) ?: mix
        val active = recipe.activeDailyMixId?.let { genreMixDao.getDailyMixById(it) }
        val songs = context.pools[mix.id].orEmpty()
        if (songs.isEmpty()) {
            // DM-16 / R9: never delete the active list. Drop only unpublished builds.
            genreMixDao.deleteUnpublishedBuilds(mix.id)
            if (recipe.pendingDailyMixId != null) {
                customMixDao.updatePendingPointer(mix.id, null)
            }
            com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
                "ftpmusic-dailymix",
                "empty pool keep-active mixId=${mix.id} activeId=${recipe.activeDailyMixId}",
            )
            return MixGenerationOutcome(emptyList(), MixGenerationStatus.EMPTY_ELIGIBLE)
        }

        val existingMix = active
        val existingIds = if (existingMix != null) {
            genreMixDao.getDailyMixTrackIds(existingMix.id)
        } else {
            emptyList()
        }

        // DM-16 Phase-5: short-circuit non-manual gen when a build is already in
        // flight or a pending ready version exists — before shouldRegenerate so
        // Home/sync cannot churn a new pending on every resume while one waits.
        if (!manual) {
            val building = genreMixDao.getDailyMixByStatus(mix.id, DailyMixStatus.BUILDING)
            if (building != null) {
                return MixGenerationOutcome(emptyList(), MixGenerationStatus.SKIPPED_FRESH)
            }
            if (recipe.pendingDailyMixId != null && existingMix != null) {
                return MixGenerationOutcome(emptyList(), MixGenerationStatus.SKIPPED_FRESH)
            }
        }

        val listenedCount = if (existingMix != null) {
            listenEventDao.countDistinctTracksForMix(
                dailyMixId = existingMix.id,
                startMs = existingMix.createdAt,
                includeBackfill = false,
            )
        } else {
            0
        }
        val previouslyListenedIds = buildPreviouslyListenedIds(recipe, existingMix)
        val shouldRegen = if (existingMix != null) {
            DailyMixGenerator.shouldRegenerate(
                createdAtMs = existingMix.createdAt,
                totalTracks = existingIds.size,
                listenedTracks = listenedCount,
                manual = manual,
            )
        } else {
            true
        }
        if (!shouldRegen) {
            com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
                "ftpmusic-dailymix",
                "skip regen mixId=${mix.id} listened=$listenedCount total=${existingIds.size} manual=$manual",
            )
            return MixGenerationOutcome(emptyList(), MixGenerationStatus.SKIPPED_FRESH)
        }

        val poolIds = songs.mapTo(mutableSetOf()) { it.id }
        val supplemental = if (mix.allowCrossMixFill) {
            context.allSongs.filter { it.id !in poolIds }
        } else {
            emptyList()
        }
        val penaltyIds = buildPenaltyIds(recipe, existingMix, context.yesterday)

        val generated = DailyMixGenerator.generate(
            songs,
            context.weightMap,
            supplemental,
            penaltyIds,
            previouslyListenedTrackIds = previouslyListenedIds,
        )
        if (generated.isEmpty()) {
            return MixGenerationOutcome(emptyList(), MixGenerationStatus.EMPTY_ELIGIBLE)
        }

        // Drop previous unpublished builds before inserting the new version.
        genreMixDao.deleteUnpublishedBuilds(mix.id)
        val nextVersion = (genreMixDao.getLatestDailyMix(mix.id)?.version ?: 0) + 1
        val now = System.currentTimeMillis()
        val buildingId = genreMixDao.upsertDailyMix(
            DailyMixEntity(
                date = context.date,
                mixId = mix.id,
                createdAt = now,
                version = nextVersion,
                status = DailyMixStatus.BUILDING,
            ),
        ).toInt()
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
            "ftpmusic-dailymix",
            "build start mixId=${mix.id} v=$nextVersion manual=$manual",
        )
        val generatedIds = generated.map { it.id }
        // Tracks first, then ready — readers never see a ready row with 0 tracks.
        genreMixDao.insertDailyMixTracks(
            generatedIds.mapIndexed { idx, trackId ->
                DailyMixTrackEntity(mixId = buildingId.toLong(), trackId = trackId, position = idx)
            },
        )
        genreMixDao.updateDailyMixStatus(buildingId, DailyMixStatus.READY)
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
            "ftpmusic-dailymix",
            "build ready mixId=${mix.id} v=$nextVersion count=${generatedIds.size}",
        )

        val shouldActivate = activate || recipe.activeDailyMixId == null
        if (shouldActivate) {
            activateDailyMixLocked(mix.id, buildingId, generatedIds)
        } else {
            customMixDao.updatePendingPointer(mix.id, buildingId)
            com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
                "ftpmusic-dailymix",
                "pending set mixId=${mix.id} v=$nextVersion id=$buildingId",
            )
        }
        return MixGenerationOutcome(generatedIds, MixGenerationStatus.PRODUCED)
    }

    /**
     * Generate every mix once and report what each outcome was.
     * [activate] true for Settings rebuild / Home refresh; false for Home/sync lazy.
     */
    suspend fun generateAllOutcome(
        date: String,
        manual: Boolean,
        activate: Boolean = manual,
        onProgress: (suspend (done: Int, total: Int) -> Unit)? = null,
    ): GenerateAllResult = generationMutex.withLock {
        withContext(ioDispatcher) {
            reconcileOrphansLocked()
            val context = prepare(date)
            val mixes = context.mixes
            if (mixes.isEmpty()) return@withContext GenerateAllResult(0, 0, 0)
            var produced = 0
            var skippedFresh = 0
            var emptyEligible = 0
            mixes.forEachIndexed { index, mix ->
                when (generateForMix(context, mix, manual, activate).status) {
                    MixGenerationStatus.PRODUCED -> produced++
                    MixGenerationStatus.SKIPPED_FRESH -> skippedFresh++
                    MixGenerationStatus.EMPTY_ELIGIBLE -> emptyEligible++
                }
                onProgress?.invoke(index + 1, mixes.size)
            }
            GenerateAllResult(produced, skippedFresh, emptyEligible)
        }
    }

    /** Generate every mix once. Returns the number of mixes produced. */
    suspend fun generateAll(
        date: String,
        manual: Boolean,
        activate: Boolean = manual,
        onProgress: (suspend (done: Int, total: Int) -> Unit)? = null,
    ): Int = generateAllOutcome(date, manual, activate, onProgress).produced

    /**
     * Home freshness check (DM-5/DM-16): true when at least one recipe's
     * **active** list is stale (or missing). Ignores building/ready versions.
     */
    suspend fun anyMixNeedsRegeneration(): Boolean = withContext(ioDispatcher) {
        val mixes = customMixDao.getAll()
        if (mixes.isEmpty()) return@withContext false
        val nowMs = System.currentTimeMillis()
        mixes.any { mix ->
            val row = genreMixDao.getActiveDailyMix(mix.id) ?: return@any true
            val totalTracks = genreMixDao.getDailyMixTrackCount(row.id)
            val listenedTracks = listenEventDao.countDistinctTracksForMix(
                dailyMixId = row.id,
                startMs = row.createdAt,
                includeBackfill = false,
            )
            DailyMixGenerator.shouldRegenerate(
                createdAtMs = row.createdAt,
                totalTracks = totalTracks,
                listenedTracks = listenedTracks,
                nowMs = nowMs,
                manual = false,
            )
        }
    }

    /**
     * Refresh a single mix. [activate] defaults to [manual] so Detail Update /
     * Settings force paths promote the new version; Home lazy callers pass false.
     */
    suspend fun generateOne(
        mixId: Long,
        manual: Boolean,
        activate: Boolean = manual,
    ): List<String> = generationMutex.withLock {
        withContext(ioDispatcher) {
            reconcileOrphansLocked()
            val mix = customMixDao.getById(mixId) ?: return@withContext emptyList()
            val context = prepare(LocalDate.now().toString())
            generateForMix(context, mix, manual, activate).trackIds
        }
    }

    /** Active track ids for a recipe (Home / Detail / Auto / cache). */
    suspend fun activeTrackIds(mixId: Long): List<String> = withContext(ioDispatcher) {
        val active = genreMixDao.getActiveDailyMix(mixId) ?: return@withContext emptyList()
        genreMixDao.getDailyMixTrackIds(active.id)
    }

    /** Active daily_mix row for a recipe, or null. */
    suspend fun getActiveDailyMix(mixId: Long): DailyMixEntity? = withContext(ioDispatcher) {
        genreMixDao.getActiveDailyMix(mixId)
    }

    /** Pending ready version waiting for activation, or null. */
    suspend fun getPendingDailyMix(mixId: Long): DailyMixEntity? = withContext(ioDispatcher) {
        genreMixDao.getPendingDailyMix(mixId)
    }

    /**
     * Activate [dailyMixId] for [mixId]. Previous active → superseded.
     * Calls [MixCacheCoordinator.onSourceChanged] (not onGenerated).
     * Serialized under [generationMutex] so Detail Update / leave cannot
     * interleave with a concurrent [generateForMix] and leave the active
     * pointer on a `superseded` row.
     */
    suspend fun activateDailyMix(mixId: Long, dailyMixId: Int): Boolean = generationMutex.withLock {
        withContext(ioDispatcher) {
            activateDailyMixLocked(mixId, dailyMixId, trackIds = null)
        }
    }

    /** Activate the pending ready version if any. */
    suspend fun activatePending(mixId: Long): Boolean = generationMutex.withLock {
        withContext(ioDispatcher) {
            val pendingId = customMixDao.getById(mixId)?.pendingDailyMixId ?: return@withContext false
            activateDailyMixLocked(mixId, pendingId, trackIds = null)
        }
    }

    /** Startup / pre-gen reconciliation for orphan building/ready rows. */
    suspend fun reconcileOrphans() = generationMutex.withLock {
        withContext(ioDispatcher) { reconcileOrphansLocked() }
    }

    private suspend fun activateDailyMixLocked(
        mixId: Long,
        dailyMixId: Int,
        trackIds: List<String>?,
    ): Boolean {
        val row = genreMixDao.getDailyMixById(dailyMixId) ?: return false
        if (row.mixId != mixId) return false
        // Never promote an in-flight build; generateForMix activates after READY.
        if (row.status == DailyMixStatus.BUILDING) return false
        val recipe = customMixDao.getById(mixId) ?: return false
        val prevActive = recipe.activeDailyMixId
        if (prevActive != null && prevActive != dailyMixId) {
            genreMixDao.updateDailyMixStatus(prevActive, DailyMixStatus.SUPERSEDED)
        }
        if (row.status == DailyMixStatus.SUPERSEDED) {
            genreMixDao.updateDailyMixStatus(dailyMixId, DailyMixStatus.READY)
        } else if (row.status != DailyMixStatus.READY) {
            genreMixDao.updateDailyMixStatus(dailyMixId, DailyMixStatus.READY)
        }
        customMixDao.updateActivationPointers(mixId, dailyMixId, null)
        // Keep last superseded for anti-repeat; drop older.
        val keep = buildList {
            add(dailyMixId)
            if (prevActive != null && prevActive != dailyMixId) add(prevActive)
        }
        genreMixDao.deleteSupersededExcept(mixId, keep)
        val ids = trackIds ?: genreMixDao.getDailyMixTrackIds(dailyMixId)
        com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
            "ftpmusic-dailymix",
            "activate mixId=$mixId v=${row.version} prev=$prevActive",
        )
        mixCacheCoordinator.onSourceChanged(mixId, ids)
        return true
    }

    private suspend fun reconcileOrphansLocked() {
        val cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        genreMixDao.deleteStaleBuilding(cutoff)
        for (ready in genreMixDao.getAllReadyDailyMixes()) {
            val recipe = customMixDao.getById(ready.mixId) ?: continue
            if (recipe.activeDailyMixId == ready.id) continue
            if (recipe.pendingDailyMixId == ready.id) continue
            // Orphan ready → pending (rescue). Do not auto-activate.
            if (recipe.pendingDailyMixId == null) {
                customMixDao.updatePendingPointer(ready.mixId, ready.id)
                com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
                    "ftpmusic-dailymix",
                    "rescue ready mixId=${ready.mixId} id=${ready.id} v=${ready.version}",
                )
            }
        }
        // Repair dangling pointers.
        for (recipe in customMixDao.getAll()) {
            val activeId = recipe.activeDailyMixId
            if (activeId != null && genreMixDao.getDailyMixById(activeId) == null) {
                customMixDao.updateActivePointer(recipe.id, null)
            }
            val pendingId = recipe.pendingDailyMixId
            if (pendingId != null && genreMixDao.getDailyMixById(pendingId) == null) {
                customMixDao.updatePendingPointer(recipe.id, null)
            }
        }
    }

    private suspend fun buildPreviouslyListenedIds(
        recipe: CustomMixEntity,
        active: DailyMixEntity?,
    ): Set<String> {
        val ids = mutableSetOf<String>()
        if (active != null) {
            ids += listenEventDao.getListenedTrackIdsForMix(
                dailyMixId = active.id,
                startMs = active.createdAt,
                includeBackfill = false,
            )
        }
        // Union last superseded for anti-repeat (DM-13 / DM-16).
        val latest = genreMixDao.getLatestDailyMix(recipe.id)
        if (latest != null && latest.id != active?.id && latest.status == DailyMixStatus.SUPERSEDED) {
            ids += listenEventDao.getListenedTrackIdsForMix(
                dailyMixId = latest.id,
                startMs = latest.createdAt,
                includeBackfill = false,
            )
        }
        return ids
    }

    private suspend fun buildPenaltyIds(
        recipe: CustomMixEntity,
        active: DailyMixEntity?,
        yesterday: String,
    ): Set<String> {
        val ids = mutableSetOf<String>()
        if (active != null) {
            ids += genreMixDao.getDailyMixTrackIds(active.id)
        }
        val yRow = genreMixDao.getDailyMix(yesterday, recipe.id)
        if (yRow != null && yRow.id != active?.id) {
            ids += genreMixDao.getDailyMixTrackIds(yRow.id)
        }
        return ids
    }

    /**
     * Filter-on-read: drop tracks that are track-disliked or belong to a
     * disliked album/artist (including name-only rows). Preserves [trackIds]
     * order. Cheap — used by Mix Detail / play so UI never waits on regen.
     */
    suspend fun filterPlayableMixTrackIds(trackIds: List<String>): List<String> = withContext(ioDispatcher) {
        if (trackIds.isEmpty()) return@withContext emptyList()
        val dislikedNames = metadataDao.getDislikedArtistNames().toSet()
        val kept = trackIds.chunked(SQL_CHUNK)
            .flatMap { trackDao.filterMixPlayableIds(it) }
            .toSet()
        val orderedKept = trackIds.filter { it in kept }
        if (dislikedNames.isEmpty() || orderedKept.isEmpty()) {
            return@withContext orderedKept
        }
        // Name-only hole: artist_id null + artist display name disliked.
        val nameBlocked = orderedKept.chunked(SQL_CHUNK)
            .flatMap { trackDao.getTracksByIds(it) }
            .filter { it.artistId == null && it.artist != null && it.artist in dislikedNames }
            .mapTo(mutableSetOf()) { it.id }
        orderedKept.filter { it !in nameBlocked }
    }

    /**
     * Resolve a recipe's pool: OR inside each dimension, AND across the
     * non-empty dimensions, empty dimension = wildcard.
     */
    private suspend fun resolvePool(mix: CustomMixEntity, caches: PoolCaches): List<DailyMixGenerator.SongInfo> {
        val dimensions = mutableListOf<Set<String>>()

        val genres = decodeNames(mix.genresJson)
        if (genres.isNotEmpty()) {
            val ids = mutableSetOf<String>()
            for (genre in genres) ids += cachedGenreIds(genre, caches)
            dimensions += ids
        }

        val decades = decodeNames(mix.decadesJson)
        if (decades.isNotEmpty()) {
            val ids = mutableSetOf<String>()
            for (label in decades) {
                val range = DECADE_RANGES[label] ?: continue
                ids += cachedDecadeIds(label, range, caches)
            }
            dimensions += ids
        }

        val explicitArtistIds = decodeNames(mix.artistsJson)
        // Legacy v47 favoriteArtists rows (no explicit ids) keep the dynamic
        // liked-artists pool. An explicit-artist-only recipe must NOT inherit
        // favorites just because its derived kind reuses the legacy label.
        val legacyDynamicFavorites =
            mix.sourceKind == KIND_FAVORITE_ARTISTS && explicitArtistIds.isEmpty()
        val includeFavorites = mix.includeFavoriteArtists || legacyDynamicFavorites
        val favoriteArtists = if (includeFavorites) caches.favoriteArtists else emptyList()
        val artistIds = (explicitArtistIds + favoriteArtists.map { it.id }).distinct()
        if (artistIds.isNotEmpty()) {
            val ids = mutableSetOf<String>()
            artistIds.chunked(SQL_CHUNK).forEach { ids += trackDao.getMixTrackIdsByArtistIds(it) }
            // Name fallback for rows without an artist_id — skip disliked names.
            val explicitNames = explicitArtistIds
                .chunked(SQL_CHUNK)
                .flatMap { chunk -> metadataDao.getArtistsByIds(chunk).map { it.name } }
            val names = (explicitNames + favoriteArtists.map { it.name })
                .distinct()
                .filter { it !in caches.dislikedArtistNames }
            names.chunked(SQL_CHUNK).forEach { ids += trackDao.getMixTrackIdsByArtistNames(it) }
            dimensions += ids
        }

        if (dimensions.isEmpty()) return emptyList()
        val intersection = dimensions.reduce { acc, set -> acc intersect set }
        if (intersection.isEmpty()) return emptyList()
        // Pre-hydrate SQL filter (belt + suspenders with pool JOINs) then load
        // only survivors — avoids discarding full TrackEntity rows.
        val playable = intersection.toList()
            .chunked(SQL_CHUNK)
            .flatMap { trackDao.filterMixPlayableIds(it) }
        if (playable.isEmpty()) return emptyList()
        return playable
            .chunked(SQL_CHUNK)
            .flatMap { trackDao.getTracksByIds(it) }
            // Name-only tracks: no artist_id → SQL artist join misses them.
            .filter { track ->
                track.artistId != null ||
                    track.artist == null ||
                    track.artist !in caches.dislikedArtistNames
            }
            .map { it.toSongInfo() }
    }

    private suspend fun cachedGenreIds(genre: String, caches: PoolCaches): Set<String> =
        caches.genreIds[genre] ?: trackDao.getMixTrackIdsByGenre(genre).toSet().also { caches.genreIds[genre] = it }

    private suspend fun cachedDecadeIds(label: String, range: IntRange, caches: PoolCaches): Set<String> =
        caches.decadeIds[label]
            ?: trackDao.getMixTrackIdsByYearRange(range.first, range.last).toSet()
                .also { caches.decadeIds[label] = it }

    private fun normalize(name: String, filters: MixFilters): CustomMixEntity? {
        val trimmed = name.trim().take(NAME_MAX)
        if (trimmed.isEmpty()) return null
        val genres = filters.genres.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val decades = filters.decades.filter { it in DECADE_RANGES }.distinct()
        val artistIds = filters.artistIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val hasArtists = artistIds.isNotEmpty() || filters.includeFavoriteArtists
        if (genres.isEmpty() && decades.isEmpty() && !hasArtists) return null
        return CustomMixEntity(
            name = trimmed,
            sourceKind = derivedKind(genres, decades, hasArtists, filters.includeFavoriteArtists),
            genresJson = encodeNames(genres),
            decadesJson = encodeNames(decades),
            artistsJson = encodeNames(artistIds),
            includeFavoriteArtists = filters.includeFavoriteArtists,
        )
    }

    private fun derivedKind(
        genres: List<String>,
        decades: List<String>,
        hasArtists: Boolean,
        includeFavorites: Boolean,
    ): String {
        val dimensions = listOf(genres.isNotEmpty(), decades.isNotEmpty(), hasArtists).count { it }
        return when {
            dimensions > 1 -> KIND_MIXED
            genres.isNotEmpty() -> KIND_GENRES
            decades.isNotEmpty() -> KIND_DECADES
            includeFavorites -> KIND_FAVORITE_ARTISTS
            else -> KIND_ARTISTS
        }
    }
}

private fun CustomMixEntity.toDomain(): CustomMix = CustomMix(
    id = id,
    name = name,
    filters = MixFilters(
        genres = DailyMixRepository.decodeNames(genresJson),
        decades = DailyMixRepository.decodeNames(decadesJson),
        artistIds = DailyMixRepository.decodeNames(artistsJson),
        // Legacy fallback is guarded by "no explicit ids" so explicit-artist
        // recipes never reopen with the favorites toggle forced on.
        includeFavoriteArtists = includeFavoriteArtists ||
            (
                sourceKind == DailyMixRepository.KIND_FAVORITE_ARTISTS &&
                    DailyMixRepository.decodeNames(artistsJson).isEmpty()
                ),
    ),
    autoCache = autoCache,
    isDefault = isDefault,
    allowCrossMixFill = allowCrossMixFill,
    fixedCoverKind = fixedCoverKind,
    fixedCoverValue = fixedCoverValue,
)

private fun CustomMixEntity.toEntity(
    autoCache: Boolean,
    allowCrossMixFill: Boolean,
    isDefault: Boolean,
    id: Long = this.id,
    createdAt: Long = this.createdAt,
    fixedCoverKind: String? = this.fixedCoverKind,
    fixedCoverValue: String? = this.fixedCoverValue,
): CustomMixEntity = CustomMixEntity(
    id = id,
    name = name,
    sourceKind = sourceKind,
    genresJson = genresJson,
    decadesJson = decadesJson,
    artistsJson = artistsJson,
    includeFavoriteArtists = includeFavoriteArtists,
    autoCache = autoCache,
    allowCrossMixFill = allowCrossMixFill,
    isDefault = isDefault,
    createdAt = createdAt,
    fixedCoverKind = fixedCoverKind,
    fixedCoverValue = fixedCoverValue,
    activeDailyMixId = activeDailyMixId,
    pendingDailyMixId = pendingDailyMixId,
)

private fun TrackEntity.toSongInfo(): DailyMixGenerator.SongInfo = DailyMixGenerator.SongInfo(
    id = id,
    title = title,
    artist = artist,
    albumId = albumId,
    duration = durationSeconds,
    trackNumber = trackNumber,
    coverArt = coverArtUrl,
)
