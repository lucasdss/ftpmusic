package com.lucasdss.ftpmusic.app.ui

/** Bottom bar / back policy for connect + first-login syncing routes. */
object SyncNavChrome {
    fun showBottomBar(route: String?): Boolean {
        if (route.isNullOrBlank()) return true
        if (route == "connect") return false
        if (route.startsWith("syncing")) return false
        return true
    }

    /** First-login syncing must not allow back (same class of hole as bottom-bar leave). */
    fun blockBack(route: String?, firstLoginSync: Boolean): Boolean =
        firstLoginSync && route != null && route.startsWith("syncing")
}
