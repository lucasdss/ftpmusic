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
    fun `B0-7 drain incomplete routes to syncing even with albums`() {
        assertEquals(
            syncingRoute("home"),
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
}
