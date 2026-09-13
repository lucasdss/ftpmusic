package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Branch coverage for the response-parsing logic in [AlbumRepository] and
 * [SearchRepository]: every `as?` cast, `?:` fallback and mapNotNull filter
 * in both directions (present/absent, valid/invalid).
 */
class RepositoriesTest {

    private val api: SubsonicApi = mockk()
    private val albumRepository = AlbumRepository(api)
    private val searchRepository = SearchRepository(api)

    // ── AlbumRepository.getAlbum ───────────────────────────────────────────

    @Test
    fun `getAlbum parses every optional field`() = runTest {
        val response = mapOf(
            "subsonic-response" to mapOf(
                "album" to mapOf(
                    "id" to "al-1", "name" to "Album", "artist" to "Artist",
                    "artistId" to "ar-1", "year" to 1999, "coverArt" to "ca-1",
                    "songCount" to 1, "duration" to 300, "genre" to "Rock",
                    "song" to listOf(
                        mapOf<String, Any?>(
                            "id" to "tr-1", "title" to "T1", "artist" to "A",
                            "artistId" to "ar-1", "album" to "Album", "albumId" to "al-1",
                            "duration" to 200, "track" to 3, "bitrate" to 320,
                            "suffix" to "mp3", "contentType" to "audio/mpeg",
                            "path" to "/x.mp3", "coverArt" to "ca-2", "size" to 12345,
                        ),
                    ),
                ),
            ),
        )
        coEvery { api.getAlbum("al-1", any()) } returns response

        val result = albumRepository.getAlbum("al-1", "u", "p")!!

        assertEquals("Artist", result.album.artist)
        assertEquals("ar-1", result.album.artistId)
        assertEquals(1999, result.album.year)
        assertEquals("ca-1", result.album.coverArt)
        assertEquals(1, result.album.songCount)
        assertEquals(300, result.album.duration)
        assertEquals("Rock", result.album.genre)
        val t = result.tracks[0]
        assertEquals("A", t.artist)
        assertEquals("ar-1", t.artistId)
        assertEquals("Album", t.album)
        assertEquals("al-1", t.albumId)
        assertEquals(200, t.duration)
        assertEquals(3, t.trackNumber)
        assertEquals(320, t.bitrate)
        assertEquals("mp3", t.suffix)
        assertEquals("audio/mpeg", t.contentType)
        assertEquals("/x.mp3", t.path)
        assertEquals("ca-2", t.coverArt)
        assertEquals(12345, t.sizeBytes)
    }

    @Test
    fun `getAlbum returns null when subsonic-response is missing or not a map`() = runTest {
        coEvery { api.getAlbum("a", any()) } returns emptyMap()
        assertNull(albumRepository.getAlbum("a", "u", "p"))
        coEvery { api.getAlbum("b", any()) } returns mapOf("subsonic-response" to "nope")
        assertNull(albumRepository.getAlbum("b", "u", "p"))
    }

    @Test
    fun `getAlbum returns null when album is missing or not a map`() = runTest {
        coEvery { api.getAlbum("a", any()) } returns mapOf("subsonic-response" to mapOf<String, Any?>())
        assertNull(albumRepository.getAlbum("a", "u", "p"))
        coEvery { api.getAlbum("b", any()) } returns
            mapOf("subsonic-response" to mapOf("album" to "not-a-map"))
        assertNull(albumRepository.getAlbum("b", "u", "p"))
    }

    @Test
    fun `getAlbum returns null when id or name is missing`() = runTest {
        coEvery { api.getAlbum("a", any()) } returns mapOf(
            "subsonic-response" to mapOf("album" to mapOf<String, Any?>("name" to "NoId")),
        )
        assertNull(albumRepository.getAlbum("a", "u", "p"))
        coEvery { api.getAlbum("b", any()) } returns mapOf(
            "subsonic-response" to mapOf("album" to mapOf<String, Any?>("id" to "NoName")),
        )
        assertNull(albumRepository.getAlbum("b", "u", "p"))
    }

