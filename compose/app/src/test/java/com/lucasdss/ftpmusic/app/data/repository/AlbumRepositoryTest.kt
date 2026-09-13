package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AlbumRepositoryTest {

    private val api: SubsonicApi = mockk()
    private val repository = AlbumRepository(api)

    @Test
    fun `getAlbum parses response with tracks`() = runTest {
        val mockResponse = mapOf(
            "subsonic-response" to mapOf(
                "album" to mapOf(
                    "id" to "al-1",
                    "name" to "Test Album",
                    "artist" to "Test Artist",
                    "artistId" to "ar-1",
                    "year" to 2024,
                    "coverArt" to "ca-1",
                    "song" to listOf(
                        mapOf(
                            "id" to "tr-1",
                            "title" to "Track 1",
                            "artist" to "Test Artist",
                            "album" to "Test Album",
                            "albumId" to "al-1",
                            "duration" to 240,
                            "track" to 1,
                        ),
                        mapOf(
                            "id" to "tr-2",
                            "title" to "Track 2",
                            "artist" to "Test Artist",
                            "album" to "Test Album",
                            "albumId" to "al-1",
                            "duration" to 300,
                            "track" to 2,
                        ),
                    ),
                ),
            ),
        )
        coEvery { api.getAlbum("al-1", any()) } returns mockResponse

        val result = repository.getAlbum("al-1", "user", "pass")

        assertNotNull(result)
        assertEquals("al-1", result!!.album.id)
        assertEquals("Test Album", result.album.name)
        assertEquals(2, result.tracks.size)
        assertEquals("tr-1", result.tracks[0].id)
        assertEquals(240, result.tracks[0].duration)
    }

    @Test
    fun `getAlbum returns null when response missing`() = runTest {
        coEvery { api.getAlbum("missing", any()) } returns emptyMap()
        assertNull(repository.getAlbum("missing", "user", "pass"))
    }
}
