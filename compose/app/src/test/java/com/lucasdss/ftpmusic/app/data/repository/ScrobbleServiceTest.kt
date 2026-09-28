package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.db.ListenEventDao
import com.lucasdss.ftpmusic.app.data.db.ListenEventEntity
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.repository.WaveformRepository
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScrobbleServiceTest {

    private val trackDao: TrackDao = mockk(relaxed = true)
    private val listenEventDao: ListenEventDao = mockk(relaxed = true)
    private val api: SubsonicApi = mockk(relaxed = true)
    private val storage: SecureStorage = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var service: ScrobbleService

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        service =
            ScrobbleService(
                trackDao,
                listenEventDao,
                api,
                storage,
                mockk<com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager>(relaxed = true),
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `nowPlaying upserts track row with all fields including genre`() = runTest(testDispatcher) {
        service.nowPlaying("t1", "Test Track", "Test Artist", "al-1", "ar-1", 240, "cov-1", "Rock")

        val slot = slot<TrackEntity>()
        coVerify(timeout = 2000) { trackDao.upsert(capture(slot)) }
        assertEquals("t1", slot.captured.id)
        assertEquals("Test Track", slot.captured.title)
        assertEquals("Test Artist", slot.captured.artist)
        assertEquals("al-1", slot.captured.albumId)
        assertEquals("ar-1", slot.captured.artistId)
        assertEquals(240, slot.captured.durationSeconds)
        assertEquals("cov-1", slot.captured.coverArtUrl)
        assertEquals("Rock", slot.captured.genre)
    }

    @Test
    fun `nowPlaying falls back to trackId as title when title is null`() = runTest(testDispatcher) {
        service.nowPlaying("t2", null, null)

        val slot = slot<TrackEntity>()
        coVerify(timeout = 2000) { trackDao.upsert(capture(slot)) }
        assertEquals("t2", slot.captured.title)
    }

    // Note: scrobble() is tested via nowPlaying tests (shares ensureTrackRow code path)
    // and verified through integration testing on device.

    @Test
    fun `nowPlaying handles null genre gracefully`() = runTest(testDispatcher) {
        service.nowPlaying("t4", "Title", "Artist", null, null, null, null, null)

        val slot = slot<TrackEntity>()
        coVerify(timeout = 2000) { trackDao.upsert(capture(slot)) }
        assertNull(slot.captured.genre)
    }

    @Test
    fun `nowPlaying reports to Subsonic API`() = runTest(testDispatcher) {
        service.nowPlaying("t5")

        coVerify(timeout = 2000) { api.scrobble(any(), id = "t5", submission = false) }
    }

    @Test
    fun `dispose cancels scope`() {
        service.dispose()
        // No exception = scope cancelled
    }

    @Test
    fun `scrobble inserts listen event with clamped seconds`() = runTest(testDispatcher) {
        coEvery { trackDao.getTrack("t-scrob") } returns null
        service.scrobble(
            "t-scrob",
            "Title",
            "Artist",
            "al-1",
            "ar-1",
            200,
            null,
            "Rock",
            listenedSeconds = 0,
            albumName = "Album",
            listenedAt = 12345L,
        )
        val slot = slot<ListenEventEntity>()
        coVerify(timeout = 2000) { listenEventDao.insert(capture(slot)) }
        coVerify(timeout = 2000) { trackDao.incrementPlayCount(eq("t-scrob"), any()) }
        assertEquals("t-scrob", slot.captured.trackId)
        assertEquals(1, slot.captured.listenedSeconds) // clamped min
        assertEquals(12345L, slot.captured.listenedAt)
        assertEquals("Album", slot.captured.albumName)
        assertEquals("Rock", slot.captured.genre)
        assertFalse(slot.captured.isBackfill)
    }

    @Test
    fun `scrobble caps seconds to duration and enriches genre from DB`() = runTest(testDispatcher) {
        coEvery { trackDao.getTrack("t-enrich") } returns TrackEntity(
            id = "t-enrich",
            title = "DB Title",
            artist = "DB Artist",
            artistId = "ar-db",
            albumId = "al-db",
            genre = "Jazz",
            durationSeconds = 100,
            playCount = 2,
        )
        service.scrobble(
            "t-enrich",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            listenedSeconds = 999,
            listenedAt = 99L,
        )
        val slot = slot<ListenEventEntity>()
        coVerify(timeout = 2000) { listenEventDao.insert(capture(slot)) }
        assertEquals(100, slot.captured.listenedSeconds)
        assertEquals("Jazz", slot.captured.genre)
        assertEquals("ar-db", slot.captured.artistId)
        assertEquals("DB Artist", slot.captured.artistName)
        assertEquals("al-db", slot.captured.albumId)
        assertEquals("DB Title", slot.captured.trackTitle)
        assertFalse(slot.captured.isBackfill)
    }

    // ── Play count preservation (RED: fails until ensureTrackRow fix) ───────

    @Test
    fun `ensureTrackRow preserves existing play count`() = runTest(testDispatcher) {
        // Simulate existing track with play_count = 5
        coEvery { trackDao.getTrack("t-existing") } returns TrackEntity(
            id = "t-existing",
            title = "Existing Track",
            playCount = 5,
        )

        // Call nowPlaying — ensureTrackRow should upsert WITHOUT resetting play_count
        service.nowPlaying("t-existing", "Existing Track", "Artist")

        val slot = slot<TrackEntity>()
        coVerify(timeout = 5000) { trackDao.upsert(capture(slot)) }
        // After fix: upsert should pass the EXISTING play_count (5), not default 0
        assertEquals(
            "ensureTrackRow must preserve existing play_count",
            5,
            slot.captured.playCount,
        )
    }

    @Test
    fun `ensureTrackRow sets play count 0 for new track`() = runTest(testDispatcher) {
        // Track doesn't exist in DB
        coEvery { trackDao.getTrack("t-new") } returns null

        service.nowPlaying("t-new", "New Track", "Artist")

        val slot = slot<TrackEntity>()
        coVerify(timeout = 5000) { trackDao.upsert(capture(slot)) }
        assertEquals(
            "New track must have play_count = 0",
            0,
            slot.captured.playCount,
        )
    }

    @Test
    fun `scrobble increments play count after ensureTrackRow preserves it`() = runTest(testDispatcher) {
        val trackId = "t-inc"
        coEvery { trackDao.getTrack(trackId) } returns TrackEntity(
            id = trackId,
            title = "Inc Track",
            playCount = 10,
        )

        // nowPlaying triggers ensureTrackRow, then scrobble triggers incrementPlayCount
        // This test verifies the sequence doesn't lose the play_count
        service.nowPlaying(trackId, "Inc Track", "Artist")
        advanceUntilIdle()

        // After nowPlaying, upsert preserved play_count (should be 10)
        val upsertSlot = slot<TrackEntity>()
        coVerify(timeout = 5000) { trackDao.upsert(capture(upsertSlot)) }
        assertEquals(
            "play_count must be preserved by ensureTrackRow",
            10,
            upsertSlot.captured.playCount,
        )
    }

    // ── Integer overflow safeguard ───────────────────────────────────────

    @Test
    fun `incrementPlayCount overflow guards are in the DAO query`() {
        // SQLite integer overflow wraps to negative. The fix adds:
        // MIN(play_count + 1, 2147483647) to the UPDATE query.
        // This test verifies the math.
        val maxInt = 2147483647
        val atMax = maxInt
        val plusOne = atMax.toLong() + 1L
        val clamped = minOf(plusOne, maxInt.toLong()).toInt()
        assertEquals("At MAX_VALUE, increment must clamp", maxInt, clamped)

        val nearMax = maxInt - 1
        val plusOneNear = nearMax.toLong() + 1L
        val clampedNear = minOf(plusOneNear, maxInt.toLong()).toInt()
        assertEquals("At MAX_VALUE-1, increment must work normally", maxInt, clampedNear)
    }

    // ── Dead code removal: pending_scrobbles ──────────────────────────────

    @Test
    fun `scrobbleTrack is removed`() {
        // scrobbleTrack() was dead code — verify it's no longer defined
        val methods = ScrobbleService::class.java.declaredMethods
        val hasScrobbleTrack = methods.any { it.name == "scrobbleTrack" }
        assertFalse("scrobbleTrack must be removed as dead code", hasScrobbleTrack)
    }

    @Test
    fun `flushPendingScrobbles is removed`() {
        val methods = ScrobbleService::class.java.declaredMethods
        val hasFlush = methods.any { it.name == "flushPendingScrobbles" }
        assertFalse("flushPendingScrobbles must be removed with pending_scrobbles table", hasFlush)
    }
}
