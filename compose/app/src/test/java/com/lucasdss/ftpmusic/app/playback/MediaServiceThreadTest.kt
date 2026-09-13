package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import androidx.media3.cast.CastPlayer
import androidx.media3.common.Player
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaServiceThreadTest {

    private lateinit var service: MediaService
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(CastContext::class)
        val mockCastContext = mockk<CastContext>(relaxed = true)
        val mockSessionManager = mockk<SessionManager>(relaxed = true)
        val mockCastSession = mockk<CastSession>(relaxed = true)
        val mockCastDevice = mockk<CastDevice>(relaxed = true)
        every { mockCastDevice.friendlyName } returns "Test Speaker"
        every { mockCastSession.castDevice } returns mockCastDevice
        every { mockCastSession.volume } returns 0.8
        every { mockCastSession.isMute } returns false
        every { mockSessionManager.currentCastSession } returns null
        every { mockCastContext.sessionManager } returns mockSessionManager
        every { CastContext.getSharedInstance(any()) } returns mockCastContext

        service = spyk(MediaService())
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val uri = mockk<Uri>(relaxed = true)
            every { uri.toString() } returns (firstArg() as String)
            uri
        }
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkStatic(Uri::class)
        unmockkStatic(CastContext::class)
        PlayerHolder.player = null
        PlayerHolder.isCasting = false
    }

    @Test
    fun `PlayerHolder isCasting defaults to false`() {
        assertFalse(PlayerHolder.isCasting)
        assertNull(PlayerHolder.castDeviceName)
    }
}
