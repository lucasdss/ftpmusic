package com.lucasdss.ftpmusic.app.data.cover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DominantGenrePickerTest {

    @Test
    fun `empty returns null`() {
        assertNull(DominantGenrePicker.majority(emptyList()))
        assertNull(DominantGenrePicker.majority(listOf(" ", "")))
    }

    @Test
    fun `single genre wins`() {
        assertEquals("Rock", DominantGenrePicker.majority(listOf("Rock")))
    }

    @Test
    fun `majority wins over minority`() {
        assertEquals(
            "Rock",
            DominantGenrePicker.majority(listOf("Rock", "Pop", "Rock", "Jazz")),
        )
    }

    @Test
    fun `tie breaks lexicographically on normalized key`() {
        assertEquals(
            "Jazz",
            DominantGenrePicker.majority(listOf("Rock", "Jazz")),
        )
    }

    @Test
    fun `case and whitespace normalized for counting`() {
        // Display form is first-seen; counting key is normalized.
        assertEquals(
            "rock",
            DominantGenrePicker.majority(listOf("rock", "  Rock  ", "Pop")),
        )
    }

    @Test
    fun `preferRecipeThenTracks uses first recipe genre`() {
        assertEquals(
            "Metal",
            DominantGenrePicker.preferRecipeThenTracks(
                recipeGenres = listOf("Metal", "Rock"),
                trackGenres = listOf("Pop", "Pop", "Pop"),
            ),
        )
    }

    @Test
    fun `preferRecipeThenTracks falls back to track majority`() {
        assertEquals(
            "Pop",
            DominantGenrePicker.preferRecipeThenTracks(
                recipeGenres = emptyList(),
                trackGenres = listOf("Pop", "Pop", "Rock"),
            ),
        )
    }
}