    @Test
    fun `getAlbum tolerates wrong-typed optional values`() = runTest {
        val response = mapOf(
            "subsonic-response" to mapOf(
                "album" to mapOf(
                    "id" to "al-1",
                    "name" to "A",
                    "year" to "nineteen-ninety",
                    "songCount" to "many",
                    "duration" to "long",
                    "song" to listOf(
                        mapOf<String, Any?>(
                            "id" to "tr-1",
                            "title" to "T",
                            "duration" to "slow",
                            "track" to "three",
                            "size" to "big",
                        ),
                    ),
                ),
            ),
        )
        coEvery { api.getAlbum("al-1", any()) } returns response

        val result = albumRepository.getAlbum("al-1", "u", "p")!!

        assertNull(result.album.year)
        assertNull(result.album.songCount)
        assertNull(result.album.duration)
        assertNull(result.tracks[0].duration)
        assertNull(result.tracks[0].trackNumber)
        assertNull(result.tracks[0].sizeBytes)
    }

    @Test
    fun `getAlbum returns empty tracks when song is absent or not a list`() = runTest {
        val base = mapOf(
            "subsonic-response" to mapOf(
                "album" to mapOf("id" to "al-1", "name" to "A"),
            ),
        )
        coEvery { api.getAlbum("a", any()) } returns base
        assertEquals(0, albumRepository.getAlbum("a", "u", "p")!!.tracks.size)

        coEvery { api.getAlbum("b", any()) } returns mapOf(
            "subsonic-response" to mapOf("album" to mapOf("id" to "al-1", "name" to "A", "song" to "nope")),
        )
        assertEquals(0, albumRepository.getAlbum("b", "u", "p")!!.tracks.size)
    }

    @Test
    fun `getAlbum filters malformed song entries`() = runTest {
        val response = mapOf(
            "subsonic-response" to mapOf(
                "album" to mapOf(
                    "id" to "al-1",
                    "name" to "A",
                    "song" to listOf(
                        "not-a-map",
                        mapOf<String, Any?>("title" to "NoId"),
                        mapOf<String, Any?>("id" to "tr-2"),
                        mapOf<String, Any?>("id" to "tr-3", "title" to "T3"),
                    ),
                ),
            ),
        )
        coEvery { api.getAlbum("al-1", any()) } returns response

        val tracks = albumRepository.getAlbum("al-1", "u", "p")!!.tracks

        assertEquals(listOf("tr-3"), tracks.map { it.id })
    }

    // ── SearchRepository.search ────────────────────────────────────────────

    @Test
    fun `search returns empty for short queries without calling api`() = runTest {
        val result = searchRepository.search("a", "u", "p")
        assertTrue(result.isEmpty)
        coEvery { api.search3(any(), any(), any(), any(), any(), any(), any(), any()) } returns mapOf()
        val result2 = searchRepository.search("x", "u", "p")
        assertTrue(result2.isEmpty)
    }

    @Test
    fun `search parses all four result categories with all fields`() = runTest {
        val response = mapOf(
            "subsonic-response" to mapOf(
                "searchResult3" to mapOf(
                    "artist" to listOf(
                        mapOf<String, Any?>("id" to "ar-1", "name" to "Artist", "coverArt" to "ca", "albumCount" to 4),
                    ),
                    "album" to listOf(
                        mapOf<String, Any?>(
                            "id" to "al-1",
                            "name" to "Album",
                            "artist" to "A",
                            "artistId" to "ar-1",
                            "year" to 2020,
                            "coverArt" to "ca",
                            "userRating" to 4,
                        ),
                    ),
                    "song" to listOf(
                        mapOf<String, Any?>(
                            "id" to "tr-1",
                            "title" to "T",
                            "artist" to "A",
                            "artistId" to "ar-1",
                            "album" to "Album",
                            "albumId" to "al-1",
                            "duration" to 100,
                            "coverArt" to "ca",
                        ),
                    ),
                    "playlist" to listOf(
                        mapOf<String, Any?>(
                            "id" to "pl-1",
                            "name" to "P",
                            "comment" to "c",
                            "songCount" to 5,
                            "duration" to 500,
                            "coverArt" to "ca",
                        ),
                    ),
                ),
            ),
        )
        coEvery { api.search3("que", 20, 20, 20, 0, 0, 0, any()) } returns response

        val r = searchRepository.search("que", "u", "p")

        assertEquals(1, r.artists.size)
        assertEquals("ar-1", r.artists[0].id)
        assertEquals(4, r.artists[0].albumCount)
        assertEquals(1, r.albums.size)
        assertEquals(2020, r.albums[0].year)
        assertEquals(4, r.albums[0].rating)
        assertEquals(1, r.tracks.size)
        assertEquals(100, r.tracks[0].duration)
        assertEquals(1, r.playlists.size)
        assertEquals(5, r.playlists[0].songCount)
        assertEquals(500, r.playlists[0].duration)
    }

