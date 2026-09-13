package com.lucasdss.ftpmusic.app.cast

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaLibraryService
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.lucasdss.ftpmusic.app.playback.CastPreferences
import com.lucasdss.ftpmusic.app.playback.MediaService
import com.lucasdss.ftpmusic.app.playback.MediaSessionPlaybackProvider
import com.lucasdss.ftpmusic.app.playback.PlaybackManager
import com.lucasdss.ftpmusic.app.playback.PlaybackState
import com.lucasdss.ftpmusic.app.playback.PlayerHolder
import com.lucasdss.ftpmusic.app.playback.QueuePersistenceManager
import com.lucasdss.ftpmusic.app.playback.buildCastConnectTimeoutMessage
import com.lucasdss.ftpmusic.app.ui.player.CastButtonState
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Round-2 review tests (H1/H2/M4/M5/M7 + Edge-05): the connect-failure paths
 * that previously left a stuck cast state. These call REAL MediaService code
 * on the spy harness (mockkStatic CastContext/MediaRouter/CastDevice +
 * reflection field injection — same pattern as MediaServiceCastQueueTest).
 */
class CastConnectFailureTest {

    private lateinit var service: MediaService
    private lateinit var core: MediaService
    private lateinit var mockCastContext: CastContext
    private lateinit var mockSessionManager: SessionManager
    private lateinit var mockCastSession: CastSession
    private lateinit var mockCastDevice: CastDevice
    private lateinit var mockRmc: RemoteMediaClient
    private lateinit var mockMediaSession: MediaLibraryService.MediaLibrarySession
    private lateinit var mockExoPlayer: Player
    private lateinit var mockCastPlayer: Player
    private lateinit var mockRouter: MediaRouter
    private lateinit var mockRoute: MediaRouter.RouteInfo

    private val deviceA = device("dev-A", "Soundbar")
    private val deviceB = device("dev-B", "Mini Speaker")

    private fun device(id: String, name: String): CastDevice {
        val d = mockk<CastDevice>()
        every { d.deviceId } returns id
        every { d.friendlyName } returns name
        return d
    }

    @Before
    fun setUp() {
        mockkStatic(CastContext::class)
        mockkStatic(MediaRouter::class)
        mockkStatic(CastDevice::class)

        // CastContext → SessionManager → CastSession → device/RMC chain
        mockCastDevice = mockk(relaxed = true)
        mockRmc = mockk(relaxed = true)
        mockCastSession = mockk(relaxed = true)
        every { mockCastSession.remoteMediaClient } returns mockRmc
        mockSessionManager = mockk(relaxed = true)
        every { mockSessionManager.currentCastSession } returns null
        mockCastContext = mockk(relaxed = true)
        every { mockCastContext.sessionManager } returns mockSessionManager
        every { CastContext.getSharedInstance(any()) } returns mockCastContext

        // MediaRouter → one route per device
        mockRoute = mockk<MediaRouter.RouteInfo>(relaxed = true)
        every { mockRoute.extras } returns Bundle()
        mockRouter = mockk<MediaRouter>(relaxed = true)
        every { MediaRouter.getInstance(any()) } returns mockRouter

        // Players + MediaSession
        mockExoPlayer = mockk<Player>(relaxed = true)
        every { mockExoPlayer.mediaItemCount } returns 0
        mockCastPlayer = mockk<Player>(relaxed = true)
        mockMediaSession = mockk<MediaLibraryService.MediaLibrarySession>(relaxed = true)
        every { mockMediaSession.player } returns mockCastPlayer

        core = MediaService()
        service = spyk(core)
        // Inject on BOTH the spy and the underlying core: mockk spy delegates
        // intercepted calls to the core instance, and the private
        // sessionManagerListener's this$0 IS the core — so the core's fields
        // must be mocked too or private-method paths read nulls.
        injectAll(core)
        injectAll(service)

        PlayerHolder.player = mockCastPlayer
        PlayerHolder.exoPlayer = mockExoPlayer
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null
        CastButtonState.isCasting.value = false
        CastButtonState.connectedDeviceName.value = null
        CastButtonState.connectingDeviceName.value = null
        CastButtonState.castErrorMessage.value = null
    }

