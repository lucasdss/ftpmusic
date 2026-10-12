package com.lucasdss.ftpmusic.app.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Track-list chrome preferences (ADR-0104 / ADR-0105 / ADR-0126).
 * Controls like/dislike glyphs, duration, leading number columns on
 * [com.lucasdss.ftpmusic.app.ui.components.SongListRow], and auto-scroll
 * to the now-playing row in detail lists / queue.
 * Now Playing / mini player ignore reaction prefs and always show reactions.
 */
data class ListChromePrefs(
    val showListReactions: Boolean = true,
    val showListDuration: Boolean = true,
    /** Album detail: metadata track tag column (default ON). */
    val showAlbumTrackNumbers: Boolean = true,
    /** Playlist / Daily Mix / Artist: sequence 1..n column (default ON). */
    val showListSequence: Boolean = true,
    /** Auto-scroll Mix/Playlist/Album/Artist/Queue to current track (default ON). */
    val autoCenterNowPlaying: Boolean = true,
) {
    companion object {
        val DEFAULT = ListChromePrefs()
    }
}

val LocalListChromePrefs = staticCompositionLocalOf { ListChromePrefs.DEFAULT }
