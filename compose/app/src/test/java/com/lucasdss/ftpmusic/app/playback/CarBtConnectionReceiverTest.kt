package com.lucasdss.ftpmusic.app.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarBtConnectionReceiverTest {

    @Test
    fun `isConnectEvent accepts A2DP connected and ACL`() {
        assertTrue(
            CarBtConnectionReceiver.isConnectEvent(
                CarBtConnectionReceiver.ACTION_A2DP_CONNECTION_STATE_CHANGED,
                CarBtConnectionReceiver.STATE_CONNECTED,
            ),
        )
        assertFalse(
            CarBtConnectionReceiver.isConnectEvent(
                CarBtConnectionReceiver.ACTION_A2DP_CONNECTION_STATE_CHANGED,
                0,
            ),
        )
        assertTrue(
            CarBtConnectionReceiver.isConnectEvent(
                CarBtConnectionReceiver.ACTION_ACL_CONNECTED,
                -1,
            ),
        )
        assertFalse(CarBtConnectionReceiver.isConnectEvent("other.action", 2))
        assertFalse(CarBtConnectionReceiver.isConnectEvent(null, 2))
        // Framework constant aliases (may equal literals on device).
        assertTrue(
            CarBtConnectionReceiver.isConnectEvent(
                android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED
                    ?: CarBtConnectionReceiver.ACTION_A2DP_CONNECTION_STATE_CHANGED,
                CarBtConnectionReceiver.STATE_CONNECTED,
            ),
        )
    }
}
