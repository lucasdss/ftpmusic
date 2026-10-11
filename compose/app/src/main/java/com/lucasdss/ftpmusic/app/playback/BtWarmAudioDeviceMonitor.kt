package com.lucasdss.ftpmusic.app.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.lucasdss.ftpmusic.app.data.security.SecureStorage

/**
 * Warm-process A2DP add listener (ADR-0119). Complements manifest
 * [BtConnectionReceiver] when [MediaService] is already alive; does not replace
 * cold-start after process death. Debounce via [BtResumePolicy] / shared map.
 */
class BtWarmAudioDeviceMonitor(
    private val context: Context,
    private val storage: SecureStorage,
    private val audioManager: AudioManager,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val casting: () -> Boolean = { PlayerHolder.isCasting },
    private val dispatch: (
        Context,
        SecureStorage,
        String?,
        Boolean,
    ) -> Boolean = { ctx, store, mac, cast ->
        BtConnectionReceiver.dispatchBtConnect(
            context = ctx,
            storage = store,
            deviceMac = mac,
            casting = cast,
        )
    },
) {
    private var registered = false

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            for (device in addedDevices) {
                if (!isBluetoothA2dp(device)) continue
                val mac = deviceAddressOrNull(device)
                android.util.Log.i(
                    "ftpmusic-bt",
                    "warm AudioDeviceCallback A2DP added macPresent=${mac != null}",
                )
                com.lucasdss.ftpmusic.app.data.diagnostics.DiagnosticLog.d(
                    "ftpmusic-bt",
                    "warm_a2dp macPresent=${mac != null}",
                )
                BtResumeEvents.record("warm_a2dp", "macPresent=${mac != null}")
                dispatch(context.applicationContext, storage, mac, casting())
            }
        }
    }

    fun start() {
        if (registered) return
        try {
            audioManager.registerAudioDeviceCallback(callback, mainHandler)
            registered = true
            android.util.Log.i("ftpmusic-bt", "warm AudioDeviceCallback registered")
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-bt", "warm AudioDeviceCallback register failed: ${e.message}")
        }
    }

    fun stop() {
        if (!registered) return
        try {
            audioManager.unregisterAudioDeviceCallback(callback)
        } catch (_: Exception) {
        }
        registered = false
    }

    companion object {
        fun isBluetoothA2dp(device: AudioDeviceInfo): Boolean = device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP

        /** Address may be empty without CONNECT; null → ANY-mode synthetic key path. */
        fun deviceAddressOrNull(device: AudioDeviceInfo): String? {
            val addr = try {
                device.address
            } catch (_: Exception) {
                null
            }
            return addr?.takeIf { it.isNotBlank() && it != "0" }
        }
    }
}
