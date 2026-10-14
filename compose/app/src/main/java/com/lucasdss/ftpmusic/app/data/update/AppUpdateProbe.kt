package com.lucasdss.ftpmusic.app.data.update

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Probes Play for an available update and publishes into
 * [AppUpdateAvailabilityHolder] (ADR-0140). Fail-soft — never throws to callers
 * except [CancellationException].
 *
 * In-process throttle avoids cold-start + immediate WorkManager double-hit noise.
 * Unavailable blips use a shorter backoff so Play is not hammered forever.
 */
@Singleton
class AppUpdateProbe @Inject constructor(
    private val checker: AppUpdateChecker,
    private val holder: AppUpdateAvailabilityHolder,
) {
    private val mutex = Mutex()

    @Volatile private var lastSuccessProbeMs: Long = 0L

    @Volatile private var lastFailureProbeMs: Long = 0L

    suspend fun probe(force: Boolean = false) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (!force) {
                if (lastSuccessProbeMs > 0L && now - lastSuccessProbeMs < SUCCESS_THROTTLE_MS) {
                    Log.d(TAG, "probe skipped — success throttle")
                    return
                }
                if (lastFailureProbeMs > 0L && now - lastFailureProbeMs < FAILURE_BACKOFF_MS) {
                    Log.d(TAG, "probe skipped — failure backoff")
                    return
                }
            }
            try {
                val result = withTimeout(PROBE_TIMEOUT_MS) { checker.check() }
                holder.publish(result)
                when (result) {
                    is UpdateCheckResult.Unavailable -> lastFailureProbeMs = now

                    else -> {
                        lastSuccessProbeMs = now
                        lastFailureProbeMs = 0L
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastFailureProbeMs = now
                Log.w(TAG, "probe failed: ${e.message}", e)
            }
        }
    }

    companion object {
        private const val TAG = "ftpmusic-update"
        const val SUCCESS_THROTTLE_MS = 5 * 60 * 1000L
        const val FAILURE_BACKOFF_MS = 60_000L
        const val PROBE_TIMEOUT_MS = 20_000L

        /** @deprecated Use [SUCCESS_THROTTLE_MS]. */
        const val THROTTLE_MS = SUCCESS_THROTTLE_MS
    }
}
