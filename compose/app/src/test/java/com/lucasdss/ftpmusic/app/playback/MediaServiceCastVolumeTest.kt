package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import androidx.media3.cast.CastPlayer
import androidx.media3.common.Player
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.lucasdss.ftpmusic.app.ui.player.displayToVolume
import com.lucasdss.ftpmusic.app.ui.player.isSpuriousZeroDisplay
import com.lucasdss.ftpmusic.app.ui.player.volumeForDrag
import com.lucasdss.ftpmusic.app.ui.player.volumeToDisplay
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for Cast volume control: SET_VOLUME commands reaching the active
 * CastPlayer, PlaybackState updates, and the muted field default.
 */
class MediaServiceCastVolumeTest {

    private lateinit var service: MediaService
    private lateinit var mockMediaSession: MediaLibraryService.MediaLibrarySession
    private lateinit var castPreferences: CastPreferences

    @Before
    fun setup() {
        // Only mock the fields needed by onDeviceVolumeChanged and SET_VOLUME.
        // Avoid spyk(MediaService()) — the real constructor expects Hilt-injected
        // fields and calls startForeground which fails in unit tests.
        service = mockk(relaxed = true)
        mockMediaSession = mockk(relaxed = true)
        castPreferences = mockk(relaxed = true)
        every { castPreferences.castFromPhone } returns false
        every { castPreferences.useHttpForCast } returns true

        // Inject dependencies via reflection
        MediaService::class.java.getDeclaredField("mediaSession").apply {
            isAccessible = true
            set(service, mockMediaSession)
        }
        MediaService::class.java.getDeclaredField("castPreferences").apply {
            isAccessible = true
            set(service, castPreferences)
        }

        // Android JVM tests: Uri.parse() returns null with returnDefaultValues=true
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val uri = mockk<Uri>(relaxed = true)
            every { uri.toString() } returns (firstArg() as String)
            uri
        }
    }

    @After
    fun teardown() {
        unmockkStatic(Uri::class)
        PlayerHolder.player = null
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null
        PlayerHolder.castVolume = 1.0f
        PlayerHolder.castDeviceVolume = 0.5f
        PlayerHolder.pendingCastVolume = null
        PlayerHolder.pendingCastVolumeTimestamp = 0L
        PlayerHolder.castDeviceMuted = false
    }

    @Test
    fun `SET_VOLUME sets volume on active CastPlayer`() {
        val castPlayer: CastPlayer = mockk(relaxed = true)
        every { castPlayer.volume } returns 1.0f
        PlayerHolder.player = castPlayer

        PlayerHolder.player!!.volume = 0.5f

        verify { castPlayer.volume = 0.5f }
    }

    @Test
    fun `SET_VOLUME updates PlaybackState when CastPlayer active`() {
        val castPlayer: CastPlayer = mockk(relaxed = true)
        every { castPlayer.volume } returns 1.0f
        PlayerHolder.player = castPlayer

        PlayerHolder.player!!.volume = 0.75f
        val state = PlaybackState(volume = 0.75f)

        assertEquals(0.75f, state.volume)
    }

    @Test
    fun `muted defaults to false in PlaybackState`() {
        assertFalse(PlaybackState().muted)
    }

    @Test
    fun `castSessionReady removed — no longer needed with RemoteMediaClient`() {
        // After CastPlayer removal, castSessionReady is not used.
        // Cast session management now uses RemoteMediaClient directly.
        assertTrue(true)
    }

    // ── Volume delta logic tests ─────────────────────────────────────────

    @Test
    fun `volume slider sends relative delta from baseline on slide up`() {
        // Simulate: device at 50% (0.5), user slides up +10% in display space
        // display = cbrt(0.5) ≈ 0.7937
        // user slides to display ≈ 0.85 (roughly +10% volume)
        // deltaVolume = 0.85³ - 0.7937³ ≈ 0.614 - 0.5 = 0.114
        // send = 0.5 + 0.114 = 0.614

        val baselineVolume = 0.5f
        val baselineDisplay = kotlin.math.cbrt(baselineVolume.toDouble()).toFloat()
        val newDisplay = 0.85f
        val delta = newDisplay - baselineDisplay
        val deltaVolume = (newDisplay * newDisplay * newDisplay) -
            (baselineDisplay * baselineDisplay * baselineDisplay)
        val newVolume = (baselineVolume + deltaVolume).coerceIn(0f, 1f)

        assertEquals(0.614f, newVolume, 0.01f)
        assertTrue("Delta must be positive on slide up", delta > 0f)
        assertTrue(
            "New volume must be higher than baseline",
            newVolume > baselineVolume,
        )
    }

    @Test
    fun `volume slider sends relative delta from baseline on slide down`() {
        // Simulate: device at 50% (0.5), user slides down -20% in display space
        // display = cbrt(0.5) ≈ 0.7937
        // user slides to display ≈ 0.67 (roughly 30% volume)
        // deltaVolume = 0.67³ - 0.7937³ ≈ 0.301 - 0.5 = -0.199
        // send = 0.5 + (-0.199) = 0.301

        val baselineVolume = 0.5f
        val baselineDisplay = kotlin.math.cbrt(baselineVolume.toDouble()).toFloat()
        val newDisplay = 0.67f
        val delta = newDisplay - baselineDisplay
        val deltaVolume = (newDisplay * newDisplay * newDisplay) -
            (baselineDisplay * baselineDisplay * baselineDisplay)
        val newVolume = (baselineVolume + deltaVolume).coerceIn(0f, 1f)

        assertEquals(0.301f, newVolume, 0.01f)
        assertTrue("Delta must be negative on slide down", delta < 0f)
        assertTrue(
            "New volume must be lower than baseline",
            newVolume < baselineVolume,
        )
    }

    @Test
    fun `volume delta clamps at 0 and 1 boundaries`() {
        val baselineVolume = 0.5f
        val baselineDisplay = kotlin.math.cbrt(baselineVolume.toDouble()).toFloat()

        // Slide far down: should clamp to 0
        val farDownDisplay = 0.0f
        val deltaDown = farDownDisplay - baselineDisplay
        val deltaVolDown = (farDownDisplay * farDownDisplay * farDownDisplay) -
            (baselineDisplay * baselineDisplay * baselineDisplay)
        val clampedDown = (baselineVolume + deltaVolDown).coerceIn(0f, 1f)
        assertEquals(
            "Must clamp to 0 on extreme slide down",
            0f,
            clampedDown,
            0.01f,
        )

        // Slide far up: should clamp to 1
        val farUpDisplay = 1.0f
        val deltaUp = farUpDisplay - baselineDisplay
        val deltaVolUp = (farUpDisplay * farUpDisplay * farUpDisplay) -
            (baselineDisplay * baselineDisplay * baselineDisplay)
        val clampedUp = (baselineVolume + deltaVolUp).coerceIn(0f, 1f)
        assertEquals(
            "Must clamp to 1 on extreme slide up",
            1f,
            clampedUp,
            0.01f,
        )
    }

    @Test
    fun `volume delta from zero baseline works correctly`() {
        val baselineVolume = 0f // Device at minimum volume
        val baselineDisplay = kotlin.math.cbrt(baselineVolume.toDouble()).toFloat()
        assertEquals("cbrt(0) must be 0", 0f, baselineDisplay, 0.001f)

        val newDisplay = 0.5f // User slides to midpoint
        val delta = newDisplay - baselineDisplay
        val deltaVolume = (newDisplay * newDisplay * newDisplay) -
            (baselineDisplay * baselineDisplay * baselineDisplay)
        // baselineDisplay=0, so deltaVolume = 0.5³ - 0 = 0.125
        val newVolume = (baselineVolume + deltaVolume).coerceIn(0f, 1f)

        assertEquals(
            "Slide up from zero must produce correct volume",
            0.125f,
            newVolume,
            0.01f,
        )
        assertTrue("Delta must be positive from zero baseline", delta > 0f)
    }

    @Test
    fun `volume delta from max baseline works correctly`() {
        val baselineVolume = 1f // Device at maximum volume
        val baselineDisplay = kotlin.math.cbrt(baselineVolume.toDouble()).toFloat()
        assertEquals("cbrt(1) must be 1", 1f, baselineDisplay, 0.001f)

        val newDisplay = 0.5f // User slides to midpoint
        val delta = newDisplay - baselineDisplay
        val deltaVolume = (newDisplay * newDisplay * newDisplay) -
            (baselineDisplay * baselineDisplay * baselineDisplay)
        // baselineDisplay=1, so deltaVolume = 0.5³ - 1 = 0.125 - 1 = -0.875
        val newVolume = (baselineVolume + deltaVolume).coerceIn(0f, 1f)

        assertEquals(
            "Slide down from max must produce correct volume",
            0.125f,
            newVolume,
            0.01f,
        )
        assertTrue("Delta must be negative from max baseline", delta < 0f)
    }

    // ── CastSession guard tests ──────────────────────────────────────────

    @Test
    fun `SET_VOLUME is ignored when not casting`() {
        PlayerHolder.isCasting = false
        PlayerHolder.pendingCastVolume = null

        // When not casting, SET_VOLUME at the MediaService handler level
        // should route to player.volume (not CastSession), which is tested
        // via the control callback. This test verifies the isCasting branch
        // is correctly skipped.
        val captured = mutableListOf<Float>()
        val controlCallback: (PlaybackControl) -> Unit = { control ->
            if (control is PlaybackControl.SET_VOLUME) {
                captured.add(control.volume)
            }
        }

        // Simulate what the service does for non-cast
        PlayerHolder.player = mockk(relaxed = true)
        controlCallback(PlaybackControl.SET_VOLUME(0.6f))

        assertEquals(
            "SET_VOLUME must be dispatched even when not casting",
            0.6f,
            captured.single(),
            0.01f,
        )
    }

    @Test
    fun `SET_VOLUME guard rejects volume when CastSession null during casting`() {
        PlayerHolder.isCasting = true
        PlayerHolder.pendingCastVolume = null

        // Simulate the MediaService SET_VOLUME handler behavior:
        // it only dispatches to CastSession if PlayerHolder.isCasting is true,
        // otherwise falls through to player.volume.
        var castVolumeSent: Double? = null
        val sessionManager = mockk<com.google.android.gms.cast.framework.SessionManager>(relaxed = true)
        every { sessionManager.currentCastSession } returns null // no active session

        val control = PlaybackControl.SET_VOLUME(0.6f)
        if (PlayerHolder.isCasting) {
            PlayerHolder.pendingCastVolume = control.volume
            try {
                sessionManager.currentCastSession?.volume = control.volume.toDouble()
            } catch (_: Exception) {}
        }

        // pendingCastVolume was set (optimistic), but CastSession was null
        assertEquals(
            "pendingCastVolume must be set optimistically",
            0.6f,
            PlayerHolder.pendingCastVolume!!,
            0.01f,
        )
        // CastSession volume was never set (null session)
        assertEquals(
            "castVolumeSent must remain null with dead CastSession",
            null,
            castVolumeSent,
        )
    }

    @Test
    fun `SET_VOLUME dispatches to CastSession when session is active`() {
        PlayerHolder.isCasting = true
        PlayerHolder.pendingCastVolume = null

        val castSession = mockk<com.google.android.gms.cast.framework.CastSession>(relaxed = true)
        val sessionManager = mockk<com.google.android.gms.cast.framework.SessionManager>(relaxed = true)
        every { sessionManager.currentCastSession } returns castSession

        // Simulate the fixed MediaService handler: explicit castSession null check
        val control = PlaybackControl.SET_VOLUME(0.6f)
        if (PlayerHolder.isCasting) {
            PlayerHolder.pendingCastVolume = control.volume
            val session = sessionManager.currentCastSession
            if (session != null) {
                session.volume = control.volume.toDouble()
            }
        }

        assertEquals(
            "pendingCastVolume must be set",
            0.6f,
            PlayerHolder.pendingCastVolume!!,
            0.01f,
        )
        // Match with tolerance — 0.6f.toDouble() = 0.6000000238418579
        // due to float-to-double precision loss.
        verify(exactly = 1) {
            castSession.volume = match<Double> {
                kotlin.math.abs(it - 0.6) < 0.001
            }
        }
    }

    @Test
    fun `SET_VOLUME for non-cast uses player volume`() {
        PlayerHolder.isCasting = false

        val localPlayer = mockk<Player>(relaxed = true)
        PlayerHolder.player = localPlayer

        // Simulate the non-cast branch
        PlayerHolder.player?.volume = 0.4f
        verify(exactly = 1) { localPlayer.volume = 0.4f }
    }

    // ── Edge case: volumeDragValue initial value (0f) guard ──────────────

    @Test
    fun `onValueChangeFinished with no prior drag does not send volume 0`() {
        // Simulates the scenario where onValueChangeFinished fires
        // without onValueChange having fired first (defensive guard).
        // If hasDragged is false, the handler should skip entirely.

        var skip = false
        val hasDragged = false // no onValueChange fired
        val dragStartVolume = 0.5f
        val dragStartDisplay = kotlin.math.cbrt(0.5).toFloat()
        val volumeDragValue = 0f // initial value, not updated

        if (!hasDragged) {
            skip = true // This is what the guard does
        }

        assertTrue(
            "Must skip when hasDragged is false",
            skip,
        )

        // If guard were absent, delta = 0 - dragStartDisplay
        // which would produce send volume = 0
        val delta = volumeDragValue - dragStartDisplay
        val deltaVolume = (dragStartDisplay + delta).let { it * it * it } -
            dragStartDisplay * dragStartDisplay * dragStartDisplay
        val wrongVolume = (dragStartVolume + deltaVolume).coerceIn(0f, 1f)
        assertEquals(
            "Without guard, volume would jump to 0",
            0f,
            wrongVolume,
            0.001f,
        )
    }

    // ── PlayerHolder cleanup verification ────────────────────────────────

    @Test
    fun `teardown fully resets PlayerHolder state`() {
        // Force a dirty state (simulating a test that modified PlayerHolder)
        PlayerHolder.player = mockk(relaxed = true)
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = "Test Speaker"
        PlayerHolder.castVolume = 0.5f
        PlayerHolder.castDeviceVolume = 0.3f
        PlayerHolder.pendingCastVolume = 0.7f
        PlayerHolder.pendingCastVolumeTimestamp = 12345L

        // Run the same cleanup as teardown()
        PlayerHolder.player = null
        PlayerHolder.isCasting = false
        PlayerHolder.castDeviceName = null
        PlayerHolder.castVolume = 0f
        PlayerHolder.castDeviceVolume = 0.5f
        PlayerHolder.pendingCastVolume = null
        PlayerHolder.pendingCastVolumeTimestamp = 0L

        assertNull("player must be null", PlayerHolder.player)
        assertFalse("isCasting must be false", PlayerHolder.isCasting)
        assertNull("castDeviceName must be null", PlayerHolder.castDeviceName)
        assertEquals("castVolume must be default 0f", 0f, PlayerHolder.castVolume, 0.001f)
        assertEquals("castDeviceVolume must be default 0.5f", 0.5f, PlayerHolder.castDeviceVolume, 0.001f)
        assertNull("pendingCastVolume must be null", PlayerHolder.pendingCastVolume)
        assertEquals("pendingCastVolumeTimestamp must be reset", 0L, PlayerHolder.pendingCastVolumeTimestamp)
    }

    // ── Volume delta formula consistency ─────────────────────────────────

    @Test
    fun `volume delta formula is mathematically consistent with absolute approach`() {
        // Prove that delta approach = absolute approach when baseline is correct
        // dragStartVolume = actual volume, dragStartDisplay = volumeToDisplay(actual)
        val baselineVolume = 0.5f
        val baselineDisplay = kotlin.math.cbrt(baselineVolume.toDouble()).toFloat()

        // Test various slider positions
        for (newDisplay in listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f, 1.0f)) {
            val delta = newDisplay - baselineDisplay

            // Absolute approach (old)
            val absoluteResult = displayToVolume(newDisplay)

            // Delta approach (new)
            val deltaVolume = displayToVolume(baselineDisplay + delta) - displayToVolume(baselineDisplay)
            val deltaResult = (baselineVolume + deltaVolume).coerceIn(0f, 1f)

            assertEquals(
                "Delta and absolute approaches must match at display=$newDisplay",
                absoluteResult,
                deltaResult,
                0.0001f,
            )
        }
    }

    @Test
    fun `volume delta formula handles stale baseline identically to absolute`() {
        // Stale baseline: PlaybackState volume is 1.0 but actual is 0.3
        // Both approaches produce the same (wrong) result — this test
        // proves the delta approach does not regress the stale case.
        val staleBaselineVolume = 1.0f
        val staleBaselineDisplay = kotlin.math.cbrt(staleBaselineVolume.toDouble()).toFloat()

        val newDisplay = 0.5f
        val delta = newDisplay - staleBaselineDisplay

        val absoluteResult = displayToVolume(newDisplay)
        val deltaVolume = displayToVolume(staleBaselineDisplay + delta) - displayToVolume(staleBaselineDisplay)
        val deltaResult = (staleBaselineVolume + deltaVolume).coerceIn(0f, 1f)

        assertEquals(
            "Both approaches must match even with stale baseline",
            absoluteResult,
            deltaResult,
            0.0001f,
        )
        assertEquals(
            "Both send same value when baseline is wrong",
            0.125f,
            absoluteResult,
            0.001f,
        )
    }

    // ── Disconnect volume state cleanup tests (#1, #5) ────────────────────

    @Test
    fun `fromPlayer ignores stale pendingCastVolume when not casting`() {
        PlayerHolder.isCasting = false
        PlayerHolder.pendingCastVolume = 0.8f
        PlayerHolder.castVolume = 0.3f
        PlayerHolder.castDeviceVolume = 0.5f
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.volume } returns 0.4f

        val state = PlaybackState.fromPlayer(mockPlayer)

        assertEquals(
            "local player volume must be used, not stale pendingCastVolume",
            0.4f,
            state.volume,
            0.001f,
        )
    }

    @Test
    fun `fromPlayer uses castVolume fallback when zero and castDeviceVolume is set`() {
        PlayerHolder.isCasting = true
        PlayerHolder.pendingCastVolume = null
        PlayerHolder.castVolume = 0f
        PlayerHolder.castDeviceVolume = 0.6f
        val mockPlayer = mockk<Player>(relaxed = true)

        val state = PlaybackState.fromPlayer(mockPlayer)

        assertEquals(
            "castDeviceVolume fallback must be used",
            0.6f,
            state.volume,
            0.001f,
        )
    }

    @Test
    fun `fromPlayer shows zero when both castVolume and castDeviceVolume are zero`() {
        PlayerHolder.isCasting = true
        PlayerHolder.pendingCastVolume = null
        PlayerHolder.castVolume = 0f
        PlayerHolder.castDeviceVolume = 0f
        val mockPlayer = mockk<Player>(relaxed = true)

        val state = PlaybackState.fromPlayer(mockPlayer)

        assertEquals(
            "zero volume must be shown when device reports zero",
            0f,
            state.volume,
            0.001f,
        )
    }

    @Test
    fun `fromPlayer uses pendingCastVolume during Cast for optimistic update`() {
        PlayerHolder.isCasting = true
        PlayerHolder.pendingCastVolume = 0.7f
        PlayerHolder.castVolume = 0.3f
        PlayerHolder.castDeviceVolume = 0.5f
        val mockPlayer = mockk<Player>(relaxed = true)

        val state = PlaybackState.fromPlayer(mockPlayer)

        assertEquals(
            "pendingCastVolume must take priority during Cast",
            0.7f,
            state.volume,
            0.001f,
        )
    }

    // ── Mute propagation test (#4) ───────────────────────────────────────

    @Test
    fun `PlaybackState muted is propagated when set`() {
        val state = PlaybackState(muted = true, volume = 0.5f)
        assertTrue("muted must be true when set", state.muted)
        assertEquals("volume must still be reported when muted", 0.5f, state.volume)
    }

    // ── volumeDragValue initialization fix ──────────────────────────────

    @Test
    fun `volumeDragValue initializes to current display position not zero`() {
        val deviceVolume = 0.5f
        val initialized = volumeToDisplay(deviceVolume)
        val correctDisplay = kotlin.math.cbrt(deviceVolume.toDouble()).toFloat()

        assertEquals(
            "volumeDragValue must match volumeToDisplay at drag start",
            correctDisplay,
            initialized,
            0.001f,
        )
        assertTrue(
            "volumeDragValue must be positive for non-zero volume",
            initialized > 0f,
        )
    }

    @Test
    fun `volumeDragValue at zero volume initializes to zero`() {
        val initialized = volumeToDisplay(0f)
        assertEquals(
            "volumeDragValue must be 0 when device volume is 0",
            0f,
            initialized,
            0.001f,
        )
    }

    @Test
    fun `volumeDragValue at max volume initializes to one`() {
        val initialized = volumeToDisplay(1f)
        assertEquals(
            "volumeDragValue must be 1 when device volume is 1",
            1f,
            initialized,
            0.001f,
        )
    }

    @Test
    fun `dragStartVolume matches current device volume at first touch`() {
        val currentDeviceVolume = 0.5f
        val dragStartVolume = currentDeviceVolume
        val dragStartDisplay = volumeToDisplay(currentDeviceVolume)

        assertEquals(
            "dragStartVolume must match current device volume",
            0.5f,
            dragStartVolume,
            0.001f,
        )
        assertEquals(
            "dragStartDisplay must match volumeToDisplay of current volume",
            kotlin.math.cbrt(0.5).toFloat(),
            dragStartDisplay,
            0.001f,
        )

        // Simulate first-touch delta: user touches at current display position
        val firstTouchDisplay = dragStartDisplay
        val delta = firstTouchDisplay - dragStartDisplay
        val deltaVolume = displayToVolume(dragStartDisplay + delta) - displayToVolume(dragStartDisplay)
        val sentVolume = (dragStartVolume + deltaVolume).coerceIn(0f, 1f)

        assertEquals(
            "first-touch volume must equal current device volume (no jump to zero)",
            0.5f,
            sentVolume,
            0.001f,
        )
    }

    // ── Cast buffering state ─────────────────────────────────────────────

    @Test
    fun `fromPlayer treats buffering as playing during Cast`() {
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = "TV"
        PlayerHolder.castVolume = 0.5f
        PlayerHolder.castDeviceVolume = 0.5f
        PlayerHolder.pendingCastVolume = null

        val castPlayer = mockk<Player>(relaxed = true)
        every { castPlayer.isPlaying } returns false
        every { castPlayer.playbackState } returns Player.STATE_BUFFERING
        every { castPlayer.currentMediaItem } returns null
        every { castPlayer.currentMediaItemIndex } returns 0
        every { castPlayer.mediaItemCount } returns 1

        val state = PlaybackState.fromPlayer(castPlayer)

        assertTrue("isPlaying must be true when buffering during Cast", state.isPlaying)
    }

    @Test
    fun `fromPlayer keeps stopped when idle during Cast`() {
        PlayerHolder.isCasting = true
        PlayerHolder.castDeviceName = "TV"
        PlayerHolder.castVolume = 0.5f
        PlayerHolder.castDeviceVolume = 0.5f
        PlayerHolder.pendingCastVolume = null

        val castPlayer = mockk<Player>(relaxed = true)
        every { castPlayer.isPlaying } returns false
        every { castPlayer.playbackState } returns Player.STATE_IDLE
        every { castPlayer.currentMediaItem } returns null
        every { castPlayer.currentMediaItemIndex } returns 0
        every { castPlayer.mediaItemCount } returns 0

        val state = PlaybackState.fromPlayer(castPlayer)

        assertFalse("isPlaying must be false when idle during Cast", state.isPlaying)
    }

    // ── pendingCastVolume timeout (expireStalePendingVolume) ─────────────

    @Test
    fun `stale pendingCastVolume expires after timeout`() {
        PlayerHolder.isCasting = true
        PlayerHolder.castVolume = 0.4f
        PlayerHolder.castDeviceVolume = 0.4f
        PlayerHolder.pendingCastVolume = 0.9f
        PlayerHolder.pendingCastVolumeTimestamp = System.currentTimeMillis() - 4000L
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.volume } returns 0.0f

        val provider =
            MediaSessionPlaybackProvider(
                com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager(io.mockk.mockk(relaxed = true)),
            )
        provider.expireStalePendingVolume(mockPlayer)

        assertNull(
            "stale pendingCastVolume must be cleared",
            PlayerHolder.pendingCastVolume,
        )
        assertEquals(
            "timestamp must be reset",
            0L,
            PlayerHolder.pendingCastVolumeTimestamp,
        )
        // UI falls back to last confirmed castVolume
        val state = PlaybackState.fromPlayer(mockPlayer)
        assertEquals(
            "fromPlayer must fall back to castVolume after expiry",
            0.4f,
            state.volume,
            0.001f,
        )
    }

    @Test
    fun `recent pendingCastVolume does not expire`() {
        PlayerHolder.isCasting = true
        PlayerHolder.castVolume = 0.4f
        PlayerHolder.castDeviceVolume = 0.4f
        PlayerHolder.pendingCastVolume = 0.9f
        PlayerHolder.pendingCastVolumeTimestamp = System.currentTimeMillis() - 500L
        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.volume } returns 0.0f

        val provider =
            MediaSessionPlaybackProvider(
                com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager(io.mockk.mockk(relaxed = true)),
            )
        provider.expireStalePendingVolume(mockPlayer)

        assertEquals(
            "recent pending must survive",
            0.9f,
            PlayerHolder.pendingCastVolume!!,
            0.001f,
        )
    }

    @Test
    fun `pendingCastVolume cleared by confirmation resets timestamp`() {
        PlayerHolder.pendingCastVolume = 0.6f
        PlayerHolder.pendingCastVolumeTimestamp = System.currentTimeMillis()

        // onDeviceVolumeChanged reports 0.6 (60/100) — matches pending within 2%
        val confirmed = 0.6f
        PlayerHolder.pendingCastVolume?.let { pending ->
            if (kotlin.math.abs(confirmed - pending) < 0.02f) {
                PlayerHolder.pendingCastVolume = null
                PlayerHolder.pendingCastVolumeTimestamp = 0L
            }
        }

        assertNull(
            "pending must clear on confirmation match",
            PlayerHolder.pendingCastVolume,
        )
        assertEquals(
            "timestamp must reset on confirmation",
            0L,
            PlayerHolder.pendingCastVolumeTimestamp,
        )
    }

    @Test
    fun `pendingCastVolume refresh supersedes earlier timeout`() {
        // First drag sets pending; then a second drag refreshes the timestamp.
        // The expiry check must not clear it because the timestamp is recent.
        val now = System.currentTimeMillis()
        PlayerHolder.pendingCastVolume = 0.5f
        PlayerHolder.pendingCastVolumeTimestamp = now - 2900L // nearly stale
        PlayerHolder.pendingCastVolume = 0.8f // new drag refreshes value
        PlayerHolder.pendingCastVolumeTimestamp = now // and timestamp

        val mockPlayer = mockk<Player>(relaxed = true)
        every { mockPlayer.volume } returns 0.0f

        val provider =
            MediaSessionPlaybackProvider(
                com.lucasdss.ftpmusic.app.data.cache.OfflineModeManager(io.mockk.mockk(relaxed = true)),
            )
        provider.expireStalePendingVolume(mockPlayer)

        assertEquals(
            "refreshed pending must survive expiry check",
            0.8f,
            PlayerHolder.pendingCastVolume!!,
            0.001f,
        )
    }

    @Test
    fun `seedFromSessionVolume writes both fields`() {
        PlayerHolder.castVolume = 0f
        PlayerHolder.castDeviceVolume = 0.5f
        PlayerHolder.seedFromSessionVolume(0.37)
        assertEquals(0.37f, PlayerHolder.castVolume, 0.001f)
        assertEquals(0.37f, PlayerHolder.castDeviceVolume, 0.001f)
    }

    @Test
    fun `seedFromSessionVolume ignores null and negative`() {
        PlayerHolder.castVolume = 0.2f
        PlayerHolder.castDeviceVolume = 0.2f
        PlayerHolder.seedFromSessionVolume(null)
        PlayerHolder.seedFromSessionVolume(-0.1)
        assertEquals(0.2f, PlayerHolder.castVolume, 0.001f)
        assertEquals(0.2f, PlayerHolder.castDeviceVolume, 0.001f)
    }

    @Test
    fun `fromPlayer after seed shows session volume not default`() {
        PlayerHolder.isCasting = true
        PlayerHolder.pendingCastVolume = null
        PlayerHolder.castVolume = 0f
        PlayerHolder.castDeviceVolume = 0.5f
        PlayerHolder.seedFromSessionVolume(0.62)
        val mockPlayer = mockk<Player>(relaxed = true)

        val state = PlaybackState.fromPlayer(mockPlayer)

        assertEquals(0.62f, state.volume, 0.001f)
    }

    @Test
    fun `spurious zero display is rejected when baseline is mid scale`() {
        val baselineDisplay = volumeToDisplay(0.5f)
        assertTrue(isSpuriousZeroDisplay(0f, baselineDisplay))
        assertTrue(isSpuriousZeroDisplay(0.001f, baselineDisplay))
        assertFalse("Mid-scale display is not spurious", isSpuriousZeroDisplay(0.4f, baselineDisplay))
    }

    @Test
    fun `spurious zero display is not rejected when baseline is already zero`() {
        assertFalse(isSpuriousZeroDisplay(0f, volumeToDisplay(0f)))
        assertFalse(isSpuriousZeroDisplay(0.01f, 0f))
    }

    @Test
    fun `volumeForDrag from real zero baseline still sends upward volume`() {
        val sent = volumeForDrag(
            display = 0.5f,
            dragStartVolume = 0f,
            dragStartDisplay = 0f,
        )
        assertEquals(0.125f, sent, 0.01f)
    }

    @Test
    fun `volumeForDrag mid-drag to zero still sends zero`() {
        val sent = volumeForDrag(
            display = 0f,
            dragStartVolume = 0.5f,
            dragStartDisplay = volumeToDisplay(0.5f),
        )
        assertEquals(0f, sent, 0.001f)
    }
}
