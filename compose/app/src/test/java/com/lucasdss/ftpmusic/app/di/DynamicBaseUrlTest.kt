package com.lucasdss.ftpmusic.app.di

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The dead phone-local proxy sentinel (http://127.0.0.1:9999/) is gone:
 * the default URL is blank (not configured), loopback addresses are detected
 * and surfaced as warnings (never silently used as a server).
 */
class DynamicBaseUrlTest {

    @Before
    fun setup() {
        // Other test classes mutate the @Volatile static without resetting it;
        // reset BEFORE each test too, so ordering/fork changes can't leak state in.
        DynamicBaseUrl.url = ""
    }

    @After
    fun teardown() {
        DynamicBaseUrl.url = ""
    }

    @Test
    fun `default url is blank and not configured`() {
        assertEquals("", DynamicBaseUrl.url)
        assertFalse(DynamicBaseUrl.isConfigured())
    }

    @Test
    fun `setting a real server url marks it configured`() {
        DynamicBaseUrl.url = "https://music.example.com"
        assertTrue(DynamicBaseUrl.isConfigured())
    }

    @Test
    fun `loopback url is detected as loopback`() {
        DynamicBaseUrl.url = "http://127.0.0.1:9999/"
        assertTrue(DynamicBaseUrl.isLoopback())
    }

    @Test
    fun `real server url is not loopback`() {
        DynamicBaseUrl.url = "https://music.example.com"
        assertFalse(DynamicBaseUrl.isLoopback())
    }

    @Test
    fun `blank url is not loopback`() {
        DynamicBaseUrl.url = ""
        assertFalse(DynamicBaseUrl.isLoopback())
    }

    // ── isLoopbackUrl (pure helper) ─────────────────────────────────────────

    @Test
    fun `isLoopbackUrl detects localhost forms`() {
        assertTrue(isLoopbackUrl("http://127.0.0.1:9999/"))
        assertTrue(isLoopbackUrl("http://127.0.0.1"))
        assertTrue(isLoopbackUrl("http://127.0.0.1:9999/rest/stream?id=x"))
        assertTrue(isLoopbackUrl("http://localhost:8080"))
        assertTrue(isLoopbackUrl("http://0.0.0.0:4533"))
        assertTrue(isLoopbackUrl("http://127.8.9.10:80"))
    }

    @Test
    fun `isLoopbackUrl passes real and private-lan addresses`() {
        assertFalse(isLoopbackUrl("https://music.example.com"))
        assertFalse(isLoopbackUrl("https://music.example.com/rest/stream?id=x"))
        assertFalse(isLoopbackUrl("http://10.0.0.5:4533"))
        assertFalse(isLoopbackUrl("http://192.168.1.10"))
        assertFalse(isLoopbackUrl("http://sub.navidrome.example"))
    }

    @Test
    fun `isLoopbackUrl is safe on malformed or blank input`() {
        assertFalse(isLoopbackUrl(""))
        assertFalse(isLoopbackUrl("   "))
        assertFalse(isLoopbackUrl("not a url"))
        assertFalse(isLoopbackUrl("https://"))
    }
}
