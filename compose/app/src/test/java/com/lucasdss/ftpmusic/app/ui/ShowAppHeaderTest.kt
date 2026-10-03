package com.lucasdss.ftpmusic.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowAppHeaderTest {

    @Test
    fun `null route hides header`() {
        assertFalse(showAppHeaderForRoute(null))
    }

    @Test
    fun `primary tabs show header`() {
        listOf("home", "library", "library?tab=albums", "favorites", "search", "search/foo")
            .forEach { assertTrue(it, showAppHeaderForRoute(it)) }
    }

    @Test
    fun `exact hidden routes hide header`() {
        listOf(
            "splash",
            "connect",
            "nowplaying",
            "syncing",
            "rebuildmix",
            "settings",
            "profile",
            "customMixes",
        )
            .forEach { assertFalse(it, showAppHeaderForRoute(it)) }
    }

    @Test
    fun `detail prefixes hide header`() {
        listOf(
            "album/a1",
            "artist/x",
            "playlist/p",
            "genre/Rock",
            "mix/daily1",
            "syncing?returnTo=home",
        ).forEach { assertFalse(it, showAppHeaderForRoute(it)) }
    }
}
