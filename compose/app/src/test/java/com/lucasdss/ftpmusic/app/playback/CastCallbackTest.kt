package com.lucasdss.ftpmusic.app.playback

import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.framework.CastSession
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests the cast device name extraction pattern used in onCastSessionAvailable.
 * CastContext.getSharedInstance cannot run in unit tests,
 * so we test the CastSession → CastDevice → friendlyName chain in isolation.
 */
class CastCallbackTest {

    @Test
    fun `CastSession provides castDevice friendlyName`() {
        val device: CastDevice = mockk()
        val session: CastSession = mockk()

        every { device.friendlyName } returns "Living Room TV"
        every { session.castDevice } returns device

        assertEquals("Living Room TV", session.castDevice!!.friendlyName)
    }

    @Test
    fun `CastSession with null castDevice returns null friendlyName`() {
        val session: CastSession = mockk()
        every { session.castDevice } returns null

        assertNull(session.castDevice)
    }

    @Test
    fun `deviceName extracted via safe call chain`() {
        val device: CastDevice = mockk()
        val session: CastSession = mockk()

        every { device.friendlyName } returns "Bedroom Speaker"
        every { session.castDevice } returns device

        // Pattern used in onCastSessionAvailable:
        val name = session.castDevice?.friendlyName
        assertEquals("Bedroom Speaker", name)
    }

    @Test
    fun `deviceName null when castDevice is null`() {
        val session: CastSession = mockk()
        every { session.castDevice } returns null

        val name = session.castDevice?.friendlyName
        assertNull(name)
    }

    @Test
    fun `PlaybackState copy pattern for cast session available`() {
        val device: CastDevice = mockk()
        val session: CastSession = mockk()
        every { device.friendlyName } returns "Living Room TV"
        every { session.castDevice } returns device

        val state = PlaybackState().copy(
            isCasting = true,
            castDeviceName = session.castDevice?.friendlyName,
        )

        assertTrue(state.isCasting)
        assertEquals("Living Room TV", state.castDeviceName)
    }

    @Test
    fun `PlaybackState copy pattern for cast session unavailable`() {
        val state = PlaybackState().copy(
            isCasting = false,
            castDeviceName = null,
        )

        assertFalse(state.isCasting)
        assertNull(state.castDeviceName)
    }

    @Test
    fun `onCastSessionReady branch with deviceName sets isCasting and name`() {
        // Pattern from MediaService.onCastSessionReady:780-785
        val device: CastDevice = mockk()
        val session: CastSession = mockk()
        every { device.friendlyName } returns "Kitchen Speaker"
        every { session.castDevice } returns device

        val deviceName = session.castDevice?.friendlyName
        val state = if (deviceName != null) {
            PlaybackState().copy(isCasting = true, castDeviceName = deviceName)
        } else {
            PlaybackState().copy(isCasting = true, castDeviceName = null)
        }

        assertTrue(state.isCasting)
        assertEquals("Kitchen Speaker", state.castDeviceName)
    }

    @Test
    fun `onCastSessionReady branch without deviceName sets isCasting only`() {
        // Pattern from MediaService.onCastSessionReady:786-789
        // When deviceName is null, isCasting=true but castDeviceName remains null
        val session: CastSession = mockk()
        every { session.castDevice } returns null

        val deviceName = session.castDevice?.friendlyName
        val state = if (deviceName != null) {
            PlaybackState().copy(isCasting = true, castDeviceName = deviceName)
        } else {
            PlaybackState().copy(isCasting = true, castDeviceName = null)
        }

        assertTrue("isCasting must be true even without device name", state.isCasting)
        assertNull("castDeviceName must be null when CastDevice unavailable", state.castDeviceName)
    }
}
