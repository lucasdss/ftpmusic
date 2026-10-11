package com.lucasdss.ftpmusic.app.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.ThumbRating
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@androidx.media3.common.util.UnstableApi
class MediaSessionReactionCommandsTest {

    @Test
    fun `buttons advertise like and dislike commands`() {
        val buttons = MediaSessionReactionCommands.buttons(isStarred = false, isDisliked = false)
        assertEquals(2, buttons.size)
        assertEquals(MediaSessionReactionCommands.ACTION_LIKE, buttons[0].sessionCommand?.customAction)
        assertEquals(MediaSessionReactionCommands.ACTION_DISLIKE, buttons[1].sessionCommand?.customAction)
        assertEquals("Like", buttons[0].displayName.toString())
        assertEquals("Dislike", buttons[1].displayName.toString())
    }

    @Test
    fun `buttons flip labels when active`() {
        val buttons = MediaSessionReactionCommands.buttons(isStarred = true, isDisliked = true)
        assertEquals("Unlike", buttons[0].displayName.toString())
        assertEquals("Remove dislike", buttons[1].displayName.toString())
    }

    @Test
    fun `thumbRating maps like dislike neither`() {
        val up = MediaSessionReactionCommands.thumbRating(isStarred = true, isDisliked = false)
        assertTrue(up.isRated)
        assertTrue(up.isThumbsUp)

        val down = MediaSessionReactionCommands.thumbRating(isStarred = false, isDisliked = true)
        assertTrue(down.isRated)
        assertFalse(down.isThumbsUp)

        val none = MediaSessionReactionCommands.thumbRating(isStarred = false, isDisliked = false)
        assertFalse(none.isRated)
    }

    @Test
    fun `mediaItemWithThumb sets ThumbRating on metadata`() {
        val item = MediaItem.Builder()
            .setMediaId("t1")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Song").build())
            .build()
        val updated = MediaSessionReactionCommands.mediaItemWithThumb(item, isStarred = true, isDisliked = false)
        val rating = updated.mediaMetadata.userRating as ThumbRating
        assertTrue(rating.isThumbsUp)
    }

    @Test
    fun `applyThumbRatingToCurrentItem no-ops when index invalid`() {
        val player = mockk<Player>(relaxed = true)
        every { player.currentMediaItemIndex } returns -1
        MediaSessionReactionCommands.applyThumbRatingToCurrentItem(player, true, false)
        verify(exactly = 0) { player.replaceMediaItem(any(), any()) }
    }

    @Test
    fun `applyThumbRatingToCurrentItem replaces current item`() {
        val item = MediaItem.Builder()
            .setMediaId("t1")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Song").build())
            .build()
        val player = mockk<Player>(relaxed = true)
        every { player.currentMediaItemIndex } returns 0
        every { player.currentMediaItem } returns item
        MediaSessionReactionCommands.applyThumbRatingToCurrentItem(player, true, false)
        verify(exactly = 1) { player.replaceMediaItem(0, any()) }
    }

    @Test
    fun `applyThumbRatingToCurrentItem swallows replace failure`() {
        val item = MediaItem.Builder().setMediaId("t1").build()
        val player = mockk<Player>(relaxed = true)
        every { player.currentMediaItemIndex } returns 0
        every { player.currentMediaItem } returns item
        every { player.replaceMediaItem(any(), any()) } throws IllegalStateException("cast")
        MediaSessionReactionCommands.applyThumbRatingToCurrentItem(player, false, true)
        // no throw
    }
}
