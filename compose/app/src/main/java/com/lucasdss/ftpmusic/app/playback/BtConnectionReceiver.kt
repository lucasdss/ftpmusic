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
 * Wakes playback when an A2DP audio device connects (any or allowlisted).
 * See ADR-0072 / ADR-0087 (goAsync for post-Doze process survival).
 */
@AndroidEntryPoint
class BtConnectionReceiver : BroadcastReceiver() {

    @Inject lateinit var storage: SecureStorage

    override fun onReceive(context: Context, intent: Intent?) {
        // ADR-0087: keep process alive across FGS start after long Doze.
        val pending = goAsync()
        try {
            handleConnectBroadcast(
                context = context.applicationContext,
                intent = intent,
                storage = storage,
                casting = PlayerHolder.isCasting,
            )
        } finally {
            pending.finish()
        }
    }

    companion object {
        /** Process-scoped debounce map shared across deliveries. */
        internal val debounceMap: MutableMap<String, Long> = mutableMapOf()

        /** A2DP connection-state broadcast action (literal for JVM unit tests). */
        internal const val ACTION_A2DP_CONNECTION_STATE_CHANGED =
            "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"

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
            // Nullable MAC: ANY-mode still resumes via ANY_UNKNOWN_MAC_KEY
            // when BLUETOOTH_CONNECT is denied (ADR-0088 / ADR-0100).
            return dispatchBtConnect(
                context = context,
                storage = storage,
                deviceMac = safeAddress(device),
                casting = casting,
            )
        }

        internal fun dispatchBtConnect(
            context: Context,
            storage: SecureStorage,
            deviceMac: String?,
            casting: Boolean,
            nowMs: Long = System.currentTimeMillis(),
            starter: (Context) -> BtAutoplayStarter.StartResult = {
                BtAutoplayStarter.startAutoplay(it)
            },
            postSelectedNullMacNotif: (Context) -> Unit = {
                BtAutoplayStarter.postResumeNotification(
                    it,
                    com.lucasdss.ftpmusic.app.R.string.bt_resume_notif_body_mac_unknown,
                )
            },
            sdkInt: Int = Build.VERSION.SDK_INT,
        ): Boolean {
            val enabled = BtResumeStorage.isEnabled(storage)
            val mode = BtResumeStorage.mode(storage)
            val allowlist = BtResumeStorage.allowlist(storage)
            // ADR-0101: SELECTED + unreadable MAC must not be silent — Resume notif only
            // (no autoplay without a verified allowlist match).
            if (enabled &&
                !casting &&
                mode == BtResumeMode.SELECTED &&
                BtResumePolicy.normalizeMac(deviceMac) == null &&
                allowlist.isNotEmpty()
            ) {
                android.util.Log.i(
                    "ftpmusic-bt",
                    "selected_null_mac — Resume notif (no autoplay) enabled=$enabled sdk=$sdkInt",
                )
                postSelectedNullMacNotif(context)
                return true
            }
            val allow = BtResumePolicy.shouldResume(
                enabled = enabled,
                mode = mode,
                allowlistedMacs = allowlist,
                deviceMac = deviceMac,
                casting = casting,
                nowMs = nowMs,
                lastAcceptedAtMsByMac = debounceMap,
            )
            if (!allow) {
                android.util.Log.d(
                    "ftpmusic-bt",
                    "skip connect mac=$deviceMac enabled=$enabled mode=$mode casting=$casting sdk=$sdkInt",
                )
                return false
            }
            android.util.Log.i(
                "ftpmusic-bt",
                "BT resume for $deviceMac mode=$mode enabled=$enabled sdk=$sdkInt",
            )
            val result = starter(context)
            android.util.Log.i(
                "ftpmusic-bt",
                "BT resume result=$result mac=$deviceMac mode=$mode sdk=$sdkInt",
            )
            return true
        }

        internal fun isConnectEvent(intent: Intent): Boolean = isConnectEvent(
            action = intent.action,
            a2dpState = intent.getIntExtra(EXTRA_STATE, -1),
        )

        /** Pure gate — A2DP connected only (ADR-0072). */
        internal fun isConnectEvent(action: String?, a2dpState: Int): Boolean {
            if (action == null) return false
            val a2dp = action == ACTION_A2DP_CONNECTION_STATE_CHANGED ||
                action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED
            return a2dp && a2dpState == STATE_CONNECTED
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
