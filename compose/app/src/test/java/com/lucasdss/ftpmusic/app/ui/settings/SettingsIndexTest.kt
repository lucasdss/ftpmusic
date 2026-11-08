package com.lucasdss.ftpmusic.app.ui.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.lucasdss.ftpmusic.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsIndexTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `every required route has an indexed node with keywords`() {
        val indexedRoutes = SettingsIndex.nodes.map { it.route }.toSet()
        SettingsIndex.requiredIndexedRoutes().forEach { route ->
            assertTrue("Missing SettingNode for $route", route in indexedRoutes)
            val node = SettingsIndex.nodes.first { it.route == route }
            assertTrue("Node ${node.id} must declare keywords (D11)", node.keywords.isNotEmpty())
        }
    }

    @Test
    fun `filter matches curated keyword case-insensitively`() {
        val state = SettingsUiState()
        val hits = SettingsIndex.filter("ReSyNc", state, context)
        assertTrue(hits.any { it.route == SettingsRoutes.NETWORK })
    }

    @Test
    fun `filter matches cache size keyword to storage`() {
        val state = SettingsUiState()
        val hits = SettingsIndex.filter("cache size", state, context)
        assertTrue(hits.any { it.route == SettingsRoutes.STORAGE })
    }

    @Test
    fun `empty query returns all nodes`() {
        val all = SettingsIndex.filter("", SettingsUiState(), context)
        assertTrue(all.size == SettingsIndex.nodes.size)
    }

    @Test
    fun `no-match query returns empty`() {
        val hits = SettingsIndex.filter("zzzz-not-a-setting", SettingsUiState(), context)
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `customMixes is a hub index requirement`() {
        assertTrue(SettingsRoutes.CUSTOM_MIXES in SettingsIndex.requiredIndexedRoutes())
        val node = SettingsIndex.nodes.first { it.route == SettingsRoutes.CUSTOM_MIXES }
        assertEquals(R.string.settings_group_home, node.groupRes)
        assertTrue(node.keywords.any { it.contains("daily mix", ignoreCase = true) })
    }

    @Test
    fun `filter daily mix hits custom mixes node`() {
        val hits = SettingsIndex.filter("daily mix", SettingsUiState(), context)
        assertTrue(hits.any { it.route == SettingsRoutes.CUSTOM_MIXES })
    }

    @Test
    fun `clear cache search resolves storage clear_cache anchor`() {
        val node = SettingsIndex.nodes.first { it.route == SettingsRoutes.STORAGE }
        val route = node.routeForQuery("clear cache")
        assertEquals(
            settingsLeafRoute(SettingsRoutes.STORAGE, SettingsAnchors.CLEAR_CACHE),
            route,
        )
    }

    @Test
    fun `hub summaries come from string resources`() {
        val profile = SettingsIndex.nodes.first { it.id == "profile" }
        assertEquals(
            context.getString(com.lucasdss.ftpmusic.app.R.string.settings_summary_profile),
            profile.summaryProvider(SettingsUiState(), context),
        )
        val about = SettingsIndex.nodes.first { it.id == "about" }
        assertEquals(
            context.getString(com.lucasdss.ftpmusic.app.R.string.settings_summary_about),
            about.summaryProvider(SettingsUiState(), context),
        )
    }
}
