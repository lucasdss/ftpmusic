package com.lucasdss.ftpmusic.app.playback

import android.app.Application
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * E1: tile listener lifecycle — the listener must be removed from the player
 * it was REGISTERED on, even when PlayerHolder.player switched (local↔Cast)
 * while the tile was listening. Previously onStopListening removed from the
 * current player, leaking the listener on the old one (stale tile updates).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlaybackTileServiceLifecycleTest {

    private lateinit var service: PlaybackTileService

    @Before
    fun setUp() {
        service = PlaybackTileService()
    }

    @After
    fun tearDown() {
        PlayerHolder.player = null
        PlayerHolder.isCasting = false
    }

    /** Fake player capturing registered listeners. */
    private class ListenerTrackingPlayer : Player by mockk(relaxed = true) {
        val listeners = mutableListOf<Player.Listener>()
        override fun addListener(listener: Player.Listener) {
            listeners.add(listener)
        }

        override fun removeListener(listener: Player.Listener) {
            listeners.remove(listener)
        }
    }

    @Test
    fun `onStopListening removes the listener from the registered player`() {
        val playerA = ListenerTrackingPlayer()
        PlayerHolder.player = playerA

        service.onStartListening()
        assertEquals("listener must register on start", 1, playerA.listeners.size)

        service.onStopListening()
        assertEquals("listener must be removed on stop", 0, playerA.listeners.size)
    }

    @Test
    fun `listener is removed from the ORIGINAL player after a switch`() {
        val playerA = ListenerTrackingPlayer()
        val playerB = ListenerTrackingPlayer()
        PlayerHolder.player = playerA

        service.onStartListening()
        // Player switches (Cast connect) while the tile is visible.
        PlayerHolder.player = playerB

        service.onStopListening()
        assertEquals("leaked listener on the old player (E1)", 0, playerA.listeners.size)
        assertEquals("must not touch the new player", 0, playerB.listeners.size)
    }

    @Test
    fun `re-listening after a switch registers on the new player`() {
        val playerA = ListenerTrackingPlayer()
        val playerB = ListenerTrackingPlayer()
        PlayerHolder.player = playerA

        service.onStartListening()
        PlayerHolder.player = playerB
        service.onStopListening()

        service.onStartListening()
        assertEquals(0, playerA.listeners.size)
        assertEquals("must register on the new player", 1, playerB.listeners.size)
        service.onStopListening()
        assertEquals(0, playerB.listeners.size)
    }

    @Test
    fun `onStartListening with null player does not crash`() {
        PlayerHolder.player = null
        service.onStartListening()
        service.onStopListening()
    }

    @Test
    fun `computeTileState maps empty queue with title to inactive`() {
        // onClick no longer toggles on an empty queue — the tile must not lie.
        val state = PlaybackState(title = "Song", isPlaying = false)
        val (label, subtitle, tileState) = PlaybackTileService.computeTileState(state)
        assertEquals("Song", label)
        assertEquals(android.service.quicksettings.Tile.STATE_INACTIVE, tileState)
    }
}
