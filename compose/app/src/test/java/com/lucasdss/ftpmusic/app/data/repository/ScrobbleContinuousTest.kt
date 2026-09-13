package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.repository.WaveformRepository
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ScrobbleContinuousTest {

    private val trackDao: TrackDao = mockk(relaxed = true)
    private val api: SubsonicApi = mockk(relaxed = true)
    private val storage: SecureStorage = mockk(relaxed = true)

    private fun createService() = ScrobbleService(
        trackDao,
        api,
        storage,
        mockk<com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager>(relaxed = true),
    )

    @Test
    fun `nowPlaying calls scrobble API with submission false`() = runTest {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "testuser"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "testpass"
        coEvery { api.scrobble(any(), id = "track-1", submission = false) } returns emptyMap()

        val service = createService()
        service.nowPlaying("track-1")

        coVerify(timeout = 2000) {
            api.scrobble(any(), id = "track-1", submission = false)
        }
    }

    @Test
    fun `scrobble calls scrobble API with submission true`() = runTest {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "testuser"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "testpass"
        coEvery { api.scrobble(any(), id = "track-1", submission = true) } returns emptyMap()

        val service = createService()
        service.scrobble("track-1")

        coVerify(timeout = 2000) {
            api.scrobble(any(), id = "track-1", submission = true)
        }
    }

    @Test
    fun `getSimilarSongs calls getSimilarSongs2 with correct params`() = runTest {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "testuser"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "testpass"

        val mockResponse = mapOf<String, Any>(
            "subsonic-response" to mapOf(
                "status" to "ok",
                "similarSongs2" to mapOf(
                    "song" to listOf(
                        mapOf(
                            "id" to "s1",
                            "title" to "Similar Song 1",
                            "artist" to "Artist A",
                            "album" to "Album A",
                            "duration" to 240,
                            "coverArt" to "cov-1",
                        ),
                        mapOf(
                            "id" to "s2",
                            "title" to "Similar Song 2",
                            "artist" to "Artist B",
                            "album" to "Album B",
                            "duration" to 180,
                            "coverArt" to "cov-2",
                        ),
                    ),
                ),
            ),
        )

        coEvery { api.getSimilarSongs2(any(), id = "track-1", count = 10) } returns mockResponse

        val service = createService()
        val result = service.fetchSimilarSongs("track-1")

        assertEquals(2, result.size)
        assertEquals("s1", result[0].id)
        assertEquals("Similar Song 1", result[0].title)
        assertEquals("Artist A", result[0].artist)
        assertEquals(240, result[0].duration)
        assertEquals("s2", result[1].id)
        assertEquals("Similar Song 2", result[1].title)
        assertEquals("Artist B", result[1].artist)
        assertEquals(180, result[1].duration)

        coVerify { api.getSimilarSongs2(any(), id = "track-1", count = 10) }
    }

    @Test
    fun `ScrobbleService handles API errors gracefully`() = runTest {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "testuser"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "testpass"
        coEvery { api.scrobble(any(), id = "track-1", submission = any()) } throws RuntimeException("network error")

        val service = createService()
        // These should not throw
        service.nowPlaying("track-1")
        service.scrobble("track-1")

        // Verify no exception thrown (if we get here, it passed)
        coVerify(exactly = 2, timeout = 2000) {
            api.scrobble(any(), id = "track-1", submission = any())
        }
    }

    @Test
    fun `fetchSimilarSongs returns empty list on API error`() = runTest {
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "testuser"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "testpass"
        coEvery { api.getSimilarSongs2(any(), id = "track-1", count = 10) } throws RuntimeException("network error")

        val service = createService()
        val result = service.fetchSimilarSongs("track-1")

        assertTrue(result.isEmpty())
    }
}
