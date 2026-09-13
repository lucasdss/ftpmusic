package com.lucasdss.ftpmusic.app.data.network

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for MusicBrainz JSON parsing — validates the exact response
 * shapes returned by the MusicBrainz Web Service v2 API.
 *
 * Uses Robolectric so the real org.json implementation is available
 * (the default unit-test stub returns defaults for everything).
 *
 * MusicBrainz returns ratings as: {"rating":{"votes-count":N,"value":X.X}}
 * Search results contain entity arrays: {"artists":[...]},
 * {"release-groups":[...]}, {"recordings":[...]}
 */
@RunWith(RobolectricTestRunner::class)
class MusicBrainzServiceTest {

    private fun parseRating(json: String): Pair<Double?, Int?> {
        val obj = JSONObject(json)
        val rating = obj.optJSONObject("rating") ?: return Pair(null, null)
        val value = if (rating.has("value") && !rating.isNull("value")) rating.getDouble("value") else null
        val votes = if (rating.has("votes-count")) rating.getInt("votes-count") else null
        return Pair(value, votes)
    }

    @Test
    fun `artist rating parses value and votes`() {
        val (value, votes) = parseRating("""{"rating":{"votes-count":80,"value":4.5}}""")
        assertEquals(4.5, value!!, 0.001)
        assertEquals(80, votes)
    }

    @Test
    fun `rating without value parses to null`() {
        val (value, votes) = parseRating("""{"rating":{"votes-count":0}}""")
        assertNull(value)
        assertEquals(0, votes)
    }

    @Test
    fun `rating with null value parses to null`() {
        val (value, votes) = parseRating("""{"rating":{"value":null,"votes-count":10}}""")
        assertNull(value)
        assertEquals(10, votes)
    }

    @Test
    fun `rating absent parses to null pair`() {
        val (value, votes) = parseRating("""{"name":"Radiohead","id":"abc"}""")
        assertNull(value)
        assertNull(votes)
    }

    @Test
    fun `artist search array extracts first id`() {
        val json = """{"artists":[{"id":"a74b1b7f-71a5-4011-9441-d0b5e4122711","name":"Radiohead","score":100}]}"""
        val artists = JSONObject(json).getJSONArray("artists")
        assertTrue(artists.length() > 0)
        val firstId = artists.getJSONObject(0).getString("id")
        assertEquals("a74b1b7f-71a5-4011-9441-d0b5e4122711", firstId)
    }

    @Test
    fun `release-group search array extracts first id`() {
        val json = """{"release-groups":[{"id":"b1536533-c774-3c4e-a080-72d2432ce449","title":"OK Computer"}]}"""
        val groups = JSONObject(json).getJSONArray("release-groups")
        assertTrue(groups.length() > 0)
        assertEquals("b1536533-c774-3c4e-a080-72d2432ce449", groups.getJSONObject(0).getString("id"))
    }

    @Test
    fun `recording search array extracts first id`() {
        val json = """{"recordings":[{"id":"047ea202-b98d-46ae-97f7-0180a20ee5cf","title":"Creep"}]}"""
        val recordings = JSONObject(json).getJSONArray("recordings")
        assertTrue(recordings.length() > 0)
        assertEquals("047ea202-b98d-46ae-97f7-0180a20ee5cf", recordings.getJSONObject(0).getString("id"))
    }

    @Test
    fun `empty search array yields no id`() {
        val json = """{"artists":[]}"""
        val artists = JSONObject(json).optJSONArray("artists")
        assertTrue(artists == null || artists.length() == 0)
    }
}
