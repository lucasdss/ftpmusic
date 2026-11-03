package com.lucasdss.ftpmusic.app.data.sort

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumTrackSortTest {

    @Test
    fun `unset or unknown key falls back to disc track`() {
        assertEquals(AlbumTrackSort.DISC_TRACK, AlbumTrackSort.fromKey(null))
        assertEquals(AlbumTrackSort.DISC_TRACK, AlbumTrackSort.fromKey(""))
        assertEquals(AlbumTrackSort.DISC_TRACK, AlbumTrackSort.fromKey("legacy_title"))
        assertEquals(AlbumTrackSort.DISC_TRACK, AlbumTrackSort.DEFAULT)
    }

    @Test
    fun `known keys round-trip`() {
        AlbumTrackSort.entries.forEach { sort ->
            assertEquals(sort, AlbumTrackSort.fromKey(sort.key))
        }
    }

    @Test
    fun `usesPlayCount only for play count modes`() {
        assertEquals(false, AlbumTrackSort.DISC_TRACK.usesPlayCount)
        assertEquals(true, AlbumTrackSort.PLAY_COUNT_DESC.usesPlayCount)
        assertEquals(true, AlbumTrackSort.PLAY_COUNT_ASC.usesPlayCount)
    }
}
