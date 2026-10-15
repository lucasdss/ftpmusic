package com.lucasdss.ftpmusic.app.data.network

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for last.fm similar-artist JSON parsing.
 * Uses Robolectric so the real org.json implementation is available.
 */
@RunWith(RobolectricTestRunner::class)
class LastFmServiceTest {

    private val service = LastFmServiceTestHelper.service

    @Test
    fun `parses similar artists with name mbid and match`() {
        val json = """{"similarartists":{"artist":[
            {"name":"Pink Floyd","mbid":"83d91898-7763-47d7-b03b-b92132375c47","match":"0.9"},
            {"name":"Led Zeppelin","mbid":"678d88b2-87b0-403b-b1d3-288b7d9cf2ee","match":"0.8"}
        ]}}"""
        val result = service.parseSimilarArtists(json)
        assertEquals(2, result.size)
        assertEquals("Pink Floyd", result[0].name)
        assertEquals("83d91898-7763-47d7-b03b-b92132375c47", result[0].mbid)
        assertEquals(0.9, result[0].match!!, 0.001)
        assertEquals("Led Zeppelin", result[1].name)
    }

    @Test
    fun `handles missing mbid`() {
        val json = """{"similarartists":{"artist":[{"name":"Unknown","match":"0.5"}]}}"""
        val result = service.parseSimilarArtists(json)
        assertEquals(1, result.size)
        assertNull(result[0].mbid)
        assertEquals(0.5, result[0].match!!, 0.001)
    }

    @Test
    fun `handles missing match`() {
        val json = """{"similarartists":{"artist":[{"name":"NoMatch"}]}}"""
        val result = service.parseSimilarArtists(json)
        assertEquals(1, result.size)
        assertNull(result[0].match)
    }

    @Test
    fun `returns empty on error response`() {
        val json = """{"error":{"code":6,"message":"The artist you supplied could not be found"}}"""
        assertTrue(service.parseSimilarArtists(json).isEmpty())
    }

    @Test
    fun `returns empty on malformed json`() {
        assertTrue(service.parseSimilarArtists("not json").isEmpty())
        assertTrue(service.parseSimilarArtists("").isEmpty())
    }

    @Test
    fun `toStoredJson round-trips with parseStoredJson`() {
        val input = listOf(
            LastFmService.SimilarArtist("Pink Floyd", "83d91898-7763-47d7-b03b-b92132375c47", 0.9),
            LastFmService.SimilarArtist("Unknown", null, null),
        )
        val stored = service.toStoredJson(input)
        val parsed = service.parseStoredJson(stored)

        assertEquals(2, parsed.size)
        assertEquals("Pink Floyd", parsed[0].name)
        assertEquals("83d91898-7763-47d7-b03b-b92132375c47", parsed[0].mbid)
        assertEquals(0.9, parsed[0].match!!, 0.001)
        assertEquals("Unknown", parsed[1].name)
        assertNull(parsed[1].mbid)
        assertNull(parsed[1].match)
    }

    @Test
    fun `toStoredJson escapes special characters in names`() {
        val input = listOf(LastFmService.SimilarArtist("AC/DC \"Live\" \\2024", "mb-1", 0.8))
        val stored = service.toStoredJson(input)
        val parsed = service.parseStoredJson(stored)

        assertEquals(1, parsed.size)
        assertEquals("AC/DC \"Live\" \\2024", parsed[0].name)
        assertEquals(0.8, parsed[0].match!!, 0.001)
    }

    @Test
    fun `parseSimilarArtists skips blank names and empty mbid`() {
        val json = """{"similarartists":{"artist":[
            {"name":"","mbid":"x","match":"0.1"},
            {"name":"Ok","mbid":"","match":null}
        ]}}"""
        val result = service.parseSimilarArtists(json)
        assertEquals(1, result.size)
        assertEquals("Ok", result[0].name)
        assertNull(result[0].mbid)
    }

    @Test
    fun `currentApiKey reads live from storage`() {
        val storage = io.mockk.mockk<com.lucasdss.ftpmusic.app.data.security.SecureStorage>(relaxed = true)
        io.mockk.every {
            storage.get(com.lucasdss.ftpmusic.app.data.security.SecureStorage.KEY_LASTFM_API_KEY)
        } returns "  key1  "
        val svc = LastFmService(storage)
        assertEquals("key1", svc.currentApiKey())
        io.mockk.every {
            storage.get(com.lucasdss.ftpmusic.app.data.security.SecureStorage.KEY_LASTFM_API_KEY)
        } returns null
        assertEquals("", svc.currentApiKey())
    }