    private fun injectAll(target: Any) {
        injectField(target, "castPreferences", mockk<CastPreferences>(relaxed = true))
        injectField(target, "persistenceManager", mockk<QueuePersistenceManager>(relaxed = true))
        injectField(target, "playbackManager", mockk<PlaybackManager>(relaxed = true))
        val mockProvider = mockk<MediaSessionPlaybackProvider>(relaxed = true)
        every { mockProvider.playbackState } returns kotlinx.coroutines.flow.MutableStateFlow(PlaybackState())
        injectField(target, "playbackProvider", mockProvider)
        injectField(target, "mediaSession", mockMediaSession)
        injectField(target, "exoPlayer", mockExoPlayer)
        injectField(target, "castPlayer", mockCastPlayer)
    }

    @After
    fun tearDown() {
        unmockkStatic(CastContext::class)
        unmockkStatic(MediaRouter::class)
        unmockkStatic(CastDevice::class)
        PlayerHolder.player = null
        PlayerHolder.exoPlayer = null
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null
        CastButtonState.isCasting.value = false
        CastButtonState.connectedDeviceName.value = null
        CastButtonState.connectingDeviceName.value = null
        CastButtonState.castErrorMessage.value = null
        CastButtonState.onConnectRequested = null
        CastButtonState.onConnectCancelled = null
        CastButtonState.onEndSessionForSwitchRequested = null
        CastButtonState.onDisconnectRequested = null
    }

    private fun injectField(target: Any, fieldName: String, value: Any?) {
        try {
            val field = target::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(target, value)
        } catch (_: NoSuchFieldException) {
            val f = target::class.java.superclass?.getDeclaredField(fieldName)
            f?.isAccessible = true
            f?.set(target, value)
        }
    }

