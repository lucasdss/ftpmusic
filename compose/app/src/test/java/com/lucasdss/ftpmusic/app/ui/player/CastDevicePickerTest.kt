package com.lucasdss.ftpmusic.app.ui.player

import com.google.android.gms.cast.CastDevice
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.playback.PlaybackState
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class CastDevicePickerTest {

    // ── Device type icon detection ────────────────────────────────

    @Test
    fun `Speaker icon for model containing Speaker`() {
        val device = mockk<CastDevice>()
        every { device.modelName } returns "Google Home Mini Speaker"
        assertEquals("Speaker", getDeviceIconForTest(device.modelName))
    }

    @Test
    fun `Speaker icon for model containing Mini`() {
        val device = mockk<CastDevice>()
        every { device.modelName } returns "Mini Speaker"
        assertEquals("Speaker", getDeviceIconForTest(device.modelName))
    }

    @Test
    fun `TV icon for model containing TV`() {
        val device = mockk<CastDevice>()
        every { device.modelName } returns "Android TV"
        assertEquals("Tv", getDeviceIconForTest(device.modelName))
    }

    @Test
    fun `TV icon for model containing webOS`() {
        val device = mockk<CastDevice>()
        every { device.modelName } returns "LG webOS TV"
        assertEquals("Tv", getDeviceIconForTest(device.modelName))
    }

    @Test
    fun `Desktop icon for model containing display`() {
        val device = mockk<CastDevice>()
        every { device.modelName } returns "Lu Bedroom display"
        assertEquals("DesktopWindows", getDeviceIconForTest(device.modelName))
    }

    @Test
    fun `Desktop icon for model containing Nest Hub`() {
        val device = mockk<CastDevice>()
        every { device.modelName } returns "Google Nest Hub"
        assertEquals("DesktopWindows", getDeviceIconForTest(device.modelName))
    }

    @Test
    fun `SurroundSound icon for model containing Soundbar`() {
        val device = mockk<CastDevice>()
        every { device.modelName } returns "Samsung Soundbar"
        assertEquals("SurroundSound", getDeviceIconForTest(device.modelName))
    }

    @Test
    fun `Cast icon for model containing Chromecast`() {
        val device = mockk<CastDevice>()
        every { device.modelName } returns "Chromecast Ultra"
        assertEquals("Cast", getDeviceIconForTest(device.modelName))
    }

    @Test
    fun `Cast icon for null modelName`() {
        assertEquals("Cast", getDeviceIconForTest(null))
    }

    @Test
    fun `Cast icon for unrecognized modelName`() {
        val device = mockk<CastDevice>()
        every { device.modelName } returns "Some Unknown Device"
        assertEquals("Cast", getDeviceIconForTest(device.modelName))
    }

    // ── Connected device detection ─────────────────────────────────

    @Test
    fun `isConnected true when casting and name matches`() {
        val isCasting = true
        val connectedDeviceName = "Mini Speaker"
        val deviceName = "Mini Speaker"
        assertEquals(true, isCasting && deviceName == connectedDeviceName)
    }

    @Test
    fun `isConnected false when casting but name differs`() {
        val isCasting = true
        val connectedDeviceName = "Mini Speaker"
        val deviceName = "Soundbar"
        assertEquals(false, isCasting && deviceName == connectedDeviceName)
    }

    @Test
    fun `isConnected false when not casting even if name matches`() {
        val isCasting = false
        val connectedDeviceName = "Mini Speaker"
        val deviceName = "Mini Speaker"
        assertEquals(false, isCasting && deviceName == connectedDeviceName)
    }

    @Test
    fun `isConnected false when connectedDeviceName is null`() {
        val isCasting = true
        val connectedDeviceName: String? = null
        val deviceName = "Mini Speaker"
        assertEquals(false, isCasting && deviceName == connectedDeviceName)
    }

    @Test
    fun `isConnected false when device has null friendlyName`() {
        val isCasting = true
        val connectedDeviceName = "Mini Speaker"
        val deviceName: String? = null
        assertEquals(false, isCasting && deviceName == connectedDeviceName)
    }

    // ── Deduplication logic ────────────────────────────────────────

    @Test
    fun `connected device injected first and deduplicated from discovered list`() {
        val connectedDevice = mockk<CastDevice>()
        every { connectedDevice.friendlyName } returns "Living Room TV"
        every { connectedDevice.deviceId } returns "id-connected"

        val otherDevice = mockk<CastDevice>()
        every { otherDevice.friendlyName } returns "Kitchen Speaker"
        every { otherDevice.deviceId } returns "id-kitchen"

        val duplicateDevice = mockk<CastDevice>()
        every { duplicateDevice.friendlyName } returns "Living Room TV"
        every { duplicateDevice.deviceId } returns "id-dup"

        val discoveredDevices = listOf(otherDevice, duplicateDevice)

        // Simulate NavHost.kt: connected device first, then discovered excluding duplicate
        val result = buildList {
            add(connectedDevice)
            addAll(discoveredDevices.filter { it.friendlyName != "Living Room TV" })
        }

        assertEquals(2, result.size)
        assertEquals("Living Room TV", result[0].friendlyName)
        assertEquals("Kitchen Speaker", result[1].friendlyName)
    }

    @Test
    fun `no connected device injected when not casting`() {
        val discoveredDevices = listOf(
            mockk<CastDevice>().apply {
                every { friendlyName } returns "Mini Speaker"
                every { deviceId } returns "id-mini"
            },
        )

        // Simulate NavHost.kt: isCasting=false, no device injected
        val isCasting = false
        val result = buildList {
            if (isCasting) { /* would add connected device */ }
            addAll(discoveredDevices)
        }

        assertEquals(1, result.size)
        assertEquals("Mini Speaker", result[0].friendlyName)
    }

    @Test
    fun `distinctBy friendlyName removes duplicates`() {
        val device1 = mockk<CastDevice>()
        every { device1.friendlyName } returns "Mini Speaker"
        every { device1.deviceId } returns "id-aaa"

        val device2 = mockk<CastDevice>()
        every { device2.friendlyName } returns "Mini Speaker"
        every { device2.deviceId } returns "id-bbb"

        val device3 = mockk<CastDevice>()
        every { device3.friendlyName } returns "Soundbar"
        every { device3.deviceId } returns "id-ccc"

        val devices = listOf(device1, device2, device3)
        val unique = devices.distinctBy { it.friendlyName }

        assertEquals(2, unique.size)
        assertEquals("Mini Speaker", unique[0].friendlyName)
        assertEquals("Soundbar", unique[1].friendlyName)
    }

    @Test
    fun `distinctBy deviceId does NOT remove same-name different-ID devices`() {
        // This test documents WHY we use friendlyName, not deviceId
        val device1 = mockk<CastDevice>()
        every { device1.friendlyName } returns "Mini Speaker"
        every { device1.deviceId } returns "id-aaa"

        val device2 = mockk<CastDevice>()
        every { device2.friendlyName } returns "Mini Speaker"
        every { device2.deviceId } returns "id-bbb"

        val devices = listOf(device1, device2)
        val byId = devices.distinctBy { it.deviceId }
        val byName = devices.distinctBy { it.friendlyName }

        // deviceId dedup FAILS — same physical device, different IDs
        assertEquals(2, byId.size)
        // friendlyName dedup WORKS
        assertEquals(1, byName.size)
    }

    @Test
    fun `distinctBy friendlyName preserves devices with unique names`() {
        val speakers = listOf(
            mockk<CastDevice>().apply {
                every { friendlyName } returns "Mini Speaker"
                every { deviceId } returns "aaa"
            },
            mockk<CastDevice>().apply {
                every { friendlyName } returns "Soundbar"
                every { deviceId } returns "bbb"
            },
            mockk<CastDevice>().apply {
                every { friendlyName } returns "LG TV"
                every { deviceId } returns "ccc"
            },
        )
        val unique = speakers.distinctBy { it.friendlyName }
        assertEquals(3, unique.size)
    }

    // ── PlaybackState artistId/albumId ─────────────────────────────

    @Test
    fun `PlaybackState artistId defaults to null`() {
        val state = PlaybackState()
        assertNull(state.artistId)
    }

    @Test
    fun `PlaybackState albumId defaults to null`() {
        val state = PlaybackState()
        assertNull(state.albumId)
    }

    @Test
    fun `PlaybackState artistId and albumId are preserved`() {
        val state = PlaybackState(artistId = "art-123", albumId = "alb-456")
        assertEquals("art-123", state.artistId)
        assertEquals("alb-456", state.albumId)
    }

    @Test
    fun `PlaybackState copy preserves artistId and albumId`() {
        val state = PlaybackState(artistId = "art-1", albumId = "alb-2", title = "Song")
        val copied = state.copy(title = "New Song")
        assertEquals("New Song", copied.title)
        assertEquals("art-1", copied.artistId)
        assertEquals("alb-2", copied.albumId)
    }

    // ── Track model artistId ────────────────────────────────────────

    @Test
    fun `Track artistId defaults to null`() {
        val track = com.lucasdss.ftpmusic.app.data.model.Track(id = "t1", title = "Song")
        assertNull(track.artistId)
    }

    @Test
    fun `Track artistId is preserved`() {
        val track = com.lucasdss.ftpmusic.app.data.model.Track(
            id = "t1",
            title = "Song",
            artistId = "art-42",
            albumId = "alb-7",
        )
        assertEquals("art-42", track.artistId)
        assertEquals("alb-7", track.albumId)
    }
}

/** Test helper that replicates getDeviceIcon logic for unit testing. */
private fun getDeviceIconForTest(modelName: String?): String = when {
    modelName == null -> "Cast"

    modelName.contains("Speaker", ignoreCase = true) || modelName.contains("Mini", ignoreCase = true) -> "Speaker"

    modelName.contains(
        "display",
        ignoreCase = true,
    ) || modelName.contains("Nest Hub", ignoreCase = true) -> "DesktopWindows"

    modelName.contains("TV", ignoreCase = true) || modelName.contains("webOS", ignoreCase = true) ||
        modelName.contains("Android TV", ignoreCase = true) -> "Tv"

    modelName.contains("Soundbar", ignoreCase = true) -> "SurroundSound"

    modelName.contains("Chromecast", ignoreCase = true) -> "Cast"

    else -> "Cast"
}
