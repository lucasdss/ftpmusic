package com.lucasdss.ftpmusic.app.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Track-list chrome preferences (ADR-0104).
 * Controls like/dislike glyphs and duration on [com.lucasdss.ftpmusic.app.ui.components.SongListRow].
 * Now Playing / mini player ignore these and always show reactions.
 */
data class ListChromePrefs(val showListReactions: Boolean = true, val showListDuration: Boolean = true) {
    companion object {
        val DEFAULT = ListChromePrefs()
    }
}

val LocalListChromePrefs = staticCompositionLocalOf { ListChromePrefs.DEFAULT }
