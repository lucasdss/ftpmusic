package com.lucasdss.ftpmusic.app.playback

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
class MediaSessionQueueCommandsTest {

    @Test
    fun `buttons advertise play next and add to queue`() {
        val buttons = MediaSessionQueueCommands.buttons()
        assertEquals(2, buttons.size)
        assertEquals(MediaSessionQueueCommands.ACTION_PLAY_NEXT, buttons[0].sessionCommand?.customAction)
        assertEquals(MediaSessionQueueCommands.ACTION_ADD_TO_QUEUE, buttons[1].sessionCommand?.customAction)
        assertEquals("Play Next", buttons[0].displayName.toString())
        assertEquals("Add to Queue", buttons[1].displayName.toString())
    }

    @Test
    fun `isQueueAction matches only queue actions`() {
        assertTrue(MediaSessionQueueCommands.isQueueAction(MediaSessionQueueCommands.ACTION_PLAY_NEXT))
        assertTrue(MediaSessionQueueCommands.isQueueAction(MediaSessionQueueCommands.ACTION_ADD_TO_QUEUE))
        assertFalse(MediaSessionQueueCommands.isQueueAction(MediaSessionReactionCommands.ACTION_LIKE))
    }

    @Test
    fun `supportedCommands list is stable`() {
        assertEquals(2, MediaSessionQueueCommands.SUPPORTED_COMMANDS.size)
        assertTrue(
            MediaSessionQueueCommands.SUPPORTED_COMMANDS.contains(
                MediaSessionQueueCommands.ACTION_PLAY_NEXT,
            ),
        )
    }
}
