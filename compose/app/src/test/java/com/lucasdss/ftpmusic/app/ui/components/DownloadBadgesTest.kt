package com.lucasdss.ftpmusic.app.ui.components

import org.junit.Assert.*
import org.junit.Test

class DownloadBadgesTest {
    @Test
    fun `downloadStatus returns downloaded when isDownloaded is true`() {
        assertEquals("downloaded", downloadStatus(true, false, false))
    }

    @Test
    fun `downloadStatus returns downloaded even when also queued and cached`() {
        assertEquals("downloaded", downloadStatus(true, true, true))
    }

    @Test
    fun `downloadStatus returns queued when not downloaded but queued`() {
        assertEquals("queued", downloadStatus(false, true, false))
    }

    @Test
    fun `downloadStatus returns cached when only cached`() {
        assertEquals("cached", downloadStatus(false, false, true))
    }

    @Test
    fun `downloadStatus returns none when all false`() {
        assertEquals("none", downloadStatus(false, false, false))
    }

    @Test
    fun `downloadStatus prioritizes downloaded over queued`() {
        assertEquals("downloaded", downloadStatus(true, true, false))
    }

    @Test
    fun `downloadStatus prioritizes queued over cached`() {
        assertEquals("queued", downloadStatus(false, true, true))
    }
}
