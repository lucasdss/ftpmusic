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

class CarBtConnectionReceiverDispatchTest {

    @Before
    fun setUp() {
        CarBtConnectionReceiver.debounceMap.clear()
    }

    @After
    fun tearDown() {
        CarBtConnectionReceiver.debounceMap.clear()
    }

    @Test
    fun `dispatch starts autoplay for allowlisted device`() {
        val storage = mockk<SecureStorage>()
        every { storage.get(SecureStorage.KEY_CAR_BT_RESUME_ENABLED) } returns "true"
        every { storage.get(SecureStorage.KEY_CAR_BT_DEVICE_MACS) } returns
            """["AA:BB:CC:DD:EE:FF"]"""
        val starter = mockk<(Context) -> CarBtAutoplayStarter.StartResult>()
        every { starter.invoke(any()) } returns CarBtAutoplayStarter.StartResult.Started
        val ctx = mockk<Context>(relaxed = true)

        val started = CarBtConnectionReceiver.dispatchCarBtConnect(
            context = ctx,
            storage = storage,
            deviceMac = "aa:bb:cc:dd:ee:ff",
            casting = false,
            nowMs = 50_000,
            starter = starter,
        )
        assertTrue(started)
        verify(exactly = 1) { starter.invoke(any()) }
    }

    @Test
    fun `dispatch skips when disabled or casting`() {
        val storage = mockk<SecureStorage>()
        every { storage.get(SecureStorage.KEY_CAR_BT_RESUME_ENABLED) } returns "false"
        every { storage.get(SecureStorage.KEY_CAR_BT_DEVICE_MACS) } returns
            """["AA:BB:CC:DD:EE:FF"]"""
        val starter = mockk<(Context) -> CarBtAutoplayStarter.StartResult>(relaxed = true)
        val ctx = mockk<Context>(relaxed = true)

        assertFalse(
            CarBtConnectionReceiver.dispatchCarBtConnect(
                context = ctx,
                storage = storage,
                deviceMac = "AA:BB:CC:DD:EE:FF",
                casting = false,
                starter = starter,
            ),
        )

        every { storage.get(SecureStorage.KEY_CAR_BT_RESUME_ENABLED) } returns "true"
        assertFalse(
            CarBtConnectionReceiver.dispatchCarBtConnect(
                context = ctx,
                storage = storage,
                deviceMac = "AA:BB:CC:DD:EE:FF",
                casting = true,
                starter = starter,
            ),
        )
        verify(exactly = 0) { starter.invoke(any()) }
    }
}
