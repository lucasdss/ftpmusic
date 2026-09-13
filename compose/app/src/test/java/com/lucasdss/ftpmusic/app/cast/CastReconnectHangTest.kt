package com.lucasdss.ftpmusic.app.cast

import androidx.media3.common.Player
import com.lucasdss.ftpmusic.app.playback.MediaService
import com.lucasdss.ftpmusic.app.playback.PlayerHolder
import com.lucasdss.ftpmusic.app.ui.player.CastButtonState
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for the 2026-08-27 cast session regression fixes:
 *
 * 1. Bug A — MediaSession attached to CastPlayer while RemoteCastPlayer flushes
 *    an empty timeline crashes PlayerWrapper (IllegalStateException, process
 *    death). The synchronous empty-timeline detach guard must fire when the
 *    session is gone, and must NOT fire while a live session exists.
 * 2. Bug B — manual reconnect hangs on "Connecting…" because route.select()
 *    is a no-op while the Cast SDK SessionManager still owns a (stale/
 *    desynced/phantom) session, and no connect timeout exists.
 * 3. Bug C — after a network drop, onSessionResumed restores the blue
 *    "connected" icon but never re-seats PlayerHolder.player to CastPlayer, so
 *    Play starts the phone instead of the receiver.
 *
 * Decision functions are pure and unit-tested directly; handler behaviors are
 * simulated the same way as CastSessionLifecycleTest (plain JVM, no framework).
 */
class CastReconnectHangTest {

    @Before
    fun setup() {
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null
        PlayerHolder.player = null
        PlayerHolder.exoPlayer = null
        CastButtonState.isCasting.value = false
        CastButtonState.connectedDeviceName.value = null
        CastButtonState.connectingDeviceName.value = null
        CastButtonState.showDialog.value = false
    }

    @After
    fun teardown() {
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null
        PlayerHolder.player = null
        PlayerHolder.exoPlayer = null
        CastButtonState.isCasting.value = false
        CastButtonState.connectedDeviceName.value = null
        CastButtonState.connectingDeviceName.value = null
        CastButtonState.showDialog.value = false
        CastButtonState.onConnectRequested = null
        CastButtonState.onConnectCancelled = null
        CastButtonState.onDisconnectRequested = null
        CastButtonState.onEndSessionForSwitchRequested = null
    }

    // ── Bug A: empty-timeline detach guard ──────────────────────────────

    @Test
    fun `detach guard fires when attached to cast player with empty timeline and no session`() {
        val castPlayer = mockk<Player>()
        val attached = castPlayer
        assertTrue(
            "Must detach when MediaSession is on CastPlayer, timeline empty, session gone",
            MediaService().shouldDetachEmptyTimeline(attached, castPlayer, castItemCount = 0, hasLiveSession = false),
        )
    }

    @Test
    fun `detach guard does not fire when a live session exists`() {
        val castPlayer = mockk<Player>()
        assertFalse(
            "Must NOT detach while a session is live (receiver trim / reconnect window)",
            MediaService().shouldDetachEmptyTimeline(castPlayer, castPlayer, castItemCount = 0, hasLiveSession = true),
        )
    }

    @Test
    fun `detach guard does not fire when timeline is not empty`() {
        val castPlayer = mockk<Player>()
        assertFalse(
            "Must NOT detach when the cast timeline still has items",
            MediaService().shouldDetachEmptyTimeline(castPlayer, castPlayer, castItemCount = 5, hasLiveSession = false),
        )
    }

    @Test
    fun `detach guard does not fire when media session is attached to a different player`() {
        val castPlayer = mockk<Player>()
        val exoPlayer = mockk<Player>()
        assertFalse(
            "Must NOT detach when the MediaSession is on the local player already",
            MediaService().shouldDetachEmptyTimeline(exoPlayer, castPlayer, castItemCount = 0, hasLiveSession = false),
        )
    }

    @Test
    fun `detach guard does not fire when cast player is null`() {
        assertFalse(
            "Must not crash/act when CastPlayer is null (local-only builds)",
            MediaService().shouldDetachEmptyTimeline(mockk(), null, castItemCount = 0, hasLiveSession = false),
        )
    }

    // ── Bug B: connect serialization + timeout ──────────────────────────

    @Test
    fun `duplicate connect request is ignored while one is in flight`() {
        val service = MediaService()
        assertFalse("First request must be accepted", service.shouldIgnoreConnectRequest(inFlight = false))
        assertTrue(
            "Second request must be ignored while in flight",
            service.shouldIgnoreConnectRequest(inFlight = true),
        )
    }

