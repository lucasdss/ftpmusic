package com.lucasdss.ftpmusic.app.ui.player

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.cast.CastDevice
import com.lucasdss.ftpmusic.app.playback.PlayerHolder

/**
 * Global holder for Cast button state — accessible from any screen without prop drilling.
 * Similar pattern to PlayerHolder.
 */
object CastButtonState {
    /** Whether the Cast device picker dialog is currently shown. */
    val showDialog = mutableStateOf(false)

    /** Currently discovered Cast devices on the network. */
    val discoveredDevices = mutableStateOf<List<CastDevice>>(emptyList())

    /** Whether a Cast session is currently active. */
    val isCasting = mutableStateOf(false)

    /** Name of the connected Cast device (null if not connected). */
    val connectedDeviceName = mutableStateOf<String?>(null)

    /** Error message to show in a dialog when Cast connection fails. */
    val castErrorMessage = mutableStateOf<String?>(null)

    /** Device name currently attempting to connect (null = not connecting). */
    val connectingDeviceName = mutableStateOf<String?>(null)

    /** Whether the Cast button should be visible (devices found OR already casting). */
    val isButtonVisible: Boolean
        get() = discoveredDevices.value.isNotEmpty() || isCasting.value

    /**
     * Callback invoked immediately when the user initiates Cast disconnection.
     * MediaService registers here to update the notification
     * and PlayerHolder before the async Cast SDK disconnect completes.
     */
    var onDisconnectRequested: (() -> Unit)? = null

    /**
     * Callback for the device-switch flow (A→B): tears down the CURRENT Cast
     * SDK session + deselects its MediaRouter route WITHOUT clearing app state,
     * so route.select(B) creates a fresh connection. MediaService registers here
     * to call endCurrentCastSession(). Distinct from onDisconnectRequested,
     * which also resets state and switches to local playback.
     */
    var onEndSessionForSwitchRequested: (() -> Unit)? = null

    /**
     * Callback for a manual connect request. MediaService registers here to own
     * route selection: it ends any stale/desynced session BEFORE route.select()
     * (so the select is never a no-op) and arms a connect timeout that clears
     * the "Connecting…" state on failure — the reconnect hang fix.
     */
    var onConnectRequested: ((CastDevice) -> Unit)? = null

    /**
     * Callback when the user cancels a pending connect (picker Cancel/dismiss).
     * MediaService cancels the connect timeout and clears connecting state.
     */
    var onConnectCancelled: (() -> Unit)? = null

    /** Disconnects the active Cast session. */
    fun disconnect() {
        try {
            // 1. Stop playback on Cast device while CastPlayer is still the active player
            try {
                PlayerHolder.player?.stop()
            } catch (_: Exception) {}

            // 2. Clear UI state
            isCasting.value = false
            connectedDeviceName.value = null
            connectingDeviceName.value = null

            // 4. MediaService handles switch to ExoPlayer, Cast SDK handles session
            onDisconnectRequested?.invoke()

            android.util.Log.d("ftpmusic-cast", "[CastButtonState] Disconnected from Cast session")
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-cast", "[CastButtonState] Disconnect failed: ${e.message}")
        }
    }

    /** Ends the current Cast session WITHOUT clearing state — for device switch A→B. */
    fun endSessionForSwitch() {
        try {
            onEndSessionForSwitchRequested?.invoke()
            android.util.Log.d("ftpmusic-cast", "[CastButtonState] Ended session for device switch")
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-cast", "[CastButtonState] End-session-for-switch failed: ${e.message}")
        }
    }
}
