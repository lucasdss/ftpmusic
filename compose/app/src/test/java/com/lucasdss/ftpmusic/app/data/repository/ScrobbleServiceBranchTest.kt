package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Branch coverage extensions for [ScrobbleService]: fetchSimilarSongs parsing
 * (every as?/?: branch), offline-mode skipping, API failure swallowing, and
 * the savePlayQueue path — none of which the primary test class reaches.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScrobbleServiceBranchTest {

    private val trackDao: TrackDao = mockk(relaxed = true)
    private val api: SubsonicApi = mockk(relaxed = true)
    private val storage: SecureStorage = mockk(relaxed = true)
    private val offlineModeManager: OfflineModeManager = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var service: ScrobbleService

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        every { offlineModeManager.isOfflineEnabled() } returns false
        service = ScrobbleService(trackDao, api, storage, offlineModeManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── fetchSimilarSongs ───────────────────────────────────────────────────

    @Test
    fun `fetchSimilarSongs parses songs with optional fields`() = runTest(testDispatcher) {
        val response = mapOf(
            "subsonic-response" to mapOf(
                "similarSongs2" to mapOf(
                    "song" to listOf(
                        mapOf<String, Any?>(
                            "id" to "s1",
                            "title" to "Similar",
                            "artist" to "Artist",
                            "album" to "Album",
                            "duration" to 200,
                            "coverArt" to "ca",
                        ),
                    ),
                ),
            ),
        )
        coEvery { api.getSimilarSongs2(any(), id = "t1", count = 10) } returns response

        val songs = service.fetchSimilarSongs("t1")

        assertEquals(1, songs.size)
        assertEquals("s1", songs[0].id)
        assertEquals("Artist", songs[0].artist)
        assertEquals("Album", songs[0].album)
        assertEquals(200, songs[0].duration)
        assertEquals("ca", songs[0].coverArt)
    }

    @Test
    fun `fetchSimilarSongs returns empty when response structure is missing`() = runTest(testDispatcher) {
        coEvery { api.getSimilarSongs2(any(), id = "a", count = 10) } returns emptyMap()
        assertTrue(service.fetchSimilarSongs("a").isEmpty())

        coEvery { api.getSimilarSongs2(any(), id = "b", count = 10) } returns
            mapOf("subsonic-response" to mapOf<String, Any?>())
        assertTrue(service.fetchSimilarSongs("b").isEmpty())

        coEvery { api.getSimilarSongs2(any(), id = "c", count = 10) } returns
            mapOf("subsonic-response" to mapOf("similarSongs2" to "nope"))
        assertTrue(service.fetchSimilarSongs("c").isEmpty())

        coEvery { api.getSimilarSongs2(any(), id = "d", count = 10) } returns
            mapOf("subsonic-response" to mapOf("similarSongs2" to mapOf<String, Any?>()))
        assertTrue(service.fetchSimilarSongs("d").isEmpty())
    }

    @Test
    fun `fetchSimilarSongs filters malformed entries`() = runTest(testDispatcher) {
        val response = mapOf(
            "subsonic-response" to mapOf(
                "similarSongs2" to mapOf(
                    "song" to listOf(
                        "junk",
                        mapOf<String, Any?>("title" to "NoId"),
                        mapOf<String, Any?>("id" to "s1"),
                        mapOf<String, Any?>("id" to "s2", "title" to "OK"),
                    ),
                ),
            ),
        )
        coEvery { api.getSimilarSongs2(any(), id = "t1", count = 10) } returns response

        val songs = service.fetchSimilarSongs("t1")

        assertEquals(listOf("s2"), songs.map { it.id })
    }

    @Test
    fun `fetchSimilarSongs returns empty when API throws`() = runTest(testDispatcher) {
        coEvery { api.getSimilarSongs2(any(), id = "boom", count = 10) } throws RuntimeException("network")
        assertTrue(service.fetchSimilarSongs("boom").isEmpty())
    }

    // ── offline mode + API failure handling ─────────────────────────────────

    @Test
    fun `nowPlaying skips server call when offline`() = runTest(testDispatcher) {
        every { offlineModeManager.isOfflineEnabled() } returns true
        service.nowPlaying("t1")
        coVerify(timeout = 2000, exactly = 0) { api.scrobble(any(), id = "t1", submission = false) }
        coVerify(timeout = 2000) { trackDao.upsert(any()) }
    }

    @Test
    fun `nowPlaying swallows API failure and still upserts`() = runTest(testDispatcher) {
        coEvery { api.scrobble(any(), id = "t1", submission = false) } throws RuntimeException("boom")
        service.nowPlaying("t1")
        coVerify(timeout = 2000) { trackDao.upsert(any()) }
    }

    @Test
    fun `scrobble increments play count and reports submission`() = runTest(testDispatcher) {
        coEvery { trackDao.getTrack("t1") } returns null
        service.scrobble("t1", "Title", "Artist")
        coVerify(timeout = 2000) { api.scrobble(any(), id = "t1", submission = true) }
        coVerify(timeout = 2000) { trackDao.incrementPlayCount("t1", any()) }
        coVerify(timeout = 2000) { trackDao.upsert(match { it.playCount == 0 }) }
    }

    @Test
    fun `scrobble swallows API failure and still updates local state`() = runTest(testDispatcher) {
        coEvery { api.scrobble(any(), id = "t1", submission = true) } throws RuntimeException("boom")
        service.scrobble("t1")
        coVerify(timeout = 2000) { trackDao.incrementPlayCount("t1", any()) }
        coVerify(timeout = 2000) { trackDao.upsert(any()) }
    }

    // ── savePlayQueue ───────────────────────────────────────────────────────

    @Test
    fun `savePlayQueue forwards request to api`() = runTest(testDispatcher) {
        service.savePlayQueue(mapOf("u" to "user"), "t1,t2", "t1", 1000L)
        coVerify(timeout = 2000) { api.savePlayQueue(any(), "t1,t2", "t1", 1000L) }
    }

    @Test
    fun `savePlayQueue swallows API failure`() = runTest(testDispatcher) {
        coEvery { api.savePlayQueue(any(), any(), any(), any()) } throws RuntimeException("boom")
        service.savePlayQueue(mapOf(), "t1", null, null)
        coVerify(timeout = 2000) { api.savePlayQueue(any(), "t1", null, null) }
    }

    // ── trackPlay + authParams edge ─────────────────────────────────────────

    @Test
    fun `trackPlay increments play count`() = runTest(testDispatcher) {
        service.trackPlay("t9")
        coVerify(timeout = 2000) { trackDao.incrementPlayCount("t9", any()) }
    }

    @Test
    fun `authParams falls back to empty strings for missing credentials`() = runTest(testDispatcher) {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns null
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns null
        service.nowPlaying("t1")
        // api.scrobble invoked with auth built from empty strings — no crash
        coVerify(timeout = 2000) { api.scrobble(any(), id = "t1", submission = false) }
    }

    @Test
    fun `ensureTrackRow uses trackId for missing title`() = runTest(testDispatcher) {
        coEvery { trackDao.getTrack("t-no-title") } returns null
        service.nowPlaying("t-no-title", null)
        coVerify(timeout = 2000) { trackDao.upsert(match { it.title == "t-no-title" && it.playCount == 0 }) }
    }

    @Test
    fun `ensureTrackRow preserves play count from existing row`() = runTest(testDispatcher) {
        coEvery { trackDao.getTrack("t-count") } returns TrackEntity(
            id = "t-count",
            title = "T",
            playCount = 42,
        )
        service.nowPlaying("t-count", "T")
        coVerify(timeout = 2000) { trackDao.upsert(match { it.playCount == 42 }) }
    }
}
