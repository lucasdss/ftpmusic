package com.lucasdss.ftpmusic.app.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistNameNormalizerTest {

    @Test
    fun `folds curly apostrophe and collapses spaces`() {
        assertEquals(
            "guns n' roses",
            ArtistNameNormalizer.normalize("  Guns N\u2019  Roses  "),
        )
        assertEquals(
            "guns n' roses",
            ArtistNameNormalizer.normalize("Guns N' Roses"),
        )
        assertEquals(
            "guns n' roses",
            ArtistNameNormalizer.normalize("Guns N\u02BC Roses"),
        )
    }

    @Test
    fun `aliasMatches requires whole token sequence`() {
        assertTrue(
            ArtistNameNormalizer.aliasMatches("guns n' roses aka", "Guns N' Roses"),
        )
        assertFalse(
            ArtistNameNormalizer.aliasMatches("pink floyd", "Guns N' Roses"),
        )
        assertFalse(ArtistNameNormalizer.aliasMatches(null, "Band"))
        assertFalse(ArtistNameNormalizer.aliasMatches("tribute", ""))
    }
}
