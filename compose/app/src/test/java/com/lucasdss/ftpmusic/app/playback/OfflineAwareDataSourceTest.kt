package com.lucasdss.ftpmusic.app.playback

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline / unreachable enforcement at the playback network boundary:
 * software offline OR server unreachable must fail fast with IOException
 * BEFORE opening a socket.
 */
class OfflineAwareDataSourceTest {

    private fun offlineManager(offline: Boolean): OfflineModeManager {
        val mgr = mockk<OfflineModeManager>(relaxed = true)
        every { mgr.isOfflineEnabled() } returns offline
        return mgr
    }

    private val spec: DataSpec = mockk(relaxed = true)

    @Test
    fun `offline mode blocks open with IOException`() {
        val delegate = mockk<DataSource>(relaxed = true)
        val source = OfflineAwareHttpDataSource(
            offlineManager(offline = true),
            delegate,
            isServerReachable = { true },
        )

        val err = assertThrows(IOException::class.java) { source.open(spec) }
        assertTrue(err.message!!.contains("Offline mode"))
        verify(exactly = 0) { delegate.open(any()) }
    }

    @Test
    fun `server unreachable blocks open with IOException`() {
        val delegate = mockk<DataSource>(relaxed = true)
        val source = OfflineAwareHttpDataSource(
            offlineManager(offline = false),
            delegate,
            isServerReachable = { false },
        )

        val err = assertThrows(IOException::class.java) { source.open(spec) }
        assertTrue(err.message!!.contains("Server unreachable"))
        verify(exactly = 0) { delegate.open(any()) }
    }

    @Test
    fun `online and reachable delegates open to the upstream source`() {
        val delegate = mockk<DataSource>(relaxed = true)
        every { delegate.open(any()) } returns 1234L
        val source = OfflineAwareHttpDataSource(
            offlineManager(offline = false),
            delegate,
            isServerReachable = { true },
        )

        assertEquals(1234L, source.open(spec))
        verify(exactly = 1) { delegate.open(spec) }
    }

    @Test
    fun `toggling offline mid-session blocks subsequent opens`() {
        val delegate = mockk<DataSource>(relaxed = true)
        every { delegate.open(any()) } returns 1234L
        val mgr = offlineManager(offline = false)
        val source = OfflineAwareHttpDataSource(mgr, delegate, isServerReachable = { true })

        source.open(spec)
        every { mgr.isOfflineEnabled() } returns true
        assertThrows(IOException::class.java) { source.open(spec) }
    }

    @Test
    fun `reachability flipping to false blocks subsequent opens`() {
        val delegate = mockk<DataSource>(relaxed = true)
        every { delegate.open(any()) } returns 1234L
        var reachable = true
        val source = OfflineAwareHttpDataSource(
            offlineManager(offline = false),
            delegate,
            isServerReachable = { reachable },
        )

        source.open(spec)
        reachable = false
        assertThrows(IOException::class.java) { source.open(spec) }
        verify(exactly = 1) { delegate.open(any()) }
    }

    @Test
    fun `factory wraps the default HTTP factory`() {
        val factory = OfflineAwareHttpDataSourceFactory(offlineManager(offline = false))
        val source = factory.createDataSource()
        assertTrue(source is OfflineAwareHttpDataSource)
    }
}

/** Integration: the factory's created source actually blocks when offline. */
class OfflineAwareDataSourceFactoryTest {

    private val calls = AtomicInteger(0)

    private val countingFactory = object : DataSource.Factory {
        override fun createDataSource(): DataSource = object : DataSource {
            override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {}
            override fun open(dataSpec: DataSpec): Long {
                calls.incrementAndGet()
                return 100L
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = -1
            override fun getUri(): android.net.Uri? = null
            override fun close() {}
        }
    }

    private fun offlineManager(offline: Boolean): OfflineModeManager {
        val mgr = mockk<OfflineModeManager>(relaxed = true)
        every { mgr.isOfflineEnabled() } returns offline
        return mgr
    }

    @Test
    fun `offline never reaches the wrapped factory's source`() {
        val factory = OfflineAwareHttpDataSourceFactory(offlineManager(offline = true), countingFactory)
        val source = factory.createDataSource()
        assertThrows(IOException::class.java) {
            source.open(mockk<DataSpec>(relaxed = true))
        }
        assertEquals(0, calls.get())
    }
}
