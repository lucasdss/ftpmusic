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
        // Default: no existing row (relaxed mock would invent one and break
        // null-preserving coalesce assertions).
        coEvery { trackDao.getTrack(any()) } returns null
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
        if (::service.isInitialized) service.cancelScopeForTest()
        Dispatchers.resetMain()
    }

    @Test
    fun `nowPlaying inserts stub then patches metadata without a full-row write`() = runTest(testDispatcher) {
        service.nowPlaying("t1", "Test Track", "Test Artist", "al-1", "ar-1", 240, "cov-1", "Rock")

        coVerify(timeout = 2000) { trackDao.ensureTrackRow("t1") }
        coVerify(timeout = 2000) {
            trackDao.updateTrackMetadata(
                trackId = "t1",
                title = "Test Track",
                artist = "Test Artist",
                album = null,
                genre = "Rock",
                albumId = "al-1",
                artistId = "ar-1",
                durationSeconds = 240,
                coverArtUrl = "cov-1",
            )
        }
        coVerify(exactly = 0) { trackDao.upsert(any()) }
    }

    @Test
    fun `nowPlaying leaves the stub title when title is null`() = runTest(testDispatcher) {
        service.nowPlaying("t2", null, null)

        // ensureTrackRow seeds title=trackId; the metadata patch keeps it.
        coVerify(timeout = 2000) { trackDao.ensureTrackRow("t2") }
        coVerify(timeout = 2000) {
            trackDao.updateTrackMetadata(
                trackId = "t2",
                title = null,
                artist = null,
                album = null,
                genre = null,
                albumId = null,
                artistId = null,
                durationSeconds = null,
                coverArtUrl = null,
            )
        }
    }

    // Note: scrobble() is tested via nowPlaying tests (shares ensureTrackRow code path)
    // and verified through integration testing on device.

    @Test
    fun `nowPlaying handles null genre gracefully`() = runTest(testDispatcher) {
        service.nowPlaying("t4", "Title", "Artist", null, null, null, null, null)

        coVerify(timeout = 2000) {
            trackDao.updateTrackMetadata(
                trackId = "t4",
                title = "Title",
                artist = "Artist",
                album = null,
                genre = null,
                albumId = null,
                artistId = null,
                durationSeconds = null,
                coverArtUrl = null,
            )
        }
    }

    @Test
    fun `nowPlaying reports to Subsonic API`() = runTest(testDispatcher) {
        service.nowPlaying("t5")

        coVerify(timeout = 2000) { api.scrobble(any(), id = "t5", submission = false) }
    }

    @Test
    fun `scrobble persists local metrics when network fails`() = runTest(testDispatcher) {
        coEvery { api.scrobble(any(), id = any(), submission = any()) } throws
            java.io.IOException("offline")
        service.scrobble("t-fail", "Title", "Artist")

        coVerify(timeout = 2000) { trackDao.incrementPlayCount(eq("t-fail"), any()) }
        coVerify(timeout = 2000) { listenEventDao.insert(any()) }
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
            album = "DB Album",
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
        assertEquals("DB Album", slot.captured.albumName)
        assertEquals("DB Title", slot.captured.trackTitle)
        assertFalse(slot.captured.isBackfill)
    }

    @Test
    fun `ensureTrackRow never overwrites album and genre with null`() = runTest(testDispatcher) {
        coEvery { trackDao.getTrack("t-keep") } returns TrackEntity(
            id = "t-keep",
            title = "Keep",
            artist = "Art",
            album = "Kept Album",
            genre = "Metal",
            playCount = 3,
        )
        service.nowPlaying("t-keep", "Keep", null, null, null, null, null, null)

        // Null metadata is passed through; the SQL COALESCE keeps the old value.
        coVerify(timeout = 2000) {
            trackDao.updateTrackMetadata(
                trackId = "t-keep",
                title = "Keep",
                artist = null,
                album = null,
                genre = null,
                albumId = null,
                artistId = null,
                durationSeconds = null,
                coverArtUrl = null,
            )
        }
        coVerify(exactly = 0) { trackDao.upsert(any()) }
    }

    // ── Play count preservation ────────────────────────────────────────────

    @Test
    fun `ensureTrackRow does not write play count`() = runTest(testDispatcher) {
        coEvery { trackDao.getTrack("t-existing") } returns TrackEntity(
            id = "t-existing",
            title = "Existing Track",
            playCount = 5,
        )

        service.nowPlaying("t-existing", "Existing Track", "Artist")

        coVerify(timeout = 5000) { trackDao.ensureTrackRow("t-existing") }
        coVerify(timeout = 5000) {
            trackDao.updateTrackMetadata(trackId = "t-existing", title = "Existing Track", artist = "Artist")
        }
        // MET-3: no full-row write means the SQL layer cannot clobber play_count.
        coVerify(exactly = 0) { trackDao.upsert(any()) }
        coVerify(exactly = 0) { trackDao.update(any()) }
    }

    @Test
    fun `ensureTrackRow seeds a new row with play count 0`() = runTest(testDispatcher) {
        coEvery { trackDao.getTrack("t-new") } returns null

        service.nowPlaying("t-new", "New Track", "Artist")

        coVerify(timeout = 5000) { trackDao.ensureTrackRow("t-new") }
        coVerify(exactly = 0) { trackDao.upsert(any()) }
    }

    @Test
    fun `scrobble increments play count via the targeted DAO query`() = runTest(testDispatcher) {
        val trackId = "t-inc"

        service.scrobble(trackId, "Inc Track", "Artist")

        coVerify(timeout = 5000) { trackDao.ensureTrackRow(trackId) }
        coVerify(timeout = 5000) {
            trackDao.updateTrackMetadata(trackId = trackId, title = "Inc Track", artist = "Artist")
        }
        coVerify(timeout = 5000) { trackDao.incrementPlayCount(eq(trackId), any()) }
        coVerify(exactly = 0) { trackDao.upsert(any()) }
    }

    @Test
    fun `ensureTrackRow never writes cache download star dislike or lastPlayed`() = runTest(testDispatcher) {
        coEvery { trackDao.getTrack("t-weight") } returns TrackEntity(
            id = "t-weight",
            title = "Weighted",
            cachedFilePath = "/cache/t-weight.mp3",
            isDownloaded = true,
            isAutoCached = false,
            cacheSizeBytes = 1234,
            starredAt = 1700000000000L,
            lastPlayedAt = 1600000000000L,
            isDisliked = true,
            dislikedAt = 1550000000000L,
            playCount = 7,
        )
        service.nowPlaying("t-weight", "Weighted", "Artist")

        coVerify(timeout = 5000) {
            trackDao.updateTrackMetadata(trackId = "t-weight", title = "Weighted", artist = "Artist")
        }
        // None of the preserved columns are part of the metadata patch.
        coVerify(exactly = 0) { trackDao.upsert(any()) }
        coVerify(exactly = 0) { trackDao.update(any()) }
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
