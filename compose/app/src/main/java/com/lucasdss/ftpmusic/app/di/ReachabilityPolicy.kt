package com.lucasdss.ftpmusic.app.di

/**
 * N8 (2026-11-01): the reachability boolean is a branch selector inside error
 * recovery (`CACHED_JUMP`/`STOP_NO_CACHED` vs `SKIP_NEXT`), so a single flapped
 * sample must not move it. A hard failure flips false only after
 * [REACHABILITY_FAILURE_THRESHOLD] failures inside
 * [REACHABILITY_FAILURE_WINDOW_MS] with no success between; success is instant.
 *
 * The dedicated keepalive probe is deliberately *not* subject to this: it is a
 * single-flight request with its own timeout, and it is the only signal when the
 * app is idle. Its verdict is trusted (see ReachabilityStateHolder.onProbeFailure).
 */
internal data class ReachabilityPolicyState(val consecutiveFailures: Int = 0, val firstFailureMs: Long = 0L)

internal const val REACHABILITY_FAILURE_THRESHOLD = 2
internal const val REACHABILITY_FAILURE_WINDOW_MS = 10_000L

internal fun ReachabilityPolicyState.onFailure(
    nowMs: Long,
    windowMs: Long = REACHABILITY_FAILURE_WINDOW_MS,
    threshold: Int = REACHABILITY_FAILURE_THRESHOLD,
): Pair<ReachabilityPolicyState, Boolean> {
    val inWindow = consecutiveFailures > 0 && nowMs - firstFailureMs <= windowMs
    val next = if (inWindow) {
        copy(consecutiveFailures = consecutiveFailures + 1)
    } else {
        ReachabilityPolicyState(consecutiveFailures = 1, firstFailureMs = nowMs)
    }
    return next to (next.consecutiveFailures >= threshold)
}

internal fun ReachabilityPolicyState.onSuccess(): ReachabilityPolicyState = ReachabilityPolicyState()
