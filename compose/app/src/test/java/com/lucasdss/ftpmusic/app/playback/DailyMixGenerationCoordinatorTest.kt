package com.lucasdss.ftpmusic.app.playback

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DailyMixGenerationCoordinatorTest {

    @Before
    fun setUp() {
        DailyMixGenerationCoordinator.resetForTest()
    }

    @After
    fun tearDown() {
        DailyMixGenerationCoordinator.resetForTest()
    }

    @Test
    fun `tryBegin succeeds once and blocks until finish`() {
        assertTrue(DailyMixGenerationCoordinator.tryBegin())
        assertTrue(DailyMixGenerationCoordinator.isRunning)
        assertFalse("Second begin while in-flight must fail", DailyMixGenerationCoordinator.tryBegin())

        DailyMixGenerationCoordinator.finish()

        assertFalse(DailyMixGenerationCoordinator.isRunning)
        assertTrue("Begin must succeed after finish", DailyMixGenerationCoordinator.tryBegin())
        DailyMixGenerationCoordinator.finish()
    }

    @Test
    fun `shouldAttemptToday defaults to true and is suppressed after an empty outcome`() {
        assertTrue(DailyMixGenerationCoordinator.shouldAttemptToday("2026-08-19"))

        DailyMixGenerationCoordinator.markEmptyAttempt("2026-08-19")

        assertFalse("Same-day retry must be suppressed", DailyMixGenerationCoordinator.shouldAttemptToday("2026-08-19"))
        assertTrue("Next-day attempt must be allowed", DailyMixGenerationCoordinator.shouldAttemptToday("2026-08-20"))
    }

    @Test
    fun `clearEmptyAttempt allows a same-day retry`() {
        DailyMixGenerationCoordinator.markEmptyAttempt("2026-08-19")
        assertFalse(DailyMixGenerationCoordinator.shouldAttemptToday("2026-08-19"))

        DailyMixGenerationCoordinator.clearEmptyAttempt()

        assertTrue(
            "Adding a mix must unblock the same day",
            DailyMixGenerationCoordinator.shouldAttemptToday("2026-08-19"),
        )
    }
}
