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
    fun `credentials but no metadata routes to syncing`() {
        assertEquals("syncing", SplashRouter.resolveRoute(hasCredentials = true, albumCount = 0))
    }

    @Test
    fun `credentials and metadata routes to home`() {
        assertEquals("home", SplashRouter.resolveRoute(hasCredentials = true, albumCount = 1))
        assertEquals("home", SplashRouter.resolveRoute(hasCredentials = true, albumCount = 42))
    }
}
