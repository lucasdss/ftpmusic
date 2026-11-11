package com.lucasdss.ftpmusic.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SplashRouterTest {

    @Test
    fun `no credentials routes to connect`() {
        assertEquals("connect", SplashRouter.resolveRoute(hasCredentials = false, albumCount = 0))
        assertEquals("connect", SplashRouter.resolveRoute(hasCredentials = false, albumCount = 100))
    }

    @Test
    fun `credentials but no metadata routes to syncing with returnTo home`() {
        assertEquals(syncingRoute("home"), SplashRouter.resolveRoute(hasCredentials = true, albumCount = 0))
    }

    @Test
    fun `credentials and metadata routes to home`() {
        assertEquals("home", SplashRouter.resolveRoute(hasCredentials = true, albumCount = 1))
        assertEquals("home", SplashRouter.resolveRoute(hasCredentials = true, albumCount = 42))
    }

    @Test
    fun `drain incomplete with albums routes home — background heal`() {
        // Soft-stop hangover must not trap returning users on SyncingScreen.
        assertEquals(
            "home",
            SplashRouter.resolveRoute(hasCredentials = true, albumCount = 10, drainIncomplete = true),
        )
    }

    @Test
    fun `drain complete with albums routes home`() {
        assertEquals(
            "home",
            SplashRouter.resolveRoute(hasCredentials = true, albumCount = 10, drainIncomplete = false),
        )
    }

    @Test
    fun `empty library still routes to syncing regardless of drain flag`() {
        assertEquals(
            syncingRoute("home"),
            SplashRouter.resolveRoute(hasCredentials = true, albumCount = 0, drainIncomplete = false),
        )
        assertEquals(
            syncingRoute("home"),
            SplashRouter.resolveRoute(hasCredentials = true, albumCount = 0, drainIncomplete = true),
        )
    }
}
