package com.lucasdss.ftpmusic.app.playback

import android.content.Intent
import androidx.media3.common.Player
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MediaActionReceiverTest {

    private lateinit var receiver: MediaActionReceiver

    // Mockk verification counters — Player interface mocks work for our limited usage
    private lateinit var player: Player

    @Before
    fun setUp() {
        receiver = MediaActionReceiver()
        player = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `PLAY_PAUSE dispatches play when not playing`() {
        every { player.isPlaying } returns false

        receiver.dispatchCommand(player, Player.COMMAND_PLAY_PAUSE.toLong())

        verify(exactly = 1) { player.play() }
        verify(exactly = 0) { player.pause() }
    }

    @Test
    fun `PLAY_PAUSE dispatches pause when playing`() {
        every { player.isPlaying } returns true

        receiver.dispatchCommand(player, Player.COMMAND_PLAY_PAUSE.toLong())

        verify(exactly = 1) { player.pause() }
        verify(exactly = 0) { player.play() }
    }

    @Test
    fun `SEEK_TO_NEXT dispatches correctly`() {
        receiver.dispatchCommand(player, Player.COMMAND_SEEK_TO_NEXT.toLong())

        verify(exactly = 1) { player.seekToNextMediaItem() }
    }

    @Test
    fun `SEEK_TO_PREVIOUS dispatches correctly`() {
        receiver.dispatchCommand(player, Player.COMMAND_SEEK_TO_PREVIOUS.toLong())

        verify(exactly = 1) { player.seekToPreviousMediaItem() }
    }

    @Test
    fun `unknown command is ignored`() {
        receiver.dispatchCommand(player, 9999L)

        verify(exactly = 0) { player.play() }
        verify(exactly = 0) { player.pause() }
    }

    @Test
    fun `wrong action intent is ignored`() {
        PlayerHolder.player = player
        val intent = Intent("com.other.ACTION").apply {
            putExtra(MediaActionReceiver.EXTRA_COMMAND, Player.COMMAND_PLAY_PAUSE.toLong())
        }
        receiver.onReceive(mockk(relaxed = true), intent)
        verify(exactly = 0) { player.play() }
        PlayerHolder.player = null
    }

    @Test
    fun `null player in onReceive does not crash`() {
        PlayerHolder.player = null
        val intent = Intent(MediaActionReceiver.ACTION).apply {
            putExtra(MediaActionReceiver.EXTRA_COMMAND, Player.COMMAND_PLAY_PAUSE.toLong())
        }
        receiver.onReceive(mockk(relaxed = true), intent)
        // Should not throw — verified by test completion
    }
}
