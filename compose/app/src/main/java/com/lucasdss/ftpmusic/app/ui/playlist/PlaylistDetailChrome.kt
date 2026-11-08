package com.lucasdss.ftpmusic.app.ui.playlist

/** Overflow / sync / rename chrome — available even for empty playlists. */
object PlaylistDetailChrome {
    fun showOverflowMenu(trackCount: Int): Boolean = true

    fun showPlayShuffle(trackCount: Int): Boolean = trackCount > 0
}
