package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Discrete mini-player hide while primary-tab lists scroll (ADR-0114).
 *
 * Vertical nested-scroll activity hides immediately; [idleDelayMs] after the
 * last activity reveals again. Horizontal deltas ignored (Search chips).
 * Route changes call [forceVisible].
 */
@Stable
class MiniPlayerScrollVisibility internal constructor(
    private val scope: CoroutineScope,
    private val idleDelayMs: Long = IDLE_DELAY_MS,
) {
    var visible by mutableStateOf(true)
        private set

    private var revealJob: Job? = null

    /** Pure helper for unit tests — same idle rule as [onVerticalScrollActivity]. */
    fun onVerticalScrollActivity() {
        if (visible) visible = false
        scheduleReveal()
    }

    fun onScrollSettled() {
        scheduleReveal()
    }

    fun forceVisible() {
        revealJob?.cancel()
        revealJob = null
        visible = true
    }

    private fun scheduleReveal() {
        revealJob?.cancel()
        revealJob = scope.launch {
            delay(idleDelayMs)
            visible = true
            revealJob = null
        }
    }

    companion object {
        const val IDLE_DELAY_MS = 200L
    }
}

@Composable
fun rememberMiniPlayerScrollVisibility(
    idleDelayMs: Long = MiniPlayerScrollVisibility.IDLE_DELAY_MS,
): MiniPlayerScrollVisibility {
    val scope = rememberCoroutineScope()
    return remember(idleDelayMs) { MiniPlayerScrollVisibility(scope, idleDelayMs) }
}

/**
 * Wraps [inner] so vertical nested-scroll activity drives [mini] hide/reveal
 * without changing header enterAlways math.
 */
fun NestedScrollConnection.withMiniPlayerScrollVisibility(mini: MiniPlayerScrollVisibility): NestedScrollConnection {
    val inner = this
    return object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (abs(available.y) > abs(available.x) && available.y != 0f) {
                mini.onVerticalScrollActivity()
            }
            return inner.onPreScroll(available, source)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (abs(consumed.y) > 0.5f) {
                mini.onVerticalScrollActivity()
            }
            return inner.onPostScroll(consumed, available, source)
        }

        override suspend fun onPreFling(available: Velocity): Velocity = inner.onPreFling(available)

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            val result = inner.onPostFling(consumed, available)
            mini.onScrollSettled()
            return result
        }
    }
}