    @Test
    fun `search passes through offset and count parameters`() = runTest {
        coEvery { api.search3(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            mapOf("subsonic-response" to mapOf("searchResult3" to mapOf<String, Any?>()))
        val r = searchRepository.search(
            "query", "u", "p",
            artistCount = 3, albumCount = 4, songCount = 5,
            artistOffset = 1, albumOffset = 2, songOffset = 3,
        )
        assertTrue(r.isEmpty)
        coVerifyOrder {
            api.search3("query", 3, 4, 5, 1, 2, 3, any())
        }
    }

    @Test
    fun `search returns empty when response structure is missing`() = runTest {
        coEvery { api.search3(any(), any(), any(), any(), any(), any(), any(), any()) } returns emptyMap()
        assertTrue(searchRepository.search("q1", "u", "p").isEmpty)
        coEvery { api.search3(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            mapOf("subsonic-response" to mapOf<String, Any?>())
        assertTrue(searchRepository.search("q2", "u", "p").isEmpty)
        coEvery { api.search3(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            mapOf("subsonic-response" to mapOf("searchResult3" to "nope"))
        assertTrue(searchRepository.search("q3", "u", "p").isEmpty)
    }

    @Test
    fun `search returns empty lists when categories are absent`() = runTest {
        coEvery { api.search3(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            mapOf("subsonic-response" to mapOf("searchResult3" to mapOf<String, Any?>()))
        val r = searchRepository.search("q1", "u", "p")
        assertTrue(r.artists.isEmpty())
        assertTrue(r.albums.isEmpty())
        assertTrue(r.tracks.isEmpty())
        assertTrue(r.playlists.isEmpty())
    }

    @Test
    fun `search filters malformed entries in every category`() = runTest {
        val response = mapOf(
            "subsonic-response" to mapOf(
                "searchResult3" to mapOf(
                    "artist" to listOf(
                        "junk",
                        mapOf<String, Any?>("name" to "NoId"),
                        mapOf<String, Any?>("id" to "ar-1"),
                        mapOf<String, Any?>("id" to "ar-2", "name" to "OK"),
                    ),
                    "album" to listOf(
                        mapOf<String, Any?>("id" to "al-1", "name" to "OK"),
                        mapOf<String, Any?>("name" to "NoId"),
                    ),
                    "song" to listOf(
                        mapOf<String, Any?>("title" to "NoId"),
                        mapOf<String, Any?>("id" to "tr-1"),
                        mapOf<String, Any?>("id" to "tr-2", "title" to "T2"),
                    ),
                    "playlist" to listOf(
                        "junk",
                        mapOf<String, Any?>("id" to "pl-1", "name" to "P"),
                        mapOf<String, Any?>("name" to "NoId"),
                        mapOf<String, Any?>("id" to "pl-2", "name" to "P2", "songCount" to "many"),
                    ),
                ),
            ),
        )
        coEvery { api.search3(any(), any(), any(), any(), any(), any(), any(), any()) } returns response

        val r = searchRepository.search("q1", "u", "p")

        assertEquals(listOf("ar-2"), r.artists.map { it.id })
        assertEquals(listOf("al-1"), r.albums.map { it.id })
        assertEquals(listOf("tr-2"), r.tracks.map { it.id })
        assertEquals(listOf("pl-1", "pl-2"), r.playlists.map { it.id })
        assertEquals(0, r.playlists[1].songCount)
    }
}
