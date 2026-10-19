package com.lucasdss.ftpmusic.app.ui

import androidx.navigation.NavOptionsBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Settings destination — off bottom nav (ADR-0055); stack-push entry (ADR-0056). */
const val SETTINGS_ROUTE = "settings"

/**
 * Graph pattern for metadata sync. Always navigate with [syncingRoute]; never bare `"syncing"`.
 * Default [returnTo] is home (first login / empty library).
 */
const val SYNCING_ROUTE_PATTERN = "syncing?returnTo={returnTo}"

/** URL-encode nav query values (JVM-safe; Uri.encode is null on plain unit tests). */
internal fun encodeNavQuery(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

fun syncingRoute(returnTo: String = "home"): String = "syncing?returnTo=${encodeNavQuery(returnTo)}"

/**
 * Documented Settings entry policy (ADR-0056 / market stack-back).
 * [popUpToHome] must stay false so Back returns to the prior screen.
 */
data class SettingsEntryPolicy(val launchSingleTop: Boolean = true, val popUpToHome: Boolean = false)

fun settingsEntryPolicy(): SettingsEntryPolicy = SettingsEntryPolicy()

/**
 * Market stack policy (Spotify/YTM): push Settings with [launchSingleTop] only.
 * System Back returns to the previous screen. No `popUpTo("home")`.
 */
fun NavOptionsBuilder.settingsStackOptions() {
    launchSingleTop = settingsEntryPolicy().launchSingleTop
}
