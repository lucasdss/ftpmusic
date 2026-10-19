package com.lucasdss.ftpmusic.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionCoverEditControllerTest {

    @Test
    fun `auto-open allowed when idle`() {
        assertTrue(canAutoOpenCoverEditSheet(hasPendingCrop = false, showAlbumPicker = false))
    }

    @Test
    fun `auto-open blocked when crop pending`() {
        assertFalse(canAutoOpenCoverEditSheet(hasPendingCrop = true, showAlbumPicker = false))
    }

    @Test
    fun `auto-open blocked when album picker open`() {
        assertFalse(canAutoOpenCoverEditSheet(hasPendingCrop = false, showAlbumPicker = true))
    }

    @Test
    fun `auto-open blocked when both in flight`() {
        assertFalse(canAutoOpenCoverEditSheet(hasPendingCrop = true, showAlbumPicker = true))
    }
}
