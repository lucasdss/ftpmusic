package com.lucasdss.ftpmusic.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncNavChromeTest {

    @Test
    fun `bottom bar hidden on connect and syncing`() {
        assertFalse(SyncNavChrome.showBottomBar("connect"))
        assertFalse(SyncNavChrome.showBottomBar("syncing?returnTo=home"))
        assertFalse(SyncNavChrome.showBottomBar("syncing"))
        assertTrue(SyncNavChrome.showBottomBar("home"))
        assertTrue(SyncNavChrome.showBottomBar("library"))
    }

    @Test
    fun `back blocked only for first-login syncing`() {
        assertTrue(SyncNavChrome.blockBack("syncing?returnTo=home", firstLoginSync = true))
        assertFalse(SyncNavChrome.blockBack("syncing?returnTo=home", firstLoginSync = false))
        assertFalse(SyncNavChrome.blockBack("home", firstLoginSync = true))
    }
}
