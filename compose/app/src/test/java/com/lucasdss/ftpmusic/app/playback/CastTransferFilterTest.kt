package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Regression tests for CastTransferFilter — guards against the
 * DefaultMediaSourceFactory.checkNotNull NPE that occurred when a
 * null-URI MediaItem (MediaItem.EMPTY from CastTimelineTracker) was
 * transferred Cast→ExoPlayer on session suspension/end after a
 * process-death/reinstall reconnect.
 *
 * Crash signature (Aug 2026): onSessionSuspended →
 * CastPlayerImpl.updateActivePlayer → TransferCallback lambda →
 * PlayerTransferState.setToPlayer → ExoPlayerImpl.setMediaItems →
 * DefaultMediaSourceFactory.createMediaSource → checkNotNull(uri) NPE.
 */
class CastTransferFilterTest {

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val u = mockk<Uri>(relaxed = true)
            every { u.toString() } returns (firstArg() as String)
            u
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    private fun playableItem(id: String, uri: String): MediaItem =
        MediaItem.Builder().setMediaId(id).setUri(Uri.parse(uri)).build()

    private fun nonPlayableItem(id: String): MediaItem = MediaItem.Builder().setMediaId(id).build()

    // ── isPlayable ────────────────────────────────────────────────────

    @Test
    fun `isPlayable true for item with URI`() {
        assertTrue(CastTransferFilter.isPlayable(playableItem("a", "https://x/rest/stream?id=a")))
    }

    @Test
    fun `isPlayable false for item without localConfiguration`() {
        assertFalse(CastTransferFilter.isPlayable(MediaItem.EMPTY))
    }

    @Test
    fun `isPlayable false for item built without URI`() {
        assertFalse(CastTransferFilter.isPlayable(nonPlayableItem("a")))
    }

    // ── computeFilter: no filtering when all playable ─────────────────

    @Test
    fun `passes through when all items have URIs`() {
        val items = listOf(
            playableItem("a", "https://x/rest/stream?id=a"),
            playableItem("b", "https://x/rest/stream?id=b"),
        )
        val result = CastTransferFilter.computeFilter(items, currentIndex = 1)

        assertEquals("no filtering expected", items, result.playable)
        assertEquals("index unchanged", 1, result.newCurrentIndex)
        assertFalse("current not filtered", result.currentFiltered)
    }

    // ── computeFilter: current item is non-playable (crash scenario) ──

    @Test
    fun `drops current non-playable item and marks currentFiltered`() {
        // currentIndex=1 points at the null-URI item (the crash scenario)
        val items = listOf(
            playableItem("a", "https://x/rest/stream?id=a"),
            nonPlayableItem("b"), // current — MediaItem.EMPTY-like
            playableItem("c", "https://x/rest/stream?id=c"),
        )
        val result = CastTransferFilter.computeFilter(items, currentIndex = 1)

        assertEquals("current item dropped", listOf(items[0], items[2]), result.playable)
        assertEquals("index stays 1 (item c)", 1, result.newCurrentIndex)
        assertTrue("currentFiltered must be true → position resets", result.currentFiltered)
    }

    // ── computeFilter: non-playable before current ────────────────────

    @Test
    fun `shifts index left when non-playable item precedes current`() {
        val items = listOf(
            nonPlayableItem("a"), // before current — dropped
            playableItem("b", "https://x/rest/stream?id=b"),
            playableItem("c", "https://x/rest/stream?id=c"),
        )
        // current is index 2 (item "c")
        val result = CastTransferFilter.computeFilter(items, currentIndex = 2)

        assertEquals("non-playable dropped", listOf(items[1], items[2]), result.playable)
        assertEquals("index shifts 2 → 1", 1, result.newCurrentIndex)
        assertFalse("current itself not filtered", result.currentFiltered)
    }

    // ── computeFilter: all items non-playable ─────────────────────────

    @Test
    fun `clears queue when all items are non-playable`() {
        val items = listOf(nonPlayableItem("a"), nonPlayableItem("b"))
        val result = CastTransferFilter.computeFilter(items, currentIndex = 0)

        assertEquals("queue cleared", emptyList<MediaItem>(), result.playable)
        assertEquals("index unset", androidx.media3.common.C.INDEX_UNSET, result.newCurrentIndex)
        assertTrue("current filtered", result.currentFiltered)
    }

    // ── computeFilter: first item non-playable while current is 0 ─────

    @Test
    fun `current is first item and it is non-playable`() {
        val items = listOf(
            nonPlayableItem("a"), // current at 0 — dropped
            playableItem("b", "https://x/rest/stream?id=b"),
        )
        val result = CastTransferFilter.computeFilter(items, currentIndex = 0)

        assertEquals(listOf(items[1]), result.playable)
        assertEquals(0, result.newCurrentIndex)
        assertTrue("current filtered", result.currentFiltered)
    }
}
