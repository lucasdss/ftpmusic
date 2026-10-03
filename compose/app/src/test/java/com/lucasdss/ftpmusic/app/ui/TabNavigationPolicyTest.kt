package com.lucasdss.ftpmusic.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabNavigationPolicyTest {

    @Test
    fun `isTabSelected uses activeBottomTab not route prefix`() {
        assertTrue(TabNavigationPolicy.isTabSelected("home", "home"))
        assertFalse(TabNavigationPolicy.isTabSelected("home", "library"))
        // Detail under Home still selects Home via ownership, not route string.
        assertTrue(TabNavigationPolicy.isTabSelected("home", "home"))
    }

    @Test
    fun `shouldPopToTabRoot when same tab nested on mix`() {
        assertTrue(
            TabNavigationPolicy.shouldPopToTabRoot(
                activeBottomTab = "home",
                clickedTab = "home",
                currentRoute = "mix/42",
            ),
        )
    }

    @Test
    fun `shouldPopToTabRoot when same tab nested on album artist playlist genre`() {
        listOf("album/a1", "artist/a2", "playlist/p1", "genre/Jazz").forEach { route ->
            assertTrue(
                route,
                TabNavigationPolicy.shouldPopToTabRoot("library", "library", route),
            )
        }
    }

    @Test
    fun `shouldNotPop when cross tab click`() {
        assertFalse(
            TabNavigationPolicy.shouldPopToTabRoot(
                activeBottomTab = "library",
                clickedTab = "home",
                currentRoute = "mix/1",
            ),
        )
    }

    @Test
    fun `shouldNotPop when already at home root`() {
        assertFalse(
            TabNavigationPolicy.shouldPopToTabRoot(
                activeBottomTab = "home",
                clickedTab = "home",
                currentRoute = "home",
            ),
        )
    }

    @Test
    fun `shouldNotPop when already at library root with query`() {
        assertFalse(
            TabNavigationPolicy.shouldPopToTabRoot(
                activeBottomTab = "library",
                clickedTab = "library",
                currentRoute = "library?tab=playlists",
            ),
        )
    }

    @Test
    fun `shouldPop on overlay when same tab reselected`() {
        listOf("nowplaying", "settings", "profile", "customMixes", "syncing?returnTo=home").forEach { route ->
            assertTrue(
                route,
                TabNavigationPolicy.shouldPopToTabRoot("home", "home", route),
            )
        }
    }

    @Test
    fun `shouldNotPop overlay when switching tabs`() {
        assertFalse(
            TabNavigationPolicy.shouldPopToTabRoot(
                activeBottomTab = "home",
                clickedTab = "library",
                currentRoute = "nowplaying",
            ),
        )
    }

    @Test
    fun `shouldNotPop when currentRoute null`() {
        assertFalse(TabNavigationPolicy.shouldPopToTabRoot("home", "home", null))
    }

    @Test
    fun `isAtTabRoot covers search variants`() {
        assertTrue(TabNavigationPolicy.isAtTabRoot("search", "search"))
        assertTrue(TabNavigationPolicy.isAtTabRoot("search", "search/beatles"))
        assertFalse(TabNavigationPolicy.isAtTabRoot("search", "album/1"))
    }

    @Test
    fun `isAtTabRoot favorites and home exact`() {
        assertTrue(TabNavigationPolicy.isAtTabRoot("favorites", "favorites"))
        assertFalse(TabNavigationPolicy.isAtTabRoot("favorites", "album/1"))
        assertTrue(TabNavigationPolicy.isAtTabRoot("home", "home"))
        assertFalse(TabNavigationPolicy.isAtTabRoot("home", "mix/1"))
    }

    @Test
    fun `isDetailRoute recognizes nest prefixes`() {
        assertTrue(TabNavigationPolicy.isDetailRoute("mix/9"))
        assertTrue(TabNavigationPolicy.isDetailRoute("album/x"))
        assertFalse(TabNavigationPolicy.isDetailRoute("home"))
        assertFalse(TabNavigationPolicy.isDetailRoute(null))
    }

    @Test
    fun `isOverlayRoute covers chrome destinations`() {
        assertTrue(TabNavigationPolicy.isOverlayRoute("nowplaying"))
        assertTrue(TabNavigationPolicy.isOverlayRoute("settings"))
        assertTrue(TabNavigationPolicy.isOverlayRoute("splash"))
        assertTrue(TabNavigationPolicy.isOverlayRoute("connect"))
        assertTrue(TabNavigationPolicy.isOverlayRoute("rebuildmix"))
        assertFalse(TabNavigationPolicy.isOverlayRoute("home"))
        assertFalse(TabNavigationPolicy.isOverlayRoute(null))
    }

    @Test
    fun `resolveActiveTabAfterClick switches ownership`() {
        assertEquals("library", TabNavigationPolicy.resolveActiveTabAfterClick("home", "library"))
        assertEquals("home", TabNavigationPolicy.resolveActiveTabAfterClick("library", "home"))
        assertEquals("home", TabNavigationPolicy.resolveActiveTabAfterClick("home", "home"))
    }

    @Test
    fun `resolveActiveTabAfterClick ignores unknown tab`() {
        assertEquals("home", TabNavigationPolicy.resolveActiveTabAfterClick("home", "settings"))
    }

    @Test
    fun `resolveActiveTabForDestination maps programmatic jumps`() {
        assertEquals("library", TabNavigationPolicy.resolveActiveTabForDestination("library?tab=playlists"))
        assertEquals("library", TabNavigationPolicy.resolveActiveTabForDestination("library"))
        assertEquals("home", TabNavigationPolicy.resolveActiveTabForDestination("home"))
        assertEquals("favorites", TabNavigationPolicy.resolveActiveTabForDestination("favorites"))
        assertEquals("search", TabNavigationPolicy.resolveActiveTabForDestination("search"))
        assertEquals("search", TabNavigationPolicy.resolveActiveTabForDestination("search/q"))
        assertNull(TabNavigationPolicy.resolveActiveTabForDestination("mix/1"))
        assertNull(TabNavigationPolicy.resolveActiveTabForDestination("nowplaying"))
    }

    @Test
    fun `tabRootPopRoute uses library graph pattern`() {
        assertEquals("library?tab={tab}", TabNavigationPolicy.tabRootPopRoute("library"))
        assertEquals("home", TabNavigationPolicy.tabRootPopRoute("home"))
        assertEquals("search", TabNavigationPolicy.tabRootPopRoute("search"))
        assertEquals("favorites", TabNavigationPolicy.tabRootPopRoute("favorites"))
    }

    @Test
    fun `tabRootPopFallbackRoutes tries library pattern then bare`() {
        assertEquals(
            listOf("library?tab={tab}", "library"),
            TabNavigationPolicy.tabRootPopFallbackRoutes("library"),
        )
        assertEquals(listOf("home"), TabNavigationPolicy.tabRootPopFallbackRoutes("home"))
        assertEquals(listOf("favorites"), TabNavigationPolicy.tabRootPopFallbackRoutes("favorites"))
    }

    @Test
    fun `isTabRootRoute true for tab destinations`() {
        assertTrue(TabNavigationPolicy.isTabRootRoute("home"))
        assertTrue(TabNavigationPolicy.isTabRootRoute("library?tab=albums"))
        assertTrue(TabNavigationPolicy.isTabRootRoute("search/foo"))
        assertFalse(TabNavigationPolicy.isTabRootRoute("mix/1"))
        assertFalse(TabNavigationPolicy.isTabRootRoute(null))
    }

    @Test
    fun `TAB_ROUTES matches bottom nav peers`() {
        assertEquals(
            setOf("home", "search", "library", "favorites"),
            TabNavigationPolicy.TAB_ROUTES,
        )
    }
}