    @Test
    fun `fetchSimilarArtists short-circuits without key`() = kotlinx.coroutines.test.runTest {
        val storage = io.mockk.mockk<com.lucasdss.ftpmusic.app.data.security.SecureStorage>(relaxed = true)
        io.mockk.every {
            storage.get(com.lucasdss.ftpmusic.app.data.security.SecureStorage.KEY_LASTFM_API_KEY)
        } returns ""
        val svc = LastFmService(storage)
        assertTrue(svc.fetchSimilarArtists("X").isEmpty())
    }

    @Test
    fun `parseArtistSearch extracts names`() {
        val json = """{"results":{"artistmatches":{"artist":[
            {"name":"Radiohead","mbid":"a1","listeners":"100","image":[
              {"#text":"https://cdn/s.jpg","size":"small"},
              {"#text":"https://cdn/xl.jpg","size":"extralarge"}
            ]},
            {"name":"Radio","mbid":"","listeners":"10"}
        ]}}}"""
        val hits = service.parseArtistSearch(json, limit = 8)
        assertEquals(2, hits.size)
        assertEquals("Radiohead", hits[0].name)
        assertEquals("a1", hits[0].mbid)
        assertEquals(100L, hits[0].listeners)
        assertEquals("https://cdn/xl.jpg", hits[0].imageUrl)
        assertNull(hits[1].mbid)
        assertNull(hits[1].imageUrl)
    }

    @Test
    fun `parseTagTopArtists extracts names`() {
        val json = """{"topartists":{"artist":[
            {"name":"Brian Eno","mbid":"b1"},
            {"name":"Aphex Twin","mbid":"b2"}
        ]}}"""
        val hits = service.parseTagTopArtists(json, limit = 5)
        assertEquals(2, hits.size)
        assertEquals("Brian Eno", hits[0].name)
    }

    @Test
    fun `searchArtists short-circuits without key`() = kotlinx.coroutines.test.runTest {
        val storage = io.mockk.mockk<com.lucasdss.ftpmusic.app.data.security.SecureStorage>(relaxed = true)
        io.mockk.every {
            storage.get(com.lucasdss.ftpmusic.app.data.security.SecureStorage.KEY_LASTFM_API_KEY)
        } returns ""
        val svc = LastFmService(storage)
        assertTrue(svc.searchArtists("radio").isEmpty())
        assertTrue(svc.fetchTagTopArtists("chill").isEmpty())
    }

    @Test
    fun `parseArtistInfoBio strips html and prefers summary`() {
        val json = """{"artist":{"name":"X","bio":{
            "summary":"<p>American <b>hard rock</b> band.</p> <a href=\"https://www.last.fm\">Read more</a>",
            "content":"Longer unused"
        }}}"""
        val bio = service.parseArtistInfoBio(json, maxChars = 200)
        assertEquals("American hard rock band. Read more", bio)
    }

    @Test
    fun `parseArtistInfoBio falls back to content and respects maxChars`() {
        val json = """{"artist":{"bio":{"summary":"","content":"<p>One two three four five</p>"}}}"""
        val bio = service.parseArtistInfoBio(json, maxChars = 7)
        assertEquals("One two", bio)
    }

    @Test
    fun `stripHtml decodes entities`() {
        assertEquals("A & B's \"x\"", service.stripHtml("A &amp; B&#39;s &quot;x&quot;"))
    }

    @Test
    fun `parseArtistInfoBio returns null on error payload`() {
        val json = """{"error":6,"message":"Artist not found"}"""
        assertNull(service.parseArtistInfoBio(json))
    }

    @Test
    fun `fetchArtistInfo short-circuits without key`() = kotlinx.coroutines.test.runTest {
        val storage = io.mockk.mockk<com.lucasdss.ftpmusic.app.data.security.SecureStorage>(relaxed = true)
        io.mockk.every {
            storage.get(com.lucasdss.ftpmusic.app.data.security.SecureStorage.KEY_LASTFM_API_KEY)
        } returns ""
        val svc = LastFmService(storage)
        assertNull(svc.fetchArtistInfo("Guns N' Roses"))
    }
}

/** Access the private parse method through the real service instance. */
private object LastFmServiceTestHelper {
    val service: LastFmService = LastFmService(io.mockk.mockk(relaxed = true))
}
