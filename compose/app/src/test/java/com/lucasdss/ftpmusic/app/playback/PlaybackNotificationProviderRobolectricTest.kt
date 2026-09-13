package com.lucasdss.ftpmusic.app.playback

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import com.google.common.collect.ImmutableList
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric tests for PlaybackNotificationProvider.buildNotification:
 * action PendingIntents must never be null (E9), rebuild memoization (P6) and
 * the base-URL-keyed cover URL cache (edge 46). Uses a REAL MediaSession
 * (final class — mockk inline cannot proxy it under Robolectric).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlaybackNotificationProviderRobolectricTest {

    private lateinit var context: Context
    private lateinit var authHelper: SubsonicAuthHelper
    private var state = PlaybackState(title = "Track", artist = "Artist", isPlaying = true, coverArtId = "cov-1")
    private var notificationsEnabled = true
    private lateinit var provider: PlaybackNotificationProvider
    private lateinit var session: MediaSession

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        authHelper = SubsonicAuthHelper()
        state = PlaybackState(title = "Track", artist = "Artist", isPlaying = true, coverArtId = "cov-1")
        notificationsEnabled = true
        provider = PlaybackNotificationProvider(
            context,
            authHelper,
            { state },
            { notificationsEnabled },
        )
        session = MediaSession.Builder(context, ExoPlayer.Builder(context).build()).build()
        mockkObject(DynamicBaseUrl)
        mockkObject(SubsonicCredentials)
        every { DynamicBaseUrl.url } returns "https://music.example.com"
        every { SubsonicCredentials.username } returns "testuser"
        every { SubsonicCredentials.password } returns "testpass"
    }

    @After
    fun tearDown() {
        PlayerHolder.player = null
        PlayerHolder.isCasting = false
        provider.dispose()
        session.release()
        unmockkObject(DynamicBaseUrl)
        unmockkObject(SubsonicCredentials)
    }

    /** ActionFactory shaped like the fixed MediaService factory (E9):
     *  every action carries a MEDIA_ACTION broadcast PendingIntent. */
    private fun liveFactory(): MediaNotification.ActionFactory = object : MediaNotification.ActionFactory {
        override fun createMediaAction(s: MediaSession, icon: IconCompat, name: CharSequence, code: Int) =
            NotificationCompat.Action.Builder(icon, name, createMediaActionPendingIntent(s, code)).build()
        override fun createCustomAction(
            s: MediaSession,
            icon: IconCompat,
            name: CharSequence,
            action: String,
            extras: android.os.Bundle,
        ) = NotificationCompat.Action.Builder(icon, name, createMediaActionPendingIntent(s, 1)).build()
        override fun createCustomActionFromCustomCommandButton(
            s: MediaSession,
            button: androidx.media3.session.CommandButton,
        ) = NotificationCompat.Action.Builder(
            IconCompat.createWithResource(context, android.R.drawable.ic_media_play),
            "Play",
            createMediaActionPendingIntent(s, 1),
        ).build()
        override fun createMediaActionPendingIntent(s: MediaSession, commandCode: Int) = PendingIntent.getBroadcast(
            context,
            commandCode,
            Intent("com.lucasdss.ftpmusic.app.MEDIA_ACTION").putExtra("command", commandCode.toLong()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun build(): MediaNotification = provider.createNotification(session, ImmutableList.of(), liveFactory()) { }

    @Test
    fun `all media actions carry live pending intents`() {
        val notif = build().notification
        assertNotNull(notif.actions)
        assertEquals(3, notif.actions.size)
        notif.actions.forEach { action ->
            assertNotNull("action '${action.title}' must have a non-null actionIntent (E9)", action.actionIntent)
        }
    }

    @Test
    fun `memoization returns the same instance for unchanged state`() {
        val first = build()
        val second = provider.buildNotification(session, liveFactory())
        assertSame("unchanged state must reuse the memoized notification (P6)", first, second)
    }

    @Test
    fun `memoization rebuilds when isPlaying changes`() {
        val first = build()
        state = state.copy(isPlaying = false)
        val second = provider.buildNotification(session, liveFactory())
        assertNotEquals(first, second)
    }

    @Test
    fun `memoization rebuilds when the track changes`() {
        val first = build()
        state = state.copy(title = "Other Track")
        val second = provider.buildNotification(session, liveFactory())
        assertNotEquals(first, second)
    }

    @Test
    fun `notifyChanged skips identical posts`() {
        var callbackCount = 0
        provider.createNotification(session, ImmutableList.of(), liveFactory()) { callbackCount++ }

        provider.notifyChanged()
        provider.notifyChanged()
        assertEquals("duplicate state must not re-post (P6)", 1, callbackCount)

        state = state.copy(isPlaying = false)
        provider.notifyChanged()
        assertEquals("changed state must post", 2, callbackCount)
    }

    @Test
    fun `minimal notification path also memoizes`() {
        notificationsEnabled = false
        val first = build()
        val second = provider.buildNotification(session, liveFactory())
        assertSame(first, second)
    }

    @Test
    fun `minimal path drops media actions`() {
        notificationsEnabled = false
        val actions = build().notification.actions
        assertTrue(
            "minimal FGS-satisfying notification must have no media controls",
            actions == null || actions.isEmpty(),
        )
    }

    @Test
    fun `cover URL cache is keyed by base url`() {
        val urlA = provider.buildCoverArtUrl("cov-1")
        every { DynamicBaseUrl.url } returns "https://other.example.com"
        val urlB = provider.buildCoverArtUrl("cov-1")
        assertNotEquals("base change must not reuse the stale auth URL (edge 46)", urlA, urlB)
        assertTrue(urlB.startsWith("https://other.example.com/rest/getCoverArt"))

        // Same base + id → cached hit (deterministic)
        assertEquals(urlB, provider.buildCoverArtUrl("cov-1"))
    }

    @Test
    fun `different cover ids produce different urls under same base`() {
        val url1 = provider.buildCoverArtUrl("cov-1")
        val url2 = provider.buildCoverArtUrl("cov-2")
        assertNotEquals(url1, url2)
    }

    // ── Channel-first regression (CannotPostForegroundServiceNotificationException) ──

    @Test
    fun `playback channel exists before any startForeground`() {
        // Provider init (and MediaService.onCreate, which calls ensureChannel
        // BEFORE the placeholder startForeground) must have created it.
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(PlaybackNotificationProvider.CHANNEL_ID)
        assertNotNull("channel must exist before the FGS placeholder is posted", channel)
        assertEquals(PlaybackNotificationProvider.CHANNEL_ID, channel!!.id)
    }

    @Test
    fun `ensureChannel is idempotent`() {
        PlaybackNotificationProvider.ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val first = manager.getNotificationChannel(PlaybackNotificationProvider.CHANNEL_ID)

        PlaybackNotificationProvider.ensureChannel(context) // must not throw

        assertEquals(first, manager.getNotificationChannel(PlaybackNotificationProvider.CHANNEL_ID))
    }
}
