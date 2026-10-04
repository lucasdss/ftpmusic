package com.lucasdss.ftpmusic.app.playback

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

data class BondedBtDevice(val name: String, val address: String)

/**
 * Lists bonded Bluetooth devices when [BLUETOOTH_CONNECT] (or legacy) is granted.
 */
object BluetoothBondedDevices {
    fun hasConnectPermission(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        // Pre-S BLUETOOTH is a normal install-time permission.
        true
    }

    fun list(context: Context): List<BondedBtDevice> {
        if (!hasConnectPermission(context)) return emptyList()
        val adapter = resolveAdapter(context) ?: return emptyList()
        return try {
            @Suppress("MissingPermission")
            adapter.bondedDevices.orEmpty().mapNotNull { device ->
                val mac = CarBtAutoplayPolicy.normalizeMac(device.address) ?: return@mapNotNull null
                val name = deviceNameOrMac(device, mac)
                BondedBtDevice(name = name, address = mac)
            }.sortedBy { it.name.lowercase() }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    internal fun resolveAdapter(context: Context): BluetoothAdapter? {
        @Suppress("DEPRECATION")
        return BluetoothAdapter.getDefaultAdapter()
            ?: context.getSystemService(BluetoothManager::class.java)?.adapter
    }

    internal fun deviceNameOrMac(device: android.bluetooth.BluetoothDevice, mac: String): String = try {
        device.name?.takeIf { it.isNotBlank() } ?: mac
    } catch (_: SecurityException) {
        mac
    }
}
