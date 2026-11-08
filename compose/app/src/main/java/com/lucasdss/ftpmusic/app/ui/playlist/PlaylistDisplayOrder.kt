package com.lucasdss.ftpmusic.app.ui.playlist

import com.lucasdss.ftpmusic.app.data.model.Track

/** View-only playlist track ordering (does not mutate Room order). */
enum class PlaylistSortMode { CUSTOM, TITLE, ARTIST, ALBUM }

/**
 * Returns (sourceIndex, track) pairs for display. Mutations must use [sourceIndex]
 * or [Track.id] — never the filtered display index alone.
 */
fun playlistDisplayTracks(
    tracks: List<Track>,
    sortMode: PlaylistSortMode,
    query: String,
): List<Pair<Int, Track>> {
    val indexed = tracks.mapIndexed { index, track -> index to track }
    val filtered = if (query.isBlank()) {
        indexed
    } else {
        val q = query.trim()
        indexed.filter { (_, t) ->
            t.title.contains(q, ignoreCase = true) ||
                (t.artist?.contains(q, ignoreCase = true) == true) ||
                (t.album?.contains(q, ignoreCase = true) == true)
        }
    }
    return when (sortMode) {
        PlaylistSortMode.CUSTOM -> filtered
        PlaylistSortMode.TITLE -> filtered.sortedBy { it.second.title.lowercase() }
        PlaylistSortMode.ARTIST -> filtered.sortedWith(
            compareBy<Pair<Int, Track>> { it.second.artist?.lowercase().orEmpty() }
                .thenBy { it.second.title.lowercase() },
        )
        PlaylistSortMode.ALBUM -> filtered.sortedWith(
            compareBy<Pair<Int, Track>> { it.second.album?.lowercase().orEmpty() }
                .thenBy { it.second.title.lowercase() },
        )
    }
}

/** Drag/reorder allowed only when display order matches Room order. */
fun playlistReorderEnabled(sortMode: PlaylistSortMode, query: String): Boolean =
    sortMode == PlaylistSortMode.CUSTOM && query.isBlank()
