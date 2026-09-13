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
}

/** Access the private parse method through the real service instance. */
private object LastFmServiceTestHelper {
    val service: LastFmService = LastFmService(io.mockk.mockk(relaxed = true))
}
