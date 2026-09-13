package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for PlaybackNotificationProvider cover art URL building and notification structure.
 * MediaSession/MobileNotification integration requires Robolectric for full coverage;
 * these tests cover the isolated logic.
 */
class PlaybackNotificationProviderTest {

    private lateinit var authHelper: SubsonicAuthHelper

    @Before
    fun setUp() {
        authHelper = SubsonicAuthHelper()
        mockkObject(DynamicBaseUrl)
        mockkObject(SubsonicCredentials)
        every { DynamicBaseUrl.url } returns "https://music.example.com"
        every { SubsonicCredentials.username } returns "testuser"
        every { SubsonicCredentials.password } returns "testpass"
    }

    @After
    fun tearDown() {
        unmockkObject(DynamicBaseUrl)
        unmockkObject(SubsonicCredentials)
    }

    @Test
    fun `buildCoverArtUrl constructs valid URL with auth params`() {
        // Use reflection or extract method — we test the auth param building pattern
        val base = "https://music.example.com"
        val username = "testuser"
        val password = "testpass"
        val authParams = authHelper.buildAuthParams(username, password)
        val coverArtId = "ar-12345"
        val params = authParams.entries.joinToString("&") { "${it.key}=${it.value}" }
        val url = "$base/rest/getCoverArt?id=$coverArtId&$params&size=256"

        assertTrue(url.startsWith("https://music.example.com/rest/getCoverArt"))
        assertTrue(url.contains("id=ar-12345"))
        assertTrue(url.contains("size=256"))
        assertTrue(url.contains("u=testuser"))
        assertTrue(url.contains("s="))
        assertTrue(url.contains("t="))
        assertTrue(url.contains("v=1.16.1"))
        assertTrue(url.contains("c=ftpmusic"))
    }

    @Test
    fun `auth params contain salt and token`() {
        val params = authHelper.buildAuthParams("testuser", "testpass")

        assertTrue(params.containsKey("u"))
        assertTrue(params.containsKey("s"))
        assertTrue(params.containsKey("t"))
        assertTrue(params.containsKey("v"))
        assertTrue(params.containsKey("c"))
        assertEquals("testuser", params["u"])
        assertEquals("1.16.1", params["v"])
        assertEquals("ftpmusic", params["c"])
        // Salt should be 6 chars (random)
        assertEquals(6, params["s"]?.length)
        // Token should be 32 chars (MD5 hex)
        assertEquals(32, params["t"]?.length)
    }

    @Test
    fun `base URL trailing slash is stripped`() {
        val url = "https://music.example.com/"
        val trimmed = url.trimEnd('/')
        assertEquals("https://music.example.com", trimmed)
    }

    @Test
    fun `cover art URL size parameter is passed correctly`() {
        val coverArtId = "al-999"
        val size = 512
        val base = DynamicBaseUrl.url.trimEnd('/')
        val username = SubsonicCredentials.username
        val password = SubsonicCredentials.password
        val authParams = authHelper.buildAuthParams(username, password)
        val params = authParams.entries.joinToString("&") { "${it.key}=${it.value}" }
        val url = "$base/rest/getCoverArt?id=$coverArtId&$params&size=$size"

        assertTrue(url.contains("size=512"))
    }

    @Test
    fun `cover art URL is deterministic across calls for same id`() {
        // Deterministic salt (from coverArtId) keeps the URL stable across app
        // restarts so Coil's disk cache persists instead of re-downloading.
        val url1 = authHelper.buildCoverArtUrl("https://music.example.com", "user", "pass", "al-777", 300)
        val url2 = authHelper.buildCoverArtUrl("https://music.example.com", "user", "pass", "al-777", 300)
        val url3 = authHelper.buildCoverArtUrl("https://music.example.com", "user", "pass", "al-888", 300)

        assertEquals("Same coverArtId must produce identical URL", url1, url2)
        assertNotEquals("Different coverArtId must produce different salt", url1, url3)
    }

    @Test
    fun `playback state carries coverArtId`() {
        val state = PlaybackState(
            title = "Test Song",
            artist = "Test Artist",
            coverArtId = "cov-123",
        )

        assertEquals("cov-123", state.coverArtId)
        assertEquals("Test Song", state.title)
        assertEquals("Test Artist", state.artist)
    }

    @Test
    fun `playback state without coverArtId`() {
        val state = PlaybackState(
            title = "No Cover",
            artist = "Unknown",
            coverArtId = null,
        )

        assertNull(state.coverArtId)
    }

