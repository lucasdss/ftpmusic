package com.lucasdss.ftpmusic.app.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchYearParserTest {
    @Test
    fun `parses exact year`() {
        val p = SearchYearParser.parse("pink floyd 1994")
        assertEquals("pink floyd", p.text)
        assertEquals(1994, p.year.exactYear)
        assertTrue(p.year.matches(1994))
        assertFalse(p.year.matches(1995))
    }

    @Test
    fun `parses short decade`() {
        val p = SearchYearParser.parse("90s rock")
        assertEquals("rock", p.text)
        assertEquals(1990, p.year.minYear)
        assertEquals(1999, p.year.maxYear)
        assertTrue(p.year.matches(1995))
    }

    @Test
    fun `parses long decade`() {
        val p = SearchYearParser.parse("2000s")
        assertEquals("", p.text)
        assertEquals(2000, p.year.minYear)
        assertEquals(2009, p.year.maxYear)
    }
}

class EditDistanceTest {
    @Test
    fun `distance zero for equal`() {
        assertEquals(0, EditDistance.distance("abc", "abc"))
    }

    @Test
    fun `within one edit`() {
        assertTrue(EditDistance.within("beatles", "beatle", 1))
        assertTrue(EditDistance.within("radiohead", "radiohed", 1))
        assertFalse(EditDistance.within("radiohead", "radiator", 1))
    }
}
