package com.lucasdss.ftpmusic.app.util

/**
 * Shared HTML → plain-text cleanup for artist bios and similar wiki fields.
 * Used by Last.fm parse path and Navidrome getArtistInfo2 enrich.
 */
object TextHtml {
    fun strip(raw: String): String {
        val noTags = raw.replace(Regex("<[^>]+>"), " ")
        return noTags
            .replace(Regex("&nbsp;?", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&quot;"), "\"")
            .replace(Regex("&#39;|&apos;"), "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Display / persist path for biography — null when blank after strip. */
    fun sanitizeBiography(raw: String?, maxChars: Int = 2000): String? {
        if (raw.isNullOrBlank()) return null
        return strip(raw).take(maxChars).trim().takeIf { it.isNotBlank() }
    }
}
