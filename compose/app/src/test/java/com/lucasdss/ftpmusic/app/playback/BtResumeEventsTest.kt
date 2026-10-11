package com.lucasdss.ftpmusic.app.playback

import android.content.Context
import com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BtResumeEventsTest {

    @Before
    fun setUp() {
        BtResumeEvents.clear()
        DiagnosticLog.clear()
    }

    @After
    fun tearDown() {
        BtResumeEvents.clear()
        DiagnosticLog.clear()
    }

    @Test
    fun `record keeps last CAP events`() {
        repeat(BtResumeEvents.CAP + 5) { i ->
            BtResumeEvents.record("reason_$i")
        }
        assertEquals(BtResumeEvents.CAP, BtResumeEvents.size())
        val recent = BtResumeEvents.recent()
        assertTrue(recent.first().contains("reason_5"))
        assertTrue(recent.last().contains("reason_${BtResumeEvents.CAP + 4}"))
    }

    @Test
    fun `snapshotSection empty`() {
        assertTrue(BtResumeEvents.snapshotSection().contains("btResumeEvents=(empty)"))
    }

    @Test
    fun `DiagnosticLog snapshot includes btResumeEvents`() {
        BtResumeEvents.record("allow", "mode=any sdk=36")
        BtResumeEvents.record("Started", "sdk=36")
        val ctx = mockk<Context>(relaxed = true)
        val snap = DiagnosticLog.snapshot(ctx, offline = false, reachable = true)
        assertTrue(snap.contains("btResumeEvents=2"))
        assertTrue(snap.contains("allow"))
        assertTrue(snap.contains("Started"))
    }
}
