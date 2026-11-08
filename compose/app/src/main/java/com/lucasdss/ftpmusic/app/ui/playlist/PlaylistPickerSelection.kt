package com.lucasdss.ftpmusic.app.ui.playlist

import com.lucasdss.ftpmusic.app.data.model.Track

/**
 * Seeds the "Add to Playlist" picker without racing menu-dismiss side effects.
 * Pure for unit tests (T1).
 */
enum class PlaylistPickerSeedMode {
    /** One track from [menuIndex] (invalid index → empty). */
    SINGLE,

    /** Every track id in [tracks] (add-all). */
    ALL,
}

fun resolvePickerSelection(
    mode: PlaylistPickerSeedMode,
    tracks: List<Track>,
    menuIndex: Int = -1,
): Set<String> {
    return when (mode) {
        PlaylistPickerSeedMode.ALL -> tracks.map { it.id }.filter { it.isNotEmpty() }.toSet()
        PlaylistPickerSeedMode.SINGLE -> {
            if (menuIndex < 0 || menuIndex >= tracks.size) emptySet()
            else {
                val id = tracks[menuIndex].id
                if (id.isEmpty()) emptySet() else setOf(id)
            }
        }
    }
}
