package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.cache.MixCacheCoordinator
import com.lucasdss.ftpmusic.app.data.db.ArtistEntity
import com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity
import com.lucasdss.ftpmusic.app.data.db.CachedGenreEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.CustomMixDao
import com.lucasdss.ftpmusic.app.data.db.CustomMixEntity
import com.lucasdss.ftpmusic.app.data.db.CustomMixStateEntity
import com.lucasdss.ftpmusic.app.data.db.DailyMixEntity
import com.lucasdss.ftpmusic.app.data.db.GenreMixDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DailyMixRepositoryTest {

    private val customMixDao: CustomMixDao = mockk(relaxed = true)
    private val genreMixDao: GenreMixDao = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val metadataDao: CachedMetadataDao = mockk(relaxed = true)
    private val mixCacheCoordinator: MixCacheCoordinator = mockk(relaxed = true)
    private val repository = DailyMixRepository(
        customMixDao,
        genreMixDao,
        trackDao,
        metadataDao,
        mixCacheCoordinator,
        UnconfinedTestDispatcher(),
    )

    private val today = java.time.LocalDate.now().toString()
    private val yesterday = java.time.LocalDate.now().minusDays(1).toString()

    private fun mixEntity(
        id: Long = 1L,
        name: String = "Rock Mix",
        kind: String = DailyMixRepository.KIND_GENRES,
        genres: String = "Rock",
        decades: String = "",
        artists: String = "",
        includeFavorites: Boolean = false,
        autoCache: Boolean = false,
        isDefault: Boolean = true,
    ) = CustomMixEntity(
        id = id,
        name = name,
        sourceKind = kind,
        genresJson = genres,
        decadesJson = decades,
        artistsJson = artists,
        includeFavoriteArtists = includeFavorites,
        autoCache = autoCache,
        isDefault = isDefault,
    )

    private fun track(id: String) = TrackEntity(id = id, title = "T $id", artist = "A", albumId = "al-$id")

    private fun seeded() {
        coEvery { customMixDao.getState() } returns CustomMixStateEntity(seededAt = 1L)
    }

    // ── Seeding ──────────────────────────────────────────────────────────────

    @Test
    fun `seedIfNeeded seeds top 20 once and marks state`() = runTest {
        coEvery { customMixDao.getState() } returns null
        coEvery { genreMixDao.getTopGenres() } returns (1..25).map {
            CachedGenreEntity(name = "G$it", songCount = 100 - it)
        }
        val inserted = slot<List<CustomMixEntity>>()
        coEvery { customMixDao.seedOnce(capture(inserted), any()) } returns true

        repository.seedIfNeeded()

        assertEquals(20, inserted.captured.size)
        assertEquals("G1 Mix", inserted.captured[0].name)
        assertTrue(inserted.captured.all { it.isDefault })
        coVerify { customMixDao.seedOnce(any(), DailyMixRepository.MAX_MIXES) }
    }

    @Test
    fun `seedIfNeeded is a no-op once seeded`() = runTest {
        coEvery { customMixDao.getState() } returns CustomMixStateEntity(seededAt = 123L)

        repository.seedIfNeeded()

        coVerify(exactly = 0) { customMixDao.seedOnce(any(), any()) }
    }

    @Test
    fun `seedIfNeeded is a no-op with no genres`() = runTest {
        coEvery { customMixDao.getState() } returns null
        coEvery { genreMixDao.getTopGenres() } returns emptyList()

        repository.seedIfNeeded()

        coVerify(exactly = 0) { customMixDao.seedOnce(any(), any()) }
        coVerify(exactly = 0) { customMixDao.upsertState(any()) }
    }

    // ── Mapping / queries ────────────────────────────────────────────────────

    @Test
    fun `getAll maps composite filters to domain`() = runTest {
        coEvery { customMixDao.getAll() } returns listOf(
            mixEntity(id = 1L, genres = "Rock\nJazz", decades = "80s", artists = "ar1"),
            mixEntity(id = 2L, kind = DailyMixRepository.KIND_FAVORITE_ARTISTS, genres = ""),
        )

        val mixes = repository.getAll()

        assertEquals(listOf("Rock", "Jazz"), mixes[0].filters.genres)
        assertEquals(listOf("80s"), mixes[0].filters.decades)
        assertEquals(listOf("ar1"), mixes[0].filters.artistIds)
        assertTrue("legacy favoriteArtists keeps dynamic behavior", mixes[1].filters.includeFavoriteArtists)
    }

    @Test
    fun `allMixGenreNames collects and dedupes every mix dimension`() = runTest {
        coEvery { customMixDao.getAll() } returns listOf(
            mixEntity(id = 1L, genres = "Rock\nJazz", decades = "90s"),
            mixEntity(id = 2L, kind = DailyMixRepository.KIND_MIXED, genres = "Jazz\nBlues", artists = "ar1"),
        )

        assertEquals(listOf("Rock", "Jazz", "Blues"), repository.allMixGenreNames())
    }

    @Test
    fun `missingSourceGenres reports genres no longer in the library`() = runTest {
        coEvery { customMixDao.getAll() } returns listOf(
            mixEntity(id = 1L, genres = "Rock\nGone"),
            mixEntity(id = 2L, genres = "Rock"),
        )
        coEvery { genreMixDao.getTopGenres() } returns listOf(CachedGenreEntity(name = "Rock", songCount = 5))

        assertEquals(mapOf(1L to listOf("Gone")), repository.missingSourceGenres())
    }

    @Test
    fun `hasMixes reflects the recipe count`() = runTest {
        coEvery { customMixDao.count() } returns 3
        assertTrue(repository.hasMixes())
        coEvery { customMixDao.count() } returns 0
        assertTrue(!repository.hasMixes())
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    @Test
    fun `addMix rejects when at capacity`() = runTest {
        coEvery { customMixDao.insertIfUnderCap(any(), DailyMixRepository.MAX_MIXES) } returns -1L

        assertNull(repository.addMix("New", MixFilters(genres = listOf("Rock")), false))
        coVerify { customMixDao.insertIfUnderCap(any(), DailyMixRepository.MAX_MIXES) }
    }

    @Test
    fun `addMix keeps every genre without a cap and stores artist ids`() = runTest {
        val inserted = slot<CustomMixEntity>()
        coEvery { customMixDao.insertIfUnderCap(capture(inserted), any()) } returns 9L
        val genres = (1..12).map { "G$it" }

        val id = repository.addMix(
            "  Big Mix  ",
            MixFilters(genres = genres, artistIds = listOf("ar1", "ar2"), includeFavoriteArtists = true),
            autoCache = true,
        )

        assertEquals(9L, id)
        assertEquals("Big Mix", inserted.captured.name)
        assertEquals(12, DailyMixRepository.decodeNames(inserted.captured.genresJson).size)
        assertEquals(listOf("ar1", "ar2"), DailyMixRepository.decodeNames(inserted.captured.artistsJson))
        assertTrue(inserted.captured.includeFavoriteArtists)
        assertEquals(DailyMixRepository.KIND_MIXED, inserted.captured.sourceKind)
        assertTrue(inserted.captured.autoCache)
    }

    @Test
    fun `addMix rejects a blank name or empty filters`() = runTest {
        assertNull(repository.addMix("   ", MixFilters(genres = listOf("Rock")), false))
        assertNull(repository.addMix("X", MixFilters(), false))
        coVerify(exactly = 0) { customMixDao.insertIfUnderCap(any(), any()) }
    }

    @Test
    fun `addMix drops unknown decades but keeps valid ones`() = runTest {
        val inserted = slot<CustomMixEntity>()
        coEvery { customMixDao.insertIfUnderCap(capture(inserted), any()) } returns 1L

        repository.addMix("Old", MixFilters(decades = listOf("80s", "bogus", "90s")), false)

        assertEquals(listOf("80s", "90s"), DailyMixRepository.decodeNames(inserted.captured.decadesJson))
    }

    @Test
    fun `updateMix filter change regenerates and reconciles cache`() = runTest {
        val old = mixEntity(id = 5L, genres = "Rock")
        coEvery { customMixDao.getById(5L) } returns old
        seeded()
        coEvery { customMixDao.getAll() } returns listOf(old)
        coEvery { trackDao.getMixTrackIdsByGenre(any()) } returns listOf("t1")
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(track("t1"))
        coEvery { genreMixDao.getDailyMix(any(), 5L) } returns null
        coEvery { genreMixDao.getDailyMix(any(), any<Long>()) } returns null

        repository.updateMix(5L, "New Name", MixFilters(genres = listOf("Jazz")), autoCache = false)

        coVerify { customMixDao.update(match { it.name == "New Name" && it.genresJson == "Jazz" }) }
        coVerify { genreMixDao.replaceDailyMix(today, 5L, any()) }
        coVerify { mixCacheCoordinator.onSourceChanged(5L, listOf("t1")) }
    }

    @Test
    fun `updateMix name-only change does not touch generation or cache`() = runTest {
        val old = mixEntity(id = 5L, genres = "Rock")
        coEvery { customMixDao.getById(5L) } returns old

        repository.updateMix(5L, "Renamed", MixFilters(genres = listOf("Rock")), autoCache = false)

        coVerify { customMixDao.update(match { it.name == "Renamed" }) }
        coVerify(exactly = 0) { genreMixDao.replaceDailyMix(any(), any(), any()) }
        coVerify(exactly = 0) { mixCacheCoordinator.onSourceChanged(any(), any()) }
        coVerify(exactly = 0) { mixCacheCoordinator.onDisabled(any()) }
    }

    @Test
    fun `updateMix auto-cache off evicts exclusives`() = runTest {
        val old = mixEntity(id = 5L, genres = "Rock", autoCache = true)
        coEvery { customMixDao.getById(5L) } returns old

        repository.updateMix(5L, "Rock Mix", MixFilters(genres = listOf("Rock")), autoCache = false)

        coVerify { mixCacheCoordinator.onDisabled(5L) }
        coVerify(exactly = 0) { genreMixDao.replaceDailyMix(any(), any(), any()) }
    }

    @Test
    fun `updateMix auto-cache on caches today's tracklist only`() = runTest {
        val old = mixEntity(id = 5L, genres = "Rock", autoCache = false)
        coEvery { customMixDao.getById(5L) } returns old
        coEvery { genreMixDao.getDailyMix(today, 5L) } returns DailyMixEntity(id = 7, date = today, mixId = 5L)
        coEvery { genreMixDao.getDailyMixTrackIds(7) } returns listOf("t1", "t2")

        repository.updateMix(5L, "Rock Mix", MixFilters(genres = listOf("Rock")), autoCache = true)

        coVerify { mixCacheCoordinator.onSourceChanged(5L, listOf("t1", "t2")) }
    }

    @Test
    fun `updateMix auto-cache on regenerates when only yesterday exists`() = runTest {
        val old = mixEntity(id = 5L, genres = "Rock", autoCache = false)
        coEvery { customMixDao.getById(5L) } returns old
        seeded()
        coEvery { customMixDao.getAll() } returns listOf(old)
        coEvery { trackDao.getMixTrackIdsByGenre("Rock") } returns listOf("g1")
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(track("g1"))
        coEvery { genreMixDao.getDailyMix(today, 5L) } returns null
        coEvery { genreMixDao.getDailyMix(yesterday, 5L) } returns
            DailyMixEntity(id = 8, date = yesterday, mixId = 5L)

        repository.updateMix(5L, "Rock Mix", MixFilters(genres = listOf("Rock")), autoCache = true)

        coVerify { genreMixDao.replaceDailyMix(today, 5L, any()) }
        coVerify { mixCacheCoordinator.onSourceChanged(5L, listOf("g1")) }
    }

    @Test
    fun `deleteMix evicts cache ownership then deletes`() = runTest {
        repository.deleteMix(9L)

        coVerify { mixCacheCoordinator.onDeleted(9L) }
        coVerify { customMixDao.deleteById(9L) }
    }

    // ── Pool resolution / generation ─────────────────────────────────────────

    @Test
    fun `generateAll ANDs dimensions and ORs picks within a dimension`() = runTest {
        val mix = mixEntity(
            id = 1L,
            kind = DailyMixRepository.KIND_MIXED,
            genres = "Rock\nJazz",
            decades = "80s",
            artists = "ar1",
        )
        seeded()
        coEvery { customMixDao.getAll() } returns listOf(mix)
        // Genres union: t1, t2, t3. Decade: t2, t3, t4. Artist: t3, t5.
        coEvery { trackDao.getMixTrackIdsByGenre("Rock") } returns listOf("t1", "t2", "t3")
        coEvery { trackDao.getMixTrackIdsByGenre("Jazz") } returns listOf("t2", "t3")
        coEvery { trackDao.getMixTrackIdsByYearRange(1980, 1989) } returns listOf("t2", "t3", "t4")
        coEvery { trackDao.getMixTrackIdsByArtistIds(listOf("ar1")) } returns listOf("t3", "t5")
        coEvery { metadataDao.getArtistsByIds(listOf("ar1")) } returns
            listOf(CachedArtistEntity(id = "ar1", name = "Artist One"))
        coEvery { trackDao.getMixTrackIdsByArtistNames(listOf("Artist One")) } returns listOf("t3")
        coEvery { trackDao.getTracksByIds(listOf("t3")) } returns listOf(track("t3"))
        coEvery { genreMixDao.getDailyMix(any(), any<Long>()) } returns null

        val produced = repository.generateAll(today, manual = false)

        assertEquals(1, produced)
        coVerify { genreMixDao.replaceDailyMix(today, 1L, match { rows -> rows.size == 1 && rows[0].trackId == "t3" }) }
    }

    @Test
    fun `generateAll treats an empty dimension as a wildcard`() = runTest {
        val mix = mixEntity(id = 1L, genres = "Rock")
        seeded()
        coEvery { customMixDao.getAll() } returns listOf(mix)
        coEvery { trackDao.getMixTrackIdsByGenre("Rock") } returns listOf("r1", "r2")
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(track("r1"), track("r2"))
        coEvery { genreMixDao.getDailyMix(any(), any<Long>()) } returns null

        assertEquals(1, repository.generateAll(today, manual = false))
        coVerify(exactly = 0) { trackDao.getMixTrackIdsByYearRange(any(), any()) }
    }

    @Test
    fun `generateAll skips a mix whose intersection is empty`() = runTest {
        val mix = mixEntity(id = 1L, kind = DailyMixRepository.KIND_MIXED, genres = "Rock", decades = "90s")
        seeded()
        coEvery { customMixDao.getAll() } returns listOf(mix)
        coEvery { trackDao.getMixTrackIdsByGenre("Rock") } returns listOf("r1")
        coEvery { trackDao.getMixTrackIdsByYearRange(1990, 1999) } returns listOf("d1")

        assertEquals(0, repository.generateAll(today, manual = false))
        coVerify(exactly = 0) { genreMixDao.replaceDailyMix(any(), any(), any()) }
    }

    @Test
    fun `generateAll includes dynamic favorite artists`() = runTest {
        val mix = mixEntity(
            id = 1L,
            kind = DailyMixRepository.KIND_FAVORITE_ARTISTS,
            genres = "",
            includeFavorites = true,
        )
        seeded()
        coEvery { customMixDao.getAll() } returns listOf(mix)
        coEvery { metadataDao.getAllStarredArtists() } returns listOf(ArtistEntity(id = "ar9", name = "Liked"))
        coEvery { trackDao.getMixTrackIdsByArtistIds(listOf("ar9")) } returns listOf("f1")
        coEvery { trackDao.getMixTrackIdsByArtistNames(listOf("Liked")) } returns emptyList()
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(track("f1"))
        coEvery { genreMixDao.getDailyMix(any(), any<Long>()) } returns null

        assertEquals(1, repository.generateAll(today, manual = false))
        coVerify { genreMixDao.replaceDailyMix(today, 1L, any()) }
    }

    @Test
    fun `generateAll chunks oversized artist id lists`() = runTest {
        val artistIds = (1..1000).map { "ar$it" }
        val mix = mixEntity(
            id = 1L,
            kind = DailyMixRepository.KIND_FAVORITE_ARTISTS,
            genres = "",
            artists = DailyMixRepository.encodeNames(artistIds),
        )
        seeded()
        coEvery { customMixDao.getAll() } returns listOf(mix)
        coEvery { trackDao.getMixTrackIdsByArtistIds(any()) } returns emptyList()
        coEvery { metadataDao.getArtistsByIds(any()) } returns emptyList()
        coEvery { trackDao.getMixTrackIdsByArtistNames(any()) } returns emptyList()

        repository.generateAll(today, manual = false)

        coVerify(exactly = 2) { trackDao.getMixTrackIdsByArtistIds(any()) }
        coVerify(exactly = 2) { metadataDao.getArtistsByIds(any()) }
    }

    @Test
    fun `generateAll persists non-empty mixes and skips empty pools`() = runTest {
        val rock = mixEntity(id = 1L, genres = "Rock")
        val jazz = mixEntity(id = 2L, genres = "Jazz")
        seeded()
        coEvery { customMixDao.getAll() } returns listOf(rock, jazz)
        coEvery { trackDao.getMixTrackIdsByGenre("Rock") } returns listOf("r1", "r2")
        coEvery { trackDao.getMixTrackIdsByGenre("Jazz") } returns emptyList()
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(track("r1"), track("r2"))
        coEvery { genreMixDao.getDailyMix(any(), any<Long>()) } returns null

        val produced = repository.generateAll(today, manual = false)

        assertEquals(1, produced)
        coVerify { genreMixDao.replaceDailyMix(today, 1L, any()) }
        coVerify(exactly = 0) { genreMixDao.replaceDailyMix(today, 2L, any()) }
        coVerify { mixCacheCoordinator.onGenerated(rock, any()) }
    }

    @Test
    fun `generateAll respects shouldRegenerate for a fresh mix`() = runTest {
        val rock = mixEntity(id = 1L, genres = "Rock")
        seeded()
        coEvery { customMixDao.getAll() } returns listOf(rock)
        coEvery { trackDao.getMixTrackIdsByGenre("Rock") } returns listOf("r1")
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(track("r1"))
        coEvery { genreMixDao.getDailyMix(today, 1L) } returns
            DailyMixEntity(id = 3, date = today, mixId = 1L, createdAt = System.currentTimeMillis())
        coEvery { genreMixDao.getDailyMixTrackIds(3) } returns listOf("r1")
        coEvery { trackDao.getTrackPlayCounts(listOf("r1")) } returns emptyList()

        val produced = repository.generateAll(today, manual = false)

        assertEquals(0, produced)
        coVerify(exactly = 0) { genreMixDao.replaceDailyMix(any(), any(), any()) }
    }

    @Test
    fun `generateAll manual forces regeneration`() = runTest {
        val rock = mixEntity(id = 1L, genres = "Rock")
        seeded()
        coEvery { customMixDao.getAll() } returns listOf(rock)
        coEvery { trackDao.getMixTrackIdsByGenre("Rock") } returns listOf("r1")
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(track("r1"))
        coEvery { genreMixDao.getDailyMix(today, 1L) } returns
            DailyMixEntity(id = 3, date = today, mixId = 1L, createdAt = System.currentTimeMillis())
        coEvery { genreMixDao.getDailyMixTrackIds(3) } returns listOf("r1")

        val produced = repository.generateAll(today, manual = true)

        assertEquals(1, produced)
        coVerify { genreMixDao.replaceDailyMix(today, 1L, any()) }
    }

    @Test
    fun `generateAll reports progress through the callback`() = runTest {
        val rock = mixEntity(id = 1L, genres = "Rock")
        seeded()
        coEvery { customMixDao.getAll() } returns listOf(rock)
        coEvery { trackDao.getMixTrackIdsByGenre("Rock") } returns listOf("r1")
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(track("r1"))
        coEvery { genreMixDao.getDailyMix(any(), any<Long>()) } returns null
        val progress = mutableListOf<Pair<Int, Int>>()

        repository.generateAll(today, manual = false) { done, total -> progress.add(done to total) }

        assertEquals(listOf(1 to 1), progress)
    }

    @Test
    fun `generateOne returns empty when the mix no longer exists`() = runTest {
        coEvery { customMixDao.getById(99L) } returns null

        assertEquals(emptyList<String>(), repository.generateOne(99L, manual = true))
    }

    @Test
    fun `explicit-artist-only mixes keep favorites off and use kind artists`() = runTest {
        val inserted = slot<CustomMixEntity>()
        coEvery { customMixDao.insertIfUnderCap(capture(inserted), any()) } returns 1L

        repository.addMix("Solo", MixFilters(artistIds = listOf("ar1")), false)

        assertEquals(DailyMixRepository.KIND_ARTISTS, inserted.captured.sourceKind)
        assertTrue(!inserted.captured.includeFavoriteArtists)
        assertEquals(listOf("ar1"), DailyMixRepository.decodeNames(inserted.captured.artistsJson))
    }

    @Test
    fun `getAll keeps explicit-artist-only mixes favorites-free`() = runTest {
        coEvery { customMixDao.getAll() } returns listOf(
            mixEntity(id = 1L, kind = DailyMixRepository.KIND_ARTISTS, genres = "", artists = "ar1"),
        )

        val mix = repository.getAll().single()

        assertTrue(!mix.filters.includeFavoriteArtists)
        assertEquals(listOf("ar1"), mix.filters.artistIds)
    }

    @Test
    fun `getAll still maps legacy favoriteArtists rows to dynamic favorites`() = runTest {
        coEvery { customMixDao.getAll() } returns listOf(
            mixEntity(id = 1L, kind = DailyMixRepository.KIND_FAVORITE_ARTISTS, genres = "", artists = ""),
        )

        assertTrue(repository.getAll().single().filters.includeFavoriteArtists)
    }

    @Test
    fun `generateAll does not consult favorites for explicit-artist-only mixes`() = runTest {
        val mix = mixEntity(id = 1L, kind = DailyMixRepository.KIND_ARTISTS, genres = "", artists = "ar1")
        seeded()
        coEvery { customMixDao.getAll() } returns listOf(mix)
        coEvery { metadataDao.getArtistsByIds(listOf("ar1")) } returns
            listOf(CachedArtistEntity(id = "ar1", name = "Explicit"))
        coEvery { trackDao.getMixTrackIdsByArtistIds(listOf("ar1")) } returns listOf("x1")
        coEvery { trackDao.getMixTrackIdsByArtistNames(listOf("Explicit")) } returns emptyList()
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(track("x1"))
        coEvery { genreMixDao.getDailyMix(any(), any<Long>()) } returns null

        assertEquals(1, repository.generateAll(today, manual = false))
        coVerify(exactly = 0) { metadataDao.getAllStarredArtists() }
    }

    @Test
    fun `empty pool deletes stale daily tracklists`() = runTest {
        val mix = mixEntity(id = 1L, genres = "Rock")
        seeded()
        coEvery { customMixDao.getAll() } returns listOf(mix)
        coEvery { trackDao.getMixTrackIdsByGenre("Rock") } returns emptyList()

        assertEquals(0, repository.generateAll(today, manual = false))
        coVerify { genreMixDao.deleteDailyMix(today, 1L) }
        coVerify { genreMixDao.deleteDailyMix(yesterday, 1L) }
        coVerify(exactly = 0) { genreMixDao.replaceDailyMix(any(), any(), any()) }
    }

    @Test
    fun `decode and encode round trip`() {
        assertEquals(listOf("a", "b"), DailyMixRepository.decodeNames(DailyMixRepository.encodeNames(listOf("a", "b"))))
        assertEquals(emptyList<String>(), DailyMixRepository.decodeNames("  \n  "))
    }

    @Test
    fun `addMix rejects filters whose only values are invalid`() = runTest {
        assertNull(repository.addMix("X", MixFilters(decades = listOf("bogus")), false))
        coVerify(exactly = 0) { customMixDao.insertIfUnderCap(any(), any()) }
    }

    @Test
    fun `addMix derives decade-only and favorite-only kinds`() = runTest {
        val inserted = slot<CustomMixEntity>()
        coEvery { customMixDao.insertIfUnderCap(capture(inserted), any()) } returns 1L

        repository.addMix("Decades", MixFilters(decades = listOf("80s")), false)
        assertEquals(DailyMixRepository.KIND_DECADES, inserted.captured.sourceKind)

        repository.addMix("Favs", MixFilters(includeFavoriteArtists = true), false)
        assertEquals(DailyMixRepository.KIND_FAVORITE_ARTISTS, inserted.captured.sourceKind)
    }

    @Test
    fun `updateMix ignores a blank name`() = runTest {
        coEvery { customMixDao.getById(2L) } returns mixEntity(id = 2L)

        repository.updateMix(2L, "   ", MixFilters(genres = listOf("Rock")), false)

        coVerify(exactly = 0) { customMixDao.update(any()) }
    }

    @Test
    fun `generateAll treats an unknown stored decade as an empty dimension`() = runTest {
        val mix = mixEntity(id = 1L, genres = "Rock", decades = "bogus")
        seeded()
        coEvery { customMixDao.getAll() } returns listOf(mix)
        coEvery { trackDao.getMixTrackIdsByGenre("Rock") } returns listOf("r1")

        assertEquals(0, repository.generateAll(today, manual = false))
        coVerify(exactly = 0) { genreMixDao.replaceDailyMix(any(), any(), any()) }
    }

    @Test
    fun `generateForMix reuses yesterday's mix for anti-repetition`() = runTest {
        val mix = mixEntity(id = 1L, genres = "Rock")
        seeded()
        coEvery { customMixDao.getAll() } returns listOf(mix)
        coEvery { trackDao.getMixTrackIdsByGenre("Rock") } returns listOf("r1", "r2")
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(track("r1"), track("r2"))
        coEvery { genreMixDao.getDailyMix(today, 1L) } returns null
        coEvery { genreMixDao.getDailyMix(yesterday, 1L) } returns
            DailyMixEntity(id = 8, date = yesterday, mixId = 1L)
        coEvery { genreMixDao.getDailyMixTrackIds(8) } returns listOf("r1")

        assertEquals(1, repository.generateAll(today, manual = true))
        coVerify { genreMixDao.replaceDailyMix(today, 1L, any()) }
    }

    @Test
    fun `generateAll regenerates a 25h-old mix with listens`() = runTest {
        val rock = mixEntity(id = 1L, genres = "Rock")
        seeded()
        coEvery { customMixDao.getAll() } returns listOf(rock)
        coEvery { trackDao.getMixTrackIdsByGenre("Rock") } returns listOf("r1")
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(track("r1"))
        coEvery { genreMixDao.getDailyMix(today, 1L) } returns
            DailyMixEntity(
                id = 3,
                date = today,
                mixId = 1L,
                createdAt = System.currentTimeMillis() - 25L * 60 * 60 * 1000,
            )
        coEvery { genreMixDao.getDailyMixTrackIds(3) } returns listOf("r1")
        coEvery { trackDao.getTrackPlayCounts(listOf("r1")) } returns
            listOf(com.lucasdss.ftpmusic.app.data.db.PlayCountInfo(id = "r1", playCount = 5))

        val produced = repository.generateAll(today, manual = false)

        assertEquals(1, produced)
        coVerify { genreMixDao.replaceDailyMix(today, 1L, any()) }
    }

    @Test
    fun `generateAll unions explicit artists with dynamic favorites`() = runTest {
        val mix = mixEntity(
            id = 1L,
            kind = DailyMixRepository.KIND_MIXED,
            genres = "",
            artists = "ar1",
            includeFavorites = true,
        )
        seeded()
        coEvery { customMixDao.getAll() } returns listOf(mix)
        coEvery { metadataDao.getAllStarredArtists() } returns listOf(ArtistEntity(id = "ar9", name = "Liked"))
        coEvery { metadataDao.getArtistsByIds(listOf("ar1")) } returns
            listOf(CachedArtistEntity(id = "ar1", name = "Explicit"))
        coEvery { trackDao.getMixTrackIdsByArtistIds(listOf("ar1", "ar9")) } returns listOf("x1")
        coEvery { trackDao.getMixTrackIdsByArtistNames(listOf("Explicit", "Liked")) } returns emptyList()
        coEvery { trackDao.getTracksByIds(any()) } returns listOf(track("x1"))
        coEvery { genreMixDao.getDailyMix(any(), any<Long>()) } returns null

        assertEquals(1, repository.generateAll(today, manual = false))
        coVerify { genreMixDao.replaceDailyMix(today, 1L, any()) }
    }
}
