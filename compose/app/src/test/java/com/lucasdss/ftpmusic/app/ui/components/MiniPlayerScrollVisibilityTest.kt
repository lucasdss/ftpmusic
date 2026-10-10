package com.lucasdss.ftpmusic.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ADR-0114 — discrete mini hide / 200ms idle reveal. */
@OptIn(ExperimentalCoroutinesApi::class)
class MiniPlayerScrollVisibilityTest {

    private fun TestScope.newVisibility(): MiniPlayerScrollVisibility =
        MiniPlayerScrollVisibility(this, idleDelayMs = MiniPlayerScrollVisibility.IDLE_DELAY_MS)

    @Test
    fun `starts visible`() = runTest {
        val mini = newVisibility()
        assertTrue(mini.visible)
    }

    @Test
    fun `vertical scroll activity hides immediately`() = runTest {
        val mini = newVisibility()
        mini.onVerticalScrollActivity()
        assertFalse(mini.visible)
    }

    @Test
    fun `idle delay reveals after last activity`() = runTest(StandardTestDispatcher()) {
        val mini = MiniPlayerScrollVisibility(this, idleDelayMs = 200L)
        mini.onVerticalScrollActivity()
        assertFalse(mini.visible)
        advanceTimeBy(199)
        runCurrent()
        assertFalse(mini.visible)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(mini.visible)
    }

    @Test
    fun `scroll during idle window keeps hidden and resets delay`() = runTest(StandardTestDispatcher()) {
        val mini = MiniPlayerScrollVisibility(this, idleDelayMs = 200L)
        mini.onVerticalScrollActivity()
        advanceTimeBy(150)
        runCurrent()
        assertFalse(mini.visible)
        mini.onVerticalScrollActivity()
        advanceTimeBy(150)
        runCurrent()
        assertFalse(mini.visible)
        advanceTimeBy(50)
        runCurrent()
        assertTrue(mini.visible)
    }

    @Test
    fun `forceVisible cancels pending reveal and shows`() = runTest(StandardTestDispatcher()) {
        val mini = MiniPlayerScrollVisibility(this, idleDelayMs = 200L)
        mini.onVerticalScrollActivity()
        assertFalse(mini.visible)
        mini.forceVisible()
        assertTrue(mini.visible)
        advanceTimeBy(500)
        runCurrent()
        assertTrue(mini.visible)
    }

    @Test
    fun `onScrollSettled schedules reveal`() = runTest(StandardTestDispatcher()) {
        val mini = MiniPlayerScrollVisibility(this, idleDelayMs = 200L)
        mini.onVerticalScrollActivity()
        mini.onScrollSettled()
        advanceTimeBy(200)
        runCurrent()
        assertTrue(mini.visible)
    }

    @Test
    fun `idle postScroll without fling reveals after delay`() = runTest(StandardTestDispatcher()) {
        val mini = MiniPlayerScrollVisibility(this, idleDelayMs = 200L)
        val noop = object : NestedScrollConnection {}
        val conn = noop.withMiniPlayerScrollVisibility(mini)

        conn.onPreScroll(Offset(0f, -40f), NestedScrollSource.UserInput)
        assertFalse(mini.visible)
        // Drag released — idle postScroll, no onPostFling.
        conn.onPostScroll(Offset.Zero, Offset.Zero, NestedScrollSource.UserInput)
        advanceTimeBy(200)
        runCurrent()
        assertTrue(mini.visible)
    }

    @Test
    fun `postFling settle still reveals`() = runTest(StandardTestDispatcher()) {
        val mini = MiniPlayerScrollVisibility(this, idleDelayMs = 200L)
        val noop = object : NestedScrollConnection {}
        val conn = noop.withMiniPlayerScrollVisibility(mini)

        conn.onPreScroll(Offset(0f, -40f), NestedScrollSource.UserInput)
        assertFalse(mini.visible)
        conn.onPostFling(Velocity.Zero, Velocity.Zero)
        advanceTimeBy(200)
        runCurrent()
        assertTrue(mini.visible)
    }
}
