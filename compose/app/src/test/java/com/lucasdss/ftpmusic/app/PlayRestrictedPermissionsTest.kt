package com.lucasdss.ftpmusic.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Play Oct 29 2026 policy regression: source AndroidManifest must not declare
 * restricted sensitive permissions while targetSdk is 36 (ADR-0153).
 */
class PlayRestrictedPermissionsTest {

    @Test
    fun `source manifest has no restricted sensitive permissions`() {
        val xml = readSourceManifest()
        val hits = PlayRestrictedPermissions.forbiddenPermissionsPresent(xml)
        assertTrue(
            "Unexpected restricted permissions in AndroidManifest.xml: $hits. " +
                "See docs/PLAY_POLICY_2026_10_29_COMPLIANCE.md",
            hits.isEmpty(),
        )
    }

    @Test
    fun `source manifest has no AppFunctions markers`() {
        val xml = readSourceManifest()
        val hits = PlayRestrictedPermissions.forbiddenMarkersPresent(xml)
        assertTrue(
            "Unexpected AppFunctions markers in AndroidManifest.xml: $hits",
            hits.isEmpty(),
        )
    }

    @Test
    fun `scanner detects a planted restricted permission`() {
        val planted = """
            <manifest>
              <uses-permission android:name="android.permission.INTERNET" />
              <uses-permission android:name="android.permission.ACCESS_LOCAL_NETWORK" />
            </manifest>
        """.trimIndent()
        assertEquals(
            listOf("android.permission.ACCESS_LOCAL_NETWORK"),
            PlayRestrictedPermissions.forbiddenPermissionsPresent(planted),
        )
    }

    @Test
    fun `scanner ignores allowed permissions`() {
        val xml = """
            <uses-permission android:name="android.permission.INTERNET" />
            <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
            <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
        """.trimIndent()
        assertTrue(PlayRestrictedPermissions.forbiddenPermissionsPresent(xml).isEmpty())
    }

    private fun readSourceManifest(): String {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("compose/app/src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: fail(
                "AndroidManifest.xml not found from cwd=${File(".").absolutePath}; " +
                    "tried ${candidates.map { it.path }}",
            ) as Nothing
        return file.readText()
    }
}
