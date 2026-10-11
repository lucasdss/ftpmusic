package com.lucasdss.ftpmusic.app.playback

import android.content.Intent
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaActionReceiverRobolectricTest {

    private lateinit var receiver: MediaActionReceiver
    private lateinit var player: Player

    @Before
    fun setUp() {
        receiver = MediaActionReceiver()
        player = mockk(relaxed = true)
        ReactionCommandBridge.handler = null
        PlayerHolder.player = null
    }

    @After
    fun tearDown() {
        ReactionCommandBridge.handler = null
        PlayerHolder.player = null
    }

    @Test
    fun `onReceive custom action hits bridge`() {
        var seen: String? = null
        ReactionCommandBridge.handler = { seen = it }
        val intent = Intent(MediaActionReceiver.ACTION).apply {
            putExtra(
                MediaActionReceiver.EXTRA_CUSTOM_ACTION,
                MediaSessionReactionCommands.ACTION_LIKE,
            )
        }
        receiver.onReceive(RuntimeEnvironment.getApplication(), intent)
        assertEquals(MediaSessionReactionCommands.ACTION_LIKE, seen)
    }

    @Test
    fun `onReceive play pause with player`() {
        PlayerHolder.player = player
        every { player.isPlaying } returns true
        val intent = Intent(MediaActionReceiver.ACTION).apply {
            putExtra(MediaActionReceiver.EXTRA_COMMAND, Player.COMMAND_PLAY_PAUSE.toLong())
        }
        receiver.onReceive(RuntimeEnvironment.getApplication(), intent)
        verify(exactly = 1) { player.pause() }
    }
}
