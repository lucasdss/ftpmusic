package com.lucasdss.ftpmusic.app.ui.player

/**
 * Queue sheet section bands matching Dual playback order (ADR-0135):
 * PRIORITY → Next from (CONTEXT) → Autoplay → Queued later (LATE).
 */
data class QueueSheetBands<T>(
    val priority: List<T>,
    val continuePlaying: List<T>,
    val autoplay: List<T>,
    val late: List<T>,
) {
    /** Drag-reorder list in the same order as on-screen sections. */
    val reorderable: List<T>
        get() = priority + continuePlaying + autoplay + late

    val isEmptyUpcoming: Boolean
        get() = priority.isEmpty() && continuePlaying.isEmpty() &&
            autoplay.isEmpty() && late.isEmpty()
}

object QueueSheetGrouping {
    fun <T> groupUpcoming(
        items: List<T>,
        isPriority: (T) -> Boolean,
        isLate: (T) -> Boolean,
        isAutoplay: (T) -> Boolean,
        include: (T) -> Boolean,
    ): QueueSheetBands<T> {
        val upcoming = items.filter(include)
        // Exclude isLate from PRIORITY/continue/autoplay so bands stay disjoint
        // even if isPriorityFlags is later narrowed to true PRIORITY only.
        val priority = upcoming.filter { isPriority(it) && !isLate(it) }
        val continuePlaying = upcoming.filter { !isPriority(it) && !isAutoplay(it) && !isLate(it) }
        val autoplay = upcoming.filter { !isPriority(it) && isAutoplay(it) && !isLate(it) }
        val late = upcoming.filter { isLate(it) }
        return QueueSheetBands(priority, continuePlaying, autoplay, late)
    }
}
