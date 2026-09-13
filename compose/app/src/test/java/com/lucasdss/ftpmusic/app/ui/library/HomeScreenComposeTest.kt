package com.lucasdss.ftpmusic.app.ui.library

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Compose UI tests for HomeScreen with a mocked LibraryViewModel. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h800dp")
class HomeScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun mockViewModel(state: LibraryState): LibraryViewModel {
        val vm = mockk<LibraryViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(state)
        every { vm.showOverwriteModal } returns MutableStateFlow(false)
        return vm
    }

    @Test
    fun `daily mix cards rendered when mixes exist`() {
        val vm = mockViewModel(
            LibraryState(
                mixCards = listOf(
                    MixCard(id = 1L, name = "Rock Mix", coverArts = emptyList(), songCount = 42),
                    MixCard(id = 2L, name = "Jazz Mix", coverArts = emptyList(), songCount = 17),
                ),
            ),
        )
        composeRule.setContent {
            HomeScreen(viewModel = vm)
        }
        composeRule.onNodeWithText("Daily Mixes").assertExists()
        composeRule.onNodeWithText("Rock Mix").assertExists()
        composeRule.onNodeWithText("Jazz Mix").assertExists()
        composeRule.onNodeWithText("42 songs").assertDoesNotExist()
        composeRule.onNodeWithText("17 songs").assertDoesNotExist()
    }

    @Test
    fun `click mix card invokes onMixClick with mix id`() {
        val vm = mockViewModel(
            LibraryState(
                mixCards = listOf(MixCard(id = 7L, name = "Rock Mix", coverArts = emptyList(), songCount = 42)),
            ),
        )
        var clickedMixId: Long? = null
        composeRule.setContent {
            HomeScreen(viewModel = vm, onMixClick = { clickedMixId = it })
        }
        composeRule.onNodeWithText("Rock Mix").performClick()
        assertEquals(7L, clickedMixId)
    }

    @Test
    fun `placeholder shown when no mixes exist`() {
        val vm = mockViewModel(LibraryState(mixCards = emptyList(), isResyncing = false))
        composeRule.setContent {
            HomeScreen(viewModel = vm)
        }
        composeRule.onNodeWithText("Daily Mixes will appear after sync completes").assertExists()
    }

    @Test
    fun `building message shown while resyncing`() {
        val vm = mockViewModel(LibraryState(mixCards = emptyList(), isResyncing = true))
        composeRule.setContent {
            HomeScreen(viewModel = vm)
        }
        composeRule.onNodeWithText("Building your Daily Mixes…").assertExists()
    }

    @Test
    fun `building message shown while lazy generation runs`() {
        val vm = mockViewModel(LibraryState(mixCards = emptyList(), isGeneratingMixes = true))
        composeRule.setContent {
            HomeScreen(viewModel = vm)
        }
        composeRule.onNodeWithText("Building your Daily Mixes…").assertExists()
        composeRule.onNodeWithText("Daily Mixes will appear after sync completes").assertDoesNotExist()
    }

    @Test
    fun `genre chips rendered and clickable`() {
        val vm = mockViewModel(LibraryState(genres = listOf("Metal", "Blues")))
        var clickedGenre: String? = null
        composeRule.setContent {
            HomeScreen(viewModel = vm, onGenreClick = { clickedGenre = it })
        }
        composeRule.onNodeWithText("Tuned In").assertExists()
        composeRule.onNodeWithText("Metal").assertExists()
        composeRule.onNodeWithText("Blues").performClick()
        assertEquals("Blues", clickedGenre)
    }

    // ── v43: Home favorite rows (toggle-gated) ────────────────────────────

    @Test
    fun `playlists row rendered for synced playlists when toggle on`() {
        val vm = mockViewModel(
            LibraryState(
                playlists = listOf(
                    PlaylistView(id = "pl-1", name = "Road Trip", trackCount = 3, isSynced = true),
                    PlaylistView(id = "pl-2", name = "Draft", trackCount = 2, isSynced = false),
                ),
                playlistMontages = mapOf("pl-1" to listOf("ca-1")),
                showPlaylistsOnHome = true,
            ),
        )
        composeRule.setContent { HomeScreen(viewModel = vm) }
        composeRule.onNodeWithText("Playlists").assertExists()
        composeRule.onNodeWithText("Road Trip").assertExists()
        composeRule.onNodeWithText("3 tracks").assertExists()
        // Unsynced draft must NOT appear on Home
        composeRule.onNodeWithText("Draft").assertDoesNotExist()
    }

    @Test
    fun `playlists row hidden when toggle off even with synced playlists`() {
        val vm = mockViewModel(
            LibraryState(
                playlists = listOf(PlaylistView(id = "pl-1", name = "Road Trip", trackCount = 3, isSynced = true)),
                showPlaylistsOnHome = false,
            ),
        )
        composeRule.setContent { HomeScreen(viewModel = vm) }
        composeRule.onNodeWithText("Playlists").assertDoesNotExist()
        composeRule.onNodeWithText("Road Trip").assertDoesNotExist()
    }

    @Test
    fun `playlists row hidden when no synced playlists even with toggle on`() {
        val vm = mockViewModel(
            LibraryState(
                playlists = listOf(PlaylistView(id = "pl-2", name = "Draft", trackCount = 2, isSynced = false)),
                showPlaylistsOnHome = true,
            ),
        )
        composeRule.setContent { HomeScreen(viewModel = vm) }
        composeRule.onNodeWithText("Playlists").assertDoesNotExist()
    }

    @Test
    fun `see all invokes onPlaylistsClick`() {
        val vm = mockViewModel(
            LibraryState(
                playlists = listOf(PlaylistView(id = "pl-1", name = "Road Trip", trackCount = 3, isSynced = true)),
                showPlaylistsOnHome = true,
            ),
        )
        var clicked = false
        composeRule.setContent { HomeScreen(viewModel = vm, onPlaylistsClick = { clicked = true }) }
        composeRule.onNodeWithText("See all").performClick()
        assertTrue(clicked)
    }

    @Test
    fun `tapping a playlist card opens that playlist not see all`() {
        val vm = mockViewModel(
            LibraryState(
                playlists = listOf(
                    PlaylistView(id = "pl-1", name = "Road Trip", trackCount = 3, isSynced = true),
                    PlaylistView(id = "pl-2", name = "Gym Mix", trackCount = 7, isSynced = true),
                ),
                showPlaylistsOnHome = true,
            ),
        )
        var openedPlaylist: String? = null
        var seeAllClicked = false
        composeRule.setContent {
            HomeScreen(
                viewModel = vm,
                onPlaylistsClick = { seeAllClicked = true },
                onPlaylistClick = { openedPlaylist = it },
            )
        }
        composeRule.onNodeWithText("Road Trip").performClick()
        assertEquals("pl-1", openedPlaylist)
        assertFalse(seeAllClicked)

        composeRule.onNodeWithText("Gym Mix").performClick()
        assertEquals("pl-2", openedPlaylist)
        assertFalse(seeAllClicked)
    }

    @Test
    fun `favorite artists row rendered when toggle on and artists exist`() {
        val vm = mockViewModel(
            LibraryState(
                starredArtists = listOf(
                    com.lucasdss.ftpmusic.app.data.db.ArtistEntity(id = "ar-1", name = "Neon Circuit"),
                ),
                showFavArtistsSection = true,
            ),
        )
        composeRule.setContent { HomeScreen(viewModel = vm) }
        composeRule.onNodeWithText("Favorite Artists").assertExists()
        composeRule.onNodeWithText("Neon Circuit").assertExists()
    }

    @Test
    fun `tapping a favorite artist opens the artist page`() {
        val vm = mockViewModel(
            LibraryState(
                starredArtists = listOf(
                    com.lucasdss.ftpmusic.app.data.db.ArtistEntity(id = "ar-1", name = "Neon Circuit"),
                    com.lucasdss.ftpmusic.app.data.db.ArtistEntity(id = "ar-2", name = "Sonic Bloom"),
                ),
                showFavArtistsSection = true,
            ),
        )
        var openedArtist: String? = null
        composeRule.setContent { HomeScreen(viewModel = vm, onArtistClick = { openedArtist = it }) }
        composeRule.onNodeWithText("Neon Circuit").performClick()
        assertEquals("ar-1", openedArtist)
        composeRule.onNodeWithText("Sonic Bloom").performClick()
        assertEquals("ar-2", openedArtist)
    }

    @Test
    fun `favorite artists row hidden when toggle off`() {
        val vm = mockViewModel(
            LibraryState(
                starredArtists = listOf(
                    com.lucasdss.ftpmusic.app.data.db.ArtistEntity(id = "ar-1", name = "Neon Circuit"),
                ),
                showFavArtistsSection = false,
            ),
        )
        composeRule.setContent { HomeScreen(viewModel = vm) }
        composeRule.onNodeWithText("Favorite Artists").assertDoesNotExist()
    }

    @Test
    fun `favorite albums row rendered when toggle on and albums exist`() {
        val vm = mockViewModel(
            LibraryState(
                starredAlbums = listOf(
                    com.lucasdss.ftpmusic.app.data.db.AlbumEntity(
                        id = "al-1",
                        name = "Static Bloom",
                        artist = "Neon Circuit",
                    ),
                ),
                showFavAlbumsSection = true,
            ),
        )
        composeRule.setContent { HomeScreen(viewModel = vm) }
        composeRule.onNodeWithText("Favorite Albums").assertExists()
        composeRule.onNodeWithText("Static Bloom").assertExists()
    }

    @Test
    fun `favorite albums row hidden when empty even with toggle on`() {
        val vm = mockViewModel(LibraryState(starredAlbums = emptyList(), showFavAlbumsSection = true))
        composeRule.setContent { HomeScreen(viewModel = vm) }
        composeRule.onNodeWithText("Favorite Albums").assertDoesNotExist()
    }

    @Test
    fun `favorite radio row rendered when toggle on and stations bookmarked`() {
        val vm = mockViewModel(
            LibraryState(
                bookmarkedRadio = listOf(
                    com.lucasdss.ftpmusic.app.data.db.RadioFavoriteEntity(
                        stationId = "st-1",
                        name = "Retro Wave",
                        streamUrl = "https://s",
                    ),
                ),
                showFavRadioSection = true,
            ),
        )
        composeRule.setContent { HomeScreen(viewModel = vm) }
        composeRule.onNodeWithText("Favorite Radio").assertExists()
        composeRule.onNodeWithText("Retro Wave").assertExists()
        composeRule.onNodeWithText("Live").assertExists()
    }

    @Test
    fun `favorite radio row hidden when toggle off`() {
        val vm = mockViewModel(
            LibraryState(
                bookmarkedRadio = listOf(
                    com.lucasdss.ftpmusic.app.data.db.RadioFavoriteEntity(
                        stationId = "st-1",
                        name = "Retro Wave",
                        streamUrl = "https://s",
                    ),
                ),
                showFavRadioSection = false,
            ),
        )
        composeRule.setContent { HomeScreen(viewModel = vm) }
        composeRule.onNodeWithText("Favorite Radio").assertDoesNotExist()
    }
}
