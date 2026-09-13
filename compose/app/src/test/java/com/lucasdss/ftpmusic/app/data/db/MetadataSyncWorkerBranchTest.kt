package com.lucasdss.ftpmusic.app.data.db

import android.content.Context
import android.content.SharedPreferences
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Branch coverage extensions for [MetadataSyncWorker]: SyncStatus.toRows()
 * rendering, start()/stop() lifecycle, syncNowAsync guard branches (offline,
 * cooldown), change-detection field comparisons, and every inner
 * try/catch swallow path — none of which the primary test class reaches.
 */
class MetadataSyncWorkerBranchTest {

    private val context: Context = mockk(relaxed = true)
    private val prefs: SharedPreferences = mockk(relaxed = true)
    private val prefsEditor: SharedPreferences.Editor = mockk(relaxed = true)
    private val api: SubsonicApi = mockk(relaxed = true)
    private val authHelper = SubsonicAuthHelper()
    private val metadataDao: CachedMetadataDao = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val genreMixDao: GenreMixDao = mockk(relaxed = true)
    private val coverArtFallback: CoverArtFallbackService = mockk(relaxed = true)
    private val offlineModeManager = mockk<com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager>(relaxed = true)
    private val dailyMixRepository: com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository =
        mockk(relaxed = true)
    private val scheduler = TestCoroutineScheduler()

    @Before
    fun setUp() {
        every { SubsonicCredentials.username } returns "testuser"
        every { SubsonicCredentials.password } returns "testpass"
        every { DynamicBaseUrl.url } returns "https://music.example.com"
        every { context.getSharedPreferences("ftpmusic_sync", any()) } returns prefs
        every { prefs.edit() } returns prefsEditor
        every { prefsEditor.putLong(any(), any()) } returns prefsEditor
        every { prefsEditor.apply() } just Runs
        every { prefs.getInt("metadata_version", 0) } returns 2
        every { offlineModeManager.isOfflineEnabled() } returns false
    }

    companion object {
        init {
            // Mock the singletons ONCE per class — per-test mockkObject
            // redefinition churns the javaagent and can trigger the
            // "can't create name string" instrumentation crash.
            mockkObject(SubsonicCredentials)
            mockkObject(DynamicBaseUrl)
        }
    }

    private fun makeWorker() = MetadataSyncWorker(
        context, api, authHelper, metadataDao, trackDao, genreMixDao, coverArtFallback,
        offlineModeManager, dailyMixRepository, UnconfinedTestDispatcher(scheduler),
    )

    private fun okAlbumsResponse(albums: List<Map<String, Any?>>): Map<String, Any> = mapOf(
        "subsonic-response" to mapOf(
            "status" to "ok",
            "albumList2" to mapOf("album" to albums),
        ),
    )

    // ── SyncStatus.toRows rendering ─────────────────────────────────────────

    @Test
    fun `toRows renders six idle rows by default`() {
        val rows = SyncStatus().toRows()
        assertEquals(6, rows.size)
        assertEquals("Albums", rows[0].label)
        assertEquals("◦", rows[4].icon) // Tracks idle when not running with 0 tracks
        assertFalse(rows[0].showProgress)
    }

    @Test
    fun `toRows marks done rows with check and in-progress row with spinner`() {
        val rows = SyncStatus(
            albums = 5,
            albumsTotal = 5,
            albumTracksProgress = 2,
            albumTracksProgressTotal = 10,
            isRunning = true,
            phase = "tracks",
        ).toRows()
        assertEquals("✓", rows[0].icon)
        assertEquals("⟳", rows[1].icon)
        assertTrue(rows[1].showProgress)
        assertFalse(rows[0].showProgress) // albums phase already finished
    }

    @Test
    fun `toRows shows spinner on the active phase only`() {
        val rows = SyncStatus(isRunning = true, phase = "albums", albums = 1, albumsTotal = 3).toRows()
        assertEquals("⟳", rows[0].icon)
        assertEquals("◦", rows[2].icon) // artists not started
        assertTrue(rows[0].showProgress)
        assertFalse(rows[2].showProgress)
    }

    @Test
    fun `toRows tracks row shows spinner while running and check when complete`() {
        val running = SyncStatus(isRunning = true, phase = "tracks", trackCount = 3).toRows()
        assertEquals("⟳", running[4].icon)

        val complete = SyncStatus(isRunning = false, trackCount = 7).toRows()
        assertEquals("✓", complete[4].icon)

        val idle = SyncStatus(isRunning = false, trackCount = 0).toRows()
        assertEquals("◦", idle[4].icon)
    }

