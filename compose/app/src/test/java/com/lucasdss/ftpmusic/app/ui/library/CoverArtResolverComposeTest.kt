package com.lucasdss.ftpmusic.app.ui.library

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.lucasdss.ftpmusic.app.data.cache.CoverArtFallbackService
import com.lucasdss.ftpmusic.app.di.ServerConfig
import com.lucasdss.ftpmusic.app.di.ServerConfigState
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric Compose tests for the cover-art resolver: observable server
 * config, deterministic URL building, and local-cache priority.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h800dp")
class CoverArtResolverComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        ServerConfigState.value = ServerConfig()
    }

    @Test
    fun `rememberCoverArtUrl returns null without server config`() {
        ServerConfigState.value = ServerConfig()
        var result: String? = "sentinel"

        composeRule.setContent {
            result = rememberCoverArtUrl("ca-1", 300)
        }
        composeRule.waitForIdle()

        assertNull(result)
    }

    @Test
    fun `rememberCoverArtUrl builds an authenticated URL once config arrives`() {
        ServerConfigState.value = ServerConfig()
        var result: String? = null

        composeRule.setContent {
            result = rememberCoverArtUrl("ca-1", 300)
        }
        composeRule.waitForIdle()
        assertNull(result)

        // Simulate the config arriving after first composition: the resolver
        // must recompose and rebuild the URL instead of staying null.
        composeRule.runOnIdle {
            ServerConfigState.value = ServerConfig("https://music.example", "user", "pass")
        }
        composeRule.waitForIdle()

        assertNotNull(result)
        assertTrue(result!!.startsWith("https://music.example/rest/getCoverArt?"))
        assertTrue(result!!.contains("id=ca-1"))
        assertTrue(result!!.contains("u=user"))
        assertTrue(result!!.contains("size=300"))
    }

    @Test
    fun `rememberPreferredCoverArt prefers a valid local cache file over remote`() {
        ServerConfigState.value = ServerConfig("https://music.example", "user", "pass")
        val context = ApplicationProvider.getApplicationContext<Application>()
        val service = CoverArtFallbackService.getInstance(context)
        val cacheKey = "artist|album".lowercase()
        val cachedFile = File(service.cacheDir, "${cacheKey.hashCode()}.jpg")
        cachedFile.parentFile?.mkdirs()
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            compress(Bitmap.CompressFormat.PNG, 100, cachedFile.outputStream())
            recycle()
        }

        var result: String? = null
        composeRule.setContent {
            result = rememberPreferredCoverArt(
                coverArtId = "ca-remote",
                artist = "Artist",
                album = "Album",
                size = 300,
                fallbackService = service,
            )
        }
        composeRule.waitForIdle()

        assertEquals("file://${cachedFile.absolutePath}", result)
    }

    @Test
    fun `rememberPreferredCoverArt evicts a corrupt cache file and falls back to remote`() {
        ServerConfigState.value = ServerConfig("https://music.example", "user", "pass")
        val context = ApplicationProvider.getApplicationContext<Application>()
        val service = CoverArtFallbackService.getInstance(context)
        val cacheKey = "artist|album".lowercase()
        val cachedFile = File(service.cacheDir, "${cacheKey.hashCode()}.jpg")
        cachedFile.parentFile?.mkdirs()
        cachedFile.writeText("corrupt")

        var result: String? = null
        composeRule.setContent {
            result = rememberPreferredCoverArt(
                coverArtId = "ca-remote",
                artist = "Artist",
                album = "Album",
                size = 300,
                fallbackService = service,
            )
        }
        composeRule.waitForIdle()

        assertTrue(result!!.startsWith("https://music.example/rest/getCoverArt?"))
        assertTrue("corrupt cache file must be evicted", !cachedFile.exists())
    }

    @Test
    fun `rememberPreferredCoverArt ignores unrelated artist key bumps`() {
        ServerConfigState.value = ServerConfig("https://music.example", "user", "pass")
        val context = ApplicationProvider.getApplicationContext<Application>()
        val service = CoverArtFallbackService.getInstance(context)
        val cacheKey = "artist|album".lowercase()
        val cachedFile = File(service.cacheDir, "${cacheKey.hashCode()}.jpg")
        cachedFile.parentFile?.mkdirs()
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            compress(Bitmap.CompressFormat.PNG, 100, cachedFile.outputStream())
            recycle()
        }

        var result: String? = null
        var recompositions = 0
        composeRule.setContent {
            recompositions++
            result = rememberPreferredCoverArt(
                coverArtId = "ca-remote",
                artist = "Artist",
                album = "Album",
                size = 300,
                fallbackService = service,
            )
        }
        composeRule.waitForIdle()
        val afterFirst = recompositions
        assertEquals("file://${cachedFile.absolutePath}", result)

        // Artist write must not force album cells to re-probe disk.
        val onFileCached = CoverArtFallbackService::class.java
            .getDeclaredMethod("onFileCached", String::class.java)
        onFileCached.isAccessible = true
        composeRule.runOnIdle {
            onFileCached.invoke(service, "artist|someone else")
        }
        composeRule.waitForIdle()

        assertEquals(afterFirst, recompositions)
        assertEquals("file://${cachedFile.absolutePath}", result)
    }
}
