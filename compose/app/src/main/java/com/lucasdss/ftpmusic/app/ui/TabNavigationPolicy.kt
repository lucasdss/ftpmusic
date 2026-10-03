package com.lucasdss.ftpmusic.app.ui

/**
 * Pure bottom-tab navigation policy (ADR-0062).
 *
 * Cross-tab: save/restore stacks. Same-tab: pop to tab root when nested.
 * Selection uses [activeBottomTab], not route-prefix matching.
 */
object TabNavigationPolicy {

    val TAB_ROUTES: Set<String> = setOf("home", "search", "library", "favorites")

    /** Routes that must not steal bottom-tab highlight. */
    fun isOverlayRoute(route: String?): Boolean {
        if (route == null) return false
        if (route == "nowplaying" || route == "settings" || route == "profile" ||
            route == "customMixes" || route == "rebuildmix" || route == "splash" ||
            route == "connect"
        ) {
            return true
        }
        if (route.startsWith("syncing")) return true
        return false
    }

    fun isDetailRoute(route: String?): Boolean {
        if (route == null) return false
        return DETAIL_PREFIXES.any { route.startsWith(it) }
    }

    fun isTabRootRoute(route: String?): Boolean {
        if (route == null) return false
        if (route in TAB_ROUTES) return true
        // library?tab=… is still the Library root destination pattern.
        if (route.startsWith("library")) return true
        if (route.startsWith("search")) return true
        return false
    }

    fun isTabSelected(activeBottomTab: String, tabRoute: String): Boolean = activeBottomTab == tabRoute

    /**
     * Same-tab reselect should pop to root when the current destination is not
     * already that tab's root (e.g. on `mix/{id}` with active Home).
     */
    fun shouldPopToTabRoot(activeBottomTab: String, clickedTab: String, currentRoute: String?): Boolean {
        if (activeBottomTab != clickedTab) return false
        if (currentRoute == null) return false
        // Same-tab while overlay (nowplaying/settings) is up: pop to tab root.
        if (isOverlayRoute(currentRoute)) return true
        return !isAtTabRoot(clickedTab, currentRoute)
    }

    fun isAtTabRoot(tabRoute: String, currentRoute: String): Boolean = when (tabRoute) {
        "home" -> currentRoute == "home"
        "favorites" -> currentRoute == "favorites"
        "search" -> currentRoute == "search" || currentRoute.startsWith("search/")
        "library" -> currentRoute == "library" || currentRoute.startsWith("library")
        else -> currentRoute == tabRoute
    }

    /**
     * Route argument for [androidx.navigation.NavController.popBackStack].
     * Library's graph destination is `library?tab={tab}`, not bare `library`.
     */
    fun tabRootPopRoute(tabRoute: String): String = when (tabRoute) {
        "library" -> "library?tab={tab}"
        else -> tabRoute
    }

    /**
     * Ordered pop targets for same-tab reselect. Primary is [tabRootPopRoute];
     * library also tries bare `library` if the pattern route is absent from the
     * back stack (Nav version / deep-link edge).
     */
    fun tabRootPopFallbackRoutes(tabRoute: String): List<String> = when (tabRoute) {
        "library" -> listOf("library?tab={tab}", "library")
        else -> listOf(tabRoute)
    }

    /**
     * Active tab after a bottom-bar click. Overlays never change ownership.
     * Cross-tab click switches ownership; same-tab keeps it.
     */
    fun resolveActiveTabAfterClick(activeBottomTab: String, clickedTab: String): String =
        clickedTab.takeIf { it in TAB_ROUTES } ?: activeBottomTab

    /**
     * Programmatic navigation that lands on another tab root (e.g. Home →
     * `library?tab=playlists`) must update ownership.
     */
    fun resolveActiveTabForDestination(destinationRoute: String): String? = when {
        destinationRoute == "home" -> "home"
        destinationRoute == "favorites" -> "favorites"
        destinationRoute.startsWith("library") -> "library"
        destinationRoute == "search" || destinationRoute.startsWith("search/") -> "search"
        else -> null
    }

    private val DETAIL_PREFIXES = listOf(
        "album/",
        "artist/",
        "playlist/",
        "genre/",
        "mix/",
    )
}
