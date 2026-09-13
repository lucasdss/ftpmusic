package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test

class PlayerHolderQueueTest {

    private val mockPlayer: Player = mockk(relaxed = true)
    private val queueManager = QueueManager(mockk())

    @Before
    fun setup() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns Uri.EMPTY
    }

    @After
    fun teardown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `playAll uses PlayerHolder player`() {
        PlayerHolder.player = mockPlayer
        every { mockPlayer.mediaItemCount } returns 0
        val items = listOf(
            queueManager.buildMediaItem("t1", "Track 1", "http://ex.com/1"),
            queueManager.buildMediaItem("t2", "Track 2", "http://ex.com/2"),
        )
        queueManager.playAll(items)
        verify { mockPlayer.setMediaItems(items) }
        verify { mockPlayer.prepare() }
        verify { mockPlayer.play() }
    }

    @Test
    fun `shuffleAndPlay uses PlayerHolder player`() {
        PlayerHolder.player = mockPlayer
        val items = listOf(
            queueManager.buildMediaItem("t1", "T1", "http://ex.com/1"),
            queueManager.buildMediaItem("t2", "T2", "http://ex.com/2"),
            queueManager.buildMediaItem("t3", "T3", "http://ex.com/3"),
        )
        queueManager.shuffleAndPlay(items)
        verify { mockPlayer.setMediaItems(any()) }
        verify { mockPlayer.prepare() }
        verify { mockPlayer.play() }
    }

    @Test
    fun `addToQueue uses PlayerHolder player`() {
        PlayerHolder.player = mockPlayer
        val item = queueManager.buildMediaItem("new", "New", "http://ex.com/new")
        queueManager.addToQueue(item)
        verify { mockPlayer.addMediaItem(item) }
    }

    @Test
    fun `playAll on empty list does nothing`() {
        PlayerHolder.player = mockPlayer
        queueManager.playAll(emptyList())
        verify(exactly = 0) { mockPlayer.setMediaItems(any()) }
        verify(exactly = 0) { mockPlayer.play() }
    }

    @Test
    fun `playAll when player is null does nothing`() {
        PlayerHolder.player = null
        val items = listOf(queueManager.buildMediaItem("t1", "T", "http://ex.com/1"))
        queueManager.playAll(items)
        // No NPE thrown — test passes
    }

    @Test
    fun `shuffleAndPlay when player is null does nothing`() {
        PlayerHolder.player = null
        val items = listOf(queueManager.buildMediaItem("t1", "T", "http://ex.com/1"))
        queueManager.shuffleAndPlay(items)
        // No NPE thrown — test passes
    }
}
