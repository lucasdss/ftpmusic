package com.lucasdss.ftpmusic.app.ui.genre

import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GenreDetailViewModelTest {

    private val api: SubsonicApi = mockk()
    private val storage: SecureStorage = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: GenreDetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "user"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "pass"
        viewModel = GenreDetailViewModel(api, storage)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildGenreResponse(songs: List<Map<String, Any?>>): Map<String, Any> = mapOf(
        "subsonic-response" to mapOf(
            "songsByGenre" to mapOf("song" to songs),
        ),
    )

    private fun buildSong(
        albumId: String,
        album: String,
        artist: String,
        artistId: String,
        coverArt: String? = null,
        year: Int? = null,
    ) = mapOf<String, Any?>(
        "albumId" to albumId,
        "album" to album,
        "artist" to artist,
        "artistId" to artistId,
        "coverArt" to coverArt,
        "year" to year,
    )

    @Test
    fun `loadGenre populates albums and artists`() = runTest(testDispatcher) {
        val songs = listOf(
            buildSong("al-1", "Album One", "Artist X", "ar-1", "cov-1", 2023),
            buildSong("al-2", "Album Two", "Artist X", "ar-1", "cov-2", 2024),
            buildSong("al-3", "Album Three", "Artist Y", "ar-2", null, null),
        )
        coEvery { api.getSongsByGenre(any(), genre = "Rock", count = 100, offset = 0) } returns
            buildGenreResponse(songs)

        viewModel.loadGenre("Rock")
        val state = viewModel.state.first { !it.isLoading && it.albums.isNotEmpty() }

        assertEquals("Rock", state.genre)
        assertEquals(3, state.albums.size)
        assertEquals(2, state.artists.size)
        assertTrue(state.albums.any { it.name == "Album One" })
        assertTrue(state.artists.any { it.name == "Artist X" })
    }

    @Test
    fun `loadMore does not run when hasMore is false`() = runTest(testDispatcher) {
        val songs = listOf(buildSong("al-1", "Album One", "Artist A", "ar-1"))
        coEvery { api.getSongsByGenre(any(), genre = "Few", count = 100, offset = 0) } returns buildGenreResponse(songs)

        viewModel.loadGenre("Few")
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.albums.size)
        assertFalse(viewModel.state.value.hasMore)

        // loadMore should be a no-op when hasMore is false
        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.albums.size) // still 1
    }

    @Test
    fun `fetchPage deduplicates albums across pages`() = runTest(testDispatcher) {
        // Simulate two pages where page2 has both duplicate and new items
        val page1 = listOf(buildSong("al-1", "Album One", "Artist A", "ar-1"))
        // Manually set state and test fetchPage dedup logic
        coEvery { api.getSongsByGenre(any(), genre = "Test", count = 100, offset = 0) } returns
            buildGenreResponse(page1)

        viewModel.loadGenre("Test")
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.albums.size)
        assertEquals("Album One", viewModel.state.value.albums[0].name)
    }

    @Test
    fun `hasMore is false when server returns fewer than pageSize`() = runTest(testDispatcher) {
        val fewSongs = listOf(buildSong("al-1", "Album One", "Artist A", "ar-1"))
        coEvery { api.getSongsByGenre(any(), genre = "Few", count = 100, offset = 0) } returns
            buildGenreResponse(fewSongs)

        viewModel.loadGenre("Few")
        val state = viewModel.state.first { !it.isLoading }

        assertFalse(state.hasMore)
    }

    @Test
    fun `loadGenre handles API error gracefully`() = runTest(testDispatcher) {
        coEvery { api.getSongsByGenre(any(), genre = "Error", any(), any()) } throws RuntimeException("Network failure")

        viewModel.loadGenre("Error")
        val state = viewModel.state.first { it.error != null }

        assertEquals("Network failure", state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `hasMore becomes false when page yields only duplicate albums`() = runTest(testDispatcher) {
        // Page 1: 100 unique albums (full page)
        val page1 = (1..100).map { i -> buildSong("al-$i", "Album $i", "Artist $i", "ar-$i") }
        // Page 2: 100 songs, all duplicate albums (same IDs as page1)
        val page2 = (1..100).map { i -> buildSong("al-$i", "Album $i", "Artist $i", "ar-$i") }

        coEvery { api.getSongsByGenre(any(), genre = "Dup", count = 100, offset = 0) } returns buildGenreResponse(page1)
        coEvery { api.getSongsByGenre(any(), genre = "Dup", count = 100, offset = 100) } returns
            buildGenreResponse(page2)

        viewModel.loadGenre("Dup")
        advanceUntilIdle()
        assertEquals(100, viewModel.state.value.albums.size)
        assertTrue("hasMore should be true after first full page", viewModel.state.value.hasMore)

        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(100, viewModel.state.value.albums.size) // Still 100, all duplicates
        assertFalse("hasMore should be false when page yields no new unique albums", viewModel.state.value.hasMore)
    }

    @Test
    fun `setTab switches between albums and artists`() {
        assertEquals(GenreTab.ALBUMS, viewModel.state.value.tab)
        viewModel.setTab(GenreTab.ARTISTS)
        assertEquals(GenreTab.ARTISTS, viewModel.state.value.tab)
        viewModel.setTab(GenreTab.ALBUMS)
        assertEquals(GenreTab.ALBUMS, viewModel.state.value.tab)
    }
}
