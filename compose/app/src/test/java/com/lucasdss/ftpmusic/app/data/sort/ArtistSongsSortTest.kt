package com.lucasdss.ftpmusic.app.data.sort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistSongsSortTest {

    @Test
    fun `unset or unknown key falls back to most played`() {
        assertEquals(ArtistSongsSort.PLAY_COUNT_DESC, ArtistSongsSort.fromKey(null))
        assertEquals(ArtistSongsSort.PLAY_COUNT_DESC, ArtistSongsSort.fromKey(""))
        assertEquals(ArtistSongsSort.PLAY_COUNT_DESC, ArtistSongsSort.fromKey("bogus"))
        assertEquals(ArtistSongsSort.PLAY_COUNT_DESC, ArtistSongsSort.DEFAULT)
    }

    @Test
    fun `known keys round-trip`() {
        ArtistSongsSort.entries.forEach { sort ->
            assertEquals(sort, ArtistSongsSort.fromKey(sort.key))
        }
    }

    @Test
    fun `natural direction per mode`() {
        assertEquals(ArtistSongsSort.PLAY_COUNT_DESC, ArtistSongsSort.natural(ArtistSongsMode.PLAY_COUNT))
        assertEquals(ArtistSongsSort.ALBUM_NAME_ASC, ArtistSongsSort.natural(ArtistSongsMode.ALBUM_NAME))
        assertEquals(ArtistSongsSort.ALBUM_YEAR_ASC, ArtistSongsSort.natural(ArtistSongsMode.ALBUM_YEAR))
        assertEquals(ArtistSongsSort.TRACK_NAME_ASC, ArtistSongsSort.natural(ArtistSongsMode.TRACK_NAME))
        assertEquals(ArtistSongsSort.RANDOM, ArtistSongsSort.natural(ArtistSongsMode.RANDOM))
    }

    @Test
    fun `withAscending changes only direction`() {
        assertEquals(ArtistSongsSort.PLAY_COUNT_ASC, ArtistSongsSort.PLAY_COUNT_DESC.withAscending(true))
        assertEquals(ArtistSongsSort.PLAY_COUNT_DESC, ArtistSongsSort.PLAY_COUNT_ASC.withAscending(false))
        assertEquals(ArtistSongsSort.ALBUM_NAME_DESC, ArtistSongsSort.ALBUM_NAME_ASC.withAscending(false))
        assertEquals(ArtistSongsSort.RANDOM, ArtistSongsSort.RANDOM.withAscending(false))
    }

    @Test
    fun `pagination strategy flags`() {
        assertTrue(ArtistSongsSort.PLAY_COUNT_DESC.isKeyset)
        assertTrue(ArtistSongsSort.TRACK_NAME_DESC.isKeyset)
        assertFalse(ArtistSongsSort.ALBUM_NAME_ASC.isKeyset)
        assertFalse(ArtistSongsSort.ALBUM_YEAR_ASC.isKeyset)
        assertFalse(ArtistSongsSort.RANDOM.isKeyset)

        assertTrue(ArtistSongsSort.ALBUM_NAME_ASC.usesAlbumBlocks)
        assertTrue(ArtistSongsSort.ALBUM_YEAR_DESC.usesAlbumBlocks)
        assertFalse(ArtistSongsSort.PLAY_COUNT_DESC.usesAlbumBlocks)

        assertFalse(ArtistSongsSort.RANDOM.supportsDirection)
        assertTrue(ArtistSongsSort.PLAY_COUNT_ASC.supportsDirection)
    }
}
