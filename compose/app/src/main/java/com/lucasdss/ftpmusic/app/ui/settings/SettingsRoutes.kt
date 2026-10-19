package com.lucasdss.ftpmusic.app.ui.settings

import androidx.navigation.NavType
import androidx.navigation.navArgument

/**
 * Route string constants for Settings IA (ADR-0149).
 * Existing top-level routes (`downloads`, `profile`, `customMixes`) stay unchanged.
 */
object SettingsRoutes {
    const val ROOT = "settings"
    const val SERVER = "settings/server"
    const val PLAYBACK = "settings/playback"
    const val BLUETOOTH = "settings/bluetooth"
    const val CAST = "settings/cast"
    const val STORAGE = "settings/storage"
    const val NETWORK = "settings/network"
    const val APPEARANCE = "settings/appearance"
    const val NOTIFICATIONS = "settings/notifications"
    const val HOME = "settings/home"
    const val LASTFM = "settings/lastfm"
    const val ADVANCED = "settings/advanced"
    const val ABOUT = "settings/about"
    const val TYPOGRAPHY = "settings/typography"

    /** Nav graph pattern with optional search scroll anchor. */
    fun leafPattern(base: String): String = "$base?anchor={anchor}"

    fun leafNavArgs() = listOf(
        navArgument("anchor") {
            type = NavType.StringType
            defaultValue = ""
        },
    )

    /** Pre-existing routes (not renamed). */
    const val DOWNLOADS = "downloads"
    const val PROFILE = "profile"
    const val CUSTOM_MIXES = "customMixes"

    /** All new settings leaf routes plus typography (excludes root + legacy top-level). */
    val nestedLeaves: List<String> = listOf(
        SERVER,
        PLAYBACK,
        BLUETOOTH,
        CAST,
        STORAGE,
        NETWORK,
        APPEARANCE,
        NOTIFICATIONS,
        HOME,
        LASTFM,
        ADVANCED,
        ABOUT,
        TYPOGRAPHY,
    )
}
