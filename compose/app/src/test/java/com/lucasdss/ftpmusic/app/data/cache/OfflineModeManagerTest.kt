package com.lucasdss.ftpmusic.app.data.cache

import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F2: the offline toggle must survive process death. The flag is persisted to
 * SecureStorage on enable/disable and restored at app startup via initialize()
 * BEFORE any worker starts — otherwise the user believes they are offline
 * while downloads/sync/scrobbles stream to the network.
 */
class OfflineModeManagerTest {

    private fun storageWith(value: String?): SecureStorage {
        val s = mockk<SecureStorage>(relaxed = true)
        every { s.get(SecureStorage.KEY_OFFLINE_MODE) } returns value
        return s
    }

    @Test
    fun `initialize enables offline when the persisted flag is true`() {
        val manager = OfflineModeManager(storageWith("true"))
        assertFalse(manager.isOfflineEnabled())
        manager.initialize()
        assertTrue(manager.isOfflineEnabled())
    }

    @Test
    fun `initialize stays online when the persisted flag is false`() {
        val manager = OfflineModeManager(storageWith("false"))
        manager.initialize()
        assertFalse(manager.isOfflineEnabled())
    }

    @Test
    fun `initialize stays online when nothing was persisted`() {
        val manager = OfflineModeManager(storageWith(null))
        manager.initialize()
        assertFalse(manager.isOfflineEnabled())
    }

    @Test
    fun `enable persists the flag and disable clears it`() {
        val s = storageWith(null)
        val manager = OfflineModeManager(s)
        manager.enable()
        assertTrue(manager.isOfflineEnabled())
        verify { s.put(SecureStorage.KEY_OFFLINE_MODE, "true") }
        manager.disable()
        assertFalse(manager.isOfflineEnabled())
        verify { s.put(SecureStorage.KEY_OFFLINE_MODE, "false") }
    }

    @Test
    fun `toggle flips the state and persists it`() {
        val s = storageWith("true")
        val manager = OfflineModeManager(s)
        manager.initialize()
        assertTrue(manager.isOfflineEnabled())
        manager.toggle()
        assertFalse(manager.isOfflineEnabled())
        verify { s.put(SecureStorage.KEY_OFFLINE_MODE, "false") }
    }

    @Test
    fun `initialize ignores a corrupted persisted value`() {
        val manager = OfflineModeManager(storageWith("not-a-boolean"))
        manager.initialize()
        assertFalse(manager.isOfflineEnabled())
    }
}
