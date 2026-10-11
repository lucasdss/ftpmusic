package com.lucasdss.ftpmusic.app.playback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class BtWarmAudioDeviceMonitorPureTest {

    @Test
    fun `isBluetoothA2dp only TYPE_BLUETOOTH_A2DP`() {
        val a2dp = mockk<AudioDeviceInfo>()
        every { a2dp.type } returns AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        assertTrue(BtWarmAudioDeviceMonitor.isBluetoothA2dp(a2dp))

        val speaker = mockk<AudioDeviceInfo>()
        every { speaker.type } returns AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        assertFalse(BtWarmAudioDeviceMonitor.isBluetoothA2dp(speaker))
    }

    @Test
    fun `deviceAddressOrNull blanks and zero`() {
        val blank = mockk<AudioDeviceInfo>()
        every { blank.address } returns ""
        assertNull(BtWarmAudioDeviceMonitor.deviceAddressOrNull(blank))

        val zero = mockk<AudioDeviceInfo>()
        every { zero.address } returns "0"
        assertNull(BtWarmAudioDeviceMonitor.deviceAddressOrNull(zero))

        val mac = mockk<AudioDeviceInfo>()
        every { mac.address } returns "AA:BB:CC:DD:EE:FF"
        org.junit.Assert.assertEquals(
            "AA:BB:CC:DD:EE:FF",
            BtWarmAudioDeviceMonitor.deviceAddressOrNull(mac),
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BtWarmAudioDeviceMonitorRobolectricTest {

    @Before
    fun setUp() {
        BtResumeEvents.clear()
    }

    @Test
    fun `start and stop register unregister callback`() {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.applicationContext } returns ctx
        val storage = mockk<SecureStorage>(relaxed = true)
        val am = mockk<AudioManager>(relaxed = true)
        val handler = Handler(Looper.getMainLooper())
        var dispatched = 0
        val monitor = BtWarmAudioDeviceMonitor(
            context = ctx,
            storage = storage,
            audioManager = am,
            mainHandler = handler,
            casting = { false },
            dispatch = { _, _, _, _ ->
                dispatched++
                true
            },
        )
        monitor.start()
        verify(exactly = 1) { am.registerAudioDeviceCallback(any(), handler) }
        monitor.start() // idempotent
        verify(exactly = 1) { am.registerAudioDeviceCallback(any(), handler) }
        monitor.stop()
        verify(exactly = 1) { am.unregisterAudioDeviceCallback(any()) }
        assertTrue(dispatched == 0)
    }
}
