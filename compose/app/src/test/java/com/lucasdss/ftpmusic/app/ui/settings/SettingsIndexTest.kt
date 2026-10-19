package com.lucasdss.ftpmusic.app.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsIndexTest {

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
        val hits = SettingsIndex.filter("ReSyNc", state) { "title" }
        assertTrue(hits.any { it.route == SettingsRoutes.NETWORK })
    }

    @Test
    fun `filter matches cache size keyword to storage`() {
        val state = SettingsUiState()
        val hits = SettingsIndex.filter("cache size", state) { id ->
            when (id) {
                else -> "x"
            }
        }
        // Match via keywords even when title resolver returns unrelated text
        assertTrue(hits.any { it.route == SettingsRoutes.STORAGE })
    }

    @Test
    fun `empty query returns all nodes`() {
        val all = SettingsIndex.filter("", SettingsUiState()) { "t" }
        assertTrue(all.size == SettingsIndex.nodes.size)
    }

    @Test
    fun `no-match query returns empty`() {
        val hits = SettingsIndex.filter("zzzz-not-a-setting", SettingsUiState()) { "t" }
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `customMixes is not a hub index requirement`() {
        assertFalse(SettingsRoutes.CUSTOM_MIXES in SettingsIndex.requiredIndexedRoutes())
    }
}
