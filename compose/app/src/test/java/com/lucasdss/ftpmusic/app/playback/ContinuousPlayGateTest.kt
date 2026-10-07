package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.db.QueueJournalEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousPlayGateTest {

    @Test
    fun `loads on last index when enabled and not yet loaded`() {
        assertTrue(
            ContinuousPlayGate.shouldLoadContinuation(
                isCasting = false,
                currentIndex = 9,
                mediaItemCount = 10,
                hasLoadedContinuation = false,
                continuousPlayEnabled = true,
            ),
        )
    }

    @Test
    fun `loads when single-item queue is on that item`() {
        assertTrue(
            ContinuousPlayGate.shouldLoadContinuation(
                isCasting = false,
                currentIndex = 0,
                mediaItemCount = 1,
                hasLoadedContinuation = false,
                continuousPlayEnabled = true,
            ),
        )
    }

    @Test
    fun `skips when Continuous Play disabled`() {
        assertFalse(
            ContinuousPlayGate.shouldLoadContinuation(
                isCasting = false,
                currentIndex = 0,
                mediaItemCount = 1,
                hasLoadedContinuation = false,
                continuousPlayEnabled = false,
            ),
        )
    }

    @Test
    fun `loads when casting if enabled on last item`() {
        // ADR-0093: Cast CP append via Dual + emitCastAddsOrCommit
        assertTrue(
            ContinuousPlayGate.shouldLoadContinuation(
                isCasting = true,
                currentIndex = 0,
                mediaItemCount = 1,
                hasLoadedContinuation = false,
                continuousPlayEnabled = true,
            ),
        )
    }

    @Test
    fun `resolveTimeline uses Dual while casting`() {
        val (idx, count) = ContinuousPlayGate.resolveTimeline(
            isCasting = true,
            playerIndex = 0,
            playerCount = 0, // CastPlayer empty/windowed
            dualIndex = 4,
            dualCount = 5,
        )
        assertEquals(4, idx)
        assertEquals(5, count)
        assertTrue(
            ContinuousPlayGate.shouldLoadContinuation(
                isCasting = true,
                currentIndex = idx,
                mediaItemCount = count,
                hasLoadedContinuation = false,
                continuousPlayEnabled = true,
            ),
        )
    }

    @Test
    fun `resolveTimeline uses player when not casting`() {
        val (idx, count) = ContinuousPlayGate.resolveTimeline(
            isCasting = false,
            playerIndex = 2,
            playerCount = 3,
            dualIndex = 99,
            dualCount = 99,
        )
        assertEquals(2, idx)
        assertEquals(3, count)
    }

    @Test
    fun `skips when continuation already loaded`() {
        assertFalse(
            ContinuousPlayGate.shouldLoadContinuation(
                isCasting = false,
                currentIndex = 0,
                mediaItemCount = 1,
                hasLoadedContinuation = true,
                continuousPlayEnabled = true,
            ),
        )
    }

    @Test
    fun `skips when not on last index`() {
        assertFalse(
            ContinuousPlayGate.shouldLoadContinuation(
                isCasting = false,
                currentIndex = 5,
                mediaItemCount = 10,
                hasLoadedContinuation = false,
                continuousPlayEnabled = true,
            ),
        )
    }

    @Test
    fun `skips empty timeline`() {
        assertFalse(
            ContinuousPlayGate.shouldLoadContinuation(
                isCasting = false,
                currentIndex = 0,
                mediaItemCount = 0,
                hasLoadedContinuation = false,
                continuousPlayEnabled = true,
            ),
        )
    }

    @Test
    fun `reset when mediaId changes`() {
        assertTrue(ContinuousPlayGate.shouldResetContinuation("old", "new"))
        assertFalse(ContinuousPlayGate.shouldResetContinuation("same", "same"))
        assertTrue(ContinuousPlayGate.shouldResetContinuation(null, "first"))
    }
}

class ContinuousPlaySurpriseMeJournalTest {

    private fun makeEntry(sourceType: String, sourceId: String, trackIdsJson: String): QueueJournalEntity =
        QueueJournalEntity(
            sourceType = sourceType,
            sourceId = sourceId,
            sourceName = "Surprise Me",
            trackIdsJson = trackIdsJson,
            position = 0,
        )

    @Test
    fun `Surprise Me journal entries are eligible for Continuous Play selection`() {
        val entries = listOf(
            makeEntry(
                sourceType = "random",
                sourceId = "surprise-me",
                trackIdsJson = "[\"s1\",\"s2\",\"s3\",\"s4\"]",
            ),
        )
        val result = JournalTrackSelector.select(entries, currentTrackIds = emptySet(), maxTracks = 10)
        assertTrue(result.isNotEmpty())
        val pool = setOf("s1", "s2", "s3", "s4")
        result.forEach { assertTrue("$it in surprise-me pool", it in pool) }
    }

    @Test
    fun `Surprise Me ids already in queue are excluded`() {
        val entries = listOf(
            makeEntry(
                sourceType = "random",
                sourceId = "surprise-me",
                trackIdsJson = "[\"s1\",\"s2\"]",
            ),
        )
        val result = JournalTrackSelector.select(entries, currentTrackIds = setOf("s1", "s2"))
        assertEquals(emptyList<String>(), result)
    }
}
