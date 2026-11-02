package com.lucasdss.ftpmusic.app.data.diagnostics

import android.content.Context
import com.lucasdss.ftpmusic.app.BuildConfig
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticLogTest {
    private val context: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        DiagnosticLog.clear()
    }

    @After
    fun tearDown() {
        DiagnosticLog.clear()
    }

    @Test
    fun ringOverflow_keepsNewestCap() {
        repeat(1020) { i -> DiagnosticLog.d("t", "line-$i") }
        assertEquals(1000, DiagnosticLog.lineCount())
        val snap = DiagnosticLog.snapshot(context, offline = false, reachable = true)
        assertFalse(snap.contains("line-0"))
        assertTrue(snap.contains("line-1019"))
        assertTrue(snap.contains("line-20"))
    }

    @Test
    fun snapshot_includesLibraryCountsWhenProvided() {
        val snap = DiagnosticLog.snapshot(
            context,
            offline = false,
            reachable = true,
            albums = 10,
            artists = 5,
            tracks = 100,
        )
        assertTrue(snap.contains("albums=10 artists=5 tracksLedger=100"))
    }

    @Test
    fun snapshot_omitsLibraryCountsWhenUnset() {
        val snap = DiagnosticLog.snapshot(context, offline = false, reachable = true)
        assertFalse(snap.contains("albums="))
    }

    @Test
    fun snapshot_containsVersionAndFlags() {
        DiagnosticLog.w("sync", "hello", RuntimeException("boom"))
        val snap = DiagnosticLog.snapshot(
            context,
            offline = true,
            reachable = false,
            lastFullSyncMs = 11L,
            lastDeltaSyncMs = 22L,
        )
        assertTrue(snap.contains("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"))
        assertTrue(snap.contains("offline=true reachable=false"))
        assertTrue(snap.contains("lastFullSyncMs=11 lastDeltaSyncMs=22"))
        assertTrue(snap.contains("| W | sync | hello"))
        assertTrue(snap.contains("RuntimeException: boom"))
    }

    @Test
    fun snapshot_includesHomeAndWmDiagnosticsWhenProvided() {
        val snap = DiagnosticLog.snapshot(
            context,
            offline = false,
            reachable = true,
            syncIntervalHours = 12,
            homeRecentFetchedMs = 99L,
            homeRecentTtlMin = 5,
            homeRecentIdsCount = 10,
            lastWmAttemptMs = 88L,
            lastWmSkipReason = "cooldown",
        )
        assertTrue(snap.contains("syncIntervalHours=12"))
        assertTrue(snap.contains("homeRecentFetchedMs=99 homeRecentTtlMin=5 homeRecentIdsCount=10"))
        assertTrue(snap.contains("lastWmAttemptMs=88 lastWmSkipReason=cooldown"))
    }

    @Test
    fun snapshot_neverRequiresTitles() {
        DiagnosticLog.d("ftpmusic-playback", "transition id=t1 reason=1 idx=0 count=3")
        val snap = DiagnosticLog.snapshot(context, offline = false, reachable = true)
        assertFalse(snap.contains("title="))
        assertTrue(snap.contains("id=t1"))
    }

    @Test
    fun clear_emptiesBuffer() {
        DiagnosticLog.e("x", "err")
        assertEquals(1, DiagnosticLog.lineCount())
        DiagnosticLog.clear()
        assertEquals(0, DiagnosticLog.lineCount())
        val snap = DiagnosticLog.snapshot(context, offline = false, reachable = true)
        assertTrue(snap.contains("---"))
        assertFalse(snap.contains("| E | x |"))
    }

    @Test
    fun scrub_throwableUrl_redacted() {
        DiagnosticLog.e(
            "sync",
            "failed",
            RuntimeException("GET https://music.example.com/rest/ping?u=x failed"),
        )
        val snap = DiagnosticLog.snapshot(context, offline = false, reachable = true)
        assertTrue(snap.contains("<url>"))
        assertFalse(snap.contains("music.example.com"))
        assertFalse(snap.contains("https://"))
        assertTrue(snap.contains("RuntimeException:"))
    }

    @Test
    fun scrub_inlineMsgUrl_redacted() {
        DiagnosticLog.w("download", "worker fail https://cdn.example/stream/track.mp3 timeout")
        val snap = DiagnosticLog.snapshot(context, offline = false, reachable = true)
        assertTrue(snap.contains("<url>"))
        assertFalse(snap.contains("cdn.example"))
    }

    @Test
    fun scrub_longMsg_truncated() {
        val long = "x".repeat(DiagnosticLog.MAX_FIELD_LEN + 50)
        DiagnosticLog.d("t", long)
        val snap = DiagnosticLog.snapshot(context, offline = false, reachable = true)
        val bodyLine = snap.lineSequence().first { it.contains("| D | t |") }
        val msg = bodyLine.substringAfter("| D | t | ")
        assertEquals(DiagnosticLog.MAX_FIELD_LEN, msg.length)
    }

    @Test
    fun scrub_nullThrowableMessage_omitsNullLiteral() {
        DiagnosticLog.e("x", "err", RuntimeException(null as String?))
        val snap = DiagnosticLog.snapshot(context, offline = false, reachable = true)
        assertTrue(snap.contains("RuntimeException"))
        assertFalse(snap.contains("RuntimeException: null"))
    }

    @Test
    fun sanitize_httpAndHttps() {
        assertEquals("hit <url> ok", DiagnosticLog.sanitize("hit http://a.b/c ok"))
        assertEquals("hit <url> ok", DiagnosticLog.sanitize("hit HTTPS://A.B/C ok"))
    }

    @Test
    fun syncFreshness_neverReadsAsNever() {
        val line = DiagnosticLog.formatSyncFreshness(
            attemptMs = 0L,
            outcome = "",
            streak = 0,
            drainCompleteMs = 0L,
        )
        assertTrue(line.contains("lastAttemptMs=never"))
        assertTrue(line.contains("lastOutcome=-"))
        assertTrue(line.contains("lastDrainCompleteMs=never"))
    }

    @Test
    fun syncFreshness_reportsAttemptAndSoftStop() {
        val line = DiagnosticLog.formatSyncFreshness(
            attemptMs = 1_700_000_000_000L,
            outcome = "soft_stop",
            streak = 3,
            drainCompleteMs = 1_699_000_000_000L,
        )
        assertTrue(line.contains("lastAttemptMs=1700000000000"))
        assertTrue(line.contains("lastOutcome=soft_stop"))
        assertTrue(line.contains("softStopStreak=3"))
        assertTrue(line.contains("lastDrainCompleteMs=1699000000000"))
    }

    @Test
    fun snapshot_includesSyncFreshnessWhenProvided() {
        val snap = DiagnosticLog.snapshot(
            context,
            offline = false,
            reachable = true,
            lastSyncAttemptMs = 123L,
            lastSyncOutcome = "success",
            softStopStreak = 0,
        )
        assertTrue(snap.contains("syncFreshness lastAttemptMs=123 lastOutcome=success"))
    }

    @Test
    fun append_includesOneLevelOfCause() {
        DiagnosticLog.e(
            "playback",
            "player error",
            RuntimeException("wrapper", IllegalStateException("body is HTML")),
        )
        val snap = DiagnosticLog.snapshot(context, offline = false, reachable = true)
        assertTrue(snap.contains("RuntimeException: wrapper caused_by IllegalStateException: body is HTML"))
    }

    @Test
    fun append_causeChainStopsAtTwoLevels() {
        val deep = IllegalStateException(
            "level3",
            IllegalArgumentException("level4"),
        )
        DiagnosticLog.e("playback", "player error", RuntimeException("level2", deep))
        val snap = DiagnosticLog.snapshot(context, offline = false, reachable = true)
        assertTrue(snap.contains("level2"))
        assertTrue(snap.contains("level3"))
        assertFalse(snap.contains("level4"))
    }

    @Test
    fun append_causeChainSanitisesUrls() {
        DiagnosticLog.e(
            "playback",
            "player error",
            RuntimeException("boom", java.io.IOException("GET https://music.example.com/x failed")),
        )
        val snap = DiagnosticLog.snapshot(context, offline = false, reachable = true)
        assertTrue(snap.contains("<url>"))
        assertFalse(snap.contains("music.example.com"))
    }

    @Test
    fun append_causeChainIsBounded() {
        DiagnosticLog.e(
            "playback",
            "player error",
            RuntimeException("x".repeat(500), IllegalStateException("y".repeat(500))),
        )
        val line = DiagnosticLog.snapshot(context, offline = false, reachable = true)
            .lineSequence()
            .first { it.contains("player error") }
        val suffix = line.substringAfterLast(" | ")
        assertTrue("suffix length=${suffix.length}", suffix.length <= DiagnosticLog.MAX_FIELD_LEN)
    }
}
