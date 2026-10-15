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
        assertEquals(
            "guns n' roses",
            ArtistNameNormalizer.normalize("Guns N\uFF07 Roses"),
        )
    }

    @Test
    fun `aliasMatches requires whole pipe-delimited entry`() {
        assertTrue(
            ArtistNameNormalizer.aliasMatches("guns n' roses|gnr", "Guns N' Roses"),
        )
        assertTrue(
            ArtistNameNormalizer.aliasMatches("gnr|guns n' roses", "Guns N' Roses"),
        )
        assertFalse(
            ArtistNameNormalizer.aliasMatches("pink floyd", "Guns N' Roses"),
        )
        assertFalse(ArtistNameNormalizer.aliasMatches(null, "Band"))
        assertFalse(ArtistNameNormalizer.aliasMatches("tribute", ""))
    }

    @Test
    fun `aliasMatches rejects tribute supersets on legacy space-joined blobs`() {
        assertFalse(
            ArtistNameNormalizer.aliasMatches(
                "australian pink floyd show",
                "Pink Floyd",
            ),
        )
        // Legacy without pipe: only exact full-string equality.
        assertTrue(
            ArtistNameNormalizer.aliasMatches("pink floyd", "Pink Floyd"),
        )
        assertFalse(
            ArtistNameNormalizer.aliasMatches("guns n' roses aka", "Guns N' Roses"),
        )
    }

    @Test
    fun `joinAliases uses pipe delimiter`() {
        assertEquals(
            "Pink Floyd|The Pink Floyd",
            ArtistNameNormalizer.joinAliases(listOf("Pink Floyd", "The Pink Floyd", "  ")),
        )
    }

    @Test
    fun `aliasMatches trims pipe segments and ignores empties`() {
        assertTrue(
            ArtistNameNormalizer.aliasMatches("| pink floyd |the pink floyd|", "Pink Floyd"),
        )
        assertFalse(
            ArtistNameNormalizer.aliasMatches("||", "Pink Floyd"),
        )
    }
}
