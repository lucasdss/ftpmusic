package com.lucasdss.ftpmusic.app.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Track-list chrome preferences (ADR-0104 / ADR-0105).
 * Controls like/dislike glyphs, duration, and leading number columns on
 * [com.lucasdss.ftpmusic.app.ui.components.SongListRow].
 * Now Playing / mini player ignore these and always show reactions.
 */
data class ListChromePrefs(
    val showListReactions: Boolean = true,
    val showListDuration: Boolean = true,
    /** Album detail: metadata track tag column (default ON). */
    val showAlbumTrackNumbers: Boolean = true,
    /** Playlist / Daily Mix / Artist: sequence 1..n column (default ON). */
    val showListSequence: Boolean = true,
) {
    companion object {
        val DEFAULT = ListChromePrefs()
    }
}

val LocalListChromePrefs = staticCompositionLocalOf { ListChromePrefs.DEFAULT }
