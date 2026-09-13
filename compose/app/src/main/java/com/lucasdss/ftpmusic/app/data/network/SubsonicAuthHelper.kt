package com.lucasdss.ftpmusic.app.data.network

import java.math.BigInteger
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubsonicAuthHelper @Inject constructor() {

    fun buildAuthParams(username: String, password: String): Map<String, String> {
        val salt = (100000..999999).random().toString()
        val token = md5("$password$salt")
        return mapOf(
            "u" to username,
            "t" to token,
            "s" to salt,
            "v" to "1.16.1",
            "c" to "ftpmusic",
            "f" to "json",
        )
    }

    fun buildStreamUrl(
        serverUrl: String,
        trackId: String,
        username: String,
        password: String,
        maxBitRate: Int = 320,
    ): String {
        val auth = buildAuthParams(username, password)
        val params = auth.entries.joinToString("&") { "${it.key}=${it.value}" }
        return "$serverUrl/rest/stream?id=$trackId&$params&maxBitRate=$maxBitRate"
    }

    /**
     * Build an authenticated cover art URL. Uses a deterministic salt derived from
     * coverArtId so the same URL is produced across app restarts — this lets Coil's
     * disk cache persist and prevents re-downloading every cover on each launch.
     */
    fun buildCoverArtUrl(
        serverUrl: String,
        username: String,
        password: String,
        coverArtId: String,
        size: Int = 300,
    ): String {
        val salt = (coverArtId.hashCode() and 0x7fffffff).toString()
        val token = md5("$password$salt")
        val params = listOf(
            "u" to username,
            "t" to token,
            "s" to salt,
            "v" to "1.16.1",
            "c" to "ftpmusic",
            "f" to "json",
            "size" to size.toString(),
        ).joinToString("&") { "${it.first}=${it.second}" }
        return "${serverUrl.trimEnd('/')}/rest/getCoverArt?id=$coverArtId&$params"
    }

    private val backingUrlCache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 256
    }
    private val coverArtUrlCache = java.util.Collections.synchronizedMap(backingUrlCache)

    /**
     * Cached version — reuses auth params per coverArtId so Coil's disk cache
     * keeps hitting the same URL. The key includes base/username/password:
     * keying on `coverArtId:size` alone returned a stale (or empty-base) URL
     * forever after a server or credential change.
     */
    fun buildCoverArtUrlCached(
        serverUrl: String,
        username: String,
        password: String,
        coverArtId: String,
        size: Int = 300,
    ): String {
        val key = "$serverUrl|$username|$password|$coverArtId|$size"
        return coverArtUrlCache.getOrPut(key) {
            buildCoverArtUrl(serverUrl, username, password, coverArtId, size)
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return BigInteger(1, digest).toString(16).padStart(32, '0')
    }

    /** Check Subsonic response status. Returns true on "ok", false on "failed". */
    fun checkResponseStatus(response: Map<String, Any>): Boolean {
        val sr = response["subsonic-response"] as? Map<*, *> ?: return true // assume ok if malformed
        return sr["status"] as? String != "failed"
    }

    /** Extract error message from a failed Subsonic response. */
    fun getResponseError(response: Map<String, Any>): String {
        val sr = response["subsonic-response"] as? Map<*, *> ?: return "Unknown error"
        val error = sr["error"] as? Map<*, *> ?: return "Unknown error"
        return "${error["code"]}: ${error["message"]}"
    }
}
