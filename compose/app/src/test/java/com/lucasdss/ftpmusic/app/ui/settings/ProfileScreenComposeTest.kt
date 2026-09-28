package com.lucasdss.ftpmusic.app.ui.settings

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lucasdss.ftpmusic.app.data.db.TopCountRow
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
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
    fun `renders listening stats and period chips`() {
        val vm = mockk<ProfileViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(
            ProfileState(
                summary = ProfileSummary(
                    listeningMinutes = 90,
                    plays = 10,
                    songCount = 7,
                    artistCount = 3,
                    streakDays = 2,
                ),
                topTracks = listOf(TopCountRow("t1", "Hit", 4)),
                isLoading = false,
            ),
        )
        composeRule.setContent {
            ProfileScreen(onBack = {}, viewModel = vm)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("My Listening").assertIsDisplayed()
        composeRule.onNodeWithText("songs").assertIsDisplayed()
        composeRule.onNodeWithText("2-day streak").assertIsDisplayed()
        composeRule.onNodeWithText("Week").assertIsDisplayed()
        composeRule.onNodeWithText("Top Songs").assertIsDisplayed()
        composeRule.onNodeWithText("Hit").assertIsDisplayed()
        verify { vm.refresh() }
    }

    @Test
    fun `renders recently played track row`() {
        val track = TrackEntity(
            id = "t1",
            title = "Song A",
            artist = "Artist",
            durationSeconds = 120,
        )
        val vm = mockk<ProfileViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(
            ProfileState(recentlyPlayed = listOf(track), isLoading = false),
        )
        composeRule.setContent {
            ProfileScreen(onBack = {}, viewModel = vm)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Song A").assertExists()
        composeRule.onNodeWithTag("profile_recent_t1").assertExists()
    }

    @Test
    fun `period chip selects month`() {
        val flow = MutableStateFlow(ProfileState(isLoading = false))
        val vm = mockk<ProfileViewModel>(relaxed = true)
        every { vm.state } returns flow
        every { vm.setPeriod(any()) } answers {
            flow.value = flow.value.copy(period = firstArg())
        }
        composeRule.setContent {
            ProfileScreen(onBack = {}, viewModel = vm)
        }
        composeRule.onNodeWithText("Month").performClick()
        verify { vm.setPeriod(StatsPeriod.MONTH) }
    }
}
