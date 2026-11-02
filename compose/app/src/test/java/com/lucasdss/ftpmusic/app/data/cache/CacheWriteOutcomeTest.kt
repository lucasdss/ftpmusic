package com.lucasdss.ftpmusic.app.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheWriteOutcomeTest {

    @Test
    fun `ok outcomes are success`() {
        assertTrue(CacheWriteOutcome.OK.isSuccess())
        assertTrue(CacheWriteOutcome.OK_ALREADY_CACHED.isSuccess())
    }

    @Test
    fun `failure outcomes are not success`() {
        listOf(
            CacheWriteOutcome.ZERO_LENGTH,
            CacheWriteOutcome.NON_AUDIO,
            CacheWriteOutcome.LOCKED,
            CacheWriteOutcome.BUSY,
            CacheWriteOutcome.IO,
        ).forEach { assertFalse(it.isSuccess()) }
    }

    @Test
    fun `every outcome has a stable reason`() {
        assertEquals("ok", CacheWriteOutcome.OK.reason())
        assertEquals("already_cached", CacheWriteOutcome.OK_ALREADY_CACHED.reason())
        assertEquals("zero_length", CacheWriteOutcome.ZERO_LENGTH.reason())
        assertEquals("non_audio", CacheWriteOutcome.NON_AUDIO.reason())
        assertEquals("locked", CacheWriteOutcome.LOCKED.reason())
        assertEquals("busy", CacheWriteOutcome.BUSY.reason())
        assertEquals("io", CacheWriteOutcome.IO.reason())
    }
}
