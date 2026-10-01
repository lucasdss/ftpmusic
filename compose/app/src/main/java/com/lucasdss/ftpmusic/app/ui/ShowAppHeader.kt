package com.lucasdss.ftpmusic.app.ui

/**
 * Whether the persistent [com.lucasdss.ftpmusic.app.ui.components.AppHeader]
 * should show for the current nav route.
 *
 * Shows on primary tabs **and** Settings (header gear destination). Detail
 * routes use [com.lucasdss.ftpmusic.app.ui.components.DetailBackButton] instead
 * (ADR-0054 / ADR-0055).
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
