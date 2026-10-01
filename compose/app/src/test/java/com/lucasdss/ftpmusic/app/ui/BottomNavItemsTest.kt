package com.lucasdss.ftpmusic.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BottomNavItemsTest {

    @Test
    fun `order is Home Search Library Favorites`() {
        assertEquals(
            listOf("home", "search", "library", "favorites"),
            bottomNavItems().map { it.route },
        )
    }

    @Test
    fun `settings is not a bottom tab`() {
        assertFalse(bottomNavItems().any { it.route == "settings" })
    }

    @Test
    fun `favorites remains a peer tab`() {
        assertEquals("Favorites", bottomNavItems().single { it.route == "favorites" }.label)
    }
}
