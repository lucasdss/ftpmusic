package com.lucasdss.ftpmusic.app.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Real-SecureStorage tests on the plain JVM (no Robolectric — JaCoCo does not
 * capture Robolectric-sandbox classes in this project). Robolectric has no
 * AndroidKeyStore and plain JUnit has no android runtime, so the
 * [MasterKeys]/[EncryptedSharedPreferences] statics are mocked to back the
 * storage with an in-memory [SharedPreferences] fake. Covers the
 * read/write surface (put/get/remove round-trips), the removed-feature key
 * purge, and the "keystore corrupted → delete and recreate" path.
 */
class SecureStorageInstanceTest {

    private val context: Context = mockk(relaxed = true)

    init {
        // Static mocks are set up ONCE per class — repeated per-test
        // redefinition of JVM statics churns the javaagent (see the
        // java.lang.instrument assertion failures under the full suite).
        mockkStatic(MasterKeys::class)
        mockkStatic(EncryptedSharedPreferences::class)
        every { MasterKeys.getOrCreate(any<KeyGenParameterSpec>()) } returns "test_master_key_alias"
        stubCreateReturnsDefault()
    }

    private fun stubCreateReturnsDefault() {
        every {
            EncryptedSharedPreferences.create(
                any<String>(),
                any<String>(),
                any<Context>(),
                any<EncryptedSharedPreferences.PrefKeyEncryptionScheme>(),
                any<EncryptedSharedPreferences.PrefValueEncryptionScheme>(),
            )
        } returns InMemoryPrefs()
    }

    @After
    fun tearDown() {
        // No static state to clear; instances are per-test.
    }

    @Test
    fun `put and get roundtrip`() {
        val storage = SecureStorage(context)
        storage.put("test_key", "hello")
        assertEquals("hello", storage.get("test_key"))
    }

    @Test
    fun `put overwrites existing value`() {
        val storage = SecureStorage(context)
        storage.put("overwrite_key", "first")
        storage.put("overwrite_key", "second")
        assertEquals("second", storage.get("overwrite_key"))
    }

    @Test
    fun `get returns null for missing key`() {
        val storage = SecureStorage(context)
        assertNull(storage.get("missing_key"))
    }

    @Test
    fun `remove deletes the value`() {
        val storage = SecureStorage(context)
        storage.put("rm_key", "v")
        storage.remove("rm_key")
        assertNull(storage.get("rm_key"))
    }

    @Test
    fun `read distinguishes missing from present values`() {
        val storage = SecureStorage(context)
        storage.put("read_key", "value")
        assertEquals(SecretRead.Ok("value"), storage.read("read_key"))
        assertEquals(SecretRead.Missing, storage.read("read_missing"))
    }

    @Test
    fun `putAll writes every key in one transaction`() {
        val storage = SecureStorage(context)
        storage.putAll(mapOf("a" to "1", "b" to "2"))
        assertEquals("1", storage.get("a"))
        assertEquals("2", storage.get("b"))

        storage.putAll(emptyMap())
        assertEquals("1", storage.get("a"))
    }

    @Test
    fun `removeRemoteSourceKeys purges new and legacy remote-source keys`() {
        val storage = SecureStorage(context)
        listOf(
            "remote_source_header_name",
            "remote_source_header_value",
            "remote_source_url",
            "api_base_path",
            "yt_header_name",
            "yt_header_value",
            "yt_server_url",
        ).forEach { storage.put(it, "secret") }
        storage.put("server_url", "keep-me")

        storage.removeRemoteSourceKeys()

        listOf(
            "remote_source_header_name",
            "remote_source_header_value",
            "remote_source_url",
            "api_base_path",
            "yt_header_name",
            "yt_header_value",
            "yt_server_url",
        ).forEach { assertNull(storage.get(it)) }
        assertEquals("keep-me", storage.get("server_url"))
    }

    @Test
    fun `removeRemoteSourceKeys no-ops on clean storage`() {
        val storage = SecureStorage(context)
        storage.removeRemoteSourceKeys()
        assertNull(storage.get("remote_source_url"))
        assertNull(storage.get("yt_server_url"))
    }

    @Test
    fun `methods degrade gracefully when encrypted prefs creation fails`() {
        // Simulate keystore corruption: every create throws, so the internal
        // lazy delete-and-recreate also fails. All public methods must no-op
        // without throwing, and reads must return null.
        every {
            EncryptedSharedPreferences.create(
                any<String>(),
                any<String>(),
                any<Context>(),
                any<EncryptedSharedPreferences.PrefKeyEncryptionScheme>(),
                any<EncryptedSharedPreferences.PrefValueEncryptionScheme>(),
            )
        } throws RuntimeException("simulated keystore corruption")
        val storage = SecureStorage(context)

        storage.put("k", "v")
        assertNull(storage.get("k"))
        assertTrue(storage.read("k") is SecretRead.Failed)
        storage.remove("k")
        storage.removeRemoteSourceKeys()
        // Restore the shared default stub for any later test in this class
        stubCreateReturnsDefault()
    }

    /** Minimal in-memory SharedPreferences used as the storage backing. */
    private class InMemoryPrefs : SharedPreferences {
        private val store = HashMap<String, Any?>()

        override fun getAll(): Map<String, *> = store.toMap()
        override fun getString(key: String, defValue: String?): String? = store[key] as String? ?: defValue

        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
            store[key] as Set<String>? ?: defValues

        override fun getInt(key: String, defValue: Int): Int = (store[key] as? Number)?.toInt() ?: defValue

        override fun getLong(key: String, defValue: Long): Long = (store[key] as? Number)?.toLong() ?: defValue

        override fun getFloat(key: String, defValue: Float): Float = (store[key] as? Number)?.toFloat() ?: defValue

        override fun getBoolean(key: String, defValue: Boolean): Boolean = store[key] as Boolean? ?: defValue

        override fun contains(key: String): Boolean = store.containsKey(key)

        override fun edit(): SharedPreferences.Editor = Editor()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val staged = HashMap<String, Any?>()
            private val removed = HashSet<String>()

            override fun putString(key: String, value: String?): SharedPreferences.Editor =
                apply { staged[key] = value }

            override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor =
                apply { staged[key] = values }

            override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { staged[key] = value }

            override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { staged[key] = value }

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { staged[key] = value }

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor =
                apply { staged[key] = value }

            override fun remove(key: String): SharedPreferences.Editor = apply { removed.add(key) }

            override fun clear(): SharedPreferences.Editor = apply {
                staged.clear()
                store.clear()
                removed.clear()
            }

            override fun commit(): Boolean {
                applyChanges()
                return true
            }

            override fun apply() = applyChanges()

            private fun applyChanges() {
                removed.forEach { store.remove(it) }
                removed.clear()
                staged.forEach { (k, v) -> store[k] = v }
                staged.clear()
            }
        }
    }
}