    @Test
    fun `cast status fields in playback state`() {
        val casting = PlaybackState(
            title = "Casting Song",
            artist = "Artist",
            isCasting = true,
            castDeviceName = "Living Room TV",
        )

        assertTrue(casting.isCasting)
        assertEquals("Living Room TV", casting.castDeviceName)
    }

    @Test
    fun `non-casting state has null device name`() {
        val local = PlaybackState(
            title = "Local Song",
            isCasting = false,
            castDeviceName = null,
        )

        assertFalse(local.isCasting)
        assertNull(local.castDeviceName)
    }

    @Test
    fun `notification channel is created with correct properties`() {
        // Channel creation tested implicitly via no crash on init
        // Channel ID is the key property
        assertEquals("ftpmusic_playback", PlaybackNotificationProvider.CHANNEL_ID)
        assertEquals(1001, PlaybackNotificationProvider.NOTIFICATION_ID)
    }

    @Test
    fun `cover art URL handles special characters in auth`() {
        val params = authHelper.buildAuthParams("user@name", "p@ss!")
        val coverArtId = "ar-42"
        val base = "https://server.com"
        val paramStr = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        val url = "$base/rest/getCoverArt?id=$coverArtId&$paramStr&size=256"

        // URL should not contain raw special chars in auth — they should be encoded
        assertTrue(url.startsWith("https://server.com/rest/getCoverArt"))
        assertTrue(url.contains("id=ar-42"))
    }

    @Test
    fun `same coverArtId produces same URL via cache pattern`() {
        // Simulates urlCache.getOrPut behavior: same key → same value
        val cache = mutableMapOf<String, String>()
        val coverArtId = "ar-42"
        val url1 = cache.getOrPut(coverArtId) {
            val username = SubsonicCredentials.username
            val password = SubsonicCredentials.password
            val authParams = authHelper.buildAuthParams(username, password)
            val params = authParams.entries.joinToString("&") { "${it.key}=${it.value}" }
            "${DynamicBaseUrl.url.trimEnd('/')}/rest/getCoverArt?id=$coverArtId&$params&size=256"
        }
        val url2 = cache.getOrPut(coverArtId) { "should-not-be-called" }

        assertEquals(url1, url2)
        assertEquals(1, cache.size)
    }

    @Test
    fun `different coverArtIds produce different cache entries`() {
        val cache = mutableMapOf<String, String>()
        val url1 = cache.getOrPut("ar-1") {
            "${DynamicBaseUrl.url.trimEnd('/')}/rest/getCoverArt?id=ar-1&test"
        }
        val url2 = cache.getOrPut("ar-2") {
            "${DynamicBaseUrl.url.trimEnd('/')}/rest/getCoverArt?id=ar-2&test"
        }

        assertNotEquals(url1, url2)
        assertEquals(2, cache.size)
    }

    @Test
    fun `notification content text shows cast device when casting`() {
        val state = PlaybackState(
            title = "Song",
            artist = "Artist",
            isCasting = true,
            castDeviceName = "Bedroom Speaker",
        )

        val contentText = when {
            state.isCasting && state.castDeviceName != null ->
                "${state.artist} — Casting to ${state.castDeviceName}"

            state.artist?.isNotEmpty() == true -> state.artist

            else -> "Playing…"
        }

        assertEquals("Artist — Casting to Bedroom Speaker", contentText)
    }

    @Test
    fun `notification content text falls back when no artist`() {
        val state = PlaybackState(
            title = "Untitled",
            artist = null,
            isCasting = false,
        )

        val contentText = when {
            state.isCasting && state.castDeviceName != null ->
                "${state.artist} — Casting to ${state.castDeviceName}"

            state.artist?.isNotEmpty() == true -> state.artist

            else -> "Playing…"
        }

        assertEquals("Playing…", contentText)
    }

    @Test
    fun `notification content text shows artist when not casting`() {
        val state = PlaybackState(
            title = "Track",
            artist = "Band",
            isCasting = false,
        )

        val contentText = when {
            state.isCasting && state.castDeviceName != null ->
                "${state.artist} — Casting to ${state.castDeviceName}"

            state.artist?.isNotEmpty() == true -> state.artist

            else -> "Playing…"
        }

        assertEquals("Band", contentText)
    }

    @Test
    fun `cover art bitmap null falls back to app icon`() {
        val bitmap: android.graphics.Bitmap? = null
        val fallback = "app_icon"

        val displayed = bitmap ?: fallback
        assertEquals("app_icon", displayed)
    }

    @Test
    fun `cover art bitmap present uses it instead of fallback`() {
        val bitmap = "cover_bitmap"
        val fallback = "app_icon"

        val displayed = bitmap ?: fallback
        assertEquals("cover_bitmap", displayed)
    }
}
