package com.lucasdss.ftpmusic.app

/**
 * Oct 29 2026 Play policy: permissions that FTP Music must not declare while
 * targetSdk is 36 (and must not declare without an ADR + Console declaration
 * thereafter). See docs/PLAY_POLICY_2026_10_29_COMPLIANCE.md and ADR-0153.
 *
 * Pure string scan of AndroidManifest.xml text — no Android framework APIs.
 */
object PlayRestrictedPermissions {
    /**
     * Forbidden while targetSdk is below 37. When the API-37 bump lands, remove
     * ACCESS_LOCAL_NETWORK from this set in the same PR as the manifest declare
     * (ADR-0153).
     */
    val FORBIDDEN: Set<String> = setOf(
        "android.permission.ACCESS_LOCAL_NETWORK",
        "android.permission.NEARBY_WIFI_DEVICES",
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.GET_ACCOUNTS",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO",
        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
        "android.permission.READ_CALL_LOG",
        "android.permission.WRITE_CALL_LOG",
        "android.permission.PROCESS_OUTGOING_CALLS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_MMS",
        "android.permission.RECEIVE_WAP_PUSH",
    )

    /** AppFunctions / agent-facing markers that must not appear until product opts in. */
    val FORBIDDEN_MARKERS: Set<String> = setOf(
        "android.app.appfunctions",
        "androidx.appfunctions",
        "AppFunctionService",
    )

    /**
     * Returns forbidden permission names declared via uses-permission in [manifestXml].
     */
    fun forbiddenPermissionsPresent(manifestXml: String): List<String> {
        val declared = PERMISSION_ATTR.findAll(manifestXml)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .toSet()
        return FORBIDDEN.filter { it in declared }.sorted()
    }

    /** Returns forbidden AppFunctions-related markers found in [manifestXml]. */
    fun forbiddenMarkersPresent(manifestXml: String): List<String> = FORBIDDEN_MARKERS.filter { marker ->
        manifestXml.contains(marker, ignoreCase = true)
    }.sorted()

    private val PERMISSION_ATTR =
        Regex("""android:name\s*=\s*"(android\.permission\.[A-Z0-9_]+)"""")
}
