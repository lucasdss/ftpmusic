package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Cover art id extraction: the artwork URI on a media item is a full
 * `getCoverArt?id=…` URL whose last PATH segment is "getCoverArt" — the id
 * must come from the metadata extra, the `id` query parameter, or the
 * `cover:<id>` scheme fallback.
 */
class PlaybackStateCoverArtTest {

    private fun mockUri(
        hierarchical: Boolean = true,
        idParam: String? = null,
        lastSegment: String? = null,
        text: String = "cover:x",
    ): Uri {
        val uri = mockk<Uri>()
        every { uri.isHierarchical } returns hierarchical
        if (hierarchical) {
            every { uri.getQueryParameter("id") } returns idParam
            every { uri.lastPathSegment } returns lastSegment
        }
        every { uri.toString() } returns text
        return uri
    }

    @Test
    fun `extractCoverArtId prefers metadata extra over uri`() {
        val extras = mockk<Bundle>()
        every { extras.getString("coverArtId") } returns "al-123"
        val metadata = MediaMetadata.Builder().setTitle("T").setExtras(extras).build()

        assertEquals("al-123", extractCoverArtId(metadata))
    }

    @Test
    fun `extractCoverArtId parses id query parameter from full URL`() {
        val uri = mockUri(hierarchical = true, idParam = "q-9", lastSegment = "getCoverArt")
        val metadata = MediaMetadata.Builder().setTitle("T").setArtworkUri(uri).build()

        assertEquals("q-9", extractCoverArtId(metadata))
    }

    @Test
    fun `extractCoverArtId falls back to lastPathSegment when no query id`() {
        val uri = mockUri(hierarchical = true, idParam = null, lastSegment = "al-77")
        val metadata = MediaMetadata.Builder().setTitle("T").setArtworkUri(uri).build()

        assertEquals("al-77", extractCoverArtId(metadata))
    }

    @Test
    fun `extractCoverArtId never returns getCoverArt path segment as id`() {
        // The regression: lastPathSegment of a full URL is "getCoverArt".
        val uri = mockUri(hierarchical = true, idParam = "real-1", lastSegment = "getCoverArt")
        val metadata = MediaMetadata.Builder().setTitle("T").setArtworkUri(uri).build()

        assertEquals("real-1", extractCoverArtId(metadata))
    }

    @Test
    fun `extractCoverArtId handles opaque cover scheme`() {
        val uri = mockUri(hierarchical = false, text = "cover:cov-7")
        val metadata = MediaMetadata.Builder().setTitle("T").setArtworkUri(uri).build()

        assertEquals("cov-7", extractCoverArtId(metadata))
    }

    @Test
    fun `extractCoverArtId returns null for empty metadata`() {
        assertNull(extractCoverArtId(null))
        assertNull(extractCoverArtId(MediaMetadata.EMPTY))
    }

    @Test
    fun `extractCoverArtId returns null when hierarchical uri has no id`() {
        val uri = mockUri(hierarchical = true, idParam = null, lastSegment = null)
        val metadata = MediaMetadata.Builder().setTitle("T").setArtworkUri(uri).build()

        assertNull(extractCoverArtId(metadata))
    }

    @Test
    fun `fromPlayer carries correct coverArtId end to end`() {
        val uri = mockUri(hierarchical = true, idParam = "al-555", lastSegment = "getCoverArt")
        val item = MediaItem.Builder()
            .setMediaId("t1")
            .setUri("http://s/t1")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("T1").setArtist("A").setAlbumTitle("Al")
                    .setArtworkUri(uri)
                    .build(),
            )
            .build()
        val player = mockk<Player>(relaxed = true)
        every { player.currentMediaItem } returns item
        every { player.currentMediaItemIndex } returns 0
        every { player.mediaItemCount } returns 1

        val state = PlaybackState.fromPlayer(player)

        assertEquals("al-555", state.coverArtId)
        assertEquals("T1", state.title)
        assertEquals("A", state.artist)
    }

    @Test
    fun `fromPlayer coverArtId null when no artwork`() {
        val item = MediaItem.Builder().setMediaId("t2").setUri("http://s/t2").build()
        val player = mockk<Player>(relaxed = true)
        every { player.currentMediaItem } returns item
        every { player.currentMediaItemIndex } returns 0
        every { player.mediaItemCount } returns 1

        assertNull(PlaybackState.fromPlayer(player).coverArtId)
    }
}
