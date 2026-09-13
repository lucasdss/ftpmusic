package com.lucasdss.ftpmusic.app.ui.settings

import com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity
import com.lucasdss.ftpmusic.app.data.db.CachedGenreEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.repository.CustomMix
import com.lucasdss.ftpmusic.app.data.repository.DailyMixRepository
import com.lucasdss.ftpmusic.app.data.repository.MixFilters
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomDailyMixesViewModelTest {

    private val repository: DailyMixRepository = mockk(relaxed = true)
    private val metadataDao: CachedMetadataDao = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.getAll() } returns emptyList()
        coEvery { repository.allGenres() } returns emptyList()
        coEvery { metadataDao.getAllStarredArtists() } returns emptyList()
        coEvery { repository.missingSourceGenres() } returns emptyMap()
        coEvery { repository.addMix(any(), any(), any()) } returns 1L
        coEvery { metadataDao.searchArtistsPaged(any(), any(), any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun mix(
        id: Long = 1L,
        name: String = "Rock Mix",
        filters: MixFilters = MixFilters(genres = listOf("Rock")),
        autoCache: Boolean = false,
    ) = CustomMix(id = id, name = name, filters = filters, autoCache = autoCache, isDefault = true)

    private fun vm() = CustomDailyMixesViewModel(repository, metadataDao)

    @Test
    fun `load populates mixes genres and liked artists`() = runTest(testDispatcher) {
        coEvery { repository.getAll() } returns listOf(mix())
        coEvery { repository.allGenres() } returns listOf(CachedGenreEntity(name = "Rock", songCount = 10))
        coEvery { metadataDao.getAllStarredArtists() } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.ArtistEntity(id = "ar1", name = "Artist One"),
        )
        coEvery { repository.missingSourceGenres() } returns mapOf(1L to listOf("Gone"))

        val model = vm()
        advanceUntilIdle()

        val state = model.state.value
        assertEquals(1, state.mixes.size)
        assertEquals(listOf("Rock"), state.genres.map { it.name })
        assertEquals(listOf("Artist One"), state.likedArtists)
        assertEquals(setOf("ar1"), state.likedArtistIds)
        assertEquals(mapOf(1L to listOf("Gone")), state.missingGenres)
        assertFalse(state.loading)
    }

    @Test
    fun `openNew is ignored at capacity`() = runTest(testDispatcher) {
        coEvery { repository.getAll() } returns (1..DailyMixRepository.MAX_MIXES).map { mix(id = it.toLong()) }
        val model = vm()
        advanceUntilIdle()

        model.openNew()

        assertNull(model.state.value.editor)
        assertTrue(model.state.value.atCapacity)
    }

    @Test
    fun `openEditor maps composite filters and resolves artist names`() = runTest(testDispatcher) {
        coEvery { metadataDao.getArtistsByIds(listOf("ar1")) } returns
            listOf(CachedArtistEntity(id = "ar1", name = "Artist One"))
        val model = vm()
        advanceUntilIdle()

        model.openEditor(
            mix(
                filters = MixFilters(
                    genres = listOf("Rock", "Jazz"),
                    decades = listOf("90s"),
                    artistIds = listOf("ar1"),
                    includeFavoriteArtists = true,
                ),
                autoCache = true,
            ),
        )
        advanceUntilIdle()

        val editor = model.state.value.editor!!
        assertEquals(setOf("Rock", "Jazz"), editor.genres)
        assertEquals(setOf("90s"), editor.decades)
        assertEquals(setOf("ar1"), editor.artistIds)
        assertTrue(editor.includeFavoriteArtists)
        assertTrue(editor.autoCache)
        assertEquals("Artist One", model.state.value.selectedArtistNames["ar1"])
    }

    @Test
    fun `setName caps at 32 characters`() = runTest(testDispatcher) {
        val model = vm()
        advanceUntilIdle()
        model.openNew()

        model.setName("x".repeat(50))

        assertEquals(32, model.state.value.editor!!.name.length)
    }

    @Test
    fun `switching tabs keeps every dimension (cumulative)`() = runTest(testDispatcher) {
        val model = vm()
        advanceUntilIdle()
        model.openNew()
        model.toggleGenre("Rock")
        model.setActiveTab(DailyMixRepository.KIND_DECADES)
        model.toggleDecade("90s")
        model.setActiveTab(DailyMixRepository.KIND_FAVORITE_ARTISTS)

        val editor = model.state.value.editor!!
        assertEquals(DailyMixRepository.KIND_FAVORITE_ARTISTS, editor.activeTab)
        assertEquals(setOf("Rock"), editor.genres)
        assertEquals(setOf("90s"), editor.decades)
    }

    @Test
    fun `toggleGenre has no cap`() = runTest(testDispatcher) {
        val model = vm()
        advanceUntilIdle()
        model.openNew()
        (1..12).forEach { model.toggleGenre("G$it") }

        assertEquals(12, model.state.value.editor!!.genres.size)
    }

    @Test
    fun `loadMoreGenres grows the visible window by 20`() = runTest(testDispatcher) {
        coEvery { repository.allGenres() } returns (1..50).map { CachedGenreEntity(name = "G$it", songCount = 50 - it) }
        val model = vm()
        advanceUntilIdle()
        model.openNew()

        assertEquals(20, model.visibleGenres(model.state.value.editor!!).size)
        model.loadMoreGenres()
        assertEquals(40, model.visibleGenres(model.state.value.editor!!).size)
        model.loadMoreGenres()
        assertEquals(50, model.visibleGenres(model.state.value.editor!!).size)
        assertFalse(model.hasMoreGenres(model.state.value.editor!!))
    }

    @Test
    fun `visibleGenres pins selected genres beyond the window`() = runTest(testDispatcher) {
        coEvery { repository.allGenres() } returns (1..30).map { CachedGenreEntity(name = "G$it", songCount = 30 - it) }
        val model = vm()
        advanceUntilIdle()
        model.openNew()
        model.toggleGenre("G30")

        val visible = model.visibleGenres(model.state.value.editor!!)

        assertEquals("G30", visible.first().name)
    }

    @Test
    fun `artist search debounces and toggling keeps names`() = runTest(testDispatcher) {
        coEvery { metadataDao.searchArtistsPaged("art", 50, 0) } returns listOf(
            CachedArtistEntity(id = "ar1", name = "Artist One"),
        )
        coEvery { metadataDao.getAllStarredArtists() } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.ArtistEntity(id = "ar1", name = "Artist One"),
        )
        val model = vm()
        advanceUntilIdle()
        model.openNew()

        model.setArtistQuery("art")
        advanceTimeBy(CustomDailyMixesViewModel.ARTIST_SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()

        val result = model.state.value.editor!!.artistResults.single()
        assertTrue(result.isFavorite)
        model.toggleArtist(result)
        assertEquals(setOf("ar1"), model.state.value.editor!!.artistIds)
        assertEquals("Artist One", model.state.value.selectedArtistNames["ar1"])

        model.toggleArtist(result)
        assertTrue(model.state.value.editor!!.artistIds.isEmpty())
    }

    @Test
    fun `loadMoreArtists appends the next page`() = runTest(testDispatcher) {
        val first = (1..50).map { CachedArtistEntity(id = "a$it", name = "A$it") }
        coEvery { metadataDao.searchArtistsPaged("", 50, 0) } returns first
        coEvery { metadataDao.searchArtistsPaged("", 50, 50) } returns listOf(
            CachedArtistEntity(id = "a51", name = "A51"),
        )
        val model = vm()
        advanceUntilIdle()
        model.openNew()

        model.setArtistQuery("")
        advanceTimeBy(CustomDailyMixesViewModel.ARTIST_SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()
        model.loadMoreArtists()
        advanceUntilIdle()

        assertEquals(51, model.state.value.editor!!.artistResults.size)
        assertTrue(model.state.value.editor!!.artistResultsExhausted)
    }

    @Test
    fun `canSave requires a name and at least one dimension`() = runTest(testDispatcher) {
        val model = vm()
        advanceUntilIdle()
        model.openNew()

        assertFalse(model.canSave(model.state.value.editor!!))
        model.setName("Mix")
        assertFalse(model.canSave(model.state.value.editor!!))
        model.toggleGenre("Rock")
        assertTrue(model.canSave(model.state.value.editor!!))
    }

    @Test
    fun `canSave allows the favorites toggle only with liked artists`() = runTest(testDispatcher) {
        coEvery { metadataDao.getAllStarredArtists() } returns emptyList()
        val model = vm()
        advanceUntilIdle()
        model.openNew()
        model.setName("Favs")
        model.setIncludeFavoriteArtists(true)

        assertFalse(model.canSave(model.state.value.editor!!))

        coEvery { metadataDao.getAllStarredArtists() } returns listOf(
            com.lucasdss.ftpmusic.app.data.db.ArtistEntity(id = "ar1", name = "Artist One"),
        )
        model.load()
        advanceUntilIdle()
        assertTrue(model.canSave(model.state.value.editor!!))
    }

    @Test
    fun `save adds a new mix with composite filters`() = runTest(testDispatcher) {
        val model = vm()
        advanceUntilIdle()
        model.openNew()
        model.setName("My Mix")
        model.toggleGenre("Rock")
        model.toggleDecade("90s")
        model.setAutoCache(true)

        model.save()
        advanceUntilIdle()

        coVerify {
            repository.addMix(
                "My Mix",
                MixFilters(genres = listOf("Rock"), decades = listOf("90s")),
                true,
            )
        }
        assertNull(model.state.value.editor)
        assertEquals("\"My Mix\" added", model.toast.value)
    }

    @Test
    fun `save keeps the editor open when the cap is reached`() = runTest(testDispatcher) {
        coEvery { repository.addMix(any(), any(), any()) } returns null
        val model = vm()
        advanceUntilIdle()
        model.openNew()
        model.setName("My Mix")
        model.toggleGenre("Rock")

        model.save()
        advanceUntilIdle()

        assertEquals("My Mix", model.state.value.editor?.name)
        assertEquals(
            "Limit reached (${DailyMixRepository.MAX_MIXES}) — delete a mix first",
            model.toast.value,
        )
        assertFalse(model.state.value.isSaving)
    }

    @Test
    fun `save called twice adds only one mix`() = runTest(testDispatcher) {
        val model = vm()
        advanceUntilIdle()
        model.openNew()
        model.setName("My Mix")
        model.toggleGenre("Rock")

        model.save()
        advanceUntilIdle()
        model.save()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.addMix(any(), any(), any()) }
    }

    @Test
    fun `save updates an existing mix`() = runTest(testDispatcher) {
        val model = vm()
        advanceUntilIdle()
        model.openEditor(mix(id = 9L, name = "Old", filters = MixFilters(decades = listOf("80s"))))
        model.setName("New")
        model.toggleDecade("90s")

        model.save()
        advanceUntilIdle()

        coVerify { repository.updateMix(9L, "New", MixFilters(decades = listOf("80s", "90s")), false) }
        assertEquals("\"New\" saved", model.toast.value)
    }

    @Test
    fun `delete from list removes the mix`() = runTest(testDispatcher) {
        val model = vm()
        advanceUntilIdle()

        model.delete(mix(id = 4L, name = "Gone"))
        advanceUntilIdle()

        coVerify { repository.deleteMix(4L) }
        assertEquals("\"Gone\" removed", model.toast.value)
    }

    @Test
    fun `confirmDelete deletes the edited mix and closes the editor`() = runTest(testDispatcher) {
        val model = vm()
        advanceUntilIdle()
        model.openEditor(mix(id = 4L, name = "Gone"))
        model.requestDelete()
        assertTrue(model.state.value.editor!!.pendingDelete)

        model.confirmDelete()
        advanceUntilIdle()

        coVerify { repository.deleteMix(4L) }
        assertNull(model.state.value.editor)
    }

    @Test
    fun `suggestions adapt to the active tab`() = runTest(testDispatcher) {
        val model = vm()
        advanceUntilIdle()

        model.openNew()
        assertTrue(model.suggestions(model.state.value.editor!!).contains("Chill Mode"))

        model.toggleGenre("Synthwave")
        assertTrue(model.suggestions(model.state.value.editor!!).contains("Synthwave Mix"))

        model.setActiveTab(DailyMixRepository.KIND_DECADES)
        model.toggleDecade("80s")
        assertTrue(model.suggestions(model.state.value.editor!!).contains("80s Hits"))

        model.setActiveTab(DailyMixRepository.KIND_FAVORITE_ARTISTS)
        assertTrue(model.suggestions(model.state.value.editor!!).contains("My Artists Mix"))
    }

    @Test
    fun `editor actions are no-ops without an open editor`() = runTest(testDispatcher) {
        val model = vm()
        advanceUntilIdle()

        model.setName("X")
        model.setActiveTab(DailyMixRepository.KIND_DECADES)
        model.toggleGenre("Rock")
        model.toggleDecade("80s")
        model.setAutoCache(true)
        model.requestDelete()
        model.cancelDelete()
        model.save()
        model.confirmDelete()
        model.loadMoreArtists()
        advanceUntilIdle()

        assertNull(model.state.value.editor)
        coVerify(exactly = 0) { repository.addMix(any(), any(), any()) }
        coVerify(exactly = 0) { repository.deleteMix(any()) }
    }

    @Test
    fun `clearToast clears the message`() = runTest(testDispatcher) {
        val model = vm()
        advanceUntilIdle()
        model.delete(mix())
        advanceUntilIdle()

        model.clearToast()

        assertNull(model.toast.value)
    }

    @Test
    fun `openEditor picks the tab matching the populated dimension`() = runTest(testDispatcher) {
        val model = vm()
        advanceUntilIdle()

        model.openEditor(mix(id = 2L, filters = MixFilters(decades = listOf("80s"))))
        assertEquals(DailyMixRepository.KIND_DECADES, model.state.value.editor!!.activeTab)

        model.openEditor(mix(id = 3L, filters = MixFilters(includeFavoriteArtists = true)))
        assertEquals(DailyMixRepository.KIND_FAVORITE_ARTISTS, model.state.value.editor!!.activeTab)
    }

    @Test
    fun `canSave accepts explicit artist selections`() = runTest(testDispatcher) {
        val model = vm()
        advanceUntilIdle()
        model.openNew()
        model.setName("Artists")
        model.toggleArtist(ArtistOption("ar1", "Artist One", isFavorite = false))

        assertTrue(model.canSave(model.state.value.editor!!))
    }

    @Test
    fun `confirmDelete ignores a new unsaved mix`() = runTest(testDispatcher) {
        val model = vm()
        advanceUntilIdle()
        model.openNew()
        model.requestDelete()

        model.confirmDelete()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.deleteMix(any()) }
    }

    @Test
    fun `loadMoreArtists is a no-op when results are exhausted`() = runTest(testDispatcher) {
        coEvery { metadataDao.searchArtistsPaged("", 50, 0) } returns emptyList()
        val model = vm()
        advanceUntilIdle()
        model.openNew()
        model.setArtistQuery("")
        advanceTimeBy(CustomDailyMixesViewModel.ARTIST_SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()
        assertTrue(model.state.value.editor!!.artistResultsExhausted)

        model.loadMoreArtists()
        advanceUntilIdle()

        coVerify(exactly = 0) { metadataDao.searchArtistsPaged("", 50, 50) }
    }

    @Test
    fun `suggestions cover multi-decade labels`() = runTest(testDispatcher) {
        val model = vm()
        advanceUntilIdle()
        model.openNew()
        model.setActiveTab(DailyMixRepository.KIND_DECADES)
        model.toggleDecade("80s")
        model.toggleDecade("90s")

        assertTrue(model.suggestions(model.state.value.editor!!).contains("80s–90s Mix"))
    }

    @Test
    fun `load more accounting ignores pinned selections`() = runTest(testDispatcher) {
        coEvery { repository.allGenres() } returns (1..30).map { CachedGenreEntity(name = "G$it", songCount = 30 - it) }
        val model = vm()
        advanceUntilIdle()
        model.openNew()
        (1..5).forEach { model.toggleGenre("G$it") }

        val editor = model.state.value.editor!!
        // 30 genres, 5 selected → 25 unselected; window is 20.
        assertEquals(5, model.remainingGenres(editor))
        assertTrue(model.hasMoreGenres(editor))

        model.loadMoreGenres()
        assertFalse(model.hasMoreGenres(model.state.value.editor!!))
        assertEquals(0, model.remainingGenres(model.state.value.editor!!))
    }

    @Test
    fun `openNew resets the genre window and artist names`() = runTest(testDispatcher) {
        coEvery { repository.allGenres() } returns (1..30).map { CachedGenreEntity(name = "G$it", songCount = 30 - it) }
        val model = vm()
        advanceUntilIdle()
        model.openNew()
        model.loadMoreGenres()
        model.toggleArtist(ArtistOption("ar1", "Artist One", false))

        model.openNew()

        assertEquals(CustomDailyMixesViewModel.GENRE_PAGE, model.state.value.genreDisplayCount)
        assertTrue(model.state.value.selectedArtistNames.isEmpty())
    }

    @Test
    fun `openEditor keeps explicit artists favorites-free`() = runTest(testDispatcher) {
        val model = vm()
        advanceUntilIdle()

        model.openEditor(mix(filters = MixFilters(artistIds = listOf("ar1"))))

        assertTrue(!model.state.value.editor!!.includeFavoriteArtists)
        assertEquals(setOf("ar1"), model.state.value.editor!!.artistIds)
    }

    @Test
    fun `changing the artist query clears stale results immediately`() = runTest(testDispatcher) {
        coEvery { metadataDao.searchArtistsPaged("a", 50, 0) } returns
            listOf(CachedArtistEntity(id = "ar1", name = "A One"))
        val model = vm()
        advanceUntilIdle()
        model.openNew()
        model.setArtistQuery("a")
        advanceTimeBy(CustomDailyMixesViewModel.ARTIST_SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()
        assertEquals(1, model.state.value.editor!!.artistResults.size)

        model.setArtistQuery("b")

        assertTrue(model.state.value.editor!!.artistResults.isEmpty())
        assertFalse(model.state.value.editor!!.artistResultsExhausted)
    }
}
