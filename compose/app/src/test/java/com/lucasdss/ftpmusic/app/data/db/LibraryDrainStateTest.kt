package com.lucasdss.ftpmusic.app.data.db

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDrainStateTest {

    @Test
    fun `never drained is incomplete`() {
        assertTrue(LibraryDrainState.isDrainIncomplete(0L, ""))
        assertTrue(LibraryDrainState.isDrainIncomplete(-1L, MetadataSyncWorker.SYNC_OUTCOME_COMPLETE))
    }

    @Test
    fun `soft-stopped is incomplete even with prior drain stamp`() {
        assertTrue(
            LibraryDrainState.isDrainIncomplete(
                1_700_000_000_000L,
                MetadataSyncWorker.SYNC_OUTCOME_SOFT_STOPPED,
            ),
        )
    }

    @Test
    fun `complete drain is not incomplete`() {
        assertFalse(
            LibraryDrainState.isDrainIncomplete(
                1_700_000_000_000L,
                MetadataSyncWorker.SYNC_OUTCOME_COMPLETE,
            ),
        )
    }
}
