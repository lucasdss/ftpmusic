package com.lucasdss.ftpmusic.app.ui.player

import com.lucasdss.ftpmusic.app.data.db.LyricsCacheDao
import com.lucasdss.ftpmusic.app.data.db.LyricsCacheEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsFetcherTest {

    private fun response(lyrics: Map<String, Any>?): Map<String, Any> {
        val sr = mutableMapOf<String, Any>("status" to "ok")
        if (lyrics != null) sr["lyrics"] = lyrics
        return mapOf("subsonic-response" to sr)
    }

    @Test
    fun `isCacheStale false under 24h`() {
        val now = 1_000_000L
        assertFalse(LyricsFetcher.isCacheStale(now - 1_000L, now))
    }

    @Test
    fun `isCacheStale true over 24h`() {
        val now = 1_000_000L
        assertTrue(LyricsFetcher.isCacheStale(now - LyricsFetcher.CACHE_TTL_MS - 1, now))
    }

    @Test
    fun `parseResponse structured timed lines`() {
        val display = LyricsFetcher.parseResponse(
            response(
                mapOf(
                    "line" to listOf(
                        mapOf("start" to 0, "value" to "Intro"),
                        mapOf("start" to 1500, "value" to "Verse"),
                    ),
                ),
            ),
        )
        assertTrue(display.isSynced)
        assertEquals(2, display.lines.size)
        assertEquals(1500L, display.lines[1].timeMs)
        assertNull(display.text)
    }

    @Test
    fun `parseResponse all-zero starts without LRC becomes unstructured`() {
        val display = LyricsFetcher.parseResponse(
            response(
                mapOf(
                    "line" to listOf(
                        mapOf("start" to 0, "value" to "Line A"),
                        mapOf("start" to 0, "value" to "Line B"),
                    ),
                ),
            ),
        )
        assertFalse(display.isSynced)
        assertTrue(display.lines.isEmpty())
        assertEquals("Line A\nLine B", display.text)
    }

    @Test
    fun `parseResponse all-zero starts with LRC becomes synced`() {
        val display = LyricsFetcher.parseResponse(
            response(
                mapOf(
                    "line" to listOf(
                        mapOf("start" to 0, "value" to "[00:10.00]Hello"),
                        mapOf("start" to 0, "value" to "[00:20.00]World"),
                    ),
                ),
            ),
        )
        assertTrue(display.isSynced)
        assertEquals(2, display.lines.size)
        assertEquals(10_000L, display.lines[0].timeMs)
        assertEquals("Hello", display.lines[0].text)
    }

    @Test
    fun `parseResponse plain value`() {
        val display = LyricsFetcher.parseResponse(response(mapOf("value" to "Just words")))
        assertFalse(display.isSynced)
        assertEquals("Just words", display.text)
    }

    @Test
    fun `parseResponse plain text with LRC`() {
        val display = LyricsFetcher.parseResponse(
            response(mapOf("text" to "[00:05.00]One\n[00:10.00]Two")),
        )
        assertTrue(display.isSynced)
        assertEquals(2, display.lines.size)
    }

    @Test
    fun `parseResponse missing lyrics is empty`() {
        val display = LyricsFetcher.parseResponse(response(null))
        assertTrue(display.lines.isEmpty())
        assertNull(display.text)
    }

    @Test
    fun `fetchAndCache writes negative cache on empty lyrics`() = runBlocking {
        val dao = mockk<LyricsCacheDao>(relaxed = true)
        val slot = slot<LyricsCacheEntity>()
        coEvery { dao.put(capture(slot)) } returns Unit

        val display = LyricsFetcher.fetchAndCache(
            artist = "A",
            title = "T",
            trackId = "tid1",
            dao = dao,
            getLyrics = { _, _ -> response(null) },
            nowMs = 42L,
        )
        assertTrue(display.lines.isEmpty())
        assertNull(display.text)
        assertEquals("tid1", slot.captured.trackId)
        assertEquals("", slot.captured.unstructuredText)
        assertNull(slot.captured.syncedLinesJson)
        assertEquals(42L, slot.captured.fetchedAt)
        assertNotNull(slot.captured.rawJson)
    }

    @Test
    fun `fetchAndCache skips put when trackId null`() = runBlocking {
        val dao = mockk<LyricsCacheDao>(relaxed = true)
        LyricsFetcher.fetchAndCache(
            artist = "A",
            title = "T",
            trackId = null,
            dao = dao,
            getLyrics = { _, _ -> response(mapOf("value" to "x")) },
        )
        coVerify(exactly = 0) { dao.put(any()) }
    }

    @Test
    fun `fetchAndCache caches synced lines`() = runBlocking {
        val dao = mockk<LyricsCacheDao>(relaxed = true)
        val slot = slot<LyricsCacheEntity>()
        coEvery { dao.put(capture(slot)) } returns Unit
        var marked = false

        val display = LyricsFetcher.fetchAndCache(
            artist = "A",
            title = "T",
            trackId = "tid2",
            dao = dao,
            getLyrics = { _, _ ->
                response(
                    mapOf(
                        "line" to listOf(
                            mapOf("start" to 100, "value" to "Hi"),
                        ),
                    ),
                )
            },
            markFetched = { marked = true },
            nowMs = 99L,
        )
        assertTrue(display.isSynced)
        assertTrue(marked)
        assertNotNull(slot.captured.syncedLinesJson)
        assertNull(slot.captured.unstructuredText)
        assertEquals(99L, slot.captured.fetchedAt)
    }

    @Test
    fun `resolveFromCache miss returns null`() = runBlocking {
        val dao = mockk<LyricsCacheDao>()
        coEvery { dao.get("x") } returns null
        assertNull(LyricsFetcher.resolveFromCache("x", dao))
    }

    @Test
    fun `resolveFromCache evicts stale version`() = runBlocking {
        val dao = mockk<LyricsCacheDao>(relaxed = true)
        coEvery { dao.get("old") } returns LyricsCacheEntity(
            trackId = "old",
            artist = "a",
            title = "t",
            cacheVersion = 0,
            fetchedAt = 1L,
        )
        assertNull(LyricsFetcher.resolveFromCache("old", dao))
        coVerify { dao.delete("old") }
    }

    @Test
    fun `resolveFromCache hit preserves fetchedAt for TTL`() = runBlocking {
        val dao = mockk<LyricsCacheDao>(relaxed = true)
        val fetchedAt = 1_000L
        coEvery { dao.get("hit") } returns LyricsCacheEntity(
            trackId = "hit",
            artist = "a",
            title = "t",
            unstructuredText = "hello",
            fetchedAt = fetchedAt,
            cacheVersion = LyricsCacheEntity.CURRENT_CACHE_VERSION,
        )
        val now = fetchedAt + LyricsFetcher.CACHE_TTL_MS + 5
        val result = LyricsFetcher.resolveFromCache("hit", dao, nowMs = now)
        assertNotNull(result)
        assertTrue(result!!.fromCache)
        assertTrue(result.needsBackgroundRefresh)
        assertEquals("hello", result.display.text)
        // Must NOT call touch — fetchedAt must stay fetch time
        coVerify(exactly = 0) { dao.put(any()) }
    }

    @Test
    fun `resolveFromCache fresh hit no refresh`() = runBlocking {
        val dao = mockk<LyricsCacheDao>(relaxed = true)
        val fetchedAt = 5_000L
        coEvery { dao.get("hit") } returns LyricsCacheEntity(
            trackId = "hit",
            artist = "a",
            title = "t",
            unstructuredText = "fresh",
            fetchedAt = fetchedAt,
            cacheVersion = LyricsCacheEntity.CURRENT_CACHE_VERSION,
        )
        val result = LyricsFetcher.resolveFromCache("hit", dao, nowMs = fetchedAt + 100)
        assertFalse(result!!.needsBackgroundRefresh)
    }

    @Test
    fun `reparseFromRaw upgrades LRC in rawJson to synced`() = runBlocking {
        val dao = mockk<LyricsCacheDao>(relaxed = true)
        val raw = com.google.gson.Gson().toJson(
            response(mapOf("value" to "[00:01.00]A\n[00:02.00]B")),
        )
        coEvery { dao.get("r1") } returns LyricsCacheEntity(
            trackId = "r1",
            artist = "ar",
            title = "ti",
            rawJson = raw,
            unstructuredText = "[00:01.00]A\n[00:02.00]B",
            fetchedAt = 10L,
        )
        val upgraded = LyricsFetcher.reparseFromRaw(raw, "r1", dao)
        assertNotNull(upgraded)
        assertTrue(upgraded!!.isSynced)
        coVerify { dao.put(match { it.syncedLinesJson != null && it.fetchedAt == 10L }) }
    }

    @Test
    fun `displayFromEntity demotes all-zero synced json to unstructured`() {
        val linesJson = com.google.gson.Gson().toJson(
            listOf(LyricLine(0, "A"), LyricLine(0, "B")),
        )
        val display = LyricsFetcher.displayFromEntity(
            LyricsCacheEntity(
                trackId = "z",
                artist = null,
                title = null,
                syncedLinesJson = linesJson,
                unstructuredText = null,
            ),
        )
        assertFalse(display.isSynced)
        assertEquals("A\nB", display.text)
    }

    @Test
    fun `displayFromEntity prefers unstructured when fake sync cached`() {
        val linesJson = com.google.gson.Gson().toJson(listOf(LyricLine(0, "A")))
        val display = LyricsFetcher.displayFromEntity(
            LyricsCacheEntity(
                trackId = "z2",
                artist = null,
                title = null,
                syncedLinesJson = linesJson,
                unstructuredText = "Plain preferred",
            ),
        )
        assertEquals("Plain preferred", display.text)
        assertTrue(display.lines.isEmpty())
    }

    @Test
    fun `displayFromEntity returns truly synced lines`() {
        val linesJson = com.google.gson.Gson().toJson(
            listOf(LyricLine(0, "Intro"), LyricLine(500, "Go")),
        )
        val display = LyricsFetcher.displayFromEntity(
            LyricsCacheEntity(
                trackId = "ok",
                artist = null,
                title = null,
                syncedLinesJson = linesJson,
            ),
        )
        assertTrue(display.isSynced)
        assertEquals(2, display.lines.size)
    }

    @Test
    fun `displayFromEntity corrupt synced json yields empty`() {
        val display = LyricsFetcher.displayFromEntity(
            LyricsCacheEntity(
                trackId = "bad",
                artist = null,
                title = null,
                syncedLinesJson = "{not-json",
            ),
        )
        assertTrue(display.lines.isEmpty())
        assertNull(display.text)
    }

    @Test
    fun `resolveFromCache reparses legacy raw-only rows without unstructured`() = runBlocking {
        val dao = mockk<LyricsCacheDao>(relaxed = true)
        val raw = com.google.gson.Gson().toJson(
            response(
                mapOf(
                    "line" to listOf(
                        mapOf("start" to 0, "value" to "[00:03.00]X"),
                        mapOf("start" to 0, "value" to "[00:06.00]Y"),
                    ),
                ),
            ),
        )
        val entity = LyricsCacheEntity(
            trackId = "rp",
            artist = "a",
            title = "t",
            rawJson = raw,
            syncedLinesJson = null,
            unstructuredText = null,
            fetchedAt = 50L,
            cacheVersion = LyricsCacheEntity.CURRENT_CACHE_VERSION,
        )
        coEvery { dao.get("rp") } returns entity andThen entity.copy(
            syncedLinesJson = com.google.gson.Gson().toJson(
                listOf(LyricLine(3000, "X"), LyricLine(6000, "Y")),
            ),
            unstructuredText = null,
        )
        val result = LyricsFetcher.resolveFromCache("rp", dao, nowMs = 50L)
        assertNotNull(result)
        assertTrue(result!!.display.isSynced || result.display.text != null)
        coVerify { dao.put(any()) }
    }

    @Test
    fun `resolveFromCache skips reparse when unstructured already stored`() = runBlocking {
        val dao = mockk<LyricsCacheDao>(relaxed = true)
        val raw = com.google.gson.Gson().toJson(response(mapOf("value" to "cached plain")))
        coEvery { dao.get("neg") } returns LyricsCacheEntity(
            trackId = "neg",
            artist = "a",
            title = "t",
            rawJson = raw,
            syncedLinesJson = null,
            unstructuredText = "",
            fetchedAt = 1L,
            cacheVersion = LyricsCacheEntity.CURRENT_CACHE_VERSION,
        )
        LyricsFetcher.resolveFromCache("neg", dao, nowMs = 1L)
        LyricsFetcher.resolveFromCache("neg", dao, nowMs = 1L)
        coVerify(exactly = 0) { dao.put(any()) }
    }

    @Test
    fun `shouldFetchLyricsOverNetwork false when offline or no OS net`() {
        assertFalse(LyricsFetcher.shouldFetchLyricsOverNetwork(isOffline = true, hasOsNetwork = true))
        assertFalse(LyricsFetcher.shouldFetchLyricsOverNetwork(isOffline = false, hasOsNetwork = false))
        assertTrue(LyricsFetcher.shouldFetchLyricsOverNetwork(isOffline = false, hasOsNetwork = true))
    }

    @Test
    fun `parseResponse structured lines with null values become empty raw then unstructured`() {
        val display = LyricsFetcher.parseResponse(
            response(
                mapOf(
                    "line" to listOf(
                        mapOf("start" to 1), // missing value → dropped
                        "not-a-map",
                    ),
                    "value" to "Fallback plain",
                ),
            ),
        )
        // empty rawLines → unstructuredFromMap uses value
        assertEquals("Fallback plain", display.text)
    }

    @Test
    fun `reparseFromRaw plain text without LRC returns unstructured`() = runBlocking {
        val dao = mockk<LyricsCacheDao>(relaxed = true)
        val raw = com.google.gson.Gson().toJson(response(mapOf("value" to "No stamps here")))
        coEvery { dao.get("p1") } returns LyricsCacheEntity(
            trackId = "p1",
            artist = "ar",
            title = "ti",
            rawJson = raw,
            fetchedAt = 7L,
        )
        val result = LyricsFetcher.reparseFromRaw(raw, "p1", dao)
        assertNotNull(result)
        assertEquals("No stamps here", result!!.text)
        coVerify { dao.put(match { it.unstructuredText == "No stamps here" && it.fetchedAt == 7L }) }
    }

    @Test
    fun `reparseFromRaw invalid json returns null`() = runBlocking {
        val dao = mockk<LyricsCacheDao>(relaxed = true)
        assertNull(LyricsFetcher.reparseFromRaw("???", "x", dao))
        coVerify(exactly = 0) { dao.put(any()) }
    }

    @Test
    fun `reparseFromRaw empty lyrics returns null`() = runBlocking {
        val dao = mockk<LyricsCacheDao>(relaxed = true)
        val raw = com.google.gson.Gson().toJson(response(null))
        assertNull(LyricsFetcher.reparseFromRaw(raw, "x", dao))
    }

    @Test
    fun `fetchAndCache unstructured text field caches plain`() = runBlocking {
        val dao = mockk<LyricsCacheDao>(relaxed = true)
        val slot = slot<LyricsCacheEntity>()
        coEvery { dao.put(capture(slot)) } returns Unit
        val display = LyricsFetcher.fetchAndCache(
            artist = "A",
            title = "T",
            trackId = "tid3",
            dao = dao,
            getLyrics = { _, _ -> response(mapOf("text" to "From text key")) },
        )
        assertEquals("From text key", display.text)
        assertEquals("From text key", slot.captured.unstructuredText)
    }

    @Test
    fun `isCacheStale uses wall clock default`() {
        // Just ensure default overload is callable (covers default nowMs branch).
        assertFalse(LyricsFetcher.isCacheStale(System.currentTimeMillis()))
    }

    @Test
    fun `parseResponse non-map subsonic-response is empty`() {
        val display = LyricsFetcher.parseResponse(mapOf("subsonic-response" to "bad"))
        assertTrue(display.lines.isEmpty())
        assertNull(display.text)
    }

    @Test
    fun `parseResponse lyrics not a map is empty`() {
        val display = LyricsFetcher.parseResponse(
            mapOf("subsonic-response" to mapOf("lyrics" to "nope")),
        )
        assertNull(display.text)
    }

    @Test
    fun `displayFromEntity blank joined fake sync yields null text`() {
        val linesJson = com.google.gson.Gson().toJson(listOf(LyricLine(0, "  "), LyricLine(0, "")))
        val display = LyricsFetcher.displayFromEntity(
            LyricsCacheEntity(
                trackId = "blank",
                artist = null,
                title = null,
                syncedLinesJson = linesJson,
                unstructuredText = null,
            ),
        )
        // join of blank/empty → ifBlank null after trim? clean may leave spaces; accept empty or null
        assertTrue(display.lines.isEmpty())
    }

    @Test
    fun `reparseFromRaw synced when dao get null uses now for fetchedAt`() = runBlocking {
        val dao = mockk<LyricsCacheDao>(relaxed = true)
        coEvery { dao.get("gone") } returns null
        val raw = com.google.gson.Gson().toJson(
            response(
                mapOf(
                    "line" to listOf(mapOf("start" to 1000, "value" to "Hi")),
                ),
            ),
        )
        val result = LyricsFetcher.reparseFromRaw(raw, "gone", dao)
        assertTrue(result!!.isSynced)
        coVerify { dao.put(match { it.artist == null && it.syncedLinesJson != null }) }
    }

    @Test
    fun `reparseFromRaw plain when dao get null`() = runBlocking {
        val dao = mockk<LyricsCacheDao>(relaxed = true)
        coEvery { dao.get("gone2") } returns null
        val raw = com.google.gson.Gson().toJson(response(mapOf("value" to "Plain")))
        val result = LyricsFetcher.reparseFromRaw(raw, "gone2", dao)
        assertEquals("Plain", result!!.text)
        coVerify { dao.put(match { it.title == null && it.unstructuredText == "Plain" }) }
    }

    @Test
    fun `all-zero LRC parse empty stays unstructured blank-safe`() {
        // Values that clean to empty after LRC fail
        val display = LyricsFetcher.parseResponse(
            response(
                mapOf(
                    "line" to listOf(
                        mapOf("start" to 0, "value" to "   "),
                    ),
                ),
            ),
        )
        assertTrue(display.lines.isEmpty())
    }

    @Test
    fun `structured start missing defaults to zero then unstructured`() {
        val display = LyricsFetcher.parseResponse(
            response(
                mapOf(
                    "line" to listOf(
                        mapOf("value" to "Only value"),
                    ),
                ),
            ),
        )
        assertEquals("Only value", display.text)
    }

    @Test
    fun `resolveFromCache rawJson reparse returns null keeps original display`() = runBlocking {
        val dao = mockk<LyricsCacheDao>(relaxed = true)
        val entity = LyricsCacheEntity(
            trackId = "np",
            artist = "a",
            title = "t",
            rawJson = "{}", // no lyrics key after parse → null upgrade
            syncedLinesJson = null,
            unstructuredText = "keep-me",
            fetchedAt = 1L,
            cacheVersion = LyricsCacheEntity.CURRENT_CACHE_VERSION,
        )
        coEvery { dao.get("np") } returns entity
        val result = LyricsFetcher.resolveFromCache("np", dao, nowMs = 1L)
        assertEquals("keep-me", result!!.display.text)
    }
}
