package com.lucasdss.ftpmusic.app.data.cover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionCoverPackOrderTest {
    @Test
    fun `B3-1 active pack first then remaining by id`() {
        val packs = listOf(
            DefaultCollectionCoverCatalog.Pack("noir", "Noir", false),
            DefaultCollectionCoverCatalog.Pack("anime", "Anime", true),
            DefaultCollectionCoverCatalog.Pack("vintage", "Vintage", false),
        )
        val thumbs = CollectionCoverPackOrder.orderedThumbs(
            packs = packs,
            activePackId = "anime",
            resolveAsset = { id -> "collection_defaults/$id/mixes/default.webp" },
        )
        assertEquals("anime", thumbs.first().packId)
        assertEquals(listOf("anime", "noir", "vintage"), thumbs.map { it.packId })
    }

    @Test
    fun `B3-2 skips packs with blank asset`() {
        val packs = listOf(
            DefaultCollectionCoverCatalog.Pack("anime", "Anime", true),
            DefaultCollectionCoverCatalog.Pack("noir", "Noir", false),
        )
        val thumbs = CollectionCoverPackOrder.orderedThumbs(
            packs = packs,
            activePackId = "anime",
            resolveAsset = { id -> if (id == "noir") null else "path/$id.webp" },
        )
        assertEquals(1, thumbs.size)
        assertEquals("anime", thumbs.single().packId)
        assertTrue(thumbs.single().assetPath.isNotBlank())
    }
}
