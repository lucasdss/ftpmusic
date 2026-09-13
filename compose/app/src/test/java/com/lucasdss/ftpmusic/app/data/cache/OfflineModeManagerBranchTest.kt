package com.lucasdss.ftpmusic.app.data.cache

import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Branch coverage for the offline toggle-from-online path.
 */
class OfflineModeManagerBranchTest {

    private fun storageWith(value: String?): SecureStorage {
        val s = mockk<SecureStorage>(relaxed = true)
        every { s.get(SecureStorage.KEY_OFFLINE_MODE) } returns value
        return s
    }

    @Test
    fun `toggle from online enables offline`() {
        val storage = storageWith("false")
        val manager = OfflineModeManager(storage)
        manager.initialize()
        manager.toggle()
        assertTrue(manager.isOfflineEnabled())
        verify { storage.put(SecureStorage.KEY_OFFLINE_MODE, "true") }
    }
}
