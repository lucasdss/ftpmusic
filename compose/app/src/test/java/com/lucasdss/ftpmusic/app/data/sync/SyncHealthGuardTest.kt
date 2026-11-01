package com.lucasdss.ftpmusic.app.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import com.lucasdss.ftpmusic.app.data.db.LibrarySyncMode
import com.lucasdss.ftpmusic.app.data.db.MetadataSyncWorker
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import com.lucasdss.ftpmusic.app.di.ReachabilityStateHolder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Persistence + trigger behaviour of the foreground self-heal guard. */
class SyncHealthGuardTest {

    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L
    private val store = mutableMapOf<String, Any?>()

    private val context: Context = mockk(relaxed = true)
    private val prefs: SharedPreferences = mockk(relaxed = true)
    private val editor: SharedPreferences.Editor = mockk(relaxed = true)
    private val metadataSyncWorker: MetadataSyncWorker = mockk(relaxed = true)
    private val offlineModeManager: OfflineModeManager = mockk(relaxed = true)
    private val storage: SecureStorage = mockk(relaxed = true)

    private lateinit var guard: SyncHealthGuard

    @Before
    fun setup() {
        store.clear()
        ReachabilityStateHolder.resetForTests(reachable = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns editor
        stubPrefs()
        every { editor.putLong(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<Long>()
            editor
        }
        every { editor.putInt(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<Int>()
            editor
        }
        every { editor.putString(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<String>()
            editor
        }
        every { editor.apply() } answers { Unit }
        every { storage.get(SecureStorage.KEY_SYNC_INTERVAL_HOURS) } returns "6"
        every { offlineModeManager.isOfflineEnabled() } returns false
        guard = SyncHealthGuard(context, metadataSyncWorker, offlineModeManager, storage)
        guard.nowMs = { now }
    }

    private fun stubPrefs() {
        every { prefs.getLong(any(), any()) } answers {
            (store[firstArg<String>()] as? Long) ?: secondArg()
        }
        every { prefs.getInt(any(), any()) } answers {
            (store[firstArg<String>()] as? Int) ?: secondArg()
        }
        every { prefs.getString(any(), any()) } answers {
            (store[firstArg<String>()] as? String) ?: secondArg()
        }
    }

    private fun lastSuccess(ms: Long) {
        store[MetadataSyncWorker.PREF_LAST_DELTA_SYNC_MS] = ms
    }

    private fun lastWmAttempt(ms: Long) {
        store[MetadataSyncWorker.PREF_LAST_WM_ATTEMPT_MS] = ms
    }

    @Test
    fun `fresh library does not trigger a sync`() {
        lastSuccess(now - hour)
        lastWmAttempt(now - hour)

        val verdict = guard.onAppForeground()

        assertEquals("fresh", SyncHealthPolicy.tag(verdict))
        verify(exactly = 0) { metadataSyncWorker.syncNowAsync(any(), any(), any()) }
    }

    @Test
    fun `silent workmanager triggers one delta sync`() {
        lastSuccess(now - 40 * hour)
        lastWmAttempt(now - 40 * hour)

        val verdict = guard.onAppForeground()

        assertTrue(verdict is SyncHealthPolicy.Verdict.Trigger)
        verify(exactly = 1) {
            metadataSyncWorker.syncNowAsync(mode = LibrarySyncMode.DELTA)
        }
    }

    @Test
    fun `the backoff stamp is persisted before the sync starts`() {
        lastSuccess(now - 40 * hour)
        lastWmAttempt(now - 40 * hour)

        guard.onAppForeground()

        verifyOrder {
            editor.putLong(SyncHealthGuard.PREF_FALLBACK_ATTEMPT_MS, now)
            metadataSyncWorker.syncNowAsync(mode = LibrarySyncMode.DELTA)
        }
        assertEquals(now, store[SyncHealthGuard.PREF_FALLBACK_ATTEMPT_MS])
        assertEquals(1, store[SyncHealthGuard.PREF_FALLBACK_STREAK])
    }

    @Test
    fun `a persisted stamp survives process death and blocks a retry`() {
        lastSuccess(now - 40 * hour)
        lastWmAttempt(now - 40 * hour)
        store[SyncHealthGuard.PREF_FALLBACK_ATTEMPT_MS] = now - 10 * 60 * 1000L
        store[SyncHealthGuard.PREF_FALLBACK_STREAK] = 1

        // A brand-new instance is what a process restart looks like.
        val restarted = SyncHealthGuard(context, metadataSyncWorker, offlineModeManager, storage)
        restarted.nowMs = { now }
        val verdict = restarted.onAppForeground()

        assertEquals("backoff", SyncHealthPolicy.tag(verdict))
        verify(exactly = 0) { metadataSyncWorker.syncNowAsync(any(), any(), any()) }
    }

    @Test
    fun `a success after the last fallback resets the streak`() {
        store[SyncHealthGuard.PREF_FALLBACK_ATTEMPT_MS] = now - 50 * hour
        store[SyncHealthGuard.PREF_FALLBACK_STREAK] = 2
        lastSuccess(now - hour)
        lastWmAttempt(now - hour)

        val verdict = guard.onAppForeground()

        assertEquals("fresh", SyncHealthPolicy.tag(verdict))
        assertEquals(0, store[SyncHealthGuard.PREF_FALLBACK_STREAK])
    }

    @Test
    fun `offline mode never triggers or stamps`() {
        lastSuccess(now - 400 * hour)
        lastWmAttempt(now - 400 * hour)
        every { offlineModeManager.isOfflineEnabled() } returns true

        val verdict = guard.onAppForeground()

        assertEquals("offline", SyncHealthPolicy.tag(verdict))
        verify(exactly = 0) { metadataSyncWorker.syncNowAsync(any(), any(), any()) }
        assertEquals(null, store[SyncHealthGuard.PREF_FALLBACK_ATTEMPT_MS])
    }

    @Test
    fun `unreachable server is skipped without consuming the backoff`() {
        lastSuccess(now - 400 * hour)
        lastWmAttempt(now - 400 * hour)
        ReachabilityStateHolder.resetForTests(reachable = false)

        val verdict = guard.onAppForeground()

        assertEquals("unreachable", SyncHealthPolicy.tag(verdict))
        verify(exactly = 0) { metadataSyncWorker.syncNowAsync(any(), any(), any()) }
        assertEquals(null, store[SyncHealthGuard.PREF_FALLBACK_ATTEMPT_MS])
    }
}
