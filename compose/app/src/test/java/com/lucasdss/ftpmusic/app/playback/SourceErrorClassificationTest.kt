package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.lucasdss.ftpmusic.app.data.cache.CacheSpanState
import com.lucasdss.ftpmusic.app.data.cache.StickyUnplayableRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceErrorClassificationTest {

    private fun classify(
        errorCode: Int,
        httpCode: Int? = null,
        causeName: String? = null,
        span: CacheSpanState = CacheSpanState.NONE,
    ) = classifySourceError(errorCode, httpCode, causeName, span)

    // --- classifier -------------------------------------------------------

    @Test
    fun `local file not found is a local fault`() {
        assertEquals(
            SourceErrorKind.LOCAL_FAULT,
            classify(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND),
        )
    }

    @Test
    fun `404 and 410 are remote missing`() {
        assertEquals(
            SourceErrorKind.REMOTE_MISSING,
            classify(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, httpCode = 404),
        )
        assertEquals(
            SourceErrorKind.REMOTE_MISSING,
            classify(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, httpCode = 410),
        )
    }

    @Test
    fun `auth failures are unknown not missing`() {
        assertEquals(
            SourceErrorKind.UNKNOWN,
            classify(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, httpCode = 401),
        )
        assertEquals(
            SourceErrorKind.UNKNOWN,
            classify(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, httpCode = 403),
        )
    }

    @Test
    fun `throttling and 5xx are network failures`() {
        assertEquals(
            SourceErrorKind.NETWORK,
            classify(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, httpCode = 429),
        )
        assertEquals(
            SourceErrorKind.NETWORK,
            classify(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, httpCode = 500),
        )
        assertEquals(
            SourceErrorKind.NETWORK,
            classify(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, httpCode = 503),
        )
    }

    @Test
    fun `bad http status without a captured code is network`() {
        assertEquals(
            SourceErrorKind.NETWORK,
            classify(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS),
        )
    }

    @Test
    fun `invalid content type means the body is not audio`() {
        assertEquals(
            SourceErrorKind.REMOTE_MISSING,
            classify(PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE),
        )
    }

    @Test
    fun `transport errors are network`() {
        assertEquals(
            SourceErrorKind.NETWORK,
            classify(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED),
        )
        assertEquals(
            SourceErrorKind.NETWORK,
            classify(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT),
        )
    }

    @Test
    fun `permission and cleartext are unknown`() {
        assertEquals(
            SourceErrorKind.UNKNOWN,
            classify(PlaybackException.ERROR_CODE_IO_NO_PERMISSION),
        )
        assertEquals(
            SourceErrorKind.UNKNOWN,
            classify(PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED),
        )
    }

    @Test
    fun `unspecified io consults the one-level cause`() {
        assertEquals(
            SourceErrorKind.NETWORK,
            classify(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, causeName = "ConnectException"),
        )
        assertEquals(
            SourceErrorKind.NETWORK,
            classify(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, causeName = "SocketTimeoutException"),
        )
        assertEquals(
            SourceErrorKind.NETWORK,
            classify(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, causeName = "UnknownHostException"),
        )
        assertEquals(
            SourceErrorKind.LOCAL_FAULT,
            classify(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, causeName = "FileNotFoundException"),
        )
        assertEquals(
            SourceErrorKind.UNKNOWN,
            classify(PlaybackException.ERROR_CODE_IO_UNSPECIFIED),
        )
        assertEquals(
            SourceErrorKind.UNKNOWN,
            classify(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, causeName = "IllegalStateException"),
        )
    }

    @Test
    fun `span is the tie breaker for parse and decode failures`() {
        val container = PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
        assertEquals(
            SourceErrorKind.LOCAL_FAULT,
            classify(container, span = CacheSpanState.UNPLAYABLE),
        )
        assertEquals(
            SourceErrorKind.REMOTE_MISSING,
            classify(container, span = CacheSpanState.NONE),
        )
        assertEquals(
            SourceErrorKind.UNKNOWN,
            classify(container, span = CacheSpanState.PLAYABLE),
        )
        assertEquals(
            SourceErrorKind.LOCAL_FAULT,
            classify(PlaybackException.ERROR_CODE_DECODING_FAILED, span = CacheSpanState.UNPLAYABLE),
        )
        assertEquals(
            SourceErrorKind.REMOTE_MISSING,
            classify(PlaybackException.ERROR_CODE_DECODING_FAILED, span = CacheSpanState.NONE),
        )
    }

    @Test
    fun `read position out of range uses the span`() {
        val code = PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
        assertEquals(SourceErrorKind.LOCAL_FAULT, classify(code, span = CacheSpanState.UNPLAYABLE))
        assertEquals(SourceErrorKind.REMOTE_MISSING, classify(code, span = CacheSpanState.NONE))
    }

    @Test
    fun `unknown media3 code is unknown`() {
        assertEquals(SourceErrorKind.UNKNOWN, classify(errorCode = 9999))
    }

    // --- MissingAlbumRun --------------------------------------------------

    @Test
    fun `first remote missing for an album takes one item`() {
        val run = MissingAlbumRun()
        assertFalse(run.onRemoteMissing("a", index = 0, nowMs = 1_000L))
        assertEquals("a", run.peekAlbumId())
        assertEquals(0, run.peekFirstIndex())
    }

    @Test
    fun `second contiguous remote missing collapses the run`() {
        val run = MissingAlbumRun()
        assertFalse(run.onRemoteMissing("a", 0, 1_000L))
        assertTrue(run.onRemoteMissing("a", 1, 1_100L))
        assertNull(run.peekAlbumId())
    }

    @Test
    fun `a third failure starts a new run`() {
        val run = MissingAlbumRun()
        run.onRemoteMissing("a", 0, 1_000L)
        run.onRemoteMissing("a", 1, 1_100L)
        assertFalse(run.onRemoteMissing("a", 2, 1_200L))
        assertEquals("a", run.peekAlbumId())
    }

    @Test
    fun `a different album resets the run`() {
        val run = MissingAlbumRun()
        assertFalse(run.onRemoteMissing("a", 0, 1_000L))
        assertFalse(run.onRemoteMissing("b", 1, 1_100L))
        assertEquals("b", run.peekAlbumId())
        assertEquals(1, run.peekFirstIndex())
    }

    @Test
    fun `a gap larger than two positions resets the run`() {
        val run = MissingAlbumRun()
        run.onRemoteMissing("a", 0, 1_000L)
        assertFalse(run.onRemoteMissing("a", 3, 1_100L))
        assertEquals(3, run.peekFirstIndex())
    }

    @Test
    fun `a failure outside the window resets the run`() {
        val run = MissingAlbumRun()
        run.onRemoteMissing("a", 0, 1_000L)
        assertFalse(
            run.onRemoteMissing("a", 1, 1_000L + MISSING_ALBUM_WINDOW_MS + 1),
        )
        assertEquals(1, run.peekFirstIndex())
    }

    @Test
    fun `window boundary is inclusive`() {
        val run = MissingAlbumRun()
        run.onRemoteMissing("a", 0, 1_000L)
        assertTrue(run.onRemoteMissing("a", 1, 1_000L + MISSING_ALBUM_WINDOW_MS))
    }

    @Test
    fun `missing album id clears the run`() {
        val run = MissingAlbumRun()
        run.onRemoteMissing("a", 0, 1_000L)
        assertFalse(run.onRemoteMissing(null, 1, 1_100L))
        assertNull(run.peekAlbumId())
        assertFalse(run.onRemoteMissing("a", 2, 1_200L))
    }

    @Test
    fun `negative index clears the run`() {
        val run = MissingAlbumRun()
        run.onRemoteMissing("a", 0, 1_000L)
        assertFalse(run.onRemoteMissing("a", -1, 1_100L))
        assertNull(run.peekAlbumId())
    }

    // --- findNextAlbumIndex ----------------------------------------------

    @Test
    fun `finds the first index of the next album`() {
        assertEquals(3, findNextAlbumIndex(listOf("a", "a", "a", "b"), 0))
        assertEquals(3, findNextAlbumIndex(listOf("a", "a", "a", "b"), 1))
    }

    @Test
    fun `interleaved albums stop at the first different entry`() {
        assertEquals(2, findNextAlbumIndex(listOf("a", "b", "a"), 1))
    }

    @Test
    fun `whole queue of one album returns size`() {
        assertEquals(4, findNextAlbumIndex(listOf("a", "a", "a", "a"), 1))
    }

    @Test
    fun `empty queue returns zero`() {
        assertEquals(0, findNextAlbumIndex(emptyList(), 0))
    }

    @Test
    fun `out of range start is coerced to the last index`() {
        assertEquals(3, findNextAlbumIndex(listOf("a", "a", "a"), 99))
    }

    @Test
    fun `null album ids group together`() {
        assertEquals(2, findNextAlbumIndex(listOf(null, null, "a"), 0))
        assertEquals(3, findNextAlbumIndex(listOf(null, null, null), 0))
    }

    // --- AutoSkipBurst ----------------------------------------------------

    @Test
    fun `burst trips at the default threshold`() {
        val burst = AutoSkipBurst()
        repeat(AUTO_SKIP_BURST_THRESHOLD - 1) { i ->
            burst.record(i, nowMs = 1_000L + i)
            assertFalse(burst.atThreshold())
        }
        burst.record(AUTO_SKIP_BURST_THRESHOLD - 1, nowMs = 2_000L)
        assertTrue(burst.atThreshold())
    }

    @Test
    fun `non disruptive recoveries do not count toward the bound`() {
        val burst = AutoSkipBurst()
        repeat(20) { i ->
            burst.record(i, nowMs = 1_000L + i, disruptiveEntry = false)
        }
        assertFalse(burst.atThreshold())
        assertEquals(20, burst.count)
        assertEquals(0, burst.disruptive)
    }

    @Test
    fun `recoveries inflate the aggregate but not the bound`() {
        // Album-run collapses stay non-disruptive recoveries: they extend the
        // summary but the trip stays on `disruptive`, so PAUSE_BURST can report
        // n=8 while the aggregate count is higher.
        val burst = AutoSkipBurst()
        repeat(AUTO_SKIP_BURST_THRESHOLD) { i ->
            burst.record(i * 2, nowMs = 1_000L + i, disruptiveEntry = true)
            if (i < AUTO_SKIP_BURST_THRESHOLD - 1) {
                burst.record(i * 2 + 1, nowMs = 1_000L + i, disruptiveEntry = false)
            }
        }
        assertEquals(AUTO_SKIP_BURST_THRESHOLD, burst.disruptive)
        assertEquals(2 * AUTO_SKIP_BURST_THRESHOLD - 1, burst.count)
        assertTrue(burst.atThreshold())
    }

    @Test
    fun `unreachable cached jumps are disruptive and trip the burst bound`() {
        val burst = AutoSkipBurst()
        repeat(AUTO_SKIP_BURST_THRESHOLD - 1) { i ->
            assertFalse(
                recordUnreachableCachedJump(
                    burst,
                    fromIdx = i,
                    cachedIdx = i + 2,
                    nowMs = 1_000L + i,
                ),
            )
        }
        assertTrue(
            recordUnreachableCachedJump(
                burst,
                fromIdx = AUTO_SKIP_BURST_THRESHOLD - 1,
                cachedIdx = AUTO_SKIP_BURST_THRESHOLD + 1,
                nowMs = 2_000L,
            ),
        )
        assertEquals(AUTO_SKIP_BURST_THRESHOLD, burst.disruptive)
        assertEquals(2 * AUTO_SKIP_BURST_THRESHOLD, burst.itemsSkipped)
    }

    @Test
    fun `unreachable jump with equal indices still counts one skipped`() {
        val burst = AutoSkipBurst()
        assertFalse(recordUnreachableCachedJump(burst, fromIdx = 3, cachedIdx = 3, nowMs = 1_000L))
        assertEquals(1, burst.itemsSkipped)
        assertEquals(1, burst.disruptive)
    }

    @Test
    fun `an entry after the window resets the run`() {
        val burst = AutoSkipBurst()
        burst.record(0, nowMs = 1_000L, disruptiveEntry = true)
        burst.record(1, nowMs = 2_000L, disruptiveEntry = true)
        burst.record(
            2,
            nowMs = 1_000L + AUTO_SKIP_BURST_WINDOW_MS + 1,
            disruptiveEntry = true,
        )
        assertEquals(1, burst.count)
        assertEquals(1, burst.disruptive)
        assertEquals(2, burst.firstIndex)
    }

    @Test
    fun `summary aggregates the run and clears it`() {
        val burst = AutoSkipBurst()
        burst.record(0, nowMs = 1_000L, skipped = 1, evictedDelta = 1)
        burst.record(1, nowMs = 1_500L, skipped = 1, evictedDelta = 0)
        burst.record(2, nowMs = 2_000L, skipped = 3, disruptiveEntry = false)
        val summary = burst.drainSummary()
        assertTrue(summary != null)
        assertTrue(summary!!.contains("count=3"))
        assertTrue(summary.contains("items=5"))
        assertTrue(summary.contains("firstIdx=0"))
        assertTrue(summary.contains("lastIdx=2"))
        assertTrue(summary.contains("durationMs=1000"))
        assertTrue(summary.contains("evicted=1"))
        assertTrue(summary.contains("disruptive=2"))
        assertEquals(0, burst.count)
        assertNull(burst.drainSummary())
    }

    @Test
    fun `empty burst has no summary`() {
        assertNull(AutoSkipBurst().drainSummary())
    }

    // --- chooseErrorSkipAction -------------------------------------------

    @Test
    fun `untripped circuit keeps the per-track action`() {
        assertEquals(
            ErrorSkipAction.SKIP_NEXT,
            chooseErrorSkipAction(ErrorSkipAction.SKIP_NEXT, circuitTripped = false, advancing = true),
        )
        assertEquals(
            ErrorSkipAction.LAST_TRACK_STOP,
            chooseErrorSkipAction(ErrorSkipAction.LAST_TRACK_STOP, circuitTripped = false, advancing = false),
        )
    }

    @Test
    fun `tripped advancing run pauses instead of stopping`() {
        assertEquals(
            ErrorSkipAction.PAUSE_BURST,
            chooseErrorSkipAction(ErrorSkipAction.SKIP_NEXT, circuitTripped = true, advancing = true),
        )
    }

    @Test
    fun `tripped stuck run still stops`() {
        assertEquals(
            ErrorSkipAction.CIRCUIT_STOP,
            chooseErrorSkipAction(ErrorSkipAction.LAST_TRACK_STOP, circuitTripped = true, advancing = false),
        )
        assertEquals(
            ErrorSkipAction.CIRCUIT_STOP,
            chooseErrorSkipAction(ErrorSkipAction.RADIO_IGNORE, circuitTripped = true, advancing = false),
        )
    }

    // --- sticky unplayable / Previous walk --------------------------------

    @Test
    fun `invalid content type is sticky without Room row`() {
        assertTrue(
            isStickyUnplayable(
                PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
                CacheSpanState.NONE,
                hasFailedCt = false,
            ),
        )
    }

    @Test
    fun `parse unsupported with no span is sticky`() {
        assertTrue(
            isStickyUnplayable(
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                CacheSpanState.NONE,
                hasFailedCt = false,
            ),
        )
    }

    @Test
    fun `parse unsupported with playable span is not sticky`() {
        assertFalse(
            isStickyUnplayable(
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                CacheSpanState.PLAYABLE,
                hasFailedCt = false,
            ),
        )
    }

    @Test
    fun `bare 404 is not sticky without failed_ct`() {
        assertFalse(
            isStickyUnplayable(
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                CacheSpanState.NONE,
                hasFailedCt = false,
            ),
        )
        assertTrue(
            isStickyUnplayable(
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                CacheSpanState.NONE,
                hasFailedCt = true,
            ),
        )
    }

    @Test
    fun `previous walk skips sticky neighbor`() {
        val ids = listOf("good", "bad", "cur")
        val sticky = setOf("bad")
        assertEquals(
            0,
            findPreviousPlayableIndex(
                fromIndex = 2,
                mediaItemCount = 3,
                mediaIdAt = { ids[it] },
                isStickyId = { sticky.contains(it) },
            ),
        )
    }

    @Test
    fun `previous walk returns null when only stickies behind`() {
        val ids = listOf("bad0", "bad1", "cur")
        val sticky = setOf("bad0", "bad1")
        assertNull(
            findPreviousPlayableIndex(
                fromIndex = 2,
                mediaItemCount = 3,
                mediaIdAt = { ids[it] },
                isStickyId = { sticky.contains(it) },
            ),
        )
    }

    @Test
    fun `next playable skips sticky current`() {
        val ids = listOf("bad", "good")
        assertEquals(
            1,
            findNextPlayableIndex(
                fromIndex = 0,
                mediaItemCount = 2,
                mediaIdAt = { ids[it] },
                isStickyId = { it == "bad" },
            ),
        )
    }

    @Test
    fun `priorMediaIds snapshots only behind current`() {
        assertEquals(
            listOf("a", "b"),
            priorMediaIds(2) { listOf("a", "b", "c")[it] },
        )
        assertEquals(emptyList<String>(), priorMediaIds(0) { "x" })
    }

    @Test
    fun `warmStickyCtRegistry marks failed_ct from empty process registry`() = kotlinx.coroutines.test.runTest {
        StickyUnplayableRegistry.clearForTests()
        val stickyInRoom = setOf("bad")
        warmStickyCtRegistry(listOf("good", "bad", "cur")) { stickyInRoom.contains(it) }
        assertTrue(StickyUnplayableRegistry.contains("bad"))
        assertFalse(StickyUnplayableRegistry.contains("good"))
        StickyUnplayableRegistry.clearForTests()
    }

    @Test
    fun `StickyPreviousBridge falls back to sync seek when no handler`() {
        StickyUnplayableRegistry.clearForTests()
        StickyPreviousBridge.handler = null
        StickyUnplayableRegistry.mark("bad")
        val player = io.mockk.mockk<Player>(relaxed = true)
        io.mockk.every { player.currentMediaItemIndex } returns 2
        io.mockk.every { player.mediaItemCount } returns 3
        io.mockk.every { player.getMediaItemAt(0) } returns
            androidx.media3.common.MediaItem.Builder().setMediaId("good").build()
        io.mockk.every { player.getMediaItemAt(1) } returns
            androidx.media3.common.MediaItem.Builder().setMediaId("bad").build()
        io.mockk.every { player.getMediaItemAt(2) } returns
            androidx.media3.common.MediaItem.Builder().setMediaId("cur").build()
        StickyPreviousBridge.requestPrevious(player)
        io.mockk.verify { player.seekTo(0, 0L) }
        StickyUnplayableRegistry.clearForTests()
    }
}
