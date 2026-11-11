package com.lucasdss.ftpmusic.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextHtmlTest {
    @Test
    fun `strip removes tags and collapses whitespace`() {
        assertEquals(
            "Born in London , English singer.",
            TextHtml.strip("<p>Born in <b>London</b>, English singer.</p>"),
        )
    }

    @Test
    fun `strip decodes common entities`() {
        assertEquals("A & B's \"x\"", TextHtml.strip("A &amp; B&#39;s &quot;x&quot;"))
    }

    @Test
    fun `sanitizeBiography null and blank`() {
        assertNull(TextHtml.sanitizeBiography(null))
        assertNull(TextHtml.sanitizeBiography("   "))
        assertNull(TextHtml.sanitizeBiography("<p></p>"))
    }

    @Test
    fun `sanitizeBiography strips html and respects maxChars`() {
        val raw = "<a href=\"x\">Hello</a> world " + "x".repeat(50)
        val out = TextHtml.sanitizeBiography(raw, maxChars = 20)
        assertEquals("Hello world xxxxxxxx", out)
        assertEquals(20, out!!.length)
    }
}
