package com.lucasdss.ftpmusic.app.ui.player

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.lucasdss.ftpmusic.app.playback.PlaybackState
import com.lucasdss.ftpmusic.app.playback.PlaybackViewModel
import com.lucasdss.ftpmusic.app.playback.PlayerHolder
import com.lucasdss.ftpmusic.app.playback.QueueRevisionTracker
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Compose UI tests for QueueScreen with a mocked PlaybackViewModel and Player. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h800dp")
class QueueScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        PlayerHolder.player = null
        PlayerHolder.exoPlayer = null
    }

    private fun mediaItem(id: String, title: String, artist: String) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).build())
        .build()

    private fun installPlayer(items: List<MediaItem>, currentIndex: Int = 0) {
        val player = mockk<Player>(relaxed = true)
        every { player.mediaItemCount } returns items.size
        every { player.currentMediaItemIndex } returns currentIndex
        items.forEachIndexed { i, item -> every { player.getMediaItemAt(i) } returns item }
        PlayerHolder.player = player
        PlayerHolder.exoPlayer = player
    }

    private fun mockViewModel(
        queueSize: Int,
        priorityQueueSize: Int = 0,
        isCasting: Boolean = false,
        castDeviceName: String? = null,
    ): PlaybackViewModel {
        val vm = mockk<PlaybackViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(
            PlaybackState(
                queueSize = queueSize,
                priorityQueueSize = priorityQueueSize,
                isCasting = isCasting,
                castDeviceName = castDeviceName,
            ),
        )
        every { vm.getTrackInfo(any()) } returns null
        return vm
    }

    private fun defaultQueue() = listOf(
        mediaItem("t1", "Track One", "Artist A"),
        mediaItem("t2", "Track Two", "Artist B"),
        mediaItem("t3", "Track Three", "Artist C"),
    )

    @Test
    fun `queue items rendered from player queue`() {
        installPlayer(defaultQueue())
        val vm = mockViewModel(queueSize = 3)
        composeRule.setContent {
            QueueScreen(onBack = {}, viewModel = vm)
        }
        composeRule.onNodeWithText("Track One").assertExists()
        composeRule.onNodeWithText("Track Two").assertExists()
        composeRule.onNodeWithText("Track Three").assertExists()
    }

    @Test
    fun `same-size queue replacement renders new items after recomposition`() {
        var items = listOf(mediaItem("old", "Old Track", "Artist A"))
        val player = mockk<Player>(relaxed = true)
        every { player.mediaItemCount } answers { items.size }
        every { player.currentMediaItemIndex } returns 0
        every { player.getMediaItemAt(any()) } answers { items[firstArg()] }
        PlayerHolder.player = player
        PlayerHolder.exoPlayer = player
        val state = MutableStateFlow(PlaybackState(queueSize = 1))
        val vm = mockk<PlaybackViewModel>(relaxed = true)
        every { vm.state } returns state
        every { vm.getTrackInfo(any()) } returns null

        composeRule.setContent {
            QueueScreen(onBack = {}, viewModel = vm)
        }
        composeRule.onNodeWithText("Old Track").assertExists()

        composeRule.runOnIdle {
            items = listOf(mediaItem("new", "New Track", "Artist B"))
            QueueRevisionTracker.notifyChanged()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("New Track").assertExists()
        composeRule.onNodeWithText("Old Track").assertDoesNotExist()
    }

    @Test
    fun `click item invokes playQueueItem with correct index`() {
        installPlayer(defaultQueue())
        val vm = mockViewModel(queueSize = 3)
        composeRule.setContent {
            QueueScreen(onBack = {}, viewModel = vm)
        }
        composeRule.onNodeWithText("Track Two").performClick()
        verify { vm.playQueueItem(1) }
    }

    @Test
    fun `swipe left removes item with correct index`() {
        installPlayer(defaultQueue())
        val vm = mockViewModel(queueSize = 3)
        composeRule.setContent {
            QueueScreen(onBack = {}, viewModel = vm)
        }
        composeRule.onNodeWithText("Track Three").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        verify { vm.removeFromQueue(2) }
    }

    @Test
    fun `clear priority queue invokes clearPriorityQueue`() {
        installPlayer(defaultQueue())
        val vm = mockViewModel(queueSize = 3, priorityQueueSize = 1)
        composeRule.setContent {
            QueueScreen(onBack = {}, viewModel = vm)
        }
        composeRule.onNodeWithText("Clear").performClick()
        verify { vm.clearPriorityQueue() }
    }

    @Test
    fun `empty queue shows placeholder`() {
        installPlayer(emptyList())
        val vm = mockViewModel(queueSize = 0)
        composeRule.setContent {
            QueueScreen(onBack = {}, viewModel = vm)
        }
        composeRule.onNodeWithText("Queue is empty").assertExists()
    }

    @Test
    fun `back button invokes onBack`() {
        installPlayer(emptyList())
        val vm = mockViewModel(queueSize = 0)
        var invoked = false
        composeRule.setContent {
            QueueScreen(onBack = { invoked = true }, viewModel = vm)
        }
        composeRule.onNodeWithContentDescription("Close").performClick()
        assertTrue(invoked)
    }

    @Test
    fun `casting subtitle shown when casting`() {
        installPlayer(defaultQueue())
        val vm = mockViewModel(queueSize = 3, isCasting = true, castDeviceName = "Living Room TV")
        composeRule.setContent {
            QueueScreen(onBack = {}, viewModel = vm)
        }
        composeRule.onNodeWithText("Casting to Living Room TV").assertExists()
    }
}
