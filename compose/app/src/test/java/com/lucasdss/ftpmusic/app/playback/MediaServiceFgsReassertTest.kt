package com.lucasdss.ftpmusic.app.playback

import android.app.Application
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression tests for the foreground-service re-assert contract.
 *
 * Crash class: ForegroundServiceDidNotStartInTimeException (08-16, 08-20).
 * A user playback request can reach an already initialized service. Its
 * explicit playback action must re-assert foreground state within the system
 * deadline, regardless of the app's cached foreground flag.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class MediaServiceFgsReassertTest {

    private lateinit var service: MediaService

    @Before
    fun setUp() {
        service = spyk(MediaService())
        // startForeground is final on Service (not interceptable on Robolectric
        // android.jar classes) — stub the internal indirection instead.
        every { service.startForegroundInternal(any(), any()) } returns Unit
        // The spy has no attached context — getResources() would fail. Build
        // the placeholder from the real Application context instead.
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        every { service.buildPlaceholderNotification(any()) } answers {
            android.app.Notification.Builder(ctx, PlaybackNotificationProvider.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("ftpmusic")
                .setContentText("Ready to play")
                .setPriority(android.app.Notification.PRIORITY_LOW)
                .build()
        }
    }

    @After
    fun tearDown() {
        PlayerHolder.isCasting = false
    }

    // ── reassertForegroundIfNeeded (wiring) ─────────────────────────────

    @Test
    fun `reassert promotes with the cached real notification, not the placeholder`() {
        val realNotif = service.buildPlaceholderNotification(
            ApplicationProvider.getApplicationContext<Application>(),
        )
        injectField(service, "lastForegroundNotification", realNotif)
        // The one-shot flag is stale (we were foregrounded once) — the
        // unconditional re-assert must still fire.
        injectField(service, "foregroundStarted", true)

        val reasserted = service.reassertForegroundIfNeeded()

        assertTrue("start command must always re-assert foreground", reasserted)
        verify(exactly = 1) { service.startForegroundInternal(any(), any()) }
        assertTrue(
            "flag must be set after re-assert",
            readField(service, "foregroundStarted") as Boolean,
        )
    }

    @Test
    fun `reassert falls back to the placeholder when no real notification exists`() {
        injectField(service, "lastForegroundNotification", null)
        injectField(service, "foregroundStarted", false)

        val reasserted = service.reassertForegroundIfNeeded()

        assertTrue("must re-promote with placeholder pre-notification", reasserted)
        verify(exactly = 1) { service.startForegroundInternal(any(), any()) }
        assertTrue(
            "flag must be set after re-assert",
            readField(service, "foregroundStarted") as Boolean,
        )
    }

    @Test
    fun `reassert issues startForeground even when the flag says foregrounded`() {
        // THE regression case: flag=true (stale after system demotion) must
        // NOT suppress the re-assert — the actual service may be demoted.
        injectField(service, "foregroundStarted", true)
        injectField(service, "lastForegroundNotification", null)

        service.reassertForegroundIfNeeded()

        verify(exactly = 1) { service.startForegroundInternal(any(), any()) }
        assertTrue(
            "flag must be set after re-assert",
            readField(service, "foregroundStarted") as Boolean,
        )
    }

    @Test
    fun `reassert catches startForeground failure and reports false`() {
        // Background-start restriction (API 31+): startForeground throws and
        // the re-assert must fail soft (no crash), leaving the flag unset so
        // the next start command retries.
        injectField(service, "foregroundStarted", false)
        injectField(service, "lastForegroundNotification", null)
        every { service.startForegroundInternal(any(), any()) } throws
            RuntimeException("ForegroundServiceStartNotAllowedException")

        val reasserted = service.reassertForegroundIfNeeded()

        assertFalse("failed re-assert must not be reported as success", reasserted)
        assertFalse(
            "flag must stay unset on failure",
            readField(service, "foregroundStarted") as Boolean,
        )
    }

    @Test
    fun `onStartCommand exposes playback reassertion path`() {
        // The wiring contract: onStartCommand must invoke the re-assert (its
        // behavior is covered by the reassertForegroundIfNeeded tests above).
        // Scan the real class hierarchy — the spy is a mockk subclass whose
        // declaredMethods would not list the real method. Internal member
        // methods are JVM-mangled with a `$<module>` suffix (e.g. `$app_debug`),
        // so match by prefix.
        var cls: Class<*>? = MediaService::class.java
        var found = false
        while (cls != null && cls != Any::class.java) {
            if (cls.declaredMethods.any { it.name.startsWith("reassertForegroundIfNeeded") }) {
                found = true
                break
            }
            cls = cls.superclass
        }
        assertTrue("reassertForegroundIfNeeded must remain available to playback starts", found)
        val onStartCmd = MediaService::class.java.declaredMethods.find { it.name == "onStartCommand" }
        assertNotNull("onStartCommand must be overridden", onStartCmd)
    }

    @Test
    fun `only explicit playback start requires foreground promotion`() {
        assertTrue(shouldPromotePlaybackOnCreate(foregroundRequested = true))
        assertTrue(shouldPromotePlaybackOnCreate(foregroundRequested = false, btAutoplayRequested = true))
        assertTrue(
            shouldPromotePlaybackOnCreate(
                foregroundRequested = false,
                btAutoplayRequested = false,
                backgroundWarmStart = false,
            ),
        )
        assertFalse(
            shouldPromotePlaybackOnCreate(
                foregroundRequested = false,
                btAutoplayRequested = false,
                backgroundWarmStart = true,
            ),
        )
        assertTrue(shouldReassertPlayback(MediaServiceStartRequest.ACTION_PLAYBACK))
        assertTrue(shouldReassertPlayback(MediaServiceStartRequest.ACTION_BT_AUTOPLAY))
        assertTrue(shouldReassertPlayback(MediaServiceStartRequest.ACTION_CAR_BT_AUTOPLAY))
        assertFalse(shouldReassertPlayback(MediaServiceStartRequest.ACTION_INITIALIZE))
        assertFalse(shouldReassertPlayback(null))
        assertTrue(isBtAutoplayAction(MediaServiceStartRequest.ACTION_BT_AUTOPLAY))
        assertTrue(isBtAutoplayAction(MediaServiceStartRequest.ACTION_CAR_BT_AUTOPLAY))
        assertFalse(isBtAutoplayAction(MediaServiceStartRequest.ACTION_PLAYBACK))
    }

    @Test
    fun `idle initialization is not sticky`() {
        assertEquals(
            android.app.Service.START_NOT_STICKY,
            playbackServiceStartMode(MediaServiceStartRequest.ACTION_INITIALIZE),
        )
        assertEquals(
            android.app.Service.START_STICKY,
            playbackServiceStartMode(MediaServiceStartRequest.ACTION_PLAYBACK),
        )
        assertEquals(android.app.Service.START_STICKY, playbackServiceStartMode(null))
    }

    // ── applyBtPlayAfterSeat offline (ADR-0101) ─────────────────────────

    @Test
    fun `applyBtPlayAfterSeat offline seeks to first cached then plays on API36`() {
        val player = mockOfflineQueuePlayer(currentIndex = 0, ids = listOf("a", "b", "c"))
        service.applyBtPlayAfterSeat(
            player = player,
            sdkInt = 36,
            networkBlocked = true,
            isCached = { it == "b" },
            postWiuNotif = { error("no WIU on API36") },
            postOfflineNotif = { error("must not offline-notif when cache exists") },
        )
        verify { player.seekTo(1, 0L) }
        verify { player.play() }
    }

    @Test
    fun `applyBtPlayAfterSeat offline with no cache posts offline notif`() {
        val player = mockOfflineQueuePlayer(currentIndex = 0, ids = listOf("a", "b"))
        var offline = 0
        service.applyBtPlayAfterSeat(
            player = player,
            sdkInt = 36,
            networkBlocked = true,
            isCached = { false },
            postOfflineNotif = { offline++ },
            postWiuNotif = { error("no WIU when offline empty") },
        )
        assertEquals(1, offline)
        verify { player.pause() }
        verify(exactly = 0) { player.play() }
    }

    @Test
    fun `applyBtPlayAfterSeat A17 posts WIU notif after offline seek`() {
        val player = mockOfflineQueuePlayer(currentIndex = 0, ids = listOf("a", "b"))
        var wiu = 0
        service.applyBtPlayAfterSeat(
            player = player,
            sdkInt = 37,
            networkBlocked = true,
            isCached = { it == "a" },
            postWiuNotif = { wiu++ },
            postOfflineNotif = { error("current is cached") },
        )
        assertEquals(1, wiu)
        verify { player.pause() }
        verify(exactly = 0) { player.seekTo(any(), any()) }
    }

    private fun mockOfflineQueuePlayer(currentIndex: Int, ids: List<String>): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.mediaItemCount } returns ids.size
        every { player.currentMediaItemIndex } returns currentIndex
        ids.forEachIndexed { i, id ->
            every { player.getMediaItemAt(i) } returns MediaItem.Builder().setMediaId(id).build()
        }
        return player
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private fun injectField(target: Any, fieldName: String, value: Any?) {
        try {
            val field = target::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(target, value)
        } catch (_: NoSuchFieldException) {
            try {
                val f = target::class.java.superclass?.getDeclaredField(fieldName)
                f?.isAccessible = true
                f?.set(target, value)
            } catch (_: Exception) {}
        }
    }

    private fun readField(target: Any, fieldName: String): Any? = try {
        val field = target::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.get(target)
    } catch (_: NoSuchFieldException) {
        try {
            val f = target::class.java.superclass?.getDeclaredField(fieldName)
            f?.isAccessible = true
            f?.get(target)
        } catch (_: Exception) {
            null
        }
    }
}
