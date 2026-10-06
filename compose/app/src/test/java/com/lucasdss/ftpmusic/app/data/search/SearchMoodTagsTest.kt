package com.lucasdss.ftpmusic.app.data.search

import com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMoodTagsTest {

    @Test
    fun `aggregateTags ranks by frequency`() {
        val artists = listOf(
            CachedArtistEntity(id = "1", name = "A", searchTags = "rock indie rock"),
            CachedArtistEntity(id = "2", name = "B", searchTags = "rock,pop"),
            CachedArtistEntity(id = "3", name = "C", searchTags = "jazz"),
        )
        val tags = SearchMoodTags.aggregateTags(artists, limit = 10)
        assertEquals("rock", tags.first())
        assertTrue(tags.contains("indie"))
        assertTrue(tags.contains("pop"))
        assertTrue(tags.contains("jazz"))
    }

    @Test
    fun `mood searchQuery uses first token`() {
        val chill = SearchMoodTags.MOODS.first { it.label == "Chill" }
        assertEquals("chill", chill.searchQuery())
    }

    @Test
    fun `matchedTagsForQuery finds overlapping tags`() {
        val artists = listOf(
            CachedArtistEntity(id = "1", name = "A", searchTags = "chill ambient"),
        )
        val matched = SearchMoodTags.matchedTagsForQuery(artists, "chill")
        assertEquals(listOf("chill"), matched)
    }

    @Test
    fun `extractTags splits punctuation`() {
        assertEquals(
            listOf("rock", "indie", "folk"),
            SearchMoodTags.extractTags("rock, indie; folk"),
        )
    }
}