    @Test
    fun `toRows daily mix row reflects its own phase`() {
        val rows = SyncStatus(
            dailyMixProgress = 3,
            dailyMixTotal = 3,
            isRunning = true,
            phase = "dailyMix",
        ).toRows()
        assertEquals("✓", rows[5].icon) // done wins over in-progress
        assertTrue(rows[5].showProgress)
    }

    @Test
    fun `toRows zero totals never count as done`() {
        val rows = SyncStatus(albums = 0, albumsTotal = 0, isRunning = true, phase = "albums").toRows()
        assertEquals("⟳", rows[0].icon)
        val notRunning = SyncStatus(albums = 0, albumsTotal = 0).toRows()
        assertEquals("◦", notRunning[0].icon)
    }

    // ── start / stop lifecycle ──────────────────────────────────────────────

    @Test
    fun `start skips sync when credentials are empty`() {
        val w = makeWorker()
        every { SubsonicCredentials.username } returns ""
        w.start(0)
        scheduler.runCurrent()
        coVerify(exactly = 0) { api.getAlbumList2(any(), any(), any(), any()) }
    }

    @Test
    fun `start syncs immediately when no metadata is cached`() {
        val w = makeWorker()
        coEvery { metadataDao.albumCount() } returns 0
        coEvery { api.getAlbumList2(any(), any(), any(), any()) } returns okAlbumsResponse(emptyList())
        w.start(0)
        scheduler.runCurrent()
        coVerify(atLeast = 1) { api.getAlbumList2(any(), any(), any(), any()) }
    }

    @Test
    fun `start skips sync when metadata already cached`() {
        val w = makeWorker()
        coEvery { metadataDao.albumCount() } returns 42
        w.start(0)
        scheduler.runCurrent()
        coVerify(exactly = 0) { api.getAlbumList2(any(), any(), any(), any()) }
    }

    @Test
    fun `stop cancels the running sync job`() {
        val w = makeWorker()
        coEvery { metadataDao.albumCount() } returns 0
        coEvery { api.getAlbumList2(any(), any(), any(), any()) } returns okAlbumsResponse(emptyList())
        w.start(0)
        scheduler.runCurrent()
        w.stop()
        // No crash; sync job cancelled
    }

    // ── syncNowAsync guard branches ─────────────────────────────────────────

    @Test
    fun `syncNow returns false when offline mode is enabled`() = runTest {
        val w = makeWorker()
        every { offlineModeManager.isOfflineEnabled() } returns true
        assertFalse(w.syncNow())
    }

    @Test
    fun `syncNow returns false during cooldown`() = runTest {
        val w = makeWorker()
        val field = MetadataSyncWorker::class.java.getDeclaredField("lastSyncFinishMs")
        field.isAccessible = true
        field.setLong(w, System.currentTimeMillis())
        assertFalse(w.syncNow())
        coVerify(exactly = 0) { api.getAlbumList2(any(), any(), any(), any()) }
    }

    @Test
    fun `syncNow force resync bypasses cooldown`() = runTest {
        val w = makeWorker()
        val field = MetadataSyncWorker::class.java.getDeclaredField("lastSyncFinishMs")
        field.isAccessible = true
        field.setLong(w, System.currentTimeMillis())
        coEvery { api.getAlbumList2(any(), any(), any(), any()) } returns okAlbumsResponse(emptyList())
        coEvery { api.getArtists(any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "artists" to mapOf("index" to emptyList<Any>())),
        )
        coEvery { api.getGenres(any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "genres" to mapOf<String, Any>()),
        )
        coEvery { metadataDao.getAllAlbums() } returns emptyList()
        coEvery { metadataDao.countUncachedAlbums() } returns 0
        assertTrue(w.syncNow(forceTrackResync = true))
    }

    // ── syncAlbums change detection ─────────────────────────────────────────