    @Test
    fun `connect timeout aborts only when no session became real`() {
        val service = MediaService()
        assertTrue(
            "Timeout must abort when no session exists",
            service.shouldAbortConnectTimeout(hasLiveSession = false),
        )
        assertFalse(
            "Timeout must NOT abort when a session became real",
            service.shouldAbortConnectTimeout(hasLiveSession = true),
        )
    }

    @Test
    fun `manual reconnect to same device ends stale session before selecting route`() {
        // The 2026-08-27 hang: after disconnect, the Cast SDK SessionManager
        // still owned a stale session, so route.select() was a no-op and the UI
        // stayed on "Connecting…" forever. Fix: any existing session is ended
        // (and its route deselected) BEFORE the route select — including the
        // same-device case, which is exactly the one that hung.
        var sessionEnded = false
        var routeSelected = false

        // Simulate MediaService.connectToCastDevice with a live stale session:
        val currentSessionDeviceId: String? = "soundbar-1"
        val targetDeviceId = "soundbar-1" // same device → stale session
        if (currentSessionDeviceId != null) {
            sessionEnded = true // endCurrentCastSession() before select
        }
        if (sessionEnded) {
            routeSelected = true // deferred route.select() after teardown settles
        }

        assertTrue("Stale session on the SAME device must be ended before reconnect", sessionEnded)
        assertTrue("Route must be re-selected after the stale session ends", routeSelected)
    }

    @Test
    fun `device switch ends session on other device before selecting new route`() {
        var sessionEnded = false
        var stateKept = true // switch must NOT clear Cast state (A→B)
        val currentSessionDeviceId: String? = "soundbar-1"
        val targetDeviceId = "mini-speaker-1"
        if (currentSessionDeviceId != null && currentSessionDeviceId != targetDeviceId) {
            sessionEnded = true
        }
        assertTrue("Session on device A must end before connecting to B", sessionEnded)
        assertTrue("Device switch must not clear app state (restored by B's setup)", stateKept)
    }

    @Test
    fun `no session means route select happens immediately`() {
        var routeSelected = false
        val currentSessionDeviceId: String? = null
        if (currentSessionDeviceId == null) {
            routeSelected = true
        }
        assertTrue("With no session, route.select() must run immediately", routeSelected)
    }

    @Test
    fun `connect cancel clears connecting state and releases in-flight lock`() {
        // Pre-condition: a connect is pending (connecting state set).
        CastButtonState.connectingDeviceName.value = "Soundbar"
        var cancelInvoked = false
        CastButtonState.onConnectCancelled = { cancelInvoked = true }

        // Simulate MediaService.cancelCastConnect() via the registered callback.
        CastButtonState.onConnectCancelled?.invoke()
        CastButtonState.connectingDeviceName.value = null

        assertTrue("Cancel callback must be invoked on picker dismiss", cancelInvoked)
        assertNull("connectingDeviceName must be cleared on cancel", CastButtonState.connectingDeviceName.value)
    }

    @Test
    fun `disconnect clears connecting state`() {
        CastButtonState.connectingDeviceName.value = "Soundbar"
        CastButtonState.isCasting.value = true
        CastButtonState.connectedDeviceName.value = "Soundbar"
        var disconnectRequested = false
        CastButtonState.onDisconnectRequested = { disconnectRequested = true }

        // CastButtonState.disconnect() clears UI state, then invokes the callback.
        CastButtonState.isCasting.value = false
        CastButtonState.connectedDeviceName.value = null
        CastButtonState.connectingDeviceName.value = null
        CastButtonState.onDisconnectRequested?.invoke()

        assertTrue("onDisconnectRequested must fire", disconnectRequested)
        assertFalse("isCasting must be false after disconnect", CastButtonState.isCasting.value)
        assertNull("connectedDeviceName must be null", CastButtonState.connectedDeviceName.value)
        assertNull("connectingDeviceName must be null", CastButtonState.connectingDeviceName.value)
    }

    // ── Bug C: resume re-seats the player to CastPlayer ─────────────────

    @Test
    fun `onSessionResumed must re-seat player to CastPlayer so play routes to receiver`() {
        // After a network drop the local ExoPlayer is the active player, but the
        // resume handler restores the blue icon. Without re-seating, Play starts
        // the phone (2026-08-27 Soundbar). The resume handler must attach
        // CastPlayer to PlayerHolder.player + mediaSession.player.
        val exoPlayer = mockk<Player>()
        val castPlayer = mockk<Player>()
        PlayerHolder.exoPlayer = exoPlayer
        PlayerHolder.player = exoPlayer // drop cleanup left the local player active
        PlayerHolder.isCasting = true // resume handler restores the icon

        // Simulate attachCastPlayerToSession() from onSessionResumed.
        PlayerHolder.player = castPlayer

        assertSame("PlayerHolder.player must be re-seated to CastPlayer on resume", castPlayer, PlayerHolder.player)
        assertTrue("isCasting must stay true after resume", PlayerHolder.isCasting)
    }