    private fun buildTestMediaItem(id: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri("https://example.com/stream?id=$id")
        .setMimeType("audio/mpeg")
        .setMediaMetadata(MediaMetadata.Builder().setTitle("Song $id").build())
        .build()

    // ── H1: failed switch / connect must recover to local playback ──────

    @Test
    fun `failCastConnect with no live session forces local playback`() {
        // Stuck-state precondition: stale switch left isCasting=true with no session.
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = "Soundbar"
        CastButtonState.isCasting.value = true
        CastButtonState.connectedDeviceName.value = "Soundbar"
        CastButtonState.connectingDeviceName.value = "Mini Speaker"
        every { mockSessionManager.currentCastSession } returns null

        service.failCastConnect("Mini Speaker", "Couldn't connect to \"Mini Speaker\". Please try again.")

        assertFalse("isCasting must be reset after a failed connect", PlayerHolder.isCasting)
        assertNull("castDeviceName must be cleared", PlayerHolder.castDeviceName)
        assertFalse("CastButtonState.isCasting must be reset", CastButtonState.isCasting.value)
        assertNull("connectedDeviceName must be cleared", CastButtonState.connectedDeviceName.value)
        assertNull("connectingDeviceName must be cleared", CastButtonState.connectingDeviceName.value)
        assertEquals(
            "Error must be surfaced",
            "Couldn't connect to \"Mini Speaker\". Please try again.",
            CastButtonState.castErrorMessage.value,
        )
        assertSame("Player must be re-seated to ExoPlayer", mockExoPlayer, PlayerHolder.player)
    }

    @Test
    fun `failCastConnect keeps cast state when a different session survived`() {
        // A failed switch where the OLD session survived (GMS endCurrentSession
        // no-op): the blue icon stays accurate — nothing is forced local.
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = "Soundbar"
        CastButtonState.isCasting.value = true
        CastButtonState.connectedDeviceName.value = "Soundbar"
        every { mockCastSession.castDevice } returns deviceA
        every { mockCastSession.isConnected } returns true
        every { mockSessionManager.currentCastSession } returns mockCastSession

        service.failCastConnect("Mini Speaker", "Couldn't connect to \"Mini Speaker\". Please try again.")

        assertTrue("Surviving session must keep isCasting=true", PlayerHolder.isCasting)
        assertEquals("Surviving session must keep its device name", "Soundbar", PlayerHolder.castDeviceName)
        assertTrue("CastButtonState.isCasting must stay true", CastButtonState.isCasting.value)
        assertEquals(
            "Error must still be surfaced",
            "Couldn't connect to \"Mini Speaker\". Please try again.",
            CastButtonState.castErrorMessage.value,
        )
    }

    @Test
    fun `cancelCastConnect after a switch teardown forces local playback`() {
        // H2-B2 ordering: the switch's onSessionEnded already skipped cleanup
        // (switch-pending), then the user cancels — cancel must recover to local.
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = "Soundbar"
        CastButtonState.isCasting.value = true
        CastButtonState.connectedDeviceName.value = "Soundbar"
        every { mockCastSession.castDevice } returns deviceA
        every { mockSessionManager.currentCastSession } returns mockCastSession
        // Start a switch A→B (sets castSwitchInProgress) then cancel it.
        CastButtonState.onEndSessionForSwitchRequested = { service.endCurrentCastSession() }
        service.connectToCastDevice(deviceB)
        every { mockSessionManager.currentCastSession } returns null // teardown completed

        service.cancelCastConnect()

        assertFalse("Cancel after a switch teardown must reset isCasting", PlayerHolder.isCasting)
        assertNull("connectedDeviceName must be cleared", CastButtonState.connectedDeviceName.value)
        assertNull("connectingDeviceName must be cleared", CastButtonState.connectingDeviceName.value)
        assertSame("Player must be re-seated to ExoPlayer", mockExoPlayer, PlayerHolder.player)
    }

    @Test
    fun `cancelCastConnect without a switch does not touch cast state`() {
        // Fresh-connect cancel (never casting): nothing to tear down.
        PlayerHolder.isCasting = false
        every { mockSessionManager.currentCastSession } returns null
        service.connectToCastDevice(deviceA) // no session → immediate select path
        // connectToCastDevice with no route match calls failCastConnect → force-local no-op (not casting)

        service.cancelCastConnect()

        assertFalse("isCasting must stay false", PlayerHolder.isCasting)
        assertNull("connectingDeviceName must be null", CastButtonState.connectingDeviceName.value)
    }

    // ── M5: start-failed during a switch cleans up ──────────────────────

    @Test
    fun `onSessionStartFailed during a switch clears connect state and forces local`() {
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = "Soundbar"
        CastButtonState.isCasting.value = true
        CastButtonState.connectedDeviceName.value = "Soundbar"
        CastButtonState.connectingDeviceName.value = "Mini Speaker"
        every { mockSessionManager.currentCastSession } returns null // B never started, A gone

        val listener = service.javaClass.getDeclaredField("sessionManagerListener").apply { isAccessible = true }
            .get(service) as SessionManagerListener<CastSession>
        listener.onSessionStartFailed(mockCastSession, 2100)

        assertFalse("Start-failed during a switch must clear isCasting", PlayerHolder.isCasting)
        assertNull("connectingDeviceName must be cleared", CastButtonState.connectingDeviceName.value)
        assertNotNull("Error must be surfaced", CastButtonState.castErrorMessage.value)
        assertSame("Player must be re-seated to ExoPlayer", mockExoPlayer, PlayerHolder.player)
    }

    // ── Edge-05: healthy vs stale same-device session ───────────────────

    @Test
    fun `healthy same-device connect does not tear down the session`() {
        every { mockCastSession.castDevice } returns deviceA
        every { mockCastSession.isConnected } returns true
        every { mockRmc.mediaStatus } returns mockk(relaxed = true) // healthy media
        every { mockSessionManager.currentCastSession } returns mockCastSession
        PlayerHolder.isCasting = true

        service.connectToCastDevice(deviceA)

        verify(exactly = 0) { mockSessionManager.endCurrentSession(any()) }
        assertNull(
            "Connecting state must clear for an already-connected device",
            CastButtonState.connectingDeviceName.value,
        )
        assertTrue("Healthy cast must keep isCasting=true", PlayerHolder.isCasting)
    }

    @Test
    fun `stale same-device connect tears down the session first`() {
        every { mockCastSession.castDevice } returns deviceA
        every { mockRmc.mediaStatus } returns null // stale/phantom — no media
        every { mockSessionManager.currentCastSession } returns mockCastSession
        PlayerHolder.isCasting = true

        service.connectToCastDevice(deviceA)

        verify(exactly = 1) { mockSessionManager.endCurrentSession(any()) }
        assertEquals(
            "Connecting state must show the device",
            "Soundbar",
            CastButtonState.connectingDeviceName.value,
        )
    }

    // ── Device switch A→B ───────────────────────────────────────────────

    @Test
    fun `device switch ends the old session before selecting the new route`() {
        every { mockCastSession.castDevice } returns deviceA
        every { mockRmc.mediaStatus } returns mockk(relaxed = true)
        every { mockSessionManager.currentCastSession } returns mockCastSession
        every { mockRouter.routes } returns emptyList() // select not reached in this test
        var endSessionForSwitchCalled = false
        CastButtonState.onEndSessionForSwitchRequested = {
            endSessionForSwitchCalled = true
            service.endCurrentCastSession()
        }
        PlayerHolder.isCasting = true

        service.connectToCastDevice(deviceB)

        assertTrue("Device switch must end the old session first", endSessionForSwitchCalled)
        verify(exactly = 1) { mockSessionManager.endCurrentSession(any()) }
        assertEquals(
            "Connecting state must show the NEW device",
            "Mini Speaker",
            CastButtonState.connectingDeviceName.value,
        )
    }

    @Test
    fun `connect with no session selects the matching route immediately`() {
        every { mockCastDevice.deviceId } returns deviceA.deviceId
        every { mockCastDevice.friendlyName } returns deviceA.friendlyName
        every { CastDevice.getFromBundle(any()) } returns mockCastDevice
        every { mockRouter.routes } returns listOf(mockRoute)
        every { mockSessionManager.currentCastSession } returns null

        service.connectToCastDevice(deviceA)

        verify(exactly = 1) { mockRoute.select() }
        assertEquals(
            "Connecting state must show the device",
            "Soundbar",
            CastButtonState.connectingDeviceName.value,
        )
    }

    // ── clearCastConnectAttempt (success path) ──────────────────────────

    @Test
    fun `clearCastConnectAttempt clears stale error and releases flags`() {
        CastButtonState.castErrorMessage.value = "Couldn't connect to \"Soundbar\". Please try again."
        PlayerHolder.isCasting = true
        every { mockSessionManager.currentCastSession } returns null

        service.clearCastConnectAttempt()

        assertNull("Stale error must be cleared on connect success", CastButtonState.castErrorMessage.value)
    }

    // ── Timeout message (GMS repair hint) ───────────────────────────────

    @Test
    fun `timeout message keeps device name and adds actionable GMS repair hint`() {
        val msg = buildCastConnectTimeoutMessage("Hallway display")

        assertTrue("Message must name the device", msg.contains("Hallway display"))
        assertTrue("Message must keep the historical first sentence", msg.contains("Please try again."))
        assertTrue("Message must mention same-Wi-Fi check", msg.contains("same Wi-Fi"))
        assertTrue(
            "Message must mention Google Play Services repair path",
            msg.contains("Google Play Services") && msg.contains("Clear cache"),
        )
    }

    @Test
    fun `timeout message interpolates device name verbatim`() {
        val msg = buildCastConnectTimeoutMessage("Living \"Room\" TV")
        // Quotes inside the device name are preserved raw (plain-text dialog,
        // no escaping needed).
        assertTrue(
            "Device name must appear verbatim inside the message",
            msg.contains("Couldn't connect to \"Living \"Room\" TV\"."),
        )
    }

    @Test
    fun `connect timeout firing surfaces GMS hint and clears connecting state`() {
        // The fire-path extracted from the startCastConnectTimeout lambda
        // (private handler timing made it untestable): firing the timeout must
        // surface the actionable message and clear the connecting state.
        CastButtonState.connectingDeviceName.value = "Hallway display"
        every { mockSessionManager.currentCastSession } returns null
        PlayerHolder.isCasting = false

        service.fireConnectTimeout("Hallway display")

        val msg = CastButtonState.castErrorMessage.value
        assertNotNull("Timeout must surface an error message", msg)
        assertTrue("Error must include the GMS repair hint", msg!!.contains("Google Play Services"))
        assertTrue("Error must include the same-Wi-Fi hint", msg.contains("same Wi-Fi"))
        assertNull(
            "connectingDeviceName must be cleared on timeout",
            CastButtonState.connectingDeviceName.value,
        )
        verify(exactly = 1) { service.failCastConnect(any(), any()) }
    }
}
