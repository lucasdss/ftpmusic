package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * streamCacheKey: the cache key for Subsonic stream URLs. Must be identical
 * across all SimpleCache users (CacheDataSource streaming writes, DownloadManager
 * imports, Cast proxy) so a stream-cached track is a read-cache hit and vice versa.
 */
class StreamCacheKeyTest {

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `cache key is the track id from the query parameter`() {
        val uri = mockk<Uri>()
        every { Uri.parse(any()) } returns uri
        every { uri.getQueryParameter("id") } returns "track-42"

        assertEquals("track-42", streamCacheKey("https://server/rest/stream?id=track-42&u=user&t=abc"))
    }

    @Test
    fun `cache key falls back to the full url when no id param`() {
        val uri = mockk<Uri>()
        every { Uri.parse(any()) } returns uri
        every { uri.getQueryParameter("id") } returns null

        val url = "https://server/radio/stream.mp3"
        assertEquals(url, streamCacheKey(url))
    }

    @Test
    fun `cache key is stable across the same track id`() {
        val uri = mockk<Uri>()
        every { Uri.parse(any()) } returns uri
        every { uri.getQueryParameter("id") } returns "track-42"

        val key1 = streamCacheKey("https://server/rest/stream?id=track-42&u=user&t=aaa")
        val key2 = streamCacheKey("https://server/rest/stream?id=track-42&u=user&t=bbb")

        // Different auth tokens (t=) must NOT produce different cache keys —
        // otherwise the DownloadManager import and streaming write would
        // diverge and never hit each other's spans.
        assertEquals(key1, key2)
    }
}
