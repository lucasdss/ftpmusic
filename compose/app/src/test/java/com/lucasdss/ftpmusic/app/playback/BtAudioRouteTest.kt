package com.lucasdss.ftpmusic.app.playback

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BtAudioRouteTest {
    @Test
    fun `isA2dpOutputActive true when isBluetoothA2dpOn`() {
        val am = mockk<AudioManager>()
        @Suppress("DEPRECATION")
        every { am.isBluetoothA2dpOn } returns true
        assertTrue(BtAudioRoute.isA2dpOutputActive(am))
    }

    @Test
    fun `isA2dpOutputActive false when A2DP off and no communication device`() {
        val am = mockk<AudioManager>(relaxed = true)
        @Suppress("DEPRECATION")
        every { am.isBluetoothA2dpOn } returns false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            every { am.communicationDevice } returns null
        }
        assertFalse(BtAudioRoute.isA2dpOutputActive(am))
    }

    @Test
    @Config(sdk = [31])
    fun `isA2dpOutputActive true when communication device is A2DP`() {
        val am = mockk<AudioManager>()
        @Suppress("DEPRECATION")
        every { am.isBluetoothA2dpOn } returns false
        val a2dp = mockk<AudioDeviceInfo>()
        every { a2dp.type } returns AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        every { am.communicationDevice } returns a2dp
        assertTrue(BtAudioRoute.isA2dpOutputActive(am))
    }
}
