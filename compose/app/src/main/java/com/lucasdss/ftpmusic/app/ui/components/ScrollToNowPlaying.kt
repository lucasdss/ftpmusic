package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** Suppress programmatic follow this long after a user-driven scroll (lyrics parity). */
const val NOW_PLAYING_SCROLL_SUPPRESS_MS = 3_000L

/** Layout wait: ~50 frames at 16ms before giving up on empty list. */
private const val LAYOUT_WAIT_ATTEMPTS = 50

/**
 * Index of [currentId] in [trackIds], or -1 when missing / null.
 */
fun indexOfTrackId(trackIds: List<String>, currentId: String?): Int {
    if (currentId.isNullOrEmpty() || trackIds.isEmpty()) return -1
    return trackIds.indexOfFirst { it == currentId }
}

/**
 * LazyColumn index for a track row that sits after [headerCount] leading items.
 * Returns -1 when [trackIndex] is invalid.
 */
fun resolveLazyIndex(headerCount: Int, trackIndex: Int): Int {
    if (trackIndex < 0 || headerCount < 0) return -1
    return headerCount + trackIndex
}

/**
 * Pixel scrollOffset for [LazyListState.animateScrollToItem] so the item sits
 * near the vertical center of the viewport. Negative = item higher in viewport.
 * Returns 0 when sizes unknown / too small (pin near top).
 */
fun centerScrollOffset(viewportPx: Int, itemSizePx: Int): Int {
    if (viewportPx <= 0 || itemSizePx <= 0) return 0
    val offset = (itemSizePx / 2) - (viewportPx / 2)
    return offset.coerceAtMost(0)
}

/**
 * True when [targetLazyIndex] is already roughly centered / fully visible enough
 * that a programmatic scroll would be a no-op yank (queue + config restore).
 */
fun isTargetReasonablyVisible(
    firstVisibleIndex: Int,
    lastVisibleIndex: Int,
    targetLazyIndex: Int,
    visibleItemCount: Int,
): Boolean {
    if (targetLazyIndex < 0 || visibleItemCount <= 0) return false
    if (targetLazyIndex < firstVisibleIndex || targetLazyIndex > lastVisibleIndex) return false
    // Prefer "near middle of visible window" so pin-top alone does not skip.
    val mid = (firstVisibleIndex + lastVisibleIndex) / 2
    val slack = maxOf(1, visibleItemCount / 3)
    return targetLazyIndex in (mid - slack)..(mid + slack)
}

/**
 * Prefer the target row's measured size; if not visible, use the median of
 * other visible non-huge items (skip hero-sized outliers) so center offset
 * does not pin-top from a tall header estimate.
 */
fun estimateRowSizePx(targetItemSizePx: Int?, visibleItemSizesPx: List<Int>, heroThresholdPx: Int = 240): Int {
    if (targetItemSizePx != null && targetItemSizePx > 0) return targetItemSizePx
    val rowLike = visibleItemSizesPx.filter { it in 1 until heroThresholdPx }
    if (rowLike.isEmpty()) return 0
    val sorted = rowLike.sorted()
    return sorted[sorted.size / 2]
}

/**
 * Auto-scroll [listState] to [targetLazyIndex] when [enabled].
 * Follows index / [scrollKey] changes; pauses ~3s after user scroll (ADR-0126).
 * Suppress wait loops until clear (does not drop a pending follow).
 * Skips animation when the target is already reasonably visible (config restore /
 * queue already at anchor).
 *
 * @param scrollKey extra key so fixed-index targets (e.g. queue anchor) re-scroll
 *   when the current track changes.
 * @param onlyIfNotVisible when true, skip scroll if target already reasonably visible.
 */
@Composable
fun ScrollToNowPlayingEffect(
    enabled: Boolean,
    listState: LazyListState,
    targetLazyIndex: Int,
    scrollKey: Any? = targetLazyIndex,
    suppressMs: Long = NOW_PLAYING_SCROLL_SUPPRESS_MS,
    onlyIfNotVisible: Boolean = false,
) {
    // Saveable so rotation mid-suppress does not immediately re-yank.
    var suppressUntil by rememberSaveable { mutableLongStateOf(0L) }
    var programmaticScroll by remember { mutableStateOf(false) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !programmaticScroll) {
            suppressUntil = System.currentTimeMillis() + suppressMs
        }
    }

    LaunchedEffect(enabled, targetLazyIndex, scrollKey, onlyIfNotVisible) {
        if (!enabled || targetLazyIndex < 0) return@LaunchedEffect

        // Pause while user is browsing; re-check so an extended suppress still
        // follows after the quiet window (do not drop the track-change follow).
        while (true) {
            val remaining = suppressUntil - System.currentTimeMillis()
            if (remaining <= 0L) break
            delay(remaining.coerceAtMost(suppressMs).coerceAtLeast(16L))
        }
        if (!enabled || targetLazyIndex < 0) return@LaunchedEffect
        if (System.currentTimeMillis() < suppressUntil) return@LaunchedEffect

        // Wait until LazyColumn has laid out enough items for the target index.
        var attempts = 0
        while (attempts < LAYOUT_WAIT_ATTEMPTS) {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems > targetLazyIndex) break
            delay(16L)
            attempts++
        }
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems <= 0 || targetLazyIndex >= totalItems) return@LaunchedEffect
        val safeIndex = targetLazyIndex.coerceIn(0, totalItems - 1)

        val info = listState.layoutInfo
        val visible = info.visibleItemsInfo
        if (onlyIfNotVisible && visible.isNotEmpty()) {
            val first = visible.first().index
            val last = visible.last().index
            if (isTargetReasonablyVisible(first, last, safeIndex, visible.size)) {
                return@LaunchedEffect
            }
        }

        val viewport = info.viewportEndOffset - info.viewportStartOffset
        val targetVisible = visible.firstOrNull { it.index == safeIndex }
        val itemSize = estimateRowSizePx(
            targetItemSizePx = targetVisible?.size,
            visibleItemSizesPx = visible.map { it.size },
        )
        val offset = centerScrollOffset(viewport, itemSize)

        programmaticScroll = true
        try {
            listState.animateScrollToItem(safeIndex, scrollOffset = offset)
        } finally {
            programmaticScroll = false
        }
    }
}
