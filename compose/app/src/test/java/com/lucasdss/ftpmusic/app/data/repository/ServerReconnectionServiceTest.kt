package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ServerReconnectionServiceTest {
    private val api: SubsonicApi = mockk()
    private val service = ServerReconnectionService(api)

    @Test
    fun `tryReconnect returns connected on success`() = runTest {
        coEvery { api.ping(any(), any(), any()) } returns mapOf("status" to "ok")
        val result = service.tryReconnect("http://srv", "user", "pass")
        assertTrue(result.connected)
        assertEquals("http://srv", result.serverUrl)
    }

    @Test
    fun `tryReconnect returns disconnected on failure`() = runTest {
        coEvery { api.ping(any(), any(), any()) } throws RuntimeException("timeout")
        val result = service.tryReconnect("http://srv", "user", "pass")
        assertFalse(result.connected)
    }
}
