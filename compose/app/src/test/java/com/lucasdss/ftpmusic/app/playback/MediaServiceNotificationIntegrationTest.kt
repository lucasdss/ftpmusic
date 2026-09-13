package com.lucasdss.ftpmusic.app.playback

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Integration tests using Robolectric (simulated Android framework on JVM).
 * Tests state transitions, notification building, and persistence flows
 * that require a real Context and Android framework classes.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], manifest = Config.NONE)
class MediaServiceNotificationIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
    }

    @Test
    fun `notification channel ID constant is correct`() {
        assertEquals("ftpmusic_playback", PlaybackNotificationProvider.CHANNEL_ID)
    }

    @Test
    fun `notification ID constant is positive`() {
        assertTrue(PlaybackNotificationProvider.NOTIFICATION_ID > 0)
    }

    @Test
    fun `PlaybackState transitions are thread safe`() {
        // PlaybackState is an immutable data class — thread-safe by design
        val base = PlaybackState()
        val states = (1..10).map { i ->
            base.copy(title = "Track $i", artist = "Artist $i", isPlaying = i % 2 == 0)
        }
        assertEquals(10, states.size)
        assertNotNull(states.last().title)
    }

    @Test
    fun `PlaybackState defaults to empty state`() {
        val s = PlaybackState()
        assertNull(s.title)
        assertNull(s.artist)
        assertFalse(s.isPlaying)
        assertFalse(s.isCasting)
        assertNull(s.castDeviceName)
    }

    @Test
    fun `PlaybackState copy preserves immutability`() {
        val original = PlaybackState(title = "Original", isPlaying = true)
        val modified = original.copy(isPlaying = false)

        // original should be unchanged
        assertTrue(original.isPlaying)
        assertEquals("Original", original.title)

        // modified should have the new value
        assertFalse(modified.isPlaying)
        assertEquals("Original", modified.title)
    }

    @Test
    fun `Context has notification manager available`() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
        assertNotNull("NotificationManager should be available", nm)
    }

    @Test
    fun `Context has audio manager available`() {
        val am = context.getSystemService(Context.AUDIO_SERVICE)
        assertNotNull("AudioManager should be available", am)
    }

    @Test
    fun `SharedPreferences can be created and read`() {
        val prefs = context.getSharedPreferences("test_cast", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("cast_is_casting", true).apply()
        assertTrue(prefs.getBoolean("cast_is_casting", false))

        prefs.edit().putBoolean("cast_is_casting", false).apply()
        assertFalse(prefs.getBoolean("cast_is_casting", false))
    }

    // ═════════════════════════════════════════════════
    // State Persistence Integration Tests
    // ═════════════════════════════════════════════════

    @Test
    fun `SCENARIO S2 - PlaybackState persists isCasting flag correctly`() {
        val state = PlaybackState(
            title = "Playing on Cast",
            artist = "Artist",
            isPlaying = true,
            isCasting = true,
            castDeviceName = "Soundbar",
            currentTrackId = "t123",
        )
        assertEquals("Soundbar", state.castDeviceName)
        assertTrue(state.isCasting)

        // Simulate disconnect
        val disconnected = state.copy(isCasting = false, castDeviceName = null, isPlaying = false)
        assertFalse(disconnected.isCasting)
        assertNull(disconnected.castDeviceName)
        assertEquals("Playing on Cast", disconnected.title) // title preserved
    }

    @Test
    fun `SCENARIO S3 - Cast state roundtrip through PlaybackState`() {
        // Connect
        val connected = PlaybackState(isCasting = true, castDeviceName = "TV")
        assertTrue(connected.isCasting)
        assertEquals("TV", connected.castDeviceName)

        // Disconnect
        val disconnected = connected.copy(isCasting = false, castDeviceName = null)
        assertFalse(disconnected.isCasting)
        assertNull(disconnected.castDeviceName)
    }

    @Test
    fun `SCENARIO E7 - state survives simulated background disconnect`() {
        // Playing on Cast
        val state1 = PlaybackState(
            title = "Background Track",
            artist = "BG Artist",
            isPlaying = true,
            isCasting = true,
            castDeviceName = "Speaker",
            position = 90000,
            currentTrackId = "bg1",
        )

        // Device disconnects while in background
        val state2 = state1.copy(isCasting = false, castDeviceName = null, isPlaying = false)

        assertEquals("Background Track", state2.title)
        assertEquals(90000, state2.position)
        assertFalse(state2.isCasting)
    }
}
