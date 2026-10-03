package com.lucasdss.ftpmusic.app.ui

/**
 * Whether the persistent [com.lucasdss.ftpmusic.app.ui.components.AppHeader]
 * should show for the current nav route.
 *
 * Shows on primary tabs only. Settings / detail / player use
 * [com.lucasdss.ftpmusic.app.ui.components.DetailBackButton] or player
 * minimize chrome instead (ADR-0054 / ADR-0063).
 */
fun showAppHeaderForRoute(route: String?): Boolean {
    if (route == null) return false
    if (route in HEADER_HIDDEN_EXACT) return false
    return HEADER_HIDDEN_PREFIXES.none { route.startsWith(it) }
}

private val HEADER_HIDDEN_EXACT = setOf(
    "splash",
    "connect",
    "nowplaying",
    "syncing",
    "rebuildmix",
    "settings",
    "profile",
    "customMixes",
)

private val HEADER_HIDDEN_PREFIXES = listOf(
    "album/",
    "artist/",
    "playlist/",
    "genre/",
    "mix/",
    "syncing",
)
