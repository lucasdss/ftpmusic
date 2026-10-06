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

    @Test
    fun `empty query inactive year matches any`() {
        val p = SearchYearParser.parse("")
        assertFalse(p.year.isActive)
        assertTrue(p.year.matches(null))
        assertTrue(p.year.matches(1990))
    }

    @Test
    fun `active year rejects null album year`() {
        val y = SearchYearConstraint(exactYear = 1994)
        assertTrue(y.isActive)
        assertFalse(y.matches(null))
    }

    @Test
    fun `decade range rejects out of band`() {
        val y = SearchYearConstraint(minYear = 1970, maxYear = 1979)
        assertTrue(y.matches(1975))
        assertFalse(y.matches(1980))
    }

    @Test
    fun `no year token leaves text intact`() {
        val p = SearchYearParser.parse("radiohead")
        assertEquals("radiohead", p.text)
        assertFalse(p.year.isActive)
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
