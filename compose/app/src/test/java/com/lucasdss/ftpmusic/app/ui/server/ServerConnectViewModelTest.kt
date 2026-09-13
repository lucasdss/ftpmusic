package com.lucasdss.ftpmusic.app.ui.server

import com.lucasdss.ftpmusic.app.data.network.ServerProbe
import com.lucasdss.ftpmusic.app.di.ServerConfig
import com.lucasdss.ftpmusic.app.di.ServerConfigStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ServerConnectViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val serverConfigStore: ServerConfigStore = mockk(relaxed = true)
    private val serverProbe: ServerProbe = mockk(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { serverConfigStore.initialize(any()) } returns ServerConfig()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `connect saves credentials on success`() = runTest {
        coEvery { serverProbe.ping(any(), any(), any()) } just runs

        val vm = ServerConnectViewModel(serverConfigStore, serverProbe)
        vm.onUrlChanged("http://192.168.1.20:4533/")
        vm.onUsernameChanged("user")
        vm.onPasswordChanged("pass")

        var callbackCalled = false
        vm.connect { callbackCalled = true }
        advanceUntilIdle()

        assertTrue(callbackCalled)
        assertTrue(vm.state.value.connected)
        verify { serverConfigStore.set("http://192.168.1.20:4533", "user", "pass") }
    }

    @Test
    fun `connect sets error on failure`() = runTest {
        coEvery { serverProbe.ping(any(), any(), any()) } throws RuntimeException("timeout")

        val vm = ServerConnectViewModel(serverConfigStore, serverProbe)
        vm.onUrlChanged("https://music.example.com")
        vm.onUsernameChanged("user")
        vm.onPasswordChanged("pass")

        vm.connect {}
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.connected)
        verify(exactly = 0) { serverConfigStore.set(any(), any(), any()) }
    }

    @Test
    fun `connect rejects invalid input without network request`() = runTest {
        val vm = ServerConnectViewModel(serverConfigStore, serverProbe)
        vm.onUrlChanged("http://public.example.com")
        vm.onUsernameChanged("user")
        vm.onPasswordChanged("pass")

        vm.connect {}
        advanceUntilIdle()

        assertEquals("HTTP is allowed only for private local-network servers", vm.state.value.error)
        coVerify(exactly = 0) { serverProbe.ping(any(), any(), any()) }
    }
}
