package com.lucasdss.ftpmusic.app.ui.artist

/**
 * Artist biography chrome contract: compact teaser only; full text in a sheet.
 * Never expand in-place (Int.MAX_VALUE steals LazyVerticalGrid height).
 */
object ArtistBioUi {
    const val TEASER_MAX_LINES = 3
    const val READ_MORE_LENGTH_THRESHOLD = 160

    fun shouldShowReadMore(bio: String): Boolean =
        bio.length > READ_MORE_LENGTH_THRESHOLD
}
