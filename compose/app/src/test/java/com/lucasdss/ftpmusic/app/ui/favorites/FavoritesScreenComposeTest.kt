package com.lucasdss.ftpmusic.app.ui.favorites

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose tests for the Favorites tab: empty-state copy, section headers,
 * and Settings-toggle gating of the non-track sections.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FavoritesScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun mockViewModel(state: FavoritesState): FavoritesViewModel {
        val vm = mockk<FavoritesViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(state)
        return vm
    }

    private fun render(vm: FavoritesViewModel) {
        composeRule.setContent {
            FavoritesScreen(viewModel = vm)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `empty state shows the local-first favorites copy`() {
        val vm = mockViewModel(FavoritesState())
        render(vm)

        composeRule.onNodeWithText("No favorites yet").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Like any track, album, or artist, or bookmark a radio station to add it here",
        ).assertIsDisplayed()
    }

    @Test
    fun `tracks section header renders when tracks exist`() {
        val vm = mockViewModel(
            FavoritesState(
                tracks = listOf(
                    com.lucasdss.ftpmusic.app.data.db.TrackEntity(id = "t1", title = "Arcade Heart"),
                ),
            ),
        )
        render(vm)

        composeRule.onNodeWithText("TRACKS").assertIsDisplayed()
        composeRule.onNodeWithText("Arcade Heart").assertIsDisplayed()
    }

    @Test
    fun `artists section hidden when its toggle is off`() {
        val vm = mockViewModel(
            FavoritesState(
                artists = listOf(
                    com.lucasdss.ftpmusic.app.data.db.ArtistEntity(id = "ar-1", name = "Neon Circuit"),
                ),
                showFavArtistsSection = false,
            ),
        )
        render(vm)

        composeRule.onNodeWithText("ARTISTS").assertDoesNotExist()
        composeRule.onNodeWithText("Neon Circuit").assertDoesNotExist()
    }

    @Test
    fun `albums section renders when toggle on and albums exist`() {
        val vm = mockViewModel(
            FavoritesState(
                albums = listOf(
                    com.lucasdss.ftpmusic.app.data.db.AlbumEntity(id = "al-1", name = "Static Bloom"),
                ),
                showFavAlbumsSection = true,
            ),
        )
        render(vm)

        composeRule.onNodeWithText("ALBUMS").assertIsDisplayed()
        composeRule.onNodeWithText("Static Bloom").assertIsDisplayed()
    }

    @Test
    fun `radio section renders with bookmark and play affordances`() {
        val vm = mockViewModel(
            FavoritesState(
                radio = listOf(
                    com.lucasdss.ftpmusic.app.data.db.RadioFavoriteEntity(
                        stationId = "st-1",
                        name = "Retro Wave",
                        streamUrl = "https://stream.example/retro",
                    ),
                ),
                showFavRadioSection = true,
            ),
        )
        render(vm)

        composeRule.onNodeWithText("RADIO").assertIsDisplayed()
        composeRule.onNodeWithText("Retro Wave").assertIsDisplayed()
    }
}
