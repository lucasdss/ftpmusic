package com.lucasdss.ftpmusic.app.data.cache

/**
 * Cellular media / data policy (ADR-0105).
 *
 * On Wi‑Fi / Ethernet the policy does not restrict cache or streaming.
 * On cellular:
 * - [AUTO_CACHE] — albums, mixes, downloads, queue prefetch, streaming OK
 * - [MINIMAL] — queue prefetch + now-playing stream only
 * - [LOCAL_ONLY] — already-cached/downloaded content only (hard local)
 */
enum class CellularMediaPolicy {
    AUTO_CACHE,
    MINIMAL,
    LOCAL_ONLY,
    ;

    val storageKey: String
        get() = when (this) {
            AUTO_CACHE -> "auto_cache"
            MINIMAL -> "minimal"
            LOCAL_ONLY -> "local_only"
        }

    companion object {
        fun fromStorage(raw: String?): CellularMediaPolicy? = when (raw?.lowercase()) {
            "auto_cache" -> AUTO_CACHE
            "minimal" -> MINIMAL
            "local_only" -> LOCAL_ONLY
            else -> null
        }

        /** Migrate legacy `download_mobile_data` boolean. */
        fun fromLegacyDownloadMobileData(allowMobile: Boolean): CellularMediaPolicy =
            if (allowMobile) AUTO_CACHE else MINIMAL
    }
}
