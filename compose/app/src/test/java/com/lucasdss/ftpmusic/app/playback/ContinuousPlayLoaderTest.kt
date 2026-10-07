package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousPlayLoaderTest {

    private fun entity(id: String, downloaded: Boolean = false, cachedPath: String? = null) = TrackEntity(
        id = id,
        albumId = "al",
        artistId = "ar",
        title = "T$id",
        artist = "A",
        isDownloaded = downloaded,
        cachedFilePath = cachedPath,
    )

    @Test
    fun `resolve skips missing DB rows`() {
        val result = ContinuousPlayLoader.resolve(
            selectedIds = listOf("a", "b"),
            localOnly = false,
            loadTrack = { id -> if (id == "a") entity("a") else null },
            buildStreamUrl = { "http://x/$it" },
        )
        assertEquals(1, result.size)
        assertEquals("a", result[0].track.id)
    }

    @Test
    fun `localOnly keeps rows with cache path only`() {
        val db = mapOf(
            "d" to entity("d", downloaded = true, cachedPath = "/pin/d"),
            "healed" to entity("healed", downloaded = true), // stale heal — skip
            "c" to entity("c", cachedPath = "/cache/c"),
            "n" to entity("n"),
        )
        val result = ContinuousPlayLoader.resolve(
            selectedIds = listOf("d", "healed", "c", "n"),
            localOnly = true,
            loadTrack = { db[it] },
            buildStreamUrl = { "http://x/$it" },
        )
        assertEquals(listOf("d", "c"), result.map { it.track.id })
    }

    @Test
    fun `online keeps metadata-only rows`() {
        val result = ContinuousPlayLoader.resolve(
            selectedIds = listOf("n"),
            localOnly = false,
            loadTrack = { entity("n") },
            buildStreamUrl = { "http://x/$it" },
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `empty selection returns empty`() {
        assertTrue(
            ContinuousPlayLoader.resolve(
                selectedIds = emptyList(),
                localOnly = false,
                loadTrack = { entity(it) },
                buildStreamUrl = { it },
            ).isEmpty(),
        )
    }

    @Test
    fun `isPlayableOffline requires cache path`() {
        // ADR-0095: is_downloaded alone (healed stale) is NOT offline-playable
        assertFalse(ContinuousPlayLoader.isPlayableOffline(entity("a", downloaded = true)))
        assertTrue(ContinuousPlayLoader.isPlayableOffline(entity("b", cachedPath = "/x")))
        assertTrue(
            ContinuousPlayLoader.isPlayableOffline(
                entity("d", downloaded = true, cachedPath = "/pin"),
            ),
        )
        assertFalse(ContinuousPlayLoader.isPlayableOffline(entity("c")))
    }
}
