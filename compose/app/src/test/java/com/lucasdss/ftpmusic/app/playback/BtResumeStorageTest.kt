package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BtResumeStorageTest {

    @Test
    fun `isEnabled falls back to legacy key`() {
        val storage = mockk<SecureStorage>()
        every { storage.get(SecureStorage.KEY_BT_RESUME_ENABLED) } returns null
        every { storage.get(SecureStorage.KEY_CAR_BT_RESUME_ENABLED) } returns "true"
        assertTrue(BtResumeStorage.isEnabled(storage))
    }

    @Test
    fun `isEnabled prefers modern key`() {
        val storage = mockk<SecureStorage>()
        every { storage.get(SecureStorage.KEY_BT_RESUME_ENABLED) } returns "false"
        every { storage.get(SecureStorage.KEY_CAR_BT_RESUME_ENABLED) } returns "true"
        assertFalse(BtResumeStorage.isEnabled(storage))
    }

    @Test
    fun `allowlist falls back to legacy macs`() {
        val storage = mockk<SecureStorage>()
        every { storage.get(SecureStorage.KEY_BT_DEVICE_MACS) } returns null
        every { storage.get(SecureStorage.KEY_CAR_BT_DEVICE_MACS) } returns
            """["AA:BB:CC:DD:EE:FF"]"""
        assertEquals(setOf("AA:BB:CC:DD:EE:FF"), BtResumeStorage.allowlist(storage))
    }

    @Test
    fun `setters write modern keys`() {
        val storage = mockk<SecureStorage>(relaxed = true)
        BtResumeStorage.setEnabled(storage, true)
        BtResumeStorage.setMode(storage, BtResumeMode.ANY)
        BtResumeStorage.setAllowlist(storage, listOf("aa:bb:cc:dd:ee:ff"))
        verify { storage.put(SecureStorage.KEY_BT_RESUME_ENABLED, "true") }
        verify { storage.put(SecureStorage.KEY_BT_RESUME_MODE, "any") }
        verify { storage.put(SecureStorage.KEY_BT_DEVICE_MACS, """["AA:BB:CC:DD:EE:FF"]""") }
    }
}
