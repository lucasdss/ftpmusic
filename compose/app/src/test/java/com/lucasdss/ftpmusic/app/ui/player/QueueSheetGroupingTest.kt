package com.lucasdss.ftpmusic.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueSheetGroupingTest {

    private data class Row(
        val id: String,
        val isPriority: Boolean = false,
        val isLate: Boolean = false,
        val isAutoplay: Boolean = false,
        val isCurrent: Boolean = false,
        val afterPlayhead: Boolean = true,
    )

    @Test
    fun `sheet bands put autoplay before late matching Dual merge`() {
        val rows = listOf(
            Row("cur", isCurrent = true, afterPlayhead = false),
            Row("pri", isPriority = true),
            Row("ctx"),
            Row("late", isLate = true, isPriority = true),
            Row("ap", isAutoplay = true),
        )
        val bands = QueueSheetGrouping.groupUpcoming(
            rows,
            isPriority = { it.isPriority },
            isLate = { it.isLate },
            isAutoplay = { it.isAutoplay },
            include = { !it.isCurrent && it.afterPlayhead },
        )
        assertEquals(listOf("pri"), bands.priority.map { it.id })
        assertEquals(listOf("ctx"), bands.continuePlaying.map { it.id })
        assertEquals(listOf("ap"), bands.autoplay.map { it.id })
        assertEquals(listOf("late"), bands.late.map { it.id })
        assertEquals(listOf("pri", "ctx", "ap", "late"), bands.reorderable.map { it.id })
    }

    @Test
    fun `late stays out of continue even if isPriority false`() {
        // Defensive: isPriorityFlags narrowed to PRIORITY-only must not dual-list LATE.
        val rows = listOf(
            Row("ctx"),
            Row("orphan-late", isLate = true, isPriority = false),
            Row("ap", isAutoplay = true),
        )
        val bands = QueueSheetGrouping.groupUpcoming(
            rows,
            isPriority = { it.isPriority },
            isLate = { it.isLate },
            isAutoplay = { it.isAutoplay },
            include = { true },
        )
        assertEquals(emptyList<String>(), bands.priority.map { it.id })
        assertEquals(listOf("ctx"), bands.continuePlaying.map { it.id })
        assertEquals(listOf("ap"), bands.autoplay.map { it.id })
        assertEquals(listOf("orphan-late"), bands.late.map { it.id })
    }
}
