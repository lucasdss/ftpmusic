package com.lucasdss.ftpmusic.app.ui.settings

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.ui.library.HomeStats
import com.lucasdss.ftpmusic.app.ui.library.LibraryState
import com.lucasdss.ftpmusic.app.ui.library.LibraryViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProfileScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders listening stats and tracks label`() {
        val vm = mockk<LibraryViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(
            LibraryState(
                stats = HomeStats(totalPlays = 10, listeningMinutes = 90, artistCount = 3, trackCount = 7),
                isLoading = false,
            ),
        )
        composeRule.setContent {
            ProfileScreen(onBack = {}, viewModel = vm)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("My Listening").assertIsDisplayed()
        composeRule.onNodeWithText("tracks").assertIsDisplayed()
        composeRule.onNodeWithText("streak").assertDoesNotExist()
        verify { vm.loadStats() }
        verify { vm.refreshRecentlyPlayed() }
    }

    @Test
    fun `track click invokes callback`() {
        val track = TrackEntity(
            id = "t1",
            title = "Song A",
            artist = "Artist",
            durationSeconds = 120,
        )
        val vm = mockk<LibraryViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(
            LibraryState(recentlyPlayed = listOf(track), isLoading = false),
        )
        var clicked: TrackEntity? = null
        composeRule.setContent {
            ProfileScreen(onBack = {}, onTrackClick = { clicked = it }, viewModel = vm)
        }
        composeRule.onNodeWithText("Song A").performClick()
        assertEquals("t1", clicked?.id)
    }
}
