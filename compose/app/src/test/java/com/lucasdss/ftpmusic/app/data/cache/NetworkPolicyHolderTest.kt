package com.lucasdss.ftpmusic.app.data.cache

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class NetworkPolicyHolderTest {

    private val storage: SecureStorage = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val connectivity: ConnectivityManager = mockk(relaxed = true)
    private val prefs = mutableMapOf<String, String>()

    @Before
    fun setUp() {
        prefs.clear()
        every { storage.get(any()) } answers { prefs[firstArg()] }
        every { storage.put(any(), any()) } answers {
            prefs[firstArg()] = secondArg()
            Unit
        }
        every { storage.remove(any()) } answers {
            prefs.remove(firstArg<String>())
            Unit
        }
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivity
        val net = mockk<Network>(relaxed = true)
        val caps = mockk<NetworkCapabilities>(relaxed = true)
        every { connectivity.activeNetwork } returns net
        every { connectivity.getNetworkCapabilities(net) } returns caps
        every { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        NetworkPolicyState.resetForTests()
        DownloadManager.allowMobileData = true
    }

    @After
    fun tearDown() {
        NetworkPolicyState.resetForTests()
        DownloadManager.allowMobileData = true
    }

    @Test
    fun `migrates legacy download_mobile_data false to MINIMAL and clears legacy key`() {
        prefs[SecureStorage.KEY_DOWNLOAD_MOBILE_DATA] = "false"
        val holder = NetworkPolicyHolder(storage, context)

        holder.initialize()

        assertEquals(CellularMediaPolicy.MINIMAL, holder.cellularMediaPolicy.value)
        assertEquals("minimal", prefs[SecureStorage.KEY_CELLULAR_MEDIA_POLICY])
        assertNull(prefs[SecureStorage.KEY_DOWNLOAD_MOBILE_DATA])
        verify(exactly = 1) { storage.remove(SecureStorage.KEY_DOWNLOAD_MOBILE_DATA) }
    }

    @Test
    fun `stored cellular policy wins over leftover legacy key`() {
        prefs[SecureStorage.KEY_CELLULAR_MEDIA_POLICY] = "local_only"
        prefs[SecureStorage.KEY_DOWNLOAD_MOBILE_DATA] = "true"
        val holder = NetworkPolicyHolder(storage, context)

        holder.initialize()

        assertEquals(CellularMediaPolicy.LOCAL_ONLY, holder.cellularMediaPolicy.value)
        // Legacy left untouched when new key already present.
        assertEquals("true", prefs[SecureStorage.KEY_DOWNLOAD_MOBILE_DATA])
    }

    @Test
    fun `wiped new key does not re-migrate after legacy cleared`() {
        prefs[SecureStorage.KEY_DOWNLOAD_MOBILE_DATA] = "false"
        val holder = NetworkPolicyHolder(storage, context)
        holder.initialize()
        assertNull(prefs[SecureStorage.KEY_DOWNLOAD_MOBILE_DATA])

        prefs.remove(SecureStorage.KEY_CELLULAR_MEDIA_POLICY)
        holder.initialize()

        // No legacy → default AUTO_CACHE (allowMobile missing treated as true).
        assertEquals(CellularMediaPolicy.AUTO_CACHE, holder.cellularMediaPolicy.value)
    }
}