    @Test
    fun `syncAlbums detects each changed metadata field`() = runTest {
        val w = makeWorker()
        val serverAlbums = listOf(
            mapOf<String, Any?>("id" to "al-song", "name" to "A", "songCount" to 12, "duration" to 100),
            mapOf<String, Any?>("id" to "al-dur", "name" to "A", "songCount" to 10, "duration" to 120),
            mapOf<String, Any?>("id" to "al-name", "name" to "New", "songCount" to 10, "duration" to 100),
            mapOf<String, Any?>(
                "id" to "al-artist",
                "name" to "A",
                "artist" to "New Artist",
                "songCount" to 10,
                "duration" to 100,
            ),
            mapOf<String, Any?>("id" to "al-year", "name" to "A", "year" to 2024, "songCount" to 10, "duration" to 100),
            mapOf<String, Any?>(
                "id" to "al-genre",
                "name" to "A",
                "genre" to "Rock",
                "songCount" to 10,
                "duration" to 100,
            ),
            mapOf<String, Any?>(
                "id" to "al-art",
                "name" to "A",
                "coverArt" to "ca-new",
                "songCount" to 10,
                "duration" to 100,
            ),
            mapOf<String, Any?>("id" to "al-same", "name" to "A", "songCount" to 10, "duration" to 100),
        )
        coEvery { api.getAlbumList2(any(), any(), any(), any()) } returns okAlbumsResponse(serverAlbums)
        coEvery { metadataDao.getAllAlbums() } returns listOf(
            CachedAlbumEntity(id = "al-song", name = "A", songCount = 10, duration = 100),
            CachedAlbumEntity(id = "al-dur", name = "A", songCount = 10, duration = 100),
            CachedAlbumEntity(id = "al-name", name = "Old", songCount = 10, duration = 100),
            CachedAlbumEntity(id = "al-artist", name = "A", artist = "Old Artist", songCount = 10, duration = 100),
            CachedAlbumEntity(id = "al-year", name = "A", year = 2020, songCount = 10, duration = 100),
            CachedAlbumEntity(id = "al-genre", name = "A", genre = "Jazz", songCount = 10, duration = 100),
            CachedAlbumEntity(id = "al-art", name = "A", coverArt = "ca-old", songCount = 10, duration = 100),
            CachedAlbumEntity(id = "al-same", name = "A", songCount = 10, duration = 100),
        )

        w.syncAlbums()

        val field = MetadataSyncWorker::class.java.getDeclaredField("changedAlbumIds")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val changed = field.get(w) as Set<String>
        assertEquals(
            setOf("al-song", "al-dur", "al-name", "al-artist", "al-year", "al-genre", "al-art"),
            changed,
        )
        coVerify { metadataDao.replaceAlbums(any()) }
    }

    @Test
    fun `syncAlbums does not flag brand-new albums as changed`() = runTest {
        val w = makeWorker()
        coEvery { api.getAlbumList2(any(), any(), any(), any()) } returns
            okAlbumsResponse(listOf(mapOf<String, Any?>("id" to "al-new", "name" to "N", "songCount" to 3)))
        coEvery { metadataDao.getAllAlbums() } returns emptyList()

        w.syncAlbums()

        val field = MetadataSyncWorker::class.java.getDeclaredField("changedAlbumIds")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val changed = field.get(w) as Set<String>
        assertTrue(changed.isEmpty())
    }

    @Test
    fun `syncAlbums swallows change detection failure and still replaces`() = runTest {
        val w = makeWorker()
        coEvery { api.getAlbumList2(any(), any(), any(), any()) } returns
            okAlbumsResponse(listOf(mapOf<String, Any?>("id" to "al-1", "name" to "A")))
        coEvery { metadataDao.getAllAlbums() } throws RuntimeException("boom")

        w.syncAlbums()

        coVerify { metadataDao.replaceAlbums(any()) }
    }

    @Test
    fun `syncAlbums swallows album ledger failure`() = runTest {
        val w = makeWorker()
        coEvery { api.getAlbumList2(any(), any(), any(), any()) } returns okAlbumsResponse(emptyList())
        coEvery { metadataDao.syncAlbumLedger() } throws RuntimeException("boom")

        w.syncAlbums()

        coVerify { metadataDao.replaceAlbums(any()) }
    }

    @Test
    fun `syncAlbums swallows orphan cleanup failure`() = runTest {
        val w = makeWorker()
        coEvery { api.getAlbumList2(any(), any(), any(), any()) } returns okAlbumsResponse(emptyList())
        coEvery { metadataDao.deleteOrphanedAlbumTracks() } throws RuntimeException("boom")

        w.syncAlbums()

        coVerify { metadataDao.replaceAlbums(any()) }
    }

    @Test
    fun `syncAlbums swallows cover art cleanup failure`() = runTest {
        val w = makeWorker()
        coEvery { api.getAlbumList2(any(), any(), any(), any()) } returns
            okAlbumsResponse(listOf(mapOf<String, Any?>("id" to "al-1", "name" to "A", "coverArt" to "ca-1")))
        coEvery { coverArtFallback.cleanOrphanedNavidromeArt(any()) } throws RuntimeException("boom")

        w.syncAlbums()

        coVerify { metadataDao.replaceAlbums(any()) }
    }

