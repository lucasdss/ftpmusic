package com.lucasdss.ftpmusic.app.data.search

import com.lucasdss.ftpmusic.app.data.model.Artist
import com.lucasdss.ftpmusic.app.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultMergerTest {

    @Test
    fun `union keeps local artist when server returns other hits`() {
        val local = listOf(Artist("local-x", "Artist X"))
        val server = (1..20).map { Artist("s$it", "Server $it") }
        val merged = SearchResultMerger.unionById(server, local) { it.id }
        assertEquals(21, merged.size)
        assertTrue(merged.any { it.id == "local-x" && it.name == "Artist X" })
        assertEquals("s1", merged.first().id)
    }

    @Test
    fun `union empty server keeps local`() {
        val local = listOf(Artist("a", "A"))
        assertEquals(local, SearchResultMerger.unionById(emptyList(), local) { it.id })
    }

    @Test
    fun `union prefers server row for shared id`() {
        val local = listOf(Artist("a1", "Local Name"))
        val server = listOf(Artist("a1", "Server Name", coverArt = "c"))
        val merged = SearchResultMerger.unionById(server, local) { it.id }
        assertEquals(1, merged.size)
        assertEquals("Server Name", merged[0].name)
        assertEquals("c", merged[0].coverArt)
    }

    @Test
    fun `rank prefers exact then prefix then contains`() {
        val items = listOf(
            Artist("1", "The Rock Band"),
            Artist("2", "Rock"),
            Artist("3", "Rocky"),
            Artist("4", "Jazz"),
        )
        val ranked = SearchResultMerger.rankByQuery(items, "rock") { it.name }
        assertEquals(listOf("Rock", "Rocky", "The Rock Band", "Jazz"), ranked.map { it.name })
    }

    @Test
    fun `rank folds diacritics`() {
        val items = listOf(Artist("1", "Björk"), Artist("2", "Other"))
        val ranked = SearchResultMerger.rankByQuery(items, "bjork") { it.name }
        assertEquals("Björk", ranked.first().name)
    }

    @Test
    fun `rankByFields prefers artist match over weak title contains`() {
        val items = listOf(
            Track("1", title = "Misc Song", artist = "Other"),
            Track("2", title = "B-side", artist = "Pink Floyd"),
            Track("3", title = "Pink Floyd Tribute", artist = "Cover Band"),
        )
        val ranked = SearchResultMerger.rankByFields(
            items,
            "pink floyd",
            fieldsOf = { listOf(it.title, it.artist, it.album) },
        )
        assertEquals("2", ranked.first().id)
    }

    @Test
    fun `rankByFields popularity tie-break prefers higher playCount`() {
        val items = listOf(
            Track("1", title = "Rock Song", playCount = 1),
            Track("2", title = "Rock Anthem", playCount = 50),
        )
        val ranked = SearchResultMerger.rankByFields(
            items,
            "rock",
            fieldsOf = { listOf(it.title) },
            popularityOf = { SearchResultMerger.trackPopularity(it.playCount, it.lastPlayedAt) },
        )
        assertEquals("2", ranked.first().id)
    }

    @Test
    fun `isExactName folds query`() {
        assertTrue(SearchResultMerger.isExactName("Radiohead", "radiohead"))
        assertFalse(SearchResultMerger.isExactName("Radiohead", "radio"))
    }

    @Test
    fun `rankByFields bm25 prefers lower score over lexical contains`() {
        val items = listOf(
            Track("weak", title = "zzz rock tribute"),
            Track("strong", title = "other"),
        )
        val ranked = SearchResultMerger.rankByFields(
            items,
            "rock",
            fieldsOf = { listOf(it.title) },
            bm25Of = { if (it.id == "strong") 0.5 else 5.0 },
        )
        assertEquals("strong", ranked.first().id)
    }

    @Test
    fun `rankByFields exact still beats better bm25`() {
        val items = listOf(
            Track("exact", title = "Rock"),
            Track("bm25", title = "zzz"),
        )
        val ranked = SearchResultMerger.rankByFields(
            items,
            "rock",
            fieldsOf = { listOf(it.title) },
            bm25Of = { if (it.id == "bm25") 0.1 else 9.0 },
        )
        assertEquals("exact", ranked.first().id)
    }

    @Test
    fun `rankByQuery uses bm25 when provided`() {
        val items = listOf(
            Artist("a", "Alpha Rock"),
            Artist("b", "Beta"),
        )
        val ranked = SearchResultMerger.rankByQuery(
            items,
            "rock",
            bm25Of = { if (it.id == "b") 0.2 else 8.0 },
        ) { it.name }
        assertEquals("b", ranked.first().id)
    }

    @Test
    fun `rankByQuery exact beats better bm25`() {
        val items = listOf(
            Artist("exact", "Rock"),
            Artist("bm25", "Other"),
        )
        val ranked = SearchResultMerger.rankByQuery(
            items,
            "rock",
            bm25Of = { if (it.id == "bm25") 0.1 else 9.0 },
        ) { it.name }
        assertEquals("exact", ranked.first().id)
    }

    @Test
    fun `rankByQuery early return for empty query or single item`() {
        val one = listOf(Artist("a", "A"))
        assertEquals(one, SearchResultMerger.rankByQuery(one, "a") { it.name })
        val many = listOf(Artist("a", "A"), Artist("b", "B"))
        assertEquals(many, SearchResultMerger.rankByQuery(many, "") { it.name })
    }

    @Test
    fun `isExactName null and empty query are false`() {
        assertFalse(SearchResultMerger.isExactName(null, "x"))
        assertFalse(SearchResultMerger.isExactName("Rock", ""))
    }

    @Test
    fun `trackPopularity null lastPlayed uses zero recency`() {
        assertEquals(50_000_000L, SearchResultMerger.trackPopularity(50, null))
    }

    @Test
    fun `union empty local keeps server`() {
        val server = listOf(Artist("s", "S"))
        assertEquals(server, SearchResultMerger.unionById(server, emptyList()) { it.id })
    }
}
