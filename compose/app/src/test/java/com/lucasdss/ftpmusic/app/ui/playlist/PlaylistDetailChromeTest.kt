package com.lucasdss.ftpmusic.app.ui.playlist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistDetailChromeTest {
    @Test
    fun `B4-2 empty playlist still shows overflow`() {
        assertTrue(PlaylistDetailChrome.showOverflowMenu(0))
        assertTrue(PlaylistDetailChrome.showOverflowMenu(12))
    }

    @Test
    fun `play shuffle only when tracks present`() {
        assertFalse(PlaylistDetailChrome.showPlayShuffle(0))
        assertTrue(PlaylistDetailChrome.showPlayShuffle(1))
    }
}
