package com.lucasdss.ftpmusic.app.ui.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pass 6 windowed alpha browse helpers (ADR-0107). */
class LibraryPagingTest {

    @Test
    fun `full page means hasMore`() {
        assertTrue(LibraryPaging.hasMore(LibraryPaging.ALPHA_PAGE_SIZE))
        assertTrue(LibraryPaging.hasMore(LibraryPaging.ALPHA_PAGE_SIZE + 1))
    }

    @Test
    fun `short page means endReached`() {
        assertFalse(LibraryPaging.hasMore(0))
        assertFalse(LibraryPaging.hasMore(LibraryPaging.ALPHA_PAGE_SIZE - 1))
    }
}
