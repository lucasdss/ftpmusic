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
    fun `skips when casting`() {
        assertFalse(
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
