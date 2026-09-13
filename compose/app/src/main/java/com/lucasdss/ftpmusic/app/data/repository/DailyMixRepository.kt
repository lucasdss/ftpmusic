package com.lucasdss.ftpmusic.app.data.repository

import androidx.compose.runtime.Immutable
import com.lucasdss.ftpmusic.app.data.cache.MixCacheCoordinator
import com.lucasdss.ftpmusic.app.data.db.ArtistEntity
import com.lucasdss.ftpmusic.app.data.db.CachedGenreEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.CustomMixDao
import com.lucasdss.ftpmusic.app.data.db.CustomMixEntity
import com.lucasdss.ftpmusic.app.data.db.CustomMixStateEntity
import com.lucasdss.ftpmusic.app.data.db.DailyMixTrackEntity
import com.lucasdss.ftpmusic.app.data.db.GenreMixDao
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
    private val mixCacheCoordinator: MixCacheCoordinator,
    private val ioDispatcher: CoroutineDispatcher,
) {
    companion object {
        const val MAX_MIXES = 20
        const val NAME_MAX = 32

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

    /** Per-run dimension caches: a genre/decade shared by several mixes is
     *  resolved once. Favorites are fetched once too. */
    private class PoolCaches(val favoriteArtists: List<ArtistEntity>) {
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
    suspend fun addMix(name: String, filters: MixFilters, autoCache: Boolean): Long? = withContext(ioDispatcher) {
        val normalized = normalize(name, filters) ?: return@withContext null
        val id = customMixDao.insertIfUnderCap(
            normalized.toEntity(autoCache = autoCache, isDefault = false),
            MAX_MIXES,
        )
        if (id == -1L) null else id
    }

    /**
     * Persist an edit. Clears the `is_default` badge (any edit makes a mix
     * user-owned). Filter edits regenerate immediately and reconcile the
     * auto-cache ownership; auto-cache toggles cache/evict immediately.
     */
    suspend fun updateMix(id: Long, name: String, filters: MixFilters, autoCache: Boolean) = withContext(ioDispatcher) {
        val old = customMixDao.getById(id) ?: return@withContext
        val normalized = normalize(name, filters) ?: return@withContext
        val filtersChanged = old.sourceKind != normalized.sourceKind ||
            old.genresJson != normalized.genresJson ||
            old.decadesJson != normalized.decadesJson ||
            old.artistsJson != normalized.artistsJson ||
            old.includeFavoriteArtists != normalized.includeFavoriteArtists
        val autoCacheChanged = old.autoCache != autoCache
        customMixDao.update(
            normalized.toEntity(autoCache = autoCache, isDefault = false, id = old.id, createdAt = old.createdAt),
        )
        when {
            filtersChanged -> {
                val ids = generateOne(id, manual = true)
                mixCacheCoordinator.onSourceChanged(id, ids)
            }

            autoCacheChanged -> {
                if (autoCache) {
                    // Today's tracklist only — never cache yesterday's stale
                    // list; regenerate when today has none yet.
                    val today = LocalDate.now().toString()
                    val todayIds = genreMixDao.getDailyMix(today, id)
                        ?.let { genreMixDao.getDailyMixTrackIds(it.id) }
                        ?: emptyList()
                    val ids = todayIds.ifEmpty { generateOne(id, manual = true) }
                    mixCacheCoordinator.onSourceChanged(id, ids)
                } else {
                    mixCacheCoordinator.onDisabled(id)
                }
            }
        }
    }

    suspend fun deleteMix(id: Long) = withContext(ioDispatcher) {
        mixCacheCoordinator.onDeleted(id)
        customMixDao.deleteById(id)
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
     * Generate/refresh one mix's tracklist for [context.date]. Never persists
     * an empty mix. Returns the generated track ids (empty when skipped or
     * when the source pool yields nothing).
     */
    suspend fun generateForMix(context: GenerationContext, mix: CustomMixEntity, manual: Boolean): List<String> {
        val songs = context.pools[mix.id] ?: return emptyList()
        if (songs.isEmpty()) {
            // The recipe no longer matches anything: drop any stale tracklist
            // so Home/detail stop serving tracks that violate the filters.
            // Deleting (rather than persisting an empty row) keeps the next
            // generation attempt unblocked.
            genreMixDao.deleteDailyMix(context.date, mix.id)
            genreMixDao.deleteDailyMix(context.yesterday, mix.id)
            return emptyList()
        }

        val existingMix = genreMixDao.getDailyMix(context.date, mix.id)
            ?: genreMixDao.getDailyMix(context.yesterday, mix.id)
        val existingIds = if (existingMix != null) genreMixDao.getDailyMixTrackIds(existingMix.id) else emptyList()
        // One batched play-count read serves both the listen-ratio check and
        // the anti-repetition set.
        val playCounts = if (existingIds.isEmpty()) {
            emptyMap()
        } else {
            trackDao.getTrackPlayCounts(existingIds).associate { it.id to it.playCount }
        }
        val shouldRegen = if (existingMix != null) {
            val listenedCount = existingIds.count { (playCounts[it] ?: 0) > 0 }
            DailyMixGenerator.shouldRegenerate(
                createdAtMs = existingMix.createdAt,
                totalTracks = existingIds.size,
                listenedTracks = listenedCount,
                manual = manual,
            )
        } else {
            true
        }
        if (!shouldRegen) return emptyList()

        val poolIds = songs.mapTo(mutableSetOf()) { it.id }
        val supplemental = context.allSongs.filter { it.id !in poolIds }
        val yesterdayMix = if (existingMix != null && existingMix.date == context.yesterday) {
            existingMix
        } else {
            genreMixDao.getDailyMix(context.yesterday, mix.id)
        }
        val yesterdayIds = if (yesterdayMix != null) {
            genreMixDao.getDailyMixTrackIds(yesterdayMix.id).toSet()
        } else {
            emptySet()
        }
        val previouslyListenedIds = existingIds.filter { (playCounts[it] ?: 0) > 0 }.toSet()

        val generated = DailyMixGenerator.generate(
            songs,
            context.weightMap,
            supplemental,
            yesterdayIds,
            previouslyListenedTrackIds = previouslyListenedIds,
        )
        if (generated.isEmpty()) return emptyList()

        genreMixDao.replaceDailyMix(
            context.date,
            mix.id,
            generated.mapIndexed { idx, s -> DailyMixTrackEntity(mixId = 0, trackId = s.id, position = idx) },
        )
        val generatedIds = generated.map { it.id }
        mixCacheCoordinator.onGenerated(mix, generatedIds)
        return generatedIds
    }

    /** Generate every mix once. [onProgress] receives (done, total). */
    suspend fun generateAll(
        date: String,
        manual: Boolean,
        onProgress: (suspend (done: Int, total: Int) -> Unit)? = null,
    ): Int = generationMutex.withLock {
        withContext(ioDispatcher) {
            val context = prepare(date)
            val mixes = context.mixes
            if (mixes.isEmpty()) return@withContext 0
            var produced = 0
            mixes.forEachIndexed { index, mix ->
                if (generateForMix(context, mix, manual).isNotEmpty()) produced++
                onProgress?.invoke(index + 1, mixes.size)
            }
            produced
        }
    }

    /** Refresh a single mix (detail screen refresh / source edit). */
    suspend fun generateOne(mixId: Long, manual: Boolean): List<String> = generationMutex.withLock {
        withContext(ioDispatcher) {
            val mix = customMixDao.getById(mixId) ?: return@withContext emptyList()
            val context = prepare(LocalDate.now().toString())
            generateForMix(context, mix, manual)
        }
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
            // Name fallback for rows without an artist_id.
            val explicitNames = explicitArtistIds
                .chunked(SQL_CHUNK)
                .flatMap { chunk -> metadataDao.getArtistsByIds(chunk).map { it.name } }
            val names = (explicitNames + favoriteArtists.map { it.name }).distinct()
            names.chunked(SQL_CHUNK).forEach { ids += trackDao.getMixTrackIdsByArtistNames(it) }
            dimensions += ids
        }

        if (dimensions.isEmpty()) return emptyList()
        val intersection = dimensions.reduce { acc, set -> acc intersect set }
        if (intersection.isEmpty()) return emptyList()
        return intersection.toList()
            .chunked(SQL_CHUNK)
            .flatMap { trackDao.getTracksByIds(it) }
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
)

private fun CustomMixEntity.toEntity(
    autoCache: Boolean,
    isDefault: Boolean,
    id: Long = this.id,
    createdAt: Long = this.createdAt,
): CustomMixEntity = CustomMixEntity(
    id = id,
    name = name,
    sourceKind = sourceKind,
    genresJson = genresJson,
    decadesJson = decadesJson,
    artistsJson = artistsJson,
    includeFavoriteArtists = includeFavoriteArtists,
    autoCache = autoCache,
    isDefault = isDefault,
    createdAt = createdAt,
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
