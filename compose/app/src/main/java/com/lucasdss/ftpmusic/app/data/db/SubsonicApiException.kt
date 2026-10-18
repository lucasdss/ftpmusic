package com.lucasdss.ftpmusic.app.data.db

/**
 * Subsonic/Navidrome API returned `subsonic-response.status=failed`.
 * Distinct from [AlbumListIncompleteException] (truncated pagination walk).
 */
internal class SubsonicApiException(
    message: String,
    val code: Any? = null,
) : IllegalStateException(message)
