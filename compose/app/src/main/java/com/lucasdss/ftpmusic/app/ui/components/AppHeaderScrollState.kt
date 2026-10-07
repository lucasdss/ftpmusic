package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * YT Music–style AppHeader collapse: scroll down hides, scroll up reveals
 * (enterAlways). Continuous float offset — no discrete show/hide (ADR 0097).
 *
 * [offsetPx] = 0 → fully shown; [offsetPx] = [headerHeightPx] → fully hidden.
 * Layout height must be `headerHeight - offset` so content reclaims space.
 */
@Stable
class AppHeaderScrollState internal constructor(private val scope: CoroutineScope) {
    var headerHeightPx by mutableFloatStateOf(0f)
        private set

    /** 0 = shown, headerHeight = hidden. Isolated float — do not hoist into shell UI state. */
    var offsetPx by mutableFloatStateOf(0f)
        private set

    private val anim = Animatable(0f)
    private var settling = false

    val visibleHeightPx: Float
        get() = (headerHeightPx - offsetPx).coerceAtLeast(0f)

    val isExpanded: Boolean
        get() = offsetPx < EXPANDED_HIT_SLOP_PX

    fun updateHeaderHeight(heightPx: Float) {
        if (heightPx <= 0f) return
        // Keep collapse fraction when height remeasures (font scale / config).
        val fraction = if (headerHeightPx > 0f) offsetPx / headerHeightPx else 0f
        headerHeightPx = heightPx
        offsetPx = (fraction * heightPx).coerceIn(0f, heightPx)
    }

    /** Instant reset — tab/detail navigation must not animate mid-collapse. */
    fun resetExpanded() {
        settling = false
        offsetPx = 0f
        scope.launch {
            anim.stop()
            anim.snapTo(0f)
        }
    }

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (headerHeightPx <= 0f) return Offset.Zero
            // Horizontal nested scroll (Search chips) must not drive header.
            if (abs(available.x) > abs(available.y)) return Offset.Zero
            if (settling) {
                settling = false
                scope.launch { anim.stop() }
            }
            val consumedY = applyScrollDelta(available.y)
            return if (consumedY == 0f) Offset.Zero else Offset(0f, consumedY)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (headerHeightPx <= 0f) return Velocity.Zero
            settle(available.y)
            return Velocity.Zero
        }
    }

    /**
     * Apply nested-scroll Y delta to the header.
     * Compose nested scroll: available.y > 0 → content pulled down (reveal header);
     * available.y < 0 → content pushed up (hide header).
     * Returns the Y consumed by the header (same sign as [deltaY]).
     */
    fun applyScrollDelta(deltaY: Float): Float {
        if (headerHeightPx <= 0f || deltaY == 0f) return 0f
        val old = offsetPx
        // Reveal when deltaY > 0 → decrease offset; hide when deltaY < 0 → increase offset.
        // Sync assignment only — no coroutine per frame (smoothness contract).
        val next = (old - deltaY).coerceIn(0f, headerHeightPx)
        offsetPx = next
        return old - next
    }

    fun snapTarget(velocityY: Float = 0f): Float {
        if (headerHeightPx <= 0f) return 0f
        // Velocity: positive = flinging content down (reveal), negative = hide.
        return when {
            velocityY > SNAP_VELOCITY_PX -> 0f
            velocityY < -SNAP_VELOCITY_PX -> headerHeightPx
            offsetPx < headerHeightPx * 0.5f -> 0f
            else -> headerHeightPx
        }
    }

    private fun settle(velocityY: Float) {
        if (headerHeightPx <= 0f) return
        val target = snapTarget(velocityY)
        if (abs(target - offsetPx) < 0.5f) {
            offsetPx = target
            return
        }
        settling = true
        scope.launch {
            try {
                anim.snapTo(offsetPx)
                anim.animateTo(
                    target,
                    animationSpec = spring(stiffness = 500f, dampingRatio = 0.9f),
                ) {
                    offsetPx = value.coerceIn(0f, headerHeightPx)
                }
                offsetPx = target
            } finally {
                settling = false
            }
        }
    }

    companion object {
        /** Taps only when essentially fully expanded. */
        const val EXPANDED_HIT_SLOP_PX = 4f
        private const val SNAP_VELOCITY_PX = 800f
    }
}

@Composable
fun rememberAppHeaderScrollState(): AppHeaderScrollState {
    val scope = rememberCoroutineScope()
    return remember { AppHeaderScrollState(scope) }
}

/** Pure helper for unit tests — same math as [AppHeaderScrollState.applyScrollDelta]. */
fun computeHeaderOffset(currentOffset: Float, headerHeight: Float, deltaY: Float): Pair<Float, Float> {
    if (headerHeight <= 0f || deltaY == 0f) return currentOffset to 0f
    val next = (currentOffset - deltaY).coerceIn(0f, headerHeight)
    val consumed = currentOffset - next
    return next to consumed
}

fun computeSnapTarget(offset: Float, headerHeight: Float, velocityY: Float): Float {
    if (headerHeight <= 0f) return 0f
    return when {
        velocityY > 800f -> 0f
        velocityY < -800f -> headerHeight
        offset < headerHeight * 0.5f -> 0f
        else -> headerHeight
    }
}
