package com.lucasdss.ftpmusic.app.data.db

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncExitGateTest {

    @Test
    fun `B0-1 phase complete without drainComplete does not auto-complete`() {
        val s = SyncStatus(phase = "complete", isRunning = false, runId = 3L, drainComplete = false)
        assertFalse(SyncExitGate.shouldAutoComplete(s, 3L))
        assertTrue(SyncExitGate.isSoftStopped(s, 3L))
    }

    @Test
    fun `B0-2 stale prior runId does not satisfy exit`() {
        val s = SyncStatus(phase = "complete", isRunning = false, runId = 1L, drainComplete = true)
        assertFalse(SyncExitGate.shouldAutoComplete(s, expectedRunId = 2L))
        assertFalse(SyncExitGate.isSoftStopped(s, expectedRunId = 2L))
    }

    @Test
    fun `B0-6 drainComplete with matching runId auto-completes`() {
        val s = SyncStatus(phase = "complete", isRunning = false, runId = 9L, drainComplete = true)
        assertTrue(SyncExitGate.shouldAutoComplete(s, 9L))
        assertFalse(SyncExitGate.isSoftStopped(s, 9L))
    }

    @Test
    fun `zero expectedRunId never completes`() {
        val s = SyncStatus(phase = "complete", isRunning = false, runId = 1L, drainComplete = true)
        assertFalse(SyncExitGate.shouldAutoComplete(s, 0L))
    }

    @Test
    fun `error for matching run`() {
        val s = SyncStatus(phase = "error", isRunning = false, runId = 4L, drainComplete = false)
        assertTrue(SyncExitGate.isErrorForRun(s, 4L))
        assertFalse(SyncExitGate.shouldAutoComplete(s, 4L))
    }
}
