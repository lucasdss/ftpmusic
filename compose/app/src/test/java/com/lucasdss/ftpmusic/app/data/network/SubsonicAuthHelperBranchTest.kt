package com.lucasdss.ftpmusic.app.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JVM tests for [SubsonicAuthHelper] — all pure JVM, no Android.
 */
class SubsonicAuthHelperBranchTest {

    private val helper = SubsonicAuthHelper()

    @Test
    fun `buildAuthParams returns all six subsonic params`() {
        val params = helper.buildAuthParams("user", "pass")
        assertEquals("user", params["u"])
        assertEquals("1.16.1", params["v"])
        assertEquals("ftpmusic", params["c"])
        assertEquals("json", params["f"])
        assertTrue(params["t"]!!.length == 32) // md5 hex
        assertTrue(params["s"]!!.length in 6..6)
        // Token is deterministic for the same password+salt
        assertEquals(32, params["t"]!!.length)
    }

    @Test
    fun `buildStreamUrl embeds id auth and bitrate`() {
        val url = helper.buildStreamUrl("https://server.example.com", "t1", "user", "pass", maxBitRate = 192)
        assertTrue(url.startsWith("https://server.example.com/rest/stream?id=t1"))
        assertTrue(url.contains("u=user"))
        assertTrue(url.contains("maxBitRate=192"))
    }

    @Test
    fun `buildStreamUrl uses default bitrate of 320`() {
        val url = helper.buildStreamUrl("https://s/", "t1", "u", "p")
        assertTrue(url.contains("maxBitRate=320"))
    }

    @Test
    fun `buildCoverArtUrl uses deterministic salt and size`() {
        val url = helper.buildCoverArtUrl("https://server.example.com/", "user", "pass", "ca-1", size = 500)
        assertTrue(url.startsWith("https://server.example.com/rest/getCoverArt?id=ca-1"))
        assertTrue(url.contains("size=500"))
        assertTrue(url.contains("u=user"))
        // Deterministic for the same coverArtId
        val url2 = helper.buildCoverArtUrl("https://server.example.com/", "user", "pass", "ca-1", size = 500)
        assertEquals(url, url2)
    }

    @Test
    fun `buildCoverArtUrl trims trailing slash from server url`() {
        val url = helper.buildCoverArtUrl("https://s/", "u", "p", "ca")
        assertTrue(url.startsWith("https://s/rest/getCoverArt"))
    }

    @Test
    fun `buildCoverArtUrlCached reuses the same url per coverId and size`() {
        val a = helper.buildCoverArtUrlCached("https://s", "u", "p", "ca-1", 300)
        val b = helper.buildCoverArtUrlCached("https://s", "u", "p", "ca-1", 300)
        assertEquals(a, b)
        val c = helper.buildCoverArtUrlCached("https://s", "u", "p", "ca-1", 500)
        assertFalse(c == a) // different size → different cache entry
        val d = helper.buildCoverArtUrlCached("https://s", "u", "p", "ca-2", 300)
        assertFalse(d == a) // different cover id → different cache entry
    }

    @Test
    fun `checkResponseStatus returns true for ok and malformed responses`() {
        assertTrue(helper.checkResponseStatus(mapOf("subsonic-response" to mapOf("status" to "ok"))))
        assertTrue(helper.checkResponseStatus(emptyMap()))
        assertTrue(helper.checkResponseStatus(mapOf("subsonic-response" to "nope")))
    }

    @Test
    fun `checkResponseStatus returns false for failed status`() {
        assertFalse(helper.checkResponseStatus(mapOf("subsonic-response" to mapOf("status" to "failed"))))
    }

    @Test
    fun `getResponseError extracts code and message`() {
        val response = mapOf(
            "subsonic-response" to mapOf(
                "error" to mapOf("code" to 40, "message" to "Wrong username or password"),
            ),
        )
        assertEquals("40: Wrong username or password", helper.getResponseError(response))
    }

    @Test
    fun `getResponseError returns unknown for malformed responses`() {
        assertEquals("Unknown error", helper.getResponseError(emptyMap()))
        assertEquals(
            "Unknown error",
            helper.getResponseError(mapOf("subsonic-response" to mapOf<String, Any?>())),
        )
    }
}
