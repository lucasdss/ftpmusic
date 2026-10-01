package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoplayMediaExtrasTest {

    private fun item(): MediaItem = MediaItem.Builder()
        .setMediaId("t1")
        .setMediaMetadata(MediaMetadata.Builder().setTitle("T").build())
        .build()

    @Test
    fun `withAutoplay stamps and isAutoplay reads flag`() {
        val stamped = item().withAutoplay(true)
        assertTrue(stamped.isAutoplay())
        assertFalse(item().isAutoplay())
    }

    @Test
    fun `withAutoplay false clears flag`() {
        val stamped = item().withAutoplay(true).withAutoplay(false)
        assertFalse(stamped.isAutoplay())
    }

    @Test
    fun `withAutoplay false on unmarked is no-op`() {
        val base = item()
        assertFalse(base.isAutoplay())
        assertFalse(base.withAutoplay(false).isAutoplay())
    }

    @Test
    fun `withAutoplay same flag returns same semantics`() {
        val once = item().withAutoplay(true)
        assertTrue(once.withAutoplay(true).isAutoplay())
    }
}
