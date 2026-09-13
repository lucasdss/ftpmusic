package com.lucasdss.ftpmusic.app.playback

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
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
 * Offline enforcement at the playback network boundary (F13): while software
 * offline mode is on, the upstream data source must fail fast with IOException
 * BEFORE opening a socket, so an uncached track errors in milliseconds and the
 * auto-skip guard advances the queue instead of burning the HTTP retry budget.
 */
class OfflineAwareDataSourceTest {

    private fun offlineManager(offline: Boolean): OfflineModeManager {
        val mgr = mockk<OfflineModeManager>(relaxed = true)
        every { mgr.isOfflineEnabled() } returns offline
        return mgr
    }

    // DataSpec is mocked — android.net.Uri.parse() returns null off-Android,
    // and the source under test never reads the spec (it blocks first).
    private val spec: DataSpec = mockk(relaxed = true)

    @Test
    fun `offline mode blocks open with IOException`() {
        val delegate = mockk<DataSource>(relaxed = true)
        val source = OfflineAwareHttpDataSource(offlineManager(offline = true), delegate)

        assertThrows(IOException::class.java) { source.open(spec) }
        // The delegate must NEVER be reached — no socket, no network
        verify(exactly = 0) { delegate.open(any()) }
    }

    @Test
    fun `online mode delegates open to the upstream source`() {
        val delegate = mockk<DataSource>(relaxed = true)
        every { delegate.open(any()) } returns 1234L
        val source = OfflineAwareHttpDataSource(offlineManager(offline = false), delegate)

        assertEquals(1234L, source.open(spec))
        verify(exactly = 1) { delegate.open(spec) }
    }

    @Test
    fun `toggling offline mid-session blocks subsequent opens`() {
        val delegate = mockk<DataSource>(relaxed = true)
        every { delegate.open(any()) } returns 1234L
        val mgr = offlineManager(offline = false)
        val source = OfflineAwareHttpDataSource(mgr, delegate)

        source.open(spec) // online — passes through
        every { mgr.isOfflineEnabled() } returns true
        assertThrows(IOException::class.java) { source.open(spec) }
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
