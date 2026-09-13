package com.lucasdss.ftpmusic.app.ui.album

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lucasdss.ftpmusic.app.data.model.Album
import com.lucasdss.ftpmusic.app.data.model.Track
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Compose UI tests for AlbumDetailScreen with a mocked AlbumDetailViewModel. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h800dp")
class AlbumDetailComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val tracks = listOf(
        Track(id = "t1", title = "First Song", duration = 120),
        Track(id = "t2", title = "Second Song", duration = 200),
    )

    private fun mockViewModel(
        albumStatus: String = "none",
        downloadedIds: Set<String> = emptySet(),
        cachedIds: Set<String> = emptySet(),
    ): AlbumDetailViewModel {
        val vm = mockk<AlbumDetailViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(
            AlbumDetailState(
                album = Album(id = "a1", name = "Greatest Hits", year = 2020),
                tracks = tracks,
                isLoading = false,
            ),
        )
        every { vm.isCached(any()) } answers { firstArg<String>() in cachedIds }
        every { vm.isDownloaded(any()) } answers { firstArg<String>() in downloadedIds }
        every { vm.isQueued(any()) } returns false
        every { vm.isTrackLiked(any()) } returns false
        every { vm.getTrackRating(any()) } returns 0
        every { vm.getAlbumDownloadStatus() } returns albumStatus
        every { vm.buildCoverArtUrl(any()) } returns ""
        return vm
    }

    @Test
    fun `album title and track list rendered`() {
        composeRule.setContent {
            AlbumDetailScreen(albumId = "a1", viewModel = mockViewModel())
        }
        composeRule.onNodeWithText("Greatest Hits").assertExists()
        composeRule.onNodeWithText("2 tracks").assertExists()
        composeRule.onNodeWithText("First Song").assertExists()
        composeRule.onNodeWithText("Second Song").assertExists()
    }

    @Test
    fun `click track plays track at index`() {
        val vm = mockViewModel()
        composeRule.setContent {
            AlbumDetailScreen(albumId = "a1", viewModel = vm)
        }
        composeRule.onNodeWithText("Second Song").performClick()
        verify { vm.playTrack(1) }
    }

    @Test
    fun `play button invokes playAll`() {
        val vm = mockViewModel()
        composeRule.setContent {
            AlbumDetailScreen(albumId = "a1", viewModel = vm)
        }
        composeRule.onNodeWithText("Play").performClick()
        verify { vm.playAll() }
    }

    @Test
    fun `shuffle button invokes shuffle`() {
        val vm = mockViewModel()
        composeRule.setContent {
            AlbumDetailScreen(albumId = "a1", viewModel = vm)
        }
        composeRule.onNodeWithText("Shuffle").performClick()
        verify { vm.shuffle() }
    }

    @Test
    fun `download badge visible for downloaded track`() {
        composeRule.setContent {
            AlbumDetailScreen(albumId = "a1", viewModel = mockViewModel(downloadedIds = setOf("t1")))
        }
        composeRule.onNodeWithContentDescription("downloaded").assertExists()
    }

    @Test
    fun `cached badge visible for cached track`() {
        composeRule.setContent {
            AlbumDetailScreen(albumId = "a1", viewModel = mockViewModel(cachedIds = setOf("t2")))
        }
        composeRule.onNodeWithContentDescription("cached").assertExists()
    }

    @Test
    fun `album download badge shown when fully downloaded`() {
        composeRule.setContent {
            AlbumDetailScreen(albumId = "a1", viewModel = mockViewModel(albumStatus = "downloaded"))
        }
        composeRule.onNodeWithText("Downloaded").assertExists()
    }

    @Test
    fun `back button invokes onBack`() {
        var invoked = false
        composeRule.setContent {
            AlbumDetailScreen(albumId = "a1", viewModel = mockViewModel(), onBack = { invoked = true })
        }
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(invoked)
    }

    @Test
    fun `loadAlbum called with album id`() {
        val vm = mockViewModel()
        composeRule.setContent {
            AlbumDetailScreen(albumId = "a1", viewModel = vm)
        }
        verify { vm.loadAlbum("a1") }
    }
}
