package com.lucasdss.ftpmusic.app.playback

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBluetoothDevice

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CarBtConnectionReceiverCompanionTest {

    @Before
    fun setUp() {
        CarBtConnectionReceiver.debounceMap.clear()
        PlayerHolder.isCasting = false
    }

    @After
    fun tearDown() {
        CarBtConnectionReceiver.debounceMap.clear()
    }

    @Test
    fun `safeAddress returns address or null on SecurityException`() {
        val ok = mockk<BluetoothDevice>()
        every { ok.address } returns "AA:BB:CC:DD:EE:FF"
        assertEquals("AA:BB:CC:DD:EE:FF", CarBtConnectionReceiver.safeAddress(ok))

        val denied = mockk<BluetoothDevice>()
        every { denied.address } throws SecurityException("no bt")
        assertNull(CarBtConnectionReceiver.safeAddress(denied))
    }

    @Test
    fun `handleConnectBroadcast allowlisted ACL`() {
        val ctx = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(ctx).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)

        val storage = mockk<SecureStorage>()
        every { storage.get(SecureStorage.KEY_CAR_BT_RESUME_ENABLED) } returns "true"
        every { storage.get(SecureStorage.KEY_CAR_BT_DEVICE_MACS) } returns
            """["AA:BB:CC:DD:EE:FF"]"""

        val device = ShadowBluetoothDevice.newInstance("AA:BB:CC:DD:EE:FF")
        val intent = Intent(CarBtConnectionReceiver.ACTION_ACL_CONNECTED)
            .putExtra(BluetoothDevice.EXTRA_DEVICE, device)

        assertEquals(device, CarBtConnectionReceiver.extractDevice(intent))
        assertEquals("AA:BB:CC:DD:EE:FF", CarBtConnectionReceiver.safeAddress(device))

        assertTrue(
            CarBtConnectionReceiver.handleConnectBroadcast(
                context = ctx,
                intent = intent,
                storage = storage,
                casting = false,
            ),
        )
        assertTrue(CarBtConnectionReceiver.debounceMap.containsKey("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `handleConnectBroadcast no-ops for null and non-connect`() {
        val storage = mockk<SecureStorage>(relaxed = true)
        val ctx = RuntimeEnvironment.getApplication()
        assertFalse(
            CarBtConnectionReceiver.handleConnectBroadcast(ctx, null, storage, casting = false),
        )
        assertFalse(
            CarBtConnectionReceiver.handleConnectBroadcast(
                ctx,
                Intent("other.action"),
                storage,
                casting = false,
            ),
        )
    }

    @Test
    fun `intent isConnectEvent wrapper`() {
        val connected = Intent(CarBtConnectionReceiver.ACTION_A2DP_CONNECTION_STATE_CHANGED)
            .putExtra(CarBtConnectionReceiver.EXTRA_STATE, CarBtConnectionReceiver.STATE_CONNECTED)
        assertTrue(CarBtConnectionReceiver.isConnectEvent(connected))
    }
}
