package com.lucasdss.ftpmusic.app.data.cover

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DefaultCollectionCoverCatalogTest {

    private lateinit var catalog: DefaultCollectionCoverCatalog

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        catalog = DefaultCollectionCoverCatalog(context)
    }

    @Test
    fun `default pack is anime`() {
        assertEquals("anime", catalog.defaultPackId())
    }

    @Test
    fun `exact mix slug resolves`() {
        val r = catalog.resolveMix("anime", "Rock Mix")
        assertNotNull(r)
        assertTrue(r!!.assetPath.contains("/mixes/rock_mix.webp") || r.assetPath.contains("/mixes/rock.webp"))
    }

    @Test
    fun `name minus mix falls back to genre slug`() {
        val r = catalog.resolveMix("anime", "Jazz Mix")
        assertNotNull(r)
        assertTrue(
            r!!.assetPath.endsWith("jazz_mix.webp") ||
                r.assetPath.endsWith("jazz.webp") ||
                r.assetPath.endsWith("default.webp"),
        )
    }

    @Test
    fun `dominantGenre exact maps to genre asset when name unknown`() {
        val r = catalog.resolveMix("anime", "Mystery Custom Mix", dominantGenre = "Rock")
        assertNotNull(r)
        assertTrue(
            "expected genre rock asset, got ${r!!.assetPath}",
            r.assetPath.endsWith("genres/rock.webp") || r.assetPath.endsWith("mixes/default.webp"),
        )
    }

    @Test
    fun `unknown dominantGenre falls through to default`() {
        val r = catalog.resolvePlaylist("anime", "Totally Unknown Playlist XYZ", dominantGenre = "NotARealGenre")
        assertNotNull(r)
        assertTrue(r!!.assetPath.endsWith("playlists/default.webp"))
    }

    @Test
    fun `listAssets returns webps and caches`() {
        val first = catalog.listAssets("anime")
        assertTrue(first.isNotEmpty())
        assertTrue(first.all { it.assetPath.endsWith(".webp") })
        val second = catalog.listAssets("anime")
        assertEquals(first.size, second.size)
        assertEquals(first.first().assetPath, second.first().assetPath)
    }

    @Test
    fun `listAssets unknown pack empty`() {
        assertTrue(catalog.listAssets("no-such-pack").isEmpty())
    }
}
