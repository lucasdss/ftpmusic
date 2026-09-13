package com.lucasdss.ftpmusic.app.playback

import java.util.concurrent.atomic.AtomicBoolean

/**
 * App-wide gate for lazy Daily Mix generation triggered from the Home screen.
 *
 * Guards:
 * - [tryBegin]/[finish]: only one generation may run at a time (Home resume
 *   events, repeated opens, and the refresh button can otherwise overlap).
 * - [shouldAttemptToday]/[markEmptyAttempt]: when a generation run produces no
 *   mixes (empty genre pools), retrying on every Home open is wasted work — the
 *   next attempt is deferred until tomorrow (or whenever the library changes).
 *
 * Not a coroutine scope and holds no references — purely a coordination flag.
 */
object DailyMixGenerationCoordinator {

    private val inFlight = AtomicBoolean(false)

    /** Date (ISO yyyy-MM-dd) of the last run that produced zero mixes. */
    @Volatile
    private var lastEmptyAttemptDate: String? = null

    /** True when no generation is currently running; the caller then owns the
     *  slot and MUST call [finish] (use try/finally). */
    fun tryBegin(): Boolean = inFlight.compareAndSet(false, true)

    fun finish() {
        inFlight.set(false)
    }

    val isRunning: Boolean get() = inFlight.get()

    /** True when a same-day empty outcome should NOT suppress a new attempt. */
    fun shouldAttemptToday(today: String): Boolean = lastEmptyAttemptDate != today

    /** Records that a run produced no mixes on [today] (suppresses retries). */
    fun markEmptyAttempt(today: String) {
        lastEmptyAttemptDate = today
    }

    /** Test hook — resets both guards between tests (the state is global). */
    internal fun resetForTest() {
        inFlight.set(false)
        lastEmptyAttemptDate = null
    }
}
