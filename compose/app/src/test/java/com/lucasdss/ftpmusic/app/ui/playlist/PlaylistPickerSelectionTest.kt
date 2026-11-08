package com.lucasdss.ftpmusic.app.ui.playlist

import com.lucasdss.ftpmusic.app.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistPickerSelectionTest {

    private val tracks = listOf(
        Track("t1", "One", duration = 100),
        Track("t2", "Two", duration = 200),
        Track("t3", "Three", duration = 300),
    )

    @Test
    fun `SINGLE seeds one track id from menu index`() {
        assertEquals(
            setOf("t2"),
            resolvePickerSelection(PlaylistPickerSeedMode.SINGLE, tracks, menuIndex = 1),
        )
    }

    @Test
    fun `SINGLE returns empty for invalid index`() {
        assertTrue(resolvePickerSelection(PlaylistPickerSeedMode.SINGLE, tracks, menuIndex = -1).isEmpty())
        assertTrue(resolvePickerSelection(PlaylistPickerSeedMode.SINGLE, tracks, menuIndex = 99).isEmpty())
    }

    @Test
    fun `SINGLE skips blank track id`() {
        val withBlank = listOf(Track("", "Blank", duration = 1), Track("t2", "Two", duration = 2))
        assertTrue(resolvePickerSelection(PlaylistPickerSeedMode.SINGLE, withBlank, menuIndex = 0).isEmpty())
    }

    @Test
    fun `ALL seeds every non-blank track id`() {
        assertEquals(
            setOf("t1", "t2", "t3"),
            resolvePickerSelection(PlaylistPickerSeedMode.ALL, tracks),
        )
    }

    @Test
    fun `ALL on empty list is empty`() {
        assertTrue(resolvePickerSelection(PlaylistPickerSeedMode.ALL, emptyList()).isEmpty())
    }
}
