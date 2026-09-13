package com.lucasdss.ftpmusic.app.ui.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConnectionInputTest {

    @Test
    fun `normalizes valid HTTPS input`() {
        val input = validateServerConnectionInput(
            "  https://music.example.com/navidrome/ ",
            " user ",
            "secret",
        ).getOrThrow()

        assertEquals("https://music.example.com/navidrome", input.url)
        assertEquals("user", input.username)
        assertEquals("secret", input.password)
        assertFalse(input.usesCleartext)
    }

    @Test
    fun `accepts HTTP only for private and local hosts`() {
        listOf(
            "http://10.0.0.1",
            "http://127.0.0.1",
            "http://192.168.1.4",
            "http://172.16.0.1",
            "http://172.31.255.255",
            "http://169.254.1.1",
            "http://music.local",
            "http://localhost",
            "http://[::1]",
            "http://[fd00::1]",
            "http://[fe80::1]",
            "http://[fe90::1]",
            "http://[fea0::1]",
            "http://[feb0::1]",
        ).forEach { url ->
            val result = validateServerConnectionInput(url, "user", "secret")
            assertTrue("$url should be accepted", result.isSuccess)
            assertTrue(result.getOrThrow().usesCleartext)
        }
    }

    @Test
    fun `rejects public and malformed HTTP hosts`() {
        listOf(
            "http://example.com",
            "http://8.8.8.8",
            "http://172.15.0.1",
            "http://172.32.0.1",
            "http://192.167.1.1",
            "http://300.1.1.1",
            "http://fcorp.com",
        ).forEach { url ->
            assertTrue(
                "$url should be rejected",
                validateServerConnectionInput(url, "user", "secret").isFailure,
            )
        }
    }

    @Test
    fun `rejects missing credentials and URL`() {
        assertEquals(
            "Server URL is required",
            validateServerConnectionInput("", "user", "secret").exceptionOrNull()?.message,
        )
        assertEquals(
            "Username is required",
            validateServerConnectionInput("https://example.com", "", "secret").exceptionOrNull()?.message,
        )
        assertEquals(
            "Password is required",
            validateServerConnectionInput("https://example.com", "user", "").exceptionOrNull()?.message,
        )
    }

    @Test
    fun `rejects unsupported or incomplete URLs`() {
        val cases = mapOf(
            "not a url" to "Enter a valid server URL",
            "https://exa mple.com" to "Enter a valid server URL",
            "ftp://example.com" to "Server URL must use HTTPS or HTTP",
            "https://" to "Enter a valid server URL",
            "https://user@example.com" to "Server URL must not contain credentials, query, or fragment",
            "https://example.com?x=1" to "Server URL must not contain credentials, query, or fragment",
            "https://example.com#part" to "Server URL must not contain credentials, query, or fragment",
        )

        cases.forEach { (url, expected) ->
            assertEquals(
                "$url error",
                expected,
                validateServerConnectionInput(url, "user", "secret").exceptionOrNull()?.message,
            )
        }
    }

    @Test
    fun `detects cleartext URL for UI warning`() {
        assertTrue(isCleartextServerUrl(" HTTP://192.168.1.2 "))
        assertFalse(isCleartextServerUrl("https://example.com"))
        assertFalse(isCleartextServerUrl("not a url"))
    }
}
