package com.lucasdss.ftpmusic.app.data.network

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Plain-JVM tests for the OkHttp interceptors in the network package.
 */
class NetworkInterceptorsTest {

    private fun chainWith(
        request: Request = Request.Builder().url("https://example.com/x").build(),
    ): Interceptor.Chain {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(any()) } answers { mockk<Response>(relaxed = true) }
        return chain
    }

    // ── CustomHeadersInterceptor ────────────────────────────────────────────

    @Test
    fun `updateHeaders filters blanks and caps at five`() {
        CustomHeadersInterceptor.updateHeaders(
            listOf(
                "a" to "1", "b" to "2", "c" to "3", "d" to "4", "e" to "5", "f" to "6",
                " " to "x", "g" to "", "" to "h",
            ),
        )
        assertEquals(5, CustomHeadersInterceptor.headers.size)
        assertEquals("a" to "1", CustomHeadersInterceptor.headers[0])
        assertEquals("e" to "5", CustomHeadersInterceptor.headers[4])
    }

    @Test
    fun `intercept adds every configured header`() {
        CustomHeadersInterceptor.updateHeaders(listOf("X-Custom" to "value1", "X-Other" to "value2"))
        val chain = chainWith()

        val builder = mockk<Request.Builder>(relaxed = true)
        every { chain.request().newBuilder() } returns builder
        every { builder.build() } returns Request.Builder().url("https://example.com/x").build()

        CustomHeadersInterceptor().intercept(chain)

        verify { builder.addHeader("X-Custom", "value1") }
        verify { builder.addHeader("X-Other", "value2") }
        verify { chain.proceed(any()) }
    }
}
