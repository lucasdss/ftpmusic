package com.lucasdss.ftpmusic.app.data.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchQueryNormalizerTest {

    @Test
    fun `escapeLike escapes percent underscore and backslash`() {
        assertEquals("""a\%b\_c\\d""", SearchQueryNormalizer.escapeLike("""a%b_c\d"""))
    }

    @Test
    fun `fold strips diacritics and lowercases`() {
        assertEquals("bjork", SearchQueryNormalizer.fold("  Björk  "))
        assertEquals("cafe", SearchQueryNormalizer.fold("Café"))
    }
}
