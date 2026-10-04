package com.lucasdss.ftpmusic.app.playback

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Wakes playback when an allowlisted car Bluetooth audio device connects.
 * See ADR-0071.
 */
@AndroidEntryPoint
class CarBtConnectionReceiver : BroadcastReceiver() {

    @Inject lateinit var storage: SecureStorage

    override fun onReceive(context: Context, intent: Intent?) {
        handleConnectBroadcast(
            context = context.applicationContext,
            intent = intent,
            storage = storage,
            casting = PlayerHolder.isCasting,
        )
    }

    companion object {
        /** Process-scoped debounce map shared across deliveries. */
        internal val debounceMap: MutableMap<String, Long> = mutableMapOf()

        /** A2DP connection-state broadcast action (literal for JVM unit tests). */
        internal const val ACTION_A2DP_CONNECTION_STATE_CHANGED =
            "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"

        /** ACL connected broadcast action (literal for JVM unit tests). */
        internal const val ACTION_ACL_CONNECTED =
            "android.bluetooth.device.action.ACL_CONNECTED"

        internal const val EXTRA_STATE = "android.bluetooth.profile.extra.STATE"
        internal const val STATE_CONNECTED = 2

        /** Intent → policy → start. Testable without constructing the Hilt receiver. */
        internal fun handleConnectBroadcast(
            context: Context,
            intent: Intent?,
            storage: SecureStorage,
            casting: Boolean,
        ): Boolean {
            if (intent == null) return false
            if (!isConnectEvent(intent)) return false
            val device = extractDevice(intent) ?: return false
            val mac = safeAddress(device) ?: return false
            return dispatchCarBtConnect(
                context = context,
                storage = storage,
                deviceMac = mac,
                casting = casting,
            )
        }

        /**
         * Policy + start. Package-visible for unit tests (Parcelable BT device
         * extras are awkward under mockk).
         */
        internal fun dispatchCarBtConnect(
            context: Context,
            storage: SecureStorage,
            deviceMac: String?,
            casting: Boolean,
            nowMs: Long = System.currentTimeMillis(),
            starter: (Context) -> CarBtAutoplayStarter.StartResult = {
                CarBtAutoplayStarter.startAutoplay(it)
            },
        ): Boolean {
            val enabled = storage.get(SecureStorage.KEY_CAR_BT_RESUME_ENABLED)
                ?.toBooleanStrictOrNull() == true
            val allowlist = CarBtAutoplayPolicy.parseMacAllowlist(
                storage.get(SecureStorage.KEY_CAR_BT_DEVICE_MACS),
            )
            val allow = CarBtAutoplayPolicy.shouldResume(
                enabled = enabled,
                allowlistedMacs = allowlist,
                deviceMac = deviceMac,
                casting = casting,
                nowMs = nowMs,
                lastAcceptedAtMsByMac = debounceMap,
            )
            if (!allow) {
                android.util.Log.d("ftpmusic-carbt", "skip connect mac=$deviceMac enabled=$enabled")
                return false
            }
            android.util.Log.i("ftpmusic-carbt", "car BT resume for $deviceMac")
            starter(context)
            return true
        }

        internal fun isConnectEvent(intent: Intent): Boolean = isConnectEvent(
            action = intent.action,
            a2dpState = intent.getIntExtra(EXTRA_STATE, -1),
        )

        /** Pure gate — unit-testable without Robolectric Intent stubs. */
        internal fun isConnectEvent(action: String?, a2dpState: Int): Boolean {
            if (action == null) return false
            val a2dp = action == ACTION_A2DP_CONNECTION_STATE_CHANGED ||
                action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED
            if (a2dp) return a2dpState == STATE_CONNECTED
            return action == ACTION_ACL_CONNECTED ||
                action == BluetoothDevice.ACTION_ACL_CONNECTED
        }

        @Suppress("DEPRECATION")
        internal fun extractDevice(intent: Intent): BluetoothDevice? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }

        internal fun safeAddress(device: BluetoothDevice): String? = try {
            device.address
        } catch (_: SecurityException) {
            null
        }
    }
}
