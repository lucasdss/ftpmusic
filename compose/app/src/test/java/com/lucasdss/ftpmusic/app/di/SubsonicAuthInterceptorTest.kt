package com.lucasdss.ftpmusic.app.di

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Plain-JVM tests for [SubsonicAuthInterceptor]'s add-if-absent behaviour. */
class SubsonicAuthInterceptorTest {

    @After
    fun tearDown() {
        SubsonicCredentials.username = ""
        SubsonicCredentials.password = ""
    }

    private fun chainWith(request: Request): Interceptor.Chain {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(any()) } answers { mockk<Response>(relaxed = true) }
        return chain
    }

    private fun interceptedUrl(request: Request): okhttp3.HttpUrl {
        val chain = chainWith(request)
        SubsonicAuthInterceptor().intercept(chain)
        val captured = slot<Request>()
        verify { chain.proceed(capture(captured)) }
        return captured.captured.url
    }

    @Test
    fun `adds auth params when absent`() {
        SubsonicCredentials.username = "user"
        SubsonicCredentials.password = "pass"

        val url = interceptedUrl(Request.Builder().url("https://example.com/rest/ping").build())

        assertEquals("user", url.queryParameter("u"))
        assertEquals(1, url.queryParameterValues("u").size)
        assertEquals("ftpmusic", url.queryParameter("c"))
        assertEquals("1.16.1", url.queryParameter("v"))
    }

    @Test
    fun `does not duplicate params already present`() {
        SubsonicCredentials.username = "user"
        SubsonicCredentials.password = "pass"
        val request = Request.Builder()
            .url("https://example.com/rest/getAlbumList2?u=user&t=abc&s=123&v=1.16.1&c=ftpmusic&f=json")
            .build()

        val url = interceptedUrl(request)

        assertEquals(1, url.queryParameterValues("u").size)
        assertEquals(1, url.queryParameterValues("t").size)
        assertEquals(1, url.queryParameterValues("s").size)
        assertEquals("abc", url.queryParameter("t"))
    }

    @Test
    fun `passes through unauthenticated when username is blank`() {
        SubsonicCredentials.username = ""
        val request = Request.Builder().url("https://example.com/rest/ping").build()

        val url = interceptedUrl(request)

        assertEquals(null, url.queryParameter("u"))
    }
}
