package com.lucasdss.ftpmusic.app.data.search

import com.lucasdss.ftpmusic.app.data.db.LyricsCacheDao
import com.lucasdss.ftpmusic.app.data.db.LyricsCacheEntity
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchLyricsDefaultsTest {

    @Test
    fun `maybeEnable turns on when unset and cache nonempty`() = runTest {
        val storage = mockk<SecureStorage>(relaxed = true)
        val dao = mockk<LyricsCacheDao>()
        every { storage.get(SecureStorage.KEY_SEARCH_LYRICS) } returns null
        coEvery { dao.count() } returns 3
        var rebuilt = false
        assertTrue(SearchLyricsDefaults.maybeEnable(storage, dao) { rebuilt = true })
        verify { storage.put(SecureStorage.KEY_SEARCH_LYRICS, "true") }
        assertTrue(rebuilt)
    }

    @Test
    fun `maybeEnable respects explicit false`() = runTest {
        val storage = mockk<SecureStorage>(relaxed = true)
        val dao = mockk<LyricsCacheDao>()
        every { storage.get(SecureStorage.KEY_SEARCH_LYRICS) } returns "false"
        assertFalse(SearchLyricsDefaults.maybeEnable(storage, dao))
        verify(exactly = 0) { storage.put(any(), any()) }
    }

    @Test
    fun `maybeEnable skips empty cache`() = runTest {
        val storage = mockk<SecureStorage>(relaxed = true)
        val dao = mockk<LyricsCacheDao>()
        every { storage.get(SecureStorage.KEY_SEARCH_LYRICS) } returns null
        coEvery { dao.count() } returns 0
        assertFalse(SearchLyricsDefaults.maybeEnable(storage, dao))
    }

    @Test
    fun `snippet finds matching line`() {
        val entity = LyricsCacheEntity(
            trackId = "t1",
            artist = "A",
            title = "T",
            unstructuredText = "Hello world\nNever gonna give you up\nOutro",
        )
        val snip = SearchLyricsDefaults.snippet(entity, "gonna give")
        assertEquals("Never gonna give you up", snip)
    }

    @Test
    fun `snippet null entity returns null`() {
        assertNull(SearchLyricsDefaults.snippet(null, "x"))
    }

    @Test
    fun `maybeEnable returns false when count throws`() = runTest {
        val storage = mockk<SecureStorage>(relaxed = true)
        val dao = mockk<LyricsCacheDao>()
        every { storage.get(SecureStorage.KEY_SEARCH_LYRICS) } returns null
        coEvery { dao.count() } throws RuntimeException("db")
        assertFalse(SearchLyricsDefaults.maybeEnable(storage, dao))
    }

    @Test
    fun `snippet blank query returns null`() {
        val entity = LyricsCacheEntity(trackId = "t1", artist = "A", title = "T", unstructuredText = "hi")
        assertNull(SearchLyricsDefaults.snippet(entity, "   "))
    }

    @Test
    fun `snippet uses synced json when unstructured null`() {
        val entity = LyricsCacheEntity(
            trackId = "t1",
            artist = "A",
            title = "T",
            unstructuredText = null,
            syncedLinesJson = "Line one lyric\nLine two",
        )
        assertEquals("Line one lyric", SearchLyricsDefaults.snippet(entity, "lyric"))
    }

    @Test
    fun `snippet falls back to first nonempty when no match`() {
        val entity = LyricsCacheEntity(
            trackId = "t1",
            artist = "A",
            title = "T",
            unstructuredText = "\n\nFirst line here\nSecond",
        )
        assertEquals("First line here", SearchLyricsDefaults.snippet(entity, "zzzznotfound"))
    }

    @Test
    fun `snippet truncates long line with ellipsis`() {
        val long = "x".repeat(100)
        val entity = LyricsCacheEntity(
            trackId = "t1",
            artist = "A",
            title = "T",
            unstructuredText = long,
        )
        val snip = SearchLyricsDefaults.snippet(entity, "xx", maxLen = 20)
        assertEquals(20, snip!!.length)
        assertTrue(snip.endsWith("…"))
    }

    @Test
    fun `snippet null when no text fields`() {
        val entity = LyricsCacheEntity(
            trackId = "t1",
            artist = "A",
            title = "T",
            unstructuredText = null,
            syncedLinesJson = null,
        )
        assertNull(SearchLyricsDefaults.snippet(entity, "hi"))
    }
}
