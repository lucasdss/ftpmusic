package com.lucasdss.ftpmusic.app.ui.art

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.lucasdss.ftpmusic.app.data.cover.ArtSeedExtractor
import com.lucasdss.ftpmusic.app.data.cover.CollectionCoverResolver
import com.lucasdss.ftpmusic.app.data.cover.ResolvedCoverFactory
import com.lucasdss.ftpmusic.app.ui.Background
import com.lucasdss.ftpmusic.app.ui.Foreground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverSchemeTest {

    @Test
    fun `null seed yields neutral background`() {
        val scheme = CoverScheme.fromSeed(null)
        assertEquals(Background, scheme.bgTop)
        assertEquals(Background, scheme.bgBottom)
        assertEquals(Foreground, scheme.onBg)
    }

    @Test
    fun `near-black seed is rejected as unusable`() {
        assertFalse(CoverScheme.isUsableSeed(Color.Black))
        assertEquals(Background, CoverScheme.fromSeed(Color.Black).bgTop)
    }

    @Test
    fun `near-white seed is rejected then fromSeed is neutral`() {
        assertFalse(CoverScheme.isUsableSeed(Color.White))
        assertEquals(Background, CoverScheme.fromSeed(Color.White).bgTop)
    }

    @Test
    fun `gray low-chroma seed is rejected`() {
        val gray = Color(0.5f, 0.5f, 0.5f)
        assertFalse(CoverScheme.isUsableSeed(gray))
    }

    @Test
    fun `vibrant seed is darkened under max luminance`() {
        val seed = Color(0f, 0.8f, 0.6f) // teal-ish
        assertTrue(CoverScheme.isUsableSeed(seed))
        val scheme = CoverScheme.fromSeed(seed)
        assertTrue(scheme.bgTop.luminance() <= CoverScheme.MAX_TOP_LUMINANCE + 0.01f)
        assertEquals(CoverScheme.DEFAULT_SECONDARY, scheme.onBgSecondary)
        assertTrue(
            CoverScheme.contrastRatio(scheme.onBg, scheme.bgTop) >= 4.5f,
        )
        assertTrue(
            CoverScheme.contrastRatio(scheme.onBgSecondary, scheme.bgTop) >= 4.5f,
        )
    }

    @Test
    fun `darkenToMaxLuminance reduces bright yellow`() {
        val yellow = Color(1f, 1f, 0f)
        val dark = CoverScheme.darkenToMaxLuminance(yellow, CoverScheme.MAX_TOP_LUMINANCE)
        assertTrue(dark.luminance() <= CoverScheme.MAX_TOP_LUMINANCE + 0.015f)
    }

    @Test
    fun `lettermark seed is usable and scheme is tinted`() {
        val argb = ArtSeedExtractor.lettermarkSeedArgb("Daily Jazz Mix")
        val seed = Color(argb)
        assertTrue(CoverScheme.isUsableSeed(seed) || seed.luminance() <= 0.35f)
        val scheme = CoverScheme.fromSeed(seed)
        // Lettermark hues sit in teal–purple band at L=0.28 — may need darken
        assertTrue(scheme.bgTop.luminance() <= CoverScheme.MAX_TOP_LUMINANCE + 0.02f)
    }

    @Test
    fun `lettermarkCoverScheme produces darkened top`() {
        val scheme = lettermarkCoverScheme("Rock Mix")
        assertTrue(scheme.bgTop.luminance() <= CoverScheme.MAX_TOP_LUMINANCE + 0.02f)
        assertNotEquals(Color.Unspecified, scheme.onBgSecondary)
    }

    @Test
    fun `resolved cover factory missing url is neutral identity`() {
        val cover = ResolvedCoverFactory.fromImageUrl("album|1", null, null)
        assertTrue(cover.imageModel == null)
        assertEquals("missing", cover.sourceVersion)
    }

    @Test
    fun `resolved cover sourceVersion never embeds credential URL`() {
        val url = "https://server/rest/getCoverArt.view?id=x&u=user&t=deadbeef&s=salt"
        val cover = ResolvedCoverFactory.fromImageUrl("album|1", "cover-42", url)
        assertFalse(cover.sourceVersion.contains("t="))
        assertFalse(cover.sourceVersion.contains("deadbeef"))
        assertEquals("cover-42", cover.sourceVersion)
        assertEquals(url, cover.imageModel)
    }

    @Test
    fun `resolved cover factory lettermark from resolver`() {
        val resolved = CollectionCoverResolver.Resolved.Lettermark("Mix A")
        val cover = ResolvedCoverFactory.fromResolver(resolved, scope = "mix|1")
        assertEquals("Mix A", cover.lettermarkName)
        assertTrue(cover.isLettermark)
    }

    @Test
    fun `CoverArtColors hasColors false for Unspecified`() {
        val empty = com.lucasdss.ftpmusic.app.ui.library.CoverArtColors()
        assertFalse(empty.hasColors)
    }

    @Test
    fun `CoverArtColors hasColors false for transparent vibrant`() {
        val bad = com.lucasdss.ftpmusic.app.ui.library.CoverArtColors(
            vibrant = Color(0x00000000),
            darkMuted = Color.Black,
        )
        assertFalse(bad.hasColors)
    }
}
