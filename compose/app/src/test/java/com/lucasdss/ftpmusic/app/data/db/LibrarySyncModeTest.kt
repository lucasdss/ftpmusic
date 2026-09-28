package com.lucasdss.ftpmusic.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySyncModeTest {

    @Test
    fun `resolvePeriodicMode FULL when never synced`() {
        assertEquals(LibrarySyncMode.FULL, MetadataSyncWorker.resolvePeriodicMode(0L))
    }

    @Test
    fun `resolvePeriodicMode FULL when older than interval`() {
        val now = 1_000_000_000_000L
        val old = now - MetadataSyncWorker.FULL_SYNC_INTERVAL_MS - 1
        assertEquals(LibrarySyncMode.FULL, MetadataSyncWorker.resolvePeriodicMode(old, now))
    }

    @Test
    fun `resolvePeriodicMode DELTA when recent full sync`() {
        val now = 1_000_000_000_000L
        val recent = now - 60_000L
        assertEquals(LibrarySyncMode.DELTA, MetadataSyncWorker.resolvePeriodicMode(recent, now))
    }
}
