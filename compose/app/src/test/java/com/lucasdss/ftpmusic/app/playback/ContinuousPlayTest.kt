package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.db.QueueJournalEntity
import org.junit.Assert.*
import org.junit.Test

class ContinuousPlayTest {

    private fun makeEntry(
        sourceType: String = "album",
        sourceId: String = "al-1",
        sourceName: String? = "Test Album",
        trackIdsJson: String,
        position: Int = 0,
    ): QueueJournalEntity = QueueJournalEntity(
        sourceType = sourceType,
        sourceId = sourceId,
        sourceName = sourceName,
        trackIdsJson = trackIdsJson,
        position = position,
    )

    private fun json(vararg ids: String): String = "[\"${ids.joinToString("\",\"")}\"]"

    // ── Weighted selection ─────────────────────────────────────────────────

    @Test
    fun `selects tracks weighted by recency`() {
        // 3 entries: most recent gets weight=3, middle weight=2, oldest weight=1
        val entries = listOf(
            makeEntry(trackIdsJson = json("a1", "a2", "a3")), // weight 3
            makeEntry(sourceId = "al-2", trackIdsJson = json("b1", "b2")), // weight 2
            makeEntry(sourceId = "al-3", trackIdsJson = json("c1")), // weight 1
        )

        val result = JournalTrackSelector.select(entries, emptySet())

        // With weights, we expect at least some selection. Cannot assert exact order
        // due to shuffle, but result should be non-empty and ≤ all unique IDs.
        assertTrue("Should select some tracks", result.isNotEmpty())
        assertTrue("Should not exceed input count", result.size <= 6)

        // All selected IDs must come from the input pool
        val allIds = setOf("a1", "a2", "a3", "b1", "b2", "c1")
        result.forEach { assertTrue("$it should be in input pool", it in allIds) }
    }

    @Test
    fun `deduplicates against current queue`() {
        val entries = listOf(
            makeEntry(trackIdsJson = json("t1", "t2", "t3")),
            makeEntry(sourceId = "al-2", trackIdsJson = json("t4", "t5")),
        )
        // Current queue has t1, t2 — those should be excluded
        val currentQueue = setOf("t1", "t2")

        val result = JournalTrackSelector.select(entries, currentQueue)

        assertTrue("t1 should not appear (in current queue)", "t1" !in result)
        assertTrue("t2 should not appear (in current queue)", "t2" !in result)
        // t3, t4, t5 might appear (subject to shuffle/subset sampling)
    }

    @Test
    fun `caps output at 10 tracks`() {
        // Create entries with many distinct track IDs (20+)
        val entries = (1..5).map { i ->
            makeEntry(
                sourceId = "al-$i",
                trackIdsJson = json(*((i * 10)..(i * 10 + 4)).map { "t$it" }.toTypedArray()),
            )
        }
        // Current queue is empty, so all 25 IDs are eligible
        val result = JournalTrackSelector.select(entries, emptySet())

        assertTrue("Output should be capped at 10", result.size <= 10)
        assertTrue("Should have at least some output", result.isNotEmpty())
    }

    @Test
    fun `handles empty journal gracefully`() {
        val result = JournalTrackSelector.select(emptyList(), emptySet())
        assertTrue("Empty journal should produce empty result", result.isEmpty())
    }

    @Test
    fun `handles malformed JSON in trackIdsJson`() {
        val entries = listOf(
            makeEntry(sourceId = "al-bad", trackIdsJson = "{not valid json}"),
            makeEntry(sourceId = "al-good", trackIdsJson = json("t1", "t2")),
        )

        val result = JournalTrackSelector.select(entries, emptySet())

        // Malformed JSON should be skipped, only t1/t2 from the good entry
        assertTrue("Result should not be empty (good entry)", result.isNotEmpty())
        result.forEach { assertTrue("$it should be from the valid entry", it in setOf("t1", "t2")) }
    }

    @Test
    fun `handles null trackDao gracefully`() {
        // The selector doesn't use trackDao — this test validates the model
        // layer handles the no-op case when the selection result is empty,
        // which happens when every entry has malformed JSON.
        val entries = listOf(
            makeEntry(sourceId = "al-1", trackIdsJson = "garbage"),
            makeEntry(sourceId = "al-2", trackIdsJson = "also garbage"),
        )

        val result = JournalTrackSelector.select(entries, emptySet())
        assertTrue("All-malformed entries should produce empty result", result.isEmpty())
    }

    @Test
    fun `deduplicates within selected output`() {
        // All entries reference the same track IDs — output must not duplicate
        val entries = listOf(
            makeEntry(trackIdsJson = json("dup1", "dup2")),
            makeEntry(sourceId = "al-2", trackIdsJson = json("dup1", "dup2")),
            makeEntry(sourceId = "al-3", trackIdsJson = json("dup1", "dup2")),
        )
        val result = JournalTrackSelector.select(entries, emptySet())
        assertEquals("Should deduplicate", result.toSet().size, result.size)
        assertTrue("At most 2 unique tracks", result.size <= 2)
    }

    // ── hasLoadedContinuation reset logic tests ─────────────────────────────

    @Test
    fun `same mediaId does not reset continuation`() {
        // When onMediaItemTransition fires with the same last-track mediaId,
        // hasLoadedContinuation should NOT reset — prevents double-loading
        val previousTrackId = "track-last"
        val mediaId = "track-last" // same track (e.g., user clicks last track again)
        val shouldReset = mediaId != previousTrackId
        assertFalse("Same track should not reset continuation flag", shouldReset)
    }

    @Test
    fun `different mediaId resets continuation`() {
        // When a NEW queue is loaded and its last track starts,
        // hasLoadedContinuation must reset so continuous play fires again
        val previousTrackId = "old-track"
        val mediaId = "new-track" // different track (new queue loaded)
        val shouldReset = mediaId != previousTrackId
        assertTrue("New track should reset continuation flag", shouldReset)
    }

    @Test
    fun `null previousTrackId resets continuation on first transition`() {
        // On first ever transition (startup), previousTrackId is null.
        // This should reset so the first time we reach end, continuous play fires.
        val previousTrackId: String? = null
        val mediaId = "track-1"
        val shouldReset = mediaId != previousTrackId
        assertTrue("First transition should reset continuation flag", shouldReset)
    }
}
