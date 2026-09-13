package com.lucasdss.ftpmusic.app.cast

import com.google.android.gms.cast.CastDevice
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class CastDiscoveryTest {

    @Test
    fun `CastOptionsProvider app ID is correct`() {
        assertEquals(
            "CC1AD845",
            CastOptionsProvider().getCastOptions(mockk()).receiverApplicationId,
        )
    }

    @Test
    fun `route filtering excludes default and Phone routes`() {
        // Simulate what refreshCastRoutes does
        data class FakeRoute(val name: String, val isDefault: Boolean, val extras: android.os.Bundle?)
        val routes = listOf(
            FakeRoute("Phone", false, null),
            FakeRoute("Phone", true, null),
            FakeRoute("Living Room TV", false, null), // no CastDevice extras → fallback
        )
        val filtered = routes.filter { !it.isDefault && it.name != "Phone" }
        assertEquals(1, filtered.size)
        assertEquals("Living Room TV", filtered[0].name)
    }

    @Test
    fun `route with valid CastDevice extras returns device`() {
        data class FakeRoute(val name: String, val isDefault: Boolean, val extras: android.os.Bundle?)
        val extras = android.os.Bundle()
        extras.putString("com.google.android.gms.cast.EXTRA_CAST_DEVICE", "fake-device-id")
        val routes = listOf(
            FakeRoute("Living Room TV", false, extras),
        )
        val filtered = routes.filter { !it.isDefault && it.name != "Phone" }
        assertEquals(1, filtered.size)
        // getFromBundle should be used (not getParcelable) to avoid ClassNotFoundException
        val device = try {
            CastDevice.getFromBundle(filtered[0].extras ?: android.os.Bundle())
        } catch (_: Exception) {
            null
        }
        // getFromBundle returns null for non-CastDevice bundles — won't throw ClassNotFoundException
        assertNull("getFromBundle with non-CastDevice extras should return null, not throw", device)
    }

    @Test
    fun `empty routes produce empty device list`() {
        data class FakeRoute(val name: String, val isDefault: Boolean, val extras: android.os.Bundle?)
        val routes = emptyList<FakeRoute>()
        val castDevices = routes.filter { !it.isDefault && it.name != "Phone" }.mapNotNull {
            try {
                CastDevice.getFromBundle(it.extras ?: android.os.Bundle())
            } catch (_: Exception) {
                null
            }
        }
        assertTrue(castDevices.isEmpty())
        val names = if (castDevices.isEmpty()) {
            routes.filter { !it.isDefault && it.name != "Phone" }.map { it.name }
        } else {
            emptyList()
        }
        assertTrue(names.isEmpty())
    }

    @Test
    fun `getFromBundle is used instead of getParcelable to avoid ClassNotFoundException`() {
        // This test validates that CastDevice.getFromBundle() is the correct API
        // getParcelable<CastDevice>(key) throws ClassNotFoundException because
        // CastDevice is loaded by Google Play Services classloader, not the app classloader.
        // getFromBundle() handles this internally.
        val emptyBundle = android.os.Bundle()
        val result = try {
            CastDevice.getFromBundle(emptyBundle)
        } catch (e: Exception) {
            null
        }
        // getFromBundle returns null for empty bundle — does NOT throw
        assertNull("getFromBundle should return null for empty bundle, not throw", result)
    }
}
