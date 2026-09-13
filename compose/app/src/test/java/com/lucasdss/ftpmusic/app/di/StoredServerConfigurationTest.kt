package com.lucasdss.ftpmusic.app.di

import com.lucasdss.ftpmusic.app.data.security.SecretRead
import com.lucasdss.ftpmusic.app.data.security.SecretStore
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StoredServerConfigurationTest {

    /** In-memory [SecretStore] that can be told to fail specific reads. */
    private class FakeSecretStore(
        private val values: MutableMap<String, String> = mutableMapOf(),
        private val failingKeys: MutableSet<String> = mutableSetOf(),
    ) : SecretStore {
        override fun get(key: String): String? = values[key]

        override fun read(key: String): SecretRead = when {
            key in failingKeys -> SecretRead.Failed(IllegalStateException("keystore unavailable"))
            values[key] != null -> SecretRead.Ok(values[key]!!)
            else -> SecretRead.Missing
        }

        override fun put(key: String, value: String) {
            values[key] = value
        }

        override fun putAll(values: Map<String, String>) {
            this.values.putAll(values)
        }

        override fun remove(key: String) {
            values.remove(key)
        }

        fun failOn(key: String) {
            failingKeys.add(key)
        }

        fun recover(key: String) {
            failingKeys.remove(key)
        }
    }

    @After
    fun tearDown() {
        DynamicBaseUrl.url = ""
        SubsonicCredentials.username = ""
        SubsonicCredentials.password = ""
        ServerConfigState.value = ServerConfig()
    }

    @Test
    fun `legacy restore makes persisted server configuration available before first request`() {
        val storage = mockk<SecureStorage>()
        every { storage.get(SecureStorage.KEY_URL) } returns "https://music.example/"
        every { storage.get(SecureStorage.KEY_USERNAME) } returns "listener"
        every { storage.get(SecureStorage.KEY_PASSWORD) } returns "secret"

        restoreStoredServerConfiguration(storage)

        assertEquals("https://music.example", DynamicBaseUrl.url)
        assertEquals("listener", SubsonicCredentials.username)
        assertEquals("secret", SubsonicCredentials.password)
    }

    @Test
    fun `legacy restore clears stale runtime configuration when storage is empty`() {
        DynamicBaseUrl.url = "https://stale.example"
        SubsonicCredentials.username = "stale-user"
        SubsonicCredentials.password = "stale-password"
        val storage = mockk<SecureStorage>()
        every { storage.get(any()) } returns null

        restoreStoredServerConfiguration(storage)

        assertEquals("", DynamicBaseUrl.url)
        assertEquals("", SubsonicCredentials.username)
        assertEquals("", SubsonicCredentials.password)
    }

    @Test
    fun `legacy restore does not publish partial configuration when storage read fails`() {
        DynamicBaseUrl.url = "https://current.example"
        SubsonicCredentials.username = "current-user"
        SubsonicCredentials.password = "current-password"
        val storage = mockk<SecureStorage>()
        every { storage.get(SecureStorage.KEY_URL) } returns "https://new.example"
        every { storage.get(SecureStorage.KEY_USERNAME) } throws IllegalStateException("keystore unavailable")

        assertThrows(IllegalStateException::class.java) {
            restoreStoredServerConfiguration(storage)
        }

        assertEquals("https://current.example", DynamicBaseUrl.url)
        assertEquals("current-user", SubsonicCredentials.username)
        assertEquals("current-password", SubsonicCredentials.password)
    }

    @Test
    fun `store initialize publishes persisted config to globals and compose state`() {
        val storage = FakeSecretStore(
            mutableMapOf(
                SecureStorage.KEY_URL to "https://music.example/",
                SecureStorage.KEY_USERNAME to "listener",
                SecureStorage.KEY_PASSWORD to "secret",
            ),
        )
        val store = ServerConfigStore(storage)

        val config = store.initialize()

        assertEquals("https://music.example", config.url)
        assertEquals("https://music.example", DynamicBaseUrl.url)
        assertEquals("listener", SubsonicCredentials.username)
        assertEquals("secret", SubsonicCredentials.password)
        assertEquals(config, ServerConfigState.value)
        assertEquals(config, store.state.value)
    }

    @Test
    fun `store initialize keeps current config on transient read failure`() {
        DynamicBaseUrl.url = "https://current.example"
        SubsonicCredentials.username = "current-user"
        SubsonicCredentials.password = "current-password"
        ServerConfigState.value = ServerConfig("https://current.example", "current-user", "current-password")

        val storage = FakeSecretStore(
            mutableMapOf(
                SecureStorage.KEY_URL to "https://new.example",
                SecureStorage.KEY_USERNAME to "new-user",
                SecureStorage.KEY_PASSWORD to "new-pass",
            ),
        )
        storage.failOn(SecureStorage.KEY_USERNAME)
        val store = ServerConfigStore(storage)

        val config = store.initialize()

        // Failure must never clobber the working config…
        assertEquals("https://current.example", config.url)
        assertEquals("https://current.example", DynamicBaseUrl.url)
        assertEquals("current-user", SubsonicCredentials.username)
        assertEquals("current-password", SubsonicCredentials.password)

        // …and a later call retries and succeeds.
        storage.recover(SecureStorage.KEY_USERNAME)
        val retried = store.initialize()
        assertEquals("https://new.example", retried.url)
        assertEquals("new-user", SubsonicCredentials.username)
    }

    @Test
    fun `store set persists all keys atomically and publishes`() {
        val storage = FakeSecretStore()
        val store = ServerConfigStore(storage)

        store.set("https://music.example/", "listener", "secret")

        assertEquals("https://music.example", storage.get(SecureStorage.KEY_URL))
        assertEquals("listener", storage.get(SecureStorage.KEY_USERNAME))
        assertEquals("secret", storage.get(SecureStorage.KEY_PASSWORD))
        assertEquals("https://music.example", DynamicBaseUrl.url)
        assertEquals("listener", SubsonicCredentials.username)
        assertEquals("secret", SubsonicCredentials.password)
    }

    @Test
    fun `Retrofit graph construction restores configuration before exposing API client`() {
        val storage = FakeSecretStore(
            mutableMapOf(
                SecureStorage.KEY_URL to "https://music.example/",
                SecureStorage.KEY_USERNAME to "listener",
                SecureStorage.KEY_PASSWORD to "secret",
            ),
        )
        val store = ServerConfigStore(storage)

        val retrofit = NetworkModule.provideRetrofit(OkHttpClient(), store)

        assertEquals("https://music.example/", retrofit.baseUrl().toString())
        assertEquals("https://music.example", DynamicBaseUrl.url)
        assertEquals("listener", SubsonicCredentials.username)
    }
}
