package com.lucasdss.ftpmusic.app.data.cover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionCoverResolverTest {

    @Test
    fun `fixed navidrome wins over derived`() {
        val r = CollectionCoverResolver.resolve(
            CollectionCoverResolver.Input(
                name = "Rock Mix",
                fixedKind = CollectionCoverKind.NAVIDROME,
                fixedValue = "al-fixed",
                derivedCoverArtIds = listOf("al-1"),
                serverCoverArtId = "pl-ca",
            ),
        )
        assertEquals(CollectionCoverResolver.Resolved.Navidrome("al-fixed"), r)
    }

    @Test
    fun `fixed local uses absolute path when present`() {
        val r = CollectionCoverResolver.resolve(
            CollectionCoverResolver.Input(
                name = "Rock Mix",
                fixedKind = CollectionCoverKind.LOCAL,
                fixedValue = "mix_1.jpg",
                localAbsolutePath = "/data/mix_1.jpg",
                derivedCoverArtIds = listOf("al-1"),
            ),
        )
        assertEquals(CollectionCoverResolver.Resolved.LocalFile("/data/mix_1.jpg"), r)
    }

    @Test
    fun `missing local file falls through to derived`() {
        val r = CollectionCoverResolver.resolve(
            CollectionCoverResolver.Input(
                name = "Rock Mix",
                fixedKind = CollectionCoverKind.LOCAL,
                fixedValue = "gone.jpg",
                localAbsolutePath = null,
                derivedCoverArtIds = listOf("al-1", "al-2"),
            ),
        )
        assertTrue(r is CollectionCoverResolver.Resolved.Derived)
        assertEquals(listOf("al-1", "al-2"), (r as CollectionCoverResolver.Resolved.Derived).coverArtIds)
    }

    @Test
    fun `server coverArt used when no derived`() {
        val r = CollectionCoverResolver.resolve(
            CollectionCoverResolver.Input(
                name = "Playlist",
                fixedKind = null,
                fixedValue = null,
                serverCoverArtId = "pl-ca",
            ),
        )
        assertEquals(listOf("pl-ca"), (r as CollectionCoverResolver.Resolved.Derived).coverArtIds)
    }

    @Test
    fun `lettermark when nothing else`() {
        val r = CollectionCoverResolver.resolve(
            CollectionCoverResolver.Input(
                name = "Empty Mix",
                fixedKind = null,
                fixedValue = null,
            ),
        )
        assertEquals(CollectionCoverResolver.Resolved.Lettermark("Empty Mix"), r)
    }

    @Test
    fun `bundled wins over lettermark when no derived`() {
        val r = CollectionCoverResolver.resolve(
            CollectionCoverResolver.Input(
                name = "Rock",
                fixedKind = null,
                fixedValue = null,
                bundledPackId = "anime",
                bundledAssetPath = "collection_defaults/anime/genres/rock.webp",
            ),
        )
        assertEquals(
            CollectionCoverResolver.Resolved.Bundled(
                "anime",
                "collection_defaults/anime/genres/rock.webp",
            ),
            r,
        )
    }

    @Test
    fun `genre skipDerived uses bundled before lettermark`() {
        val r = CollectionCoverResolver.resolve(
            CollectionCoverResolver.Input(
                name = "Rock",
                fixedKind = null,
                fixedValue = null,
                derivedCoverArtIds = listOf("al-1"),
                skipDerived = true,
                bundledPackId = "vintage",
                bundledAssetPath = "collection_defaults/vintage/genres/rock.webp",
            ),
        )
        assertTrue(r is CollectionCoverResolver.Resolved.Bundled)
    }

    @Test
    fun `initials from two words`() {
        assertEquals("RM", CollectionCoverResolver.initials("Rock Mix"))
    }

    @Test
    fun `initials from single word`() {
        assertEquals("RO", CollectionCoverResolver.initials("Rock"))
    }

    @Test
    fun `hue stable for same name`() {
        val a = CollectionCoverResolver.hueFromName("Jazz Mix")
        val b = CollectionCoverResolver.hueFromName("Jazz Mix")
        assertEquals(a, b, 0.001f)
        assertTrue(a in 160f..300f)
    }

    @Test
    fun `isUsableFixed navidrome needs value`() {
        assertTrue(
            CollectionCoverResolver.isUsableFixed(CollectionCoverKind.NAVIDROME, "al-1", null),
        )
        assertFalse(
            CollectionCoverResolver.isUsableFixed(CollectionCoverKind.NAVIDROME, "", null),
        )
    }

    @Test
    fun `isUsableFixed local requires absolute path`() {
        assertFalse(
            CollectionCoverResolver.isUsableFixed(CollectionCoverKind.LOCAL, "mix_1.jpg", null),
        )
        assertTrue(
            CollectionCoverResolver.isUsableFixed(
                CollectionCoverKind.LOCAL,
                "mix_1.jpg",
                "/data/mix_1.jpg",
            ),
        )
    }

    @Test
    fun `fixed bundled wins over derived`() {
        val path = "collection_defaults/anime/genres/rock.webp"
        val r = CollectionCoverResolver.resolve(
            CollectionCoverResolver.Input(
                name = "Rock Mix",
                fixedKind = CollectionCoverKind.BUNDLED,
                fixedValue = path,
                derivedCoverArtIds = listOf("al-1"),
            ),
        )
        assertEquals(CollectionCoverResolver.Resolved.Bundled("anime", path), r)
    }

    @Test
    fun `isUsableFixed bundled needs value`() {
        assertTrue(
            CollectionCoverResolver.isUsableFixed(
                CollectionCoverKind.BUNDLED,
                "collection_defaults/anime/genres/default.webp",
                null,
            ),
        )
        assertFalse(
            CollectionCoverResolver.isUsableFixed(CollectionCoverKind.BUNDLED, "", null),
        )
    }
}
