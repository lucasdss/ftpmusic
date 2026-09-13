package com.lucasdss.ftpmusic.app.util

/**
 * Resolves MIME type for audio tracks.
 * Prefers server-provided contentType, falls back to suffix-derived mapping.
 */
object MimeTypeResolver {

    private val suffixToMimeType = mapOf(
        "mp3" to "audio/mpeg",
        "ogg" to "audio/ogg",
        "opus" to "audio/ogg;codecs=opus",
        "flac" to "audio/flac",
        "aac" to "audio/aac",
        "m4a" to "audio/mp4",
        "mp4" to "audio/mp4",
        "wav" to "audio/wav",
        "wma" to "audio/x-ms-wma",
        "aiff" to "audio/aiff",
        "aif" to "audio/aiff",
        "webm" to "audio/webm",
        "dsf" to "audio/dsf",
        "dff" to "audio/dff",
        "wv" to "audio/wavpack",
    )

    /** Returns the best MIME type: contentType first, then suffix fallback, then default. */
    fun resolve(contentType: String?, suffix: String?): String? {
        // Prefer server-provided contentType (e.g. "audio/mpeg" from Subsonic API)
        if (!contentType.isNullOrBlank()) return contentType.trim()

        // Fallback: derive from file suffix
        val cleanSuffix = suffix?.trim()?.lowercase()?.trimStart('.')
        if (cleanSuffix != null) {
            suffixToMimeType[cleanSuffix]?.let { return it }
        }

        // Last resort: null — caller must decide
        return null
    }
}
