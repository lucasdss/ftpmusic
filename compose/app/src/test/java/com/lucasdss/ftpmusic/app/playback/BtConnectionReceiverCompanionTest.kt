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
class BtConnectionReceiverCompanionTest {

    @Before
    fun setUp() {
        BtConnectionReceiver.debounceMap.clear()
        PlayerHolder.isCasting = false
    }

    @After
    fun tearDown() {
        BtConnectionReceiver.debounceMap.clear()
    }

    @Test
    fun `safeAddress returns address or null on SecurityException`() {
        val ok = mockk<BluetoothDevice>()
        every { ok.address } returns "AA:BB:CC:DD:EE:FF"
        assertEquals("AA:BB:CC:DD:EE:FF", BtConnectionReceiver.safeAddress(ok))

        val denied = mockk<BluetoothDevice>()
        every { denied.address } throws SecurityException("no bt")
        assertNull(BtConnectionReceiver.safeAddress(denied))
    }

    @Test
    fun `handleConnectBroadcast allowlisted A2DP`() {
        val ctx = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(ctx).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)

        val storage = mockk<SecureStorage>()
        every { storage.get(SecureStorage.KEY_BT_RESUME_ENABLED) } returns "true"
        every { storage.get(SecureStorage.KEY_BT_RESUME_MODE) } returns "selected"
        every { storage.get(SecureStorage.KEY_BT_DEVICE_MACS) } returns
            """["AA:BB:CC:DD:EE:FF"]"""
        every { storage.get(SecureStorage.KEY_CAR_BT_RESUME_ENABLED) } returns null
        every { storage.get(SecureStorage.KEY_CAR_BT_DEVICE_MACS) } returns null

        val device = ShadowBluetoothDevice.newInstance("AA:BB:CC:DD:EE:FF")
        val intent = Intent(BtConnectionReceiver.ACTION_A2DP_CONNECTION_STATE_CHANGED)
            .putExtra(BluetoothDevice.EXTRA_DEVICE, device)
            .putExtra(BtConnectionReceiver.EXTRA_STATE, BtConnectionReceiver.STATE_CONNECTED)

        assertEquals(device, BtConnectionReceiver.extractDevice(intent))
        assertTrue(BtConnectionReceiver.isConnectEvent(intent))
        assertTrue(
            BtConnectionReceiver.handleConnectBroadcast(
                context = ctx,
                intent = intent,
                storage = storage,
                casting = false,
            ),
        )
        assertTrue(BtConnectionReceiver.debounceMap.containsKey("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `handleConnectBroadcast no-ops for null and ACL`() {
        val storage = mockk<SecureStorage>(relaxed = true)
        val ctx = RuntimeEnvironment.getApplication()
        assertFalse(
            BtConnectionReceiver.handleConnectBroadcast(ctx, null, storage, casting = false),
        )
        assertFalse(
            BtConnectionReceiver.handleConnectBroadcast(
                ctx,
                Intent("android.bluetooth.device.action.ACL_CONNECTED"),
                storage,
                casting = false,
            ),
        )
    }
}
