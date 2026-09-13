package com.lucasdss.ftpmusic.app.ui.settings

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity
import com.lucasdss.ftpmusic.app.data.db.CachedGenreEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.repository.CustomMix
import com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository
import com.lucasdss.ftpmusic.app.data.repository.MixFilters
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose tests for the Custom Daily Mixes list + composite editor (v48). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h800dp")
class CustomDailyMixesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val repository: DailyMixRepository = mockk(relaxed = true)
    private val metadataDao: CachedMetadataDao = mockk(relaxed = true)

    private fun mix(
        id: Long = 1L,
        name: String = "Rock Mix",
        filters: MixFilters = MixFilters(genres = listOf("Rock")),
    ) = CustomMix(id = id, name = name, filters = filters, autoCache = false, isDefault = true)

    private fun stubDefaults() {
        coEvery { repository.allGenres() } returns emptyList()
        coEvery { metadataDao.getAllStarredArtists() } returns emptyList()
        coEvery { metadataDao.searchArtistsPaged(any(), any(), any()) } returns emptyList()
        coEvery { repository.missingSourceGenres() } returns emptyMap()
    }

    private fun render() {
        composeRule.setContent {
            CustomDailyMixesScreen(
                onBack = {},
                viewModel = CustomDailyMixesViewModel(repository, metadataDao),
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `list renders composite mixes and badges`() {
        stubDefaults()
        coEvery { repository.getAll() } returns listOf(
            mix(1L, "Rock Mix"),
            mix(
                2L,
                "Mixed",
                MixFilters(genres = listOf("Rock"), decades = listOf("90s"), artistIds = listOf("ar1")),
            ),
        )

        render()

        composeRule.onNodeWithText("Custom Daily Mixes").assertIsDisplayed()
        composeRule.onNodeWithText("2 / 20 configured").assertIsDisplayed()
        composeRule.onNodeWithText("Rock Mix").assertIsDisplayed()
        composeRule.onNodeWithText("Mixed").assertIsDisplayed()
    }

    @Test
    fun `empty state prompts the user to add a mix`() {
        stubDefaults()
        coEvery { repository.getAll() } returns emptyList()

        render()

        composeRule.onNodeWithText("No mixes yet").assertIsDisplayed()
    }

    @Test
    fun `capacity notice shown at twenty mixes`() {
        stubDefaults()
        coEvery { repository.getAll() } returns (1..20).map { mix(id = it.toLong(), name = "Mix $it") }

        render()

        composeRule.onNodeWithText("Limit reached (20). Delete a mix to add another.").assertIsDisplayed()
    }

    @Test
    fun `add opens the editor and tabs accumulate selections`() {
        stubDefaults()
        coEvery { repository.getAll() } returns emptyList()
        coEvery { repository.allGenres() } returns listOf(CachedGenreEntity(name = "Rock", songCount = 10))

        render()
        composeRule.onNodeWithTag("custom_mixes_add").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("New Mix").assertIsDisplayed()
        composeRule.onNodeWithText("SOURCE (combine freely)").assertIsDisplayed()
        composeRule.onNodeWithTag("genre_chip_Rock").performClick()
        composeRule.onNodeWithTag("source_tab_decades").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("decade_chip_90s").performClick()
        composeRule.waitForIdle()

        // Cumulative: both dimensions kept, tab labels show counts.
        composeRule.onNodeWithText("Genre (1)").assertIsDisplayed()
        composeRule.onNodeWithText("Decade (1)").assertIsDisplayed()
    }

    @Test
    fun `genre load more reveals the next page`() {
        stubDefaults()
        coEvery { repository.getAll() } returns emptyList()
        coEvery { repository.allGenres() } returns (1..30).map { CachedGenreEntity(name = "G$it", songCount = 30 - it) }

        render()
        composeRule.onNodeWithTag("custom_mixes_add").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("genre_chip_G20").assertExists()
        composeRule.onNodeWithTag("genre_chip_G21").assertDoesNotExist()
        composeRule.onNodeWithTag("genre_load_more").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("genre_chip_G21").assertExists()
    }

    @Test
    fun `artist tab offers search and the favorites toggle`() {
        stubDefaults()
        coEvery { repository.getAll() } returns emptyList()

        render()
        composeRule.onNodeWithTag("custom_mixes_add").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("source_tab_favoriteArtists").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("artist_search").assertExists()
        composeRule.onNodeWithTag("artist_favorites_toggle").assertExists()
    }

    @Test
    fun `artist search results can be added`() {
        stubDefaults()
        coEvery { repository.getAll() } returns emptyList()
        coEvery { metadataDao.searchArtistsPaged(any(), any(), any()) } returns listOf(
            CachedArtistEntity(id = "ar1", name = "Artist One"),
        )

        render()
        composeRule.onNodeWithTag("custom_mixes_add").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("source_tab_favoriteArtists").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("artist_search").performClick()
        composeRule.onNodeWithTag("artist_search").performTextInput("art")
        // Flush the 250ms debounce on the main looper, then let Compose recompose.
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(400))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Artist One").assertExists()
    }

    @Test
    fun `missing source genres surface as a row warning`() {
        stubDefaults()
        coEvery { repository.getAll() } returns listOf(mix(1L, "Rock Mix"))
        coEvery { repository.missingSourceGenres() } returns mapOf(1L to listOf("Gone"))

        render()

        composeRule.onNodeWithText("Missing: Gone").assertExists()
    }

    @Test
    fun `missing selected genre renders a removable chip`() {
        stubDefaults()
        coEvery { repository.getAll() } returns listOf(mix(1L, "Rock Mix", MixFilters(genres = listOf("Gone"))))
        coEvery { repository.allGenres() } returns listOf(CachedGenreEntity(name = "Rock", songCount = 10))

        render()
        composeRule.onNodeWithTag("custom_mix_edit_1").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("genre_chip_Gone").assertExists()
        composeRule.onNodeWithTag("genre_chip_Gone").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("genre_chip_Gone").assertDoesNotExist()
    }
}