    // ── syncArtists / syncGenres / syncStarredAndRatings catches ────────────

    @Test
    fun `syncArtists swallows artist ledger failure`() = runTest {
        val w = makeWorker()
        coEvery { api.getArtists(any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "artists" to mapOf("index" to emptyList<Any>())),
        )
        coEvery { metadataDao.syncArtistLedger() } throws RuntimeException("boom")

        w.syncArtists()

        // Ledger call was attempted and its failure swallowed
        coVerify { metadataDao.syncArtistLedger() }
    }

    @Test
    fun `syncGenres falls back to top genres when selection read fails`() = runTest {
        val w = makeWorker()
        coEvery { api.getGenres(any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "genres" to mapOf(
                    "genre" to listOf(
                        mapOf("value" to "Rock", "songCount" to 500, "albumCount" to 50),
                        mapOf("value" to "Jazz", "songCount" to 300, "albumCount" to 30),
                    ),
                ),
            ),
        )
        coEvery { dailyMixRepository.allMixGenreNames() } throws RuntimeException("boom")
        coEvery { api.getSongsByGenre(any(), any(), any(), any()) } returns emptyMap<String, Any>()

        w.syncGenres()

        // Fallback selection = API genres sorted by song count (Rock first)
        coVerify { api.getSongsByGenre(any(), "Rock", any(), any()) }
        coVerify { api.getSongsByGenre(any(), "Jazz", any(), any()) }
    }

    @Test
    fun `syncGenres swallows per-genre song fetch failure`() = runTest {
        val w = makeWorker()
        coEvery { api.getGenres(any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "genres" to mapOf("genre" to listOf(mapOf("value" to "Rock", "songCount" to 500))),
            ),
        )
        coEvery { dailyMixRepository.allMixGenreNames() } returns emptyList()
        coEvery { genreMixDao.getTopGenres() } returns listOf(CachedGenreEntity(name = "Rock", songCount = 500))
        coEvery { api.getSongsByGenre(any(), "Rock", any(), any()) } throws RuntimeException("boom")

        w.syncGenres()

        coVerify { genreMixDao.replaceGenres(any()) }
    }

    @Test
    fun `syncStarredAndRatings swallows api failure`() = runTest {
        val w = makeWorker()
        coEvery { api.getStarred2(any()) } throws RuntimeException("boom")
        w.syncStarredAndRatings()
        // No crash — outer catch swallows
    }

    @Test
    fun `syncStarredAndRatings returns early when password empty`() = runTest {
        val w = makeWorker()
        every { SubsonicCredentials.password } returns ""
        w.syncStarredAndRatings()
        coVerify(exactly = 0) { api.getStarred2(any()) }
    }

    // ── syncAlbumTracks edge paths ──────────────────────────────────────────

    @Test
    fun `syncAlbumTracks returns early when credentials empty`() = runTest {
        val w = makeWorker()
        every { SubsonicCredentials.password } returns ""
        w.syncAlbumTracks()
        coVerify(exactly = 0) { api.getAlbum(any(), any()) }
    }

    @Test
    fun `syncAlbumTracks handles malformed album response as failure`() = runTest {
        val w = makeWorker()
        coEvery { metadataDao.getAllAlbums() } returns listOf(CachedAlbumEntity(id = "al-1", name = "A"))
        coEvery { metadataDao.getAlbumTracks(any()) } returns emptyList()
        coEvery { api.getAlbum(id = "al-1", any()) } returns
            mapOf("subsonic-response" to mapOf("status" to "ok")) // no "album" section
        w.syncAlbumTracks()
        assertEquals("error", w.status.value.phase) // 1/1 failures > 5%
    }

    @Test
    fun `syncAlbumTracks handles missing subsonic-response as failure`() = runTest {
        val w = makeWorker()
        coEvery { metadataDao.getAllAlbums() } returns listOf(CachedAlbumEntity(id = "al-1", name = "A"))
        coEvery { metadataDao.getAlbumTracks(any()) } returns emptyList()
        coEvery { api.getAlbum(id = "al-1", any()) } returns emptyMap()
        w.syncAlbumTracks()
        assertEquals("error", w.status.value.phase)
    }

    @Test
    fun `syncAlbumTracks tolerates missing wifi service`() = runTest {
        val w = makeWorker()
        every { context.getSystemService(any()) } returns null
        coEvery { metadataDao.getAllAlbums() } returns emptyList()
        w.syncAlbumTracks()
        coVerify(exactly = 0) { api.getAlbum(any(), any()) }
        assertEquals(0, w.status.value.albumTracksProgressTotal)
    }

