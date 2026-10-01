package com.lucasdss.ftpmusic.app.ui

import androidx.navigation.NavOptionsBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNavigationTest {

    @Test
    fun `settings route constant`() {
        assertEquals("settings", SETTINGS_ROUTE)
    }

    @Test
    fun `syncingRoute always includes returnTo query`() {
        assertEquals("syncing?returnTo=home", syncingRoute())
        assertEquals("syncing?returnTo=home", syncingRoute("home"))
        assertEquals("syncing?returnTo=settings", syncingRoute("settings"))
    }

    @Test
    fun `entry policy is launchSingleTop without popUpTo home`() {
        val policy = settingsEntryPolicy()
        assertTrue(policy.launchSingleTop)
        assertFalse(policy.popUpToHome)
    }

    @Test
    fun `stack options apply policy without crash`() {
        val builder = NavOptionsBuilder()
        builder.settingsStackOptions()
        assertNotNull(builder)
    }

    @Test
    fun `syncing route pattern matches graph`() {
        assertEquals("syncing?returnTo={returnTo}", SYNCING_ROUTE_PATTERN)
    }
}
