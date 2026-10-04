package com.lucasdss.ftpmusic.app.playback

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.Build
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBluetoothDevice

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BluetoothBondedDevicesTest {

    @Test
    fun `hasConnectPermission false without grant on S+`() {
        val ctx = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(ctx).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        assertFalse(BluetoothBondedDevices.hasConnectPermission(ctx))
        assertTrue(BluetoothBondedDevices.list(ctx).isEmpty())
    }

    @Test
    fun `list returns bonded devices when permitted`() {
        val ctx = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(ctx).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        assertTrue(BluetoothBondedDevices.hasConnectPermission(ctx))

        val adapter = BluetoothAdapter.getDefaultAdapter()
        val named = ShadowBluetoothDevice.newInstance("AA:BB:CC:DD:EE:FF")
        Shadows.shadowOf(named).setName("Test Car")
        val unnamed = ShadowBluetoothDevice.newInstance("11:22:33:44:55:66")
        Shadows.shadowOf(unnamed).setName("")
        val invalid = ShadowBluetoothDevice.newInstance("00:00:00:00:00:00")
        Shadows.shadowOf(adapter).setEnabled(true)
        Shadows.shadowOf(adapter).setBondedDevices(setOf(named, unnamed, invalid))

        val devices = BluetoothBondedDevices.list(ctx)
        assertEquals(2, devices.size)
        assertEquals("AA:BB:CC:DD:EE:FF", devices.first { it.name == "Test Car" }.address)
        assertTrue(devices.any { it.address == "11:22:33:44:55:66" && it.name == "11:22:33:44:55:66" })
    }

    @Test
    fun `list empty when adapter has no bonded devices`() {
        val ctx = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(ctx).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        val adapter = BluetoothAdapter.getDefaultAdapter()
        Shadows.shadowOf(adapter).setEnabled(true)
        Shadows.shadowOf(adapter).setBondedDevices(emptySet())
        assertTrue(BluetoothBondedDevices.list(ctx).isEmpty())
    }

    @Test
    fun `deviceNameOrMac falls back on SecurityException`() {
        val device = mockk<BluetoothDevice>()
        every { device.name } throws SecurityException("denied")
        assertEquals("AA:BB:CC:DD:EE:FF", BluetoothBondedDevices.deviceNameOrMac(device, "AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `resolveAdapter returns default adapter`() {
        val ctx = RuntimeEnvironment.getApplication()
        assertTrue(BluetoothBondedDevices.resolveAdapter(ctx) != null)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BluetoothBondedDevicesPreSTest {

    @Test
    fun `pre-S hasConnectPermission true and lists`() {
        val ctx = RuntimeEnvironment.getApplication()
        assertTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
        assertTrue(BluetoothBondedDevices.hasConnectPermission(ctx))

        val adapter = BluetoothAdapter.getDefaultAdapter()
        val device = ShadowBluetoothDevice.newInstance("11:22:33:44:55:66")
        Shadows.shadowOf(adapter).setEnabled(true)
        Shadows.shadowOf(adapter).setBondedDevices(setOf(device))
        val devices = BluetoothBondedDevices.list(ctx)
        assertTrue(devices.any { it.address == "11:22:33:44:55:66" })
    }
}
