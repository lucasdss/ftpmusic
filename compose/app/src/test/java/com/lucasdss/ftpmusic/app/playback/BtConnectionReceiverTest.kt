package com.lucasdss.ftpmusic.app.playback

import android.content.Context
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BtConnectionReceiverTest {

    @Before
    fun setUp() {
        BtConnectionReceiver.debounceMap.clear()
    }

    @After
    fun tearDown() {
        BtConnectionReceiver.debounceMap.clear()
    }

    @Test
    fun `isConnectEvent A2DP only — ACL rejected`() {
        assertTrue(
            BtConnectionReceiver.isConnectEvent(
                BtConnectionReceiver.ACTION_A2DP_CONNECTION_STATE_CHANGED,
                BtConnectionReceiver.STATE_CONNECTED,
            ),
        )
        assertFalse(
            BtConnectionReceiver.isConnectEvent(
                BtConnectionReceiver.ACTION_A2DP_CONNECTION_STATE_CHANGED,
                0,
            ),
        )
        assertFalse(
            BtConnectionReceiver.isConnectEvent(
                "android.bluetooth.device.action.ACL_CONNECTED",
                -1,
            ),
        )
        assertFalse(BtConnectionReceiver.isConnectEvent(null, 2))
    }

    @Test
    fun `dispatch mode any starts without allowlist`() {
        val storage = mockk<SecureStorage>()
        every { storage.get(SecureStorage.KEY_BT_RESUME_ENABLED) } returns "true"
        every { storage.get(SecureStorage.KEY_BT_RESUME_MODE) } returns "any"
        every { storage.get(SecureStorage.KEY_BT_DEVICE_MACS) } returns null
        every { storage.get(SecureStorage.KEY_CAR_BT_RESUME_ENABLED) } returns null
        every { storage.get(SecureStorage.KEY_CAR_BT_DEVICE_MACS) } returns null
        val starter = mockk<(Context) -> BtAutoplayStarter.StartResult>()
        every { starter.invoke(any()) } returns BtAutoplayStarter.StartResult.Started
        assertTrue(
            BtConnectionReceiver.dispatchBtConnect(
                context = mockk(relaxed = true),
                storage = storage,
                deviceMac = "AA:BB:CC:DD:EE:FF",
                casting = false,
                nowMs = 50_000,
                starter = starter,
            ),
        )
        verify(exactly = 1) { starter.invoke(any()) }
    }

    @Test
    fun `dispatch mode selected skips unknown mac`() {
        val storage = mockk<SecureStorage>()
        every { storage.get(SecureStorage.KEY_BT_RESUME_ENABLED) } returns "true"
        every { storage.get(SecureStorage.KEY_BT_RESUME_MODE) } returns "selected"
        every { storage.get(SecureStorage.KEY_BT_DEVICE_MACS) } returns
            """["11:22:33:44:55:66"]"""
        every { storage.get(SecureStorage.KEY_CAR_BT_RESUME_ENABLED) } returns null
        every { storage.get(SecureStorage.KEY_CAR_BT_DEVICE_MACS) } returns null
        val starter = mockk<(Context) -> BtAutoplayStarter.StartResult>(relaxed = true)
        assertFalse(
            BtConnectionReceiver.dispatchBtConnect(
                context = mockk(relaxed = true),
                storage = storage,
                deviceMac = "AA:BB:CC:DD:EE:FF",
                casting = false,
                starter = starter,
            ),
        )
        verify(exactly = 0) { starter.invoke(any()) }
    }
}