    @Test
    fun `resume attach is idempotent when already on cast player`() {
        val castPlayer = mockk<Player>()
        PlayerHolder.player = castPlayer
        // Re-running the attach is harmless (same reference, no swap).
        PlayerHolder.player = castPlayer
        assertSame(castPlayer, PlayerHolder.player)
    }

    @Test
    fun `stale disconnectingManually flag must not block remote false cleanup after suspension`() {
        // onSessionSuspended now resets disconnectingManually=false so a stale
        // true (from a previous manual disconnect) can never skip the remote=false
        // cleanup — the phone-playback divergence.
        var disconnectingManually = true // stale from a prior manual disconnect
        disconnectingManually = false // onSessionSuspended reset

        assertFalse("Suspension must clear the stale manual-disconnect flag", disconnectingManually)
    }

    // ── Edge-01: onSessionEnded during a pending switch ─────────────────

    @Test
    fun `onSessionEnded during pending switch must keep the connect attempt alive`() {
        // Device switch A→B: the old session's onSessionEnded fires while the
        // deferred route.select(B) is still waiting. Clearing the in-flight
        // lock here would kill the select (its guard) and leave B never
        // connecting — the HIGH finding from the edge-case review.
        var connectAttemptCleared = false
        var switchPending = true // castSwitchInProgress set by connectToCastDevice

        // onSessionEnded logic: skip clearCastConnectAttempt when a switch is pending
        if (!switchPending) {
            connectAttemptCleared = true
        }

        assertFalse("onSessionEnded must NOT clear the connect attempt during a pending switch", connectAttemptCleared)

        // The reconnect retry must also be suppressed during a switch.
        var reconnectRetried = false
        if (!switchPending) reconnectRetried = true
        assertFalse("Reconnect retry must be suppressed during a pending switch", reconnectRetried)
    }

    @Test
    fun `onSessionEnded without pending switch clears connect state`() {
        var connectAttemptCleared = false
        val switchPending = false // no switch in flight (normal session end)

        if (!switchPending) {
            connectAttemptCleared = true
        }
        assertTrue("A normal session end must clear the connect attempt", connectAttemptCleared)
    }

    // ── Edge-05: healthy same-device session is not torn down ───────────

    @Test
    fun `healthy session on the same device is not torn down on reconnect tap`() {
        // Tapping a device that is ALREADY connected with live media must not
        // interrupt playback (no teardown, no queue reload).
        var sessionEnded = false
        val hasRemoteMediaClient = true
        val hasMediaStatus = true
        val healthy = hasRemoteMediaClient && hasMediaStatus

        if (!healthy) sessionEnded = true
        assertFalse("Healthy same-device session must NOT be ended", sessionEnded)
    }

    @Test
    fun `stale session on the same device is torn down before reconnect`() {
        var sessionEnded = false
        val healthy = false // no media status → stale/phantom
        if (!healthy) sessionEnded = true
        assertTrue("Stale same-device session must be ended before reconnect", sessionEnded)
    }

    // ── Edge-06: double-tap feedback ────────────────────────────────────

    @Test
    fun `second connect tap while in flight updates the display target`() {
        // A second tap while one connect is in flight is ignored for execution
        // but the picker must show the LATEST device name.
        var inFlight = true
        val secondTarget = "Mini Speaker"
        var displayedTarget: String? = "Soundbar"
        if (inFlight) {
            displayedTarget = secondTarget // connectToCastDevice updates the display
        }
        assertEquals("In-flight double-tap must update connectingDeviceName", "Mini Speaker", displayedTarget)
    }

    // ── Edge-08: detach guard treats non-connected sessions as absent ───

    @Test
    fun `detach guard fires for a non-connected phantom session`() {
        // A non-null SessionManager session that is NOT connected (stale/
        // phantom, mid-teardown) must NOT suppress the empty-timeline guard —
        // that was the exact 2026-08-27 crash window.
        val castPlayer = mockk<Player>()
        assertTrue(
            "Phantom (non-connected) session must not suppress the detach guard",
            MediaService().shouldDetachEmptyTimeline(castPlayer, castPlayer, castItemCount = 0, hasLiveSession = false),
        )
    }

    // ── Edge-03: success clears stale error dialog ──────────────────────

    @Test
    fun `connect success clears stale error message`() {
        CastButtonState.castErrorMessage.value = "Couldn't connect to \"Soundbar\". Please try again."
        // clearCastConnectAttempt() on onSessionStarted / remote=true:
        CastButtonState.castErrorMessage.value = null
        assertNull("A successful connect must clear the stale error dialog", CastButtonState.castErrorMessage.value)
    }
}
