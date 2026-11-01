package com.lucasdss.ftpmusic.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure decision table for the foreground sync self-heal (report §11.4). */
class SyncHealthPolicyTest {

    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L

    private fun signals(
        lastSuccessMs: Long = now - hour,
        lastWmAttemptMs: Long = now - hour,
        lastFallbackAttemptMs: Long = 0L,
        streak: Int = 0,
        intervalMs: Long = 6 * hour,
        offline: Boolean = false,
        reachable: Boolean = true,
    ) = SyncHealthPolicy.Signals(
        nowMs = now,
        lastSuccessMs = lastSuccessMs,
        lastWmAttemptMs = lastWmAttemptMs,
        lastFallbackAttemptMs = lastFallbackAttemptMs,
        fallbackStreak = streak,
        intervalMs = intervalMs,
        offline = offline,
        reachable = reachable,
    )

    @Test
    fun `never ran is not a failure - first run handles it`() {
        val verdict = SyncHealthPolicy.decide(signals(lastSuccessMs = 0, lastWmAttemptMs = 0))
        assertEquals(SyncHealthPolicy.Verdict.NeverRan, verdict)
    }

    @Test
    fun `fresh success does nothing`() {
        val verdict = SyncHealthPolicy.decide(signals(lastSuccessMs = now - hour))
        assertEquals("fresh", SyncHealthPolicy.tag(verdict))
    }

    @Test
    fun `a workmanager attempt counts as a sign of life even when the sync failed`() {
        // Success is ancient, but WM tried 1 h ago and the server was down.
        val verdict = SyncHealthPolicy.decide(
            signals(lastSuccessMs = now - 30 * hour, lastWmAttemptMs = now - hour),
        )
        assertEquals("fresh", SyncHealthPolicy.tag(verdict))
    }

    @Test
    fun `silence past interval plus grace triggers`() {
        val verdict = SyncHealthPolicy.decide(
            signals(lastSuccessMs = now - 13 * hour, lastWmAttemptMs = now - 13 * hour),
        )
        assertTrue(verdict is SyncHealthPolicy.Verdict.Trigger)
        assertEquals(13 * hour, (verdict as SyncHealthPolicy.Verdict.Trigger).silenceMs)
    }

    @Test
    fun `one millisecond short of the deadline is still fresh`() {
        val deadline = 6 * hour + SyncHealthPolicy.DEFAULT_GRACE_MS
        val verdict = SyncHealthPolicy.decide(
            signals(lastSuccessMs = now - deadline + 1, lastWmAttemptMs = now - deadline + 1),
        )
        assertEquals("fresh", SyncHealthPolicy.tag(verdict))
    }

    @Test
    fun `persisted backoff blocks a second attempt inside the step`() {
        val verdict = SyncHealthPolicy.decide(
            signals(
                lastSuccessMs = now - 40 * hour,
                lastWmAttemptMs = now - 40 * hour,
                lastFallbackAttemptMs = now - 30 * 60 * 1000L,
                streak = 1,
            ),
        )
        assertTrue(verdict is SyncHealthPolicy.Verdict.Backoff)
    }

    @Test
    fun `backoff steps escalate and cap at twenty four hours`() {
        assertEquals(hour, SyncHealthPolicy.backoffFor(0))
        assertEquals(6 * hour, SyncHealthPolicy.backoffFor(1))
        assertEquals(24 * hour, SyncHealthPolicy.backoffFor(2))
        assertEquals(24 * hour, SyncHealthPolicy.backoffFor(99))
    }

    @Test
    fun `expired backoff triggers again`() {
        val verdict = SyncHealthPolicy.decide(
            signals(
                lastSuccessMs = now - 80 * hour,
                lastWmAttemptMs = now - 80 * hour,
                lastFallbackAttemptMs = now - 7 * hour,
                streak = 1,
            ),
        )
        assertTrue(verdict is SyncHealthPolicy.Verdict.Trigger)
        assertEquals(1, (verdict as SyncHealthPolicy.Verdict.Trigger).streak)
    }

    @Test
    fun `offline wins over every other signal`() {
        val verdict = SyncHealthPolicy.decide(
            signals(lastSuccessMs = now - 400 * hour, lastWmAttemptMs = now - 400 * hour, offline = true),
        )
        assertEquals("offline", SyncHealthPolicy.tag(verdict))
    }

    @Test
    fun `unreachable server is not retried from the guard`() {
        val verdict = SyncHealthPolicy.decide(
            signals(lastSuccessMs = now - 400 * hour, lastWmAttemptMs = now - 400 * hour, reachable = false),
        )
        assertEquals("unreachable", SyncHealthPolicy.tag(verdict))
    }

    @Test
    fun `negative silence is clamped`() {
        val verdict = SyncHealthPolicy.decide(signals(lastSuccessMs = now + hour, lastWmAttemptMs = now))
        assertEquals(0L, (verdict as SyncHealthPolicy.Verdict.NotDue).silenceMs)
    }
}
