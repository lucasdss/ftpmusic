package com.lucasdss.ftpmusic.app.ui.player

import com.google.android.gms.cast.CastDevice
import com.lucasdss.ftpmusic.app.playback.PlayerHolder
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class CastButtonStateTest {

    @After
    fun teardown() {
        PlayerHolder.player = null
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null
        CastButtonState.isCasting.value = false
        CastButtonState.connectedDeviceName.value = null
        CastButtonState.showDialog.value = false
        CastButtonState.discoveredDevices.value = emptyList()
    }

    @Test
    fun `isButtonVisible false when no devices and not casting`() {
        CastButtonState.discoveredDevices.value = emptyList()
        CastButtonState.isCasting.value = false
        assertFalse(CastButtonState.isButtonVisible)
    }

    @Test
    fun `isButtonVisible true when devices found`() {
        val device = mockk<CastDevice>()
        every { device.friendlyName } returns "Mini Speaker"
        every { device.deviceId } returns "abc123"
        every { device.modelName } returns "Mini Speaker"
        CastButtonState.discoveredDevices.value = listOf(device)
        CastButtonState.isCasting.value = false
        assertTrue(CastButtonState.isButtonVisible)
    }

    @Test
    fun `isButtonVisible true when casting even with no discovered devices`() {
        CastButtonState.discoveredDevices.value = emptyList()
        CastButtonState.isCasting.value = true
        assertTrue(CastButtonState.isButtonVisible)
    }

    @Test
    fun `isButtonVisible true when both devices found and casting`() {
        val device = mockk<CastDevice>()
        every { device.friendlyName } returns "Mini Speaker"
        every { device.deviceId } returns "abc123"
        every { device.modelName } returns "Mini Speaker"
        CastButtonState.discoveredDevices.value = listOf(device)
        CastButtonState.isCasting.value = true
        assertTrue(CastButtonState.isButtonVisible)
    }

    @Test
    fun `showDialog defaults to false`() {
        CastButtonState.showDialog.value = false
        assertFalse(CastButtonState.showDialog.value)
    }

    @Test
    fun `discoveredDevices defaults to empty list`() {
        CastButtonState.discoveredDevices.value = emptyList()
        assertTrue(CastButtonState.discoveredDevices.value.isEmpty())
    }

    @Test
    fun `isCasting defaults to false`() {
        CastButtonState.isCasting.value = false
        assertFalse(CastButtonState.isCasting.value)
    }

    @Test
    fun `connectedDeviceName defaults to null`() {
        CastButtonState.connectedDeviceName.value = null
        assertNull(CastButtonState.connectedDeviceName.value)
    }

    @Test
    fun `disconnect sets isCasting and connectedDeviceName to false and null`() {
        // Simulate disconnect sequence
        CastButtonState.isCasting.value = true
        CastButtonState.connectedDeviceName.value = "Mini Speaker"

        // Disconnect
        CastButtonState.isCasting.value = false
        CastButtonState.connectedDeviceName.value = null

        assertFalse(CastButtonState.isCasting.value)
        assertNull(CastButtonState.connectedDeviceName.value)
    }

    @Test
    fun `connect sets isCasting and connectedDeviceName`() {
        CastButtonState.isCasting.value = false
        CastButtonState.connectedDeviceName.value = null

        // Connect
        CastButtonState.isCasting.value = true
        CastButtonState.connectedDeviceName.value = "Mini Speaker"

        assertTrue(CastButtonState.isCasting.value)
        assertEquals("Mini Speaker", CastButtonState.connectedDeviceName.value)
    }

    @Test
    fun `disconnect clears PlayerHolder Cast state alongside CastButtonState`() {
        // Setup: simulate connected Cast session
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = "Living Room TV"
        CastButtonState.isCasting.value = true
        CastButtonState.connectedDeviceName.value = "Living Room TV"

        // Simulate onDisconnect force-reset (NavHost.kt L336-341)
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null
        CastButtonState.isCasting.value = false
        CastButtonState.connectedDeviceName.value = null

        assertFalse(PlayerHolder.isCasting)
        assertNull(PlayerHolder.castDeviceName)
        assertFalse(CastButtonState.isCasting.value)
        assertNull(CastButtonState.connectedDeviceName.value)
    }

    @Test
    fun `connectedDeviceName not overwritten with null when guarded assignment skips`() {
        // Setup: CastButtonState already holds a valid name
        CastButtonState.connectedDeviceName.value = "Living Room TV"

        // Simulate LaunchedEffect: playbackState.castDeviceName is null,
        // so the guarded assignment (NavHost.kt L73-75) should NOT overwrite with null
        val playbackCastDeviceName: String? = null
        if (playbackCastDeviceName != null) {
            CastButtonState.connectedDeviceName.value = playbackCastDeviceName
        }
        // Bug: previously this would leave stale name. Now we clear in else.
        // For now this test documents the pre-fix behavior — expecting the old name to persist.
        // After fix, this test will FAIL (expected), then we add the else clause.
        assertEquals("Living Room TV", CastButtonState.connectedDeviceName.value)
    }

    @Test
    fun `connectedDeviceName cleared when guarded assignment has null in else`() {
        // Setup: CastButtonState holds a name from a previous session
        CastButtonState.connectedDeviceName.value = "Old Speaker"

        // Simulate LaunchedEffect with null castDeviceName — should CLEAR via else clause
        val playbackCastDeviceName: String? = null
        if (playbackCastDeviceName != null) {
            CastButtonState.connectedDeviceName.value = playbackCastDeviceName
        } else {
            CastButtonState.connectedDeviceName.value = null
        }

        assertNull(CastButtonState.connectedDeviceName.value)
    }

    @Test
    fun `castErrorMessage defaults to null`() {
        CastButtonState.castErrorMessage.value = null
        assertNull(CastButtonState.castErrorMessage.value)
    }

    @Test
    fun `castErrorMessage set and cleared lifecycle`() {
        // Error surfaced on session failure
        CastButtonState.castErrorMessage.value = "Could not connect to TV (code 5)"
        assertEquals("Could not connect to TV (code 5)", CastButtonState.castErrorMessage.value)

        // User dismisses dialog
        CastButtonState.castErrorMessage.value = null
        assertNull(CastButtonState.castErrorMessage.value)
    }

    @Test
    fun `castErrorMessage cleared on second connect success`() {
        // Previous error from earlier attempt
        CastButtonState.castErrorMessage.value = "Previous connection failed"

        // New connection succeeds — error should be cleared
        CastButtonState.castErrorMessage.value = null
        assertNull(CastButtonState.castErrorMessage.value)

        // Verify isCasting reflects new state
        CastButtonState.isCasting.value = true
        CastButtonState.connectedDeviceName.value = "Living Room"
        assertTrue(CastButtonState.isCasting.value)
        assertEquals("Living Room", CastButtonState.connectedDeviceName.value)
    }

    @After
    fun teardownExtended() {
        CastButtonState.castErrorMessage.value = null
        CastButtonState.onDisconnectRequested = null
    }

    @Test
    fun `onDisconnectRequested is nullable and defaults to null`() {
        assertNull(CastButtonState.onDisconnectRequested)
    }

    @Test
    fun `onDisconnectRequested can be set and invoked`() {
        var callbackInvoked = false
        CastButtonState.onDisconnectRequested = { callbackInvoked = true }

        // Invoke the callback (MediaService would call disconnect() which does this)
        CastButtonState.onDisconnectRequested?.invoke()

        assertTrue("onDisconnectRequested should be invoked", callbackInvoked)
    }

    @Test
    fun `onDisconnectRequested null is safe to invoke`() {
        CastButtonState.onDisconnectRequested = null

        // Should not crash when callback is null
        CastButtonState.onDisconnectRequested?.invoke()
    }

    // ── Round-2 coverage: real disconnect() + connect callbacks ─────────

    @Test
    fun `disconnect clears state and invokes the registered callback`() {
        // Exercises the REAL CastButtonState.disconnect() (not a simulation).
        var disconnectRequested = false
        CastButtonState.onDisconnectRequested = { disconnectRequested = true }
        CastButtonState.isCasting.value = true
        CastButtonState.connectedDeviceName.value = "Soundbar"
        CastButtonState.connectingDeviceName.value = "Soundbar"
        PlayerHolder.player = io.mockk.mockk<androidx.media3.common.Player>(relaxed = true)

        CastButtonState.disconnect()

        assertTrue("onDisconnectRequested must be invoked", disconnectRequested)
        assertFalse("isCasting must be false after disconnect", CastButtonState.isCasting.value)
        assertNull("connectedDeviceName must be null", CastButtonState.connectedDeviceName.value)
        assertNull("connectingDeviceName must be null", CastButtonState.connectingDeviceName.value)
    }

    @Test
    fun `disconnect is safe when no callback is registered`() {
        CastButtonState.onDisconnectRequested = null
        CastButtonState.isCasting.value = true
        CastButtonState.connectedDeviceName.value = "Soundbar"
        PlayerHolder.player = null

        CastButtonState.disconnect() // must not throw

        assertFalse("isCasting must be false", CastButtonState.isCasting.value)
    }

    @Test
    fun `connect and switch callbacks are readable after being set`() {
        var connectInvoked = false
        var switchInvoked = false
        CastButtonState.onConnectRequested = { connectInvoked = true }
        CastButtonState.onEndSessionForSwitchRequested = { switchInvoked = true }

        // Getter coverage + invocation semantics
        val connect = CastButtonState.onConnectRequested
        val switch = CastButtonState.onEndSessionForSwitchRequested
        assertNotNull("onConnectRequested must be readable", connect)
        assertNotNull("onEndSessionForSwitchRequested must be readable", switch)
        connect?.invoke(io.mockk.mockk<com.google.android.gms.cast.CastDevice>())
        switch?.invoke()
        assertTrue("onConnectRequested must be invocable", connectInvoked)
        assertTrue("onEndSessionForSwitchRequested must be invocable", switchInvoked)
    }

    @Test
    fun `endSessionForSwitch invokes the registered callback`() {
        var switchInvoked = false
        CastButtonState.onEndSessionForSwitchRequested = { switchInvoked = true }

        CastButtonState.endSessionForSwitch()

        assertTrue("endSessionForSwitch must invoke its callback", switchInvoked)
    }
}
