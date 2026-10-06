package com.lucasdss.ftpmusic.app.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFtsQueryTest {

    @Test
    fun `toMatchQuery adds prefix stars`() {
        assertEquals("pink* floyd*", SearchFtsQuery.toMatchQuery("Pink Floyd"))
    }

    @Test
    fun `toMatchQuery strips punctuation`() {
        val q = SearchFtsQuery.toMatchQuery("rock-n-roll")
        assertTrue(q.contains("rock"))
        assertTrue(q.endsWith("*") || q.contains("*"))
    }

    @Test
    fun `toMatchQuery single short token uses fallback star`() {
        assertEquals("a*", SearchFtsQuery.toMatchQuery("a"))
    }

    @Test
    fun `toMatchQuery empty-ish input returns empty string`() {
        assertEquals("", SearchFtsQuery.toMatchQuery("   "))
        assertEquals("", SearchFtsQuery.toMatchQuery("!!"))
    }
}
