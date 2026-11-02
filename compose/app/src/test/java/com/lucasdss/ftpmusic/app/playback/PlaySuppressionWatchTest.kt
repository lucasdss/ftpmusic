package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaySuppressionWatchTest {

    private val recorded = mutableListOf<String>()
    private var now = 1_000L
    private var scheduled: Runnable? = null

    private fun newWatch() = PlaySuppressionWatch(
        graceMs = 100L,
        debounceMs = 500L,
        nowMs = { now },
        schedule = { _, _, runnable -> scheduled = runnable },
        cancelScheduled = { scheduled = null },
        record = { reason, extras -> recorded += "$reason|$extras" },
    )

    @Before
    fun setUp() {
        recorded.clear()
        scheduled = null
        now = 1_000L
        BtResumeEvents.clear()
    }

    @Test
    fun `READY muted after grace records play_suppressed`() {
        val watch = newWatch()
        watch.arm("autoplay", 37) { }
        assertTrue(watch.isArmed())
        watch.evaluate(
            playWhenReady = true,
            isPlaying = false,
            playbackState = Player.STATE_READY,
            casting = false,
        )
        assertTrue(recorded.any { it.startsWith("play_suppressed|") && it.contains("src=autoplay") })
        assertFalse(watch.isArmed())
    }

    @Test
    fun `isPlaying before evaluate cancels without record`() {
        val watch = newWatch()
        watch.arm("autoplay", 36) { }
        watch.onPlayerSnapshot(
            playWhenReady = true,
            isPlaying = true,
            playbackState = Player.STATE_READY,
            casting = false,
        )
        assertFalse(watch.isArmed())
        watch.evaluate(
            playWhenReady = true,
            isPlaying = true,
            playbackState = Player.STATE_READY,
            casting = false,
        )
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun `playWhenReady false cancels without record`() {
        val watch = newWatch()
        watch.arm("user_play", 37) { }
        watch.onPlayerSnapshot(
            playWhenReady = false,
            isPlaying = false,
            playbackState = Player.STATE_READY,
            casting = false,
        )
        assertFalse(watch.isArmed())
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun `BUFFERING evaluate does not record`() {
        val watch = newWatch()
        watch.arm("autoplay", 37) { }
        watch.evaluate(
            playWhenReady = true,
            isPlaying = false,
            playbackState = Player.STATE_BUFFERING,
            casting = false,
        )
        assertTrue(recorded.isEmpty())
        assertFalse(watch.isArmed())
    }

    @Test
    fun `debounce suppresses second record`() {
        val watch = newWatch()
        watch.arm("autoplay", 37) { }
        watch.evaluate(true, false, Player.STATE_READY, false)
        assertEqualsOneSuppress()
        now += 100L
        watch.arm("tap_play", 37) { }
        watch.evaluate(true, false, Player.STATE_READY, false)
        assertEqualsOneSuppress()
        now += 600L
        watch.arm("tap_play", 37) { }
        watch.evaluate(true, false, Player.STATE_READY, false)
        assertTrue(recorded.size == 2)
    }

    private fun assertEqualsOneSuppress() {
        assertTrue(recorded.size == 1)
        assertTrue(recorded[0].startsWith("play_suppressed|"))
    }
}
