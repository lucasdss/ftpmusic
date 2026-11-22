package com.lucasdss.ftpmusic.app.ui.library

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Compose UI tests for MixDetailScreen sticky Play/Shuffle/⋮ chrome. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h800dp")
class MixDetailComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val tracks = (1..12).map { i ->
        GenreMixTrack(
            id = "t$i",
            title = "Mix Track $i",
            artist = "Artist $i",
            albumId = "al$i",
            duration = 100 + i,
            trackNumber = i,
            coverArt = null,
        )
    }

    private fun mockViewModel(): MixDetailViewModel {
        val vm = mockk<MixDetailViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(
            MixDetailViewModel.State(
                tracks = tracks,
                isLoading = false,
            ),
        )
        every { vm.toast } returns MutableStateFlow(null)
        every { vm.updateAvailable } returns MutableStateFlow(false)
        every { vm.showOverwriteModal } returns MutableStateFlow(false)
        every { vm.isDownloaded(any()) } returns false
        every { vm.mixName } returns MutableStateFlow("Focus Mix")
        return vm
    }

    @Test
    fun `mix title tracks and sticky actions rendered`() {
        composeRule.setContent {
            MixDetailScreen(
                mixId = 1L,
                mixName = "Focus Mix",
                onBack = {},
                viewModel = mockViewModel(),
            )
        }
        composeRule.onNodeWithText("Focus Mix").assertExists()
        composeRule.onNodeWithText("${tracks.size} tracks").assertExists()
        composeRule.onNodeWithText("Mix Track 1").assertExists()
        composeRule.onNodeWithTag("mix_detail_sticky_actions").assertIsDisplayed()
        composeRule.onNodeWithTag("mix_detail_play").assertExists()
        composeRule.onNodeWithTag("mix_detail_shuffle").assertExists()
        composeRule.onNodeWithTag("mix_detail_more").assertExists()
    }

    @Test
    fun `play shuffle more invoke view model`() {
        val vm = mockViewModel()
        composeRule.setContent {
            MixDetailScreen(
                mixId = 1L,
                mixName = "Focus Mix",
                onBack = {},
                viewModel = vm,
            )
        }
        composeRule.onNodeWithTag("mix_detail_play").performClick()
        verify { vm.playAll() }
        composeRule.onNodeWithTag("mix_detail_shuffle").performClick()
        verify { vm.shuffle() }
        composeRule.onNodeWithTag("mix_detail_more").performClick()
        composeRule.onNodeWithText("Refresh Mix").assertExists()
    }

    @Test
    fun `sticky actions remain displayed after swipe scroll`() {
        composeRule.setContent {
            MixDetailScreen(
                mixId = 1L,
                mixName = "Focus Mix",
                onBack = {},
                viewModel = mockViewModel(),
            )
        }
        composeRule.onNodeWithTag("mix_detail_sticky_actions").assertIsDisplayed()
        // Swipe the list so the cover leaves the viewport; sticky header must stay.
        composeRule.onNodeWithTag("mix_detail_cover_art").performTouchInput {
            repeat(4) { swipeUp() }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mix_detail_sticky_actions").assertIsDisplayed()
        composeRule.onNodeWithTag("mix_detail_play").assertIsDisplayed()
    }

    @Test
    fun `loadMix called with mix id`() {
        val vm = mockViewModel()
        composeRule.setContent {
            MixDetailScreen(
                mixId = 42L,
                mixName = "Focus Mix",
                onBack = {},
                viewModel = vm,
            )
        }
        verify { vm.loadMix(42L) }
    }

    @Test
    fun `click track plays at index`() {
        val vm = mockViewModel()
        composeRule.setContent {
            MixDetailScreen(
                mixId = 1L,
                mixName = "Focus Mix",
                onBack = {},
                viewModel = vm,
            )
        }
        composeRule.onNodeWithText("Mix Track 2").performClick()
        verify { vm.playTrack(1) }
    }
}
