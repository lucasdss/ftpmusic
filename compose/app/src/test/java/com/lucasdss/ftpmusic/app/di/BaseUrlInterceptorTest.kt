package com.lucasdss.ftpmusic.app.di

import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BaseUrlInterceptor must NEVER send requests to the old dead phone proxy
 * (127.0.0.1:9999) or a blank base: unconfigured → typed ServerNotConfiguredException.
 */
class BaseUrlInterceptorTest {

    @After
    fun teardown() {
        DynamicBaseUrl.url = ""
        ReachabilityStateHolder.onApiSuccess() // restore optimistic default
    }

    private fun captureChain(original: Request): Pair<Interceptor.Chain, MutableList<Request>> {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns original
        val captured = mutableListOf<Request>()
        every { chain.proceed(any()) } answers {
            captured.add(firstArg())
            mockk<Response>(relaxed = true)
        }
        return chain to captured
    }

    @Test
    fun `unconfigured base throws ServerNotConfiguredException`() {
        DynamicBaseUrl.url = ""
        val (chain, _) = captureChain(Request.Builder().url("https://placeholder.invalid/rest/ping").build())
        val ex = assertThrows(ServerNotConfiguredException::class.java) {
            BaseUrlInterceptor().intercept(chain)
        }
        assertTrue(ex.message.orEmpty().contains("not configured"))
    }

    @Test
    fun `configured base rewrites host and keeps path and query`() {
        DynamicBaseUrl.url = "https://music.example.com"
        val original = Request.Builder().url("https://placeholder.invalid/rest/getAlbumList2?size=10").build()
        val (chain, captured) = captureChain(original)
        BaseUrlInterceptor().intercept(chain)
        assertEquals("https://music.example.com/rest/getAlbumList2?size=10", captured.first().url.toString())
    }

    @Test
    fun `loopback configured url is still routed (warning layer handles UX)`() {
        // The interceptor's job: never route to blank. A loopback config is the
        // UI's warning concern; here it must still rewrite (and the reachability
        // layer flips isReachable=false on connect failure).
        DynamicBaseUrl.url = "http://127.0.0.1:9999"
        val (chain, captured) = captureChain(Request.Builder().url("https://p.invalid/x").build())
        BaseUrlInterceptor().intercept(chain)
        assertEquals("http://127.0.0.1:9999/x", captured.first().url.toString())
    }
}
