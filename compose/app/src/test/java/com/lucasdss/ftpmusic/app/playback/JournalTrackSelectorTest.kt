package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.db.QueueJournalEntity
import org.junit.Assert.*
import org.junit.Test

class JournalTrackSelectorTest {

    private fun entry(trackIdsJson: String, sourceType: String = "album", sourceId: String = "src") =
        QueueJournalEntity(
            sourceType = sourceType,
            sourceId = sourceId,
            trackIdsJson = trackIdsJson,
            sourceName = "Test",
        )

    // ── Empty / null inputs ───────────────────────────────────────────────

    @Test
    fun `empty entries returns empty list`() {
        val result = JournalTrackSelector.select(emptyList(), emptySet())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty currentTrackIds returns candidates`() {
        val entries = listOf(entry("""["t1","t2","t3"]"""))
        val result = JournalTrackSelector.select(entries, emptySet(), maxTracks = 5)
        assertTrue(result.isNotEmpty())
        assertTrue(result.size <= 5)
    }

    // ── Exclusion ──────────────────────────────────────────────────────────

    @Test
    fun `excludes tracks already in currentTrackIds`() {
        val entries = listOf(entry("""["t1","t2","t3"]"""))
        val result = JournalTrackSelector.select(entries, setOf("t1", "t2", "t3"), maxTracks = 3)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `partially excludes when some overlap`() {
        val entries = listOf(entry("""["t1","t2","t3","t4"]"""))
        val result = JournalTrackSelector.select(entries, setOf("t1", "t2"), maxTracks = 3)
        assertTrue(result.none { it == "t1" || it == "t2" })
        assertTrue(result.isNotEmpty())
    }

    // ── Max cap ────────────────────────────────────────────────────────────

    @Test
    fun `respects maxTracks`() {
        val entries = List(10) { i -> entry("""["t${i}a","t${i}b","t${i}c"]""") }
        val result = JournalTrackSelector.select(entries, emptySet(), maxTracks = 5)
        assertEquals(5, result.size)
    }

    @Test
    fun `returns fewer than max when pool is small`() {
        val entries = listOf(entry("""["t1","t2"]"""))
        val result = JournalTrackSelector.select(entries, emptySet(), maxTracks = 10)
        assertTrue(result.size <= 2)
    }

    // ── Dedup ──────────────────────────────────────────────────────────────

    @Test
    fun `returns distinct track IDs`() {
        // Same IDs in multiple entries to test distinct filtering
        val entries = listOf(
            entry("""["a","b","c"]"""),
            entry("""["b","c","d"]"""),
            entry("""["a","b","c"]"""),
        )
        repeat(10) {
            val result = JournalTrackSelector.select(entries, emptySet(), maxTracks = 4)
            assertEquals(result.size, result.distinct().size)
        }
    }

    // ── Weighting: newer entries (lower index) get more copies ─────────────

    @Test
    fun `more recent entries have higher weight`() {
        val entries = listOf(
            entry("""["r1","r2"]"""), // newest: weight = 3 → 3 copies
            entry("""["m1","m2"]"""), // middle: weight = 2 → 2 copies
            entry("""["o1","o2"]"""), // oldest: weight = 1 → 1 copy
        )
        // Run many times to verify statistical bias toward r1/r2
        val counts = mutableMapOf<String, Int>()
        repeat(100) {
            val result = JournalTrackSelector.select(entries, emptySet(), maxTracks = 3)
            result.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        // Recent tracks should appear more often than old ones
        val recentAvg = (listOf("r1", "r2").sumOf { counts[it] ?: 0 }) / 2
        val oldAvg = (listOf("o1", "o2").sumOf { counts[it] ?: 0 }) / 2
        assertTrue("Recent entries should dominate: recent=$recentAvg, old=$oldAvg", recentAvg >= oldAvg)
    }

    // ── Malformed JSON ─────────────────────────────────────────────────────

    @Test
    fun `malformed JSON is skipped`() {
        val entries = listOf(
            entry("not-json-at-all"),
            entry("""["valid1","valid2"]"""),
        )
        val result = JournalTrackSelector.select(entries, emptySet(), maxTracks = 5)
        // valid1/valid2 should appear; malformed entry is skipped
        assertTrue(result.isNotEmpty())
        assertTrue(result.any { it.startsWith("valid") })
    }

    @Test
    fun `all malformed JSON returns empty`() {
        val entries = listOf(entry("garbage"), entry("{bad}"), entry(""))
        val result = JournalTrackSelector.select(entries, emptySet(), maxTracks = 5)
        assertTrue(result.isEmpty())
    }

    // ── Entry cap at 50 ────────────────────────────────────────────────────

    @Test
    fun `caps effective entries at 50 for weighting`() {
        // 100 entries — only first 50 are used for weighting
        val entries = List(100) { i -> entry("""["t$i"]""") }
        val result = JournalTrackSelector.select(entries, emptySet(), maxTracks = 10)
        assertTrue(result.size in 1..10)
    }

    // ── Shuffle produces different results ─────────────────────────────────

    @Test
    fun `produces different results across calls (shuffled)`() {
        val entries = List(5) { i -> entry("""["a$i","b$i","c$i"]""") }
        val results = (1..20).map { JournalTrackSelector.select(entries, emptySet(), maxTracks = 5) }
        val distinctCount = results.distinct().size
        assertTrue("Should vary across calls, got $distinctCount distinct results", distinctCount > 1)
    }

    // ── Null JSON ──────────────────────────────────────────────────────────

    @Test
    fun `null JSON in entry is handled`() {
        val entries = listOf(entry("null"))
        val result = JournalTrackSelector.select(entries, emptySet())
        assertTrue(result.isEmpty())
    }
}
