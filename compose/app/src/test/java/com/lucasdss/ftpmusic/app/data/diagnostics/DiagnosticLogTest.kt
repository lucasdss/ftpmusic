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
        assertTrue(snap.contains("albums=10 artists=5 tracks=100"))
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
}
