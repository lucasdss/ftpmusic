package com.lucasdss.ftpmusic.app.data.network

import org.junit.Assert.*
import org.junit.Test

class SubsonicAuthHelperTest {

    private val helper = SubsonicAuthHelper()

    @Test
    fun `buildAuthParams returns required keys`() {
        val params = helper.buildAuthParams("user", "pass")
        assertTrue(params.containsKey("u"))
        assertTrue(params.containsKey("t"))
        assertTrue(params.containsKey("s"))
        assertEquals("user", params["u"])
        assertEquals("1.16.1", params["v"])
        assertEquals("ftpmusic", params["c"])
        assertEquals("json", params["f"])
    }

    @Test
    fun `salt is random between calls`() {
        val p1 = helper.buildAuthParams("user", "pass")
        val p2 = helper.buildAuthParams("user", "pass")
        assertNotEquals(p1["s"], p2["s"])
        assertNotEquals(p1["t"], p2["t"])
    }

    @Test
    fun `token is 32 char hex`() {
        val params = helper.buildAuthParams("user", "pass")
        val token = params["t"]!!
        assertEquals(32, token.length)
        assertTrue(token.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `buildStreamUrl includes all params`() {
        val url = helper.buildStreamUrl(
            "http://192.168.1.1:4533",
            "track-123",
            "user",
            "pass",
            256,
        )
        assertTrue(url.startsWith("http://192.168.1.1:4533/rest/stream"))
        assertTrue(url.contains("id=track-123"))
        assertTrue(url.contains("u=user"))
        assertTrue(url.contains("maxBitRate=256"))
    }

    @Test
    fun `cached cover url is stable and keyed on the full config`() {
        val first = helper.buildCoverArtUrlCached("https://a.example", "user", "pass", "ca-1", 300)
        val second = helper.buildCoverArtUrlCached("https://a.example", "user", "pass", "ca-1", 300)
        assertEquals(first, second)

        // A different server/credential/size must not return the stale URL.
        val otherServer = helper.buildCoverArtUrlCached("https://b.example", "user", "pass", "ca-1", 300)
        val otherUser = helper.buildCoverArtUrlCached("https://a.example", "other", "pass", "ca-1", 300)
        val otherPass = helper.buildCoverArtUrlCached("https://a.example", "user", "other", "ca-1", 300)
        val otherSize = helper.buildCoverArtUrlCached("https://a.example", "user", "pass", "ca-1", 600)

        assertNotEquals(first, otherServer)
        assertNotEquals(first, otherUser)
        assertNotEquals(first, otherPass)
        assertNotEquals(first, otherSize)
        assertTrue(otherServer.startsWith("https://b.example/rest/getCoverArt?"))
    }

    @Test
    fun `cover url uses a deterministic salt per coverArtId`() {
        val first = helper.buildCoverArtUrl("https://a.example", "user", "pass", "ca-42", 300)
        val second = helper.buildCoverArtUrl("https://a.example", "user", "pass", "ca-42", 300)
        assertEquals(first, second)
    }
}
