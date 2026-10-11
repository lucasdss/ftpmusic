package com.lucasdss.ftpmusic.app.playback

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueRestoreSafelyTest {

    @Test
    fun `success runs finally and returns value`() = runTest {
        var finallyCount = 0
        val result = QueueRestoreSafely.run(
            finallyBlock = { finallyCount++ },
        ) { 42 }
        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
        assertEquals(1, finallyCount)
    }

    @Test
    fun `failure invokes onFailure runs finally and does not throw`() = runTest {
        var finallyCount = 0
        var failure: Exception? = null
        val result = QueueRestoreSafely.run(
            onFailure = { failure = it },
            finallyBlock = { finallyCount++ },
        ) {
            error("bad queue")
        }
        assertTrue(result.isFailure)
        assertEquals("bad queue", failure?.message)
        assertEquals(1, finallyCount)
    }
}