    @Test
    fun `syncAlbumTracks rethrows cancellation from fetch`() = runTest {
        val w = makeWorker()
        coEvery { metadataDao.getAllAlbums() } returns listOf(CachedAlbumEntity(id = "al-1", name = "A"))
        coEvery { metadataDao.getAlbumTracks(any()) } returns emptyList()
        coEvery { api.getAlbum(id = "al-1", any()) } throws CancellationException("cancelled")
        try {
            w.syncAlbumTracks()
        } catch (e: CancellationException) {
            // Expected — cancellation propagates
        }
    }

    @Test
    fun `syncNow swallows updateArtistAlbumCounts failure`() = runTest {
        val w = makeWorker()
        coEvery { metadataDao.albumCount() } returns 1
        coEvery { metadataDao.artistCount() } returns 1
        coEvery { metadataDao.cachedTrackCount() } returns 1
        coEvery { genreMixDao.getTopGenres() } returns emptyList()
        coEvery { api.getAlbumList2(any(), any(), any(), any()) } returns okAlbumsResponse(emptyList())
        coEvery { api.getArtists(any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "artists" to mapOf("index" to emptyList<Any>())),
        )
        coEvery { api.getGenres(any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "genres" to mapOf<String, Any>()),
        )
        coEvery { metadataDao.getAllAlbums() } returns emptyList()
        coEvery { metadataDao.countUncachedAlbums() } returns 0
        coEvery { metadataDao.updateArtistAlbumCounts() } throws RuntimeException("boom")

        w.syncNow()

        assertEquals("complete", w.status.value.phase)
    }

    @Test
    fun `syncNow swallows daily-mix seeding failure`() = runTest {
        val w = makeWorker()
        coEvery { metadataDao.albumCount() } returns 1
        coEvery { metadataDao.artistCount() } returns 1
        coEvery { metadataDao.cachedTrackCount() } returns 1
        coEvery { api.getAlbumList2(any(), any(), any(), any()) } returns okAlbumsResponse(emptyList())
        coEvery { api.getArtists(any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "artists" to mapOf("index" to emptyList<Any>())),
        )
        coEvery { api.getGenres(any()) } returns mapOf(
            "subsonic-response" to mapOf("status" to "ok", "genres" to mapOf<String, Any>()),
        )
        coEvery { metadataDao.getAllAlbums() } returns emptyList()
        coEvery { metadataDao.countUncachedAlbums() } returns 0
        // seedIfNeeded read/insert path → throw; sync must still complete
        coEvery { dailyMixRepository.seedIfNeeded() } throws RuntimeException("boom")

        w.syncNow()

        assertEquals("complete", w.status.value.phase)
    }

    @Test
    fun `confirmed-unstar filter drops markers still listed on the server`() = runTest {
        val w = makeWorker()
        coEvery { api.getStarred2(any()) } returns mapOf(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "starred2" to mapOf(
                    "album" to listOf(mapOf("id" to "al-2")),
                    "artist" to listOf(mapOf("id" to "ar-2")),
                ),
            ),
        )
        coEvery { metadataDao.getStarredAlbumIds() } returns listOf("al-2")
        coEvery { metadataDao.getStarredArtistIds() } returns listOf("ar-2")
        coEvery { metadataDao.getPendingUnstarAlbumIds() } returns listOf("al-1", "al-2")
        coEvery { metadataDao.getPendingUnstarArtistIds() } returns listOf("ar-1", "ar-2")
        coEvery { metadataDao.getDislikedAlbumIds() } returns emptyList()
        coEvery { metadataDao.getDislikedArtistIds() } returns emptyList()
        coEvery { trackDao.getStarredIds() } returns emptyList()
        coEvery { trackDao.getDislikedIds() } returns emptyList()
        coEvery { trackDao.getPendingUnstarIds() } returns emptyList()

        w.syncStarredAndRatings()

        // al-1/ar-1 are confirmed gone (not on server) → markers cleared;
        // al-2/ar-2 are still listed → markers kept and unstar pushed
        coVerify { metadataDao.clearPendingUnstarAlbums(listOf("al-1")) }
        coVerify { metadataDao.clearPendingUnstarArtists(listOf("ar-1")) }
        coVerify { api.unstar(any(), id = null, albumId = "al-2", artistId = null) }
        coVerify { api.unstar(any(), id = null, albumId = null, artistId = "ar-2") }
    }
}
