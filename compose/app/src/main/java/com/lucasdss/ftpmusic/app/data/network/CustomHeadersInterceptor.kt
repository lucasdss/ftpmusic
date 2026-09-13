package com.lucasdss.ftpmusic.app.data.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Injects custom HTTP headers into every request to the Subsonic server.
 * Headers are set by the user in Settings and persisted to SecureStorage.
 */
class CustomHeadersInterceptor : Interceptor {

    companion object {
        /** Max 5 custom headers. Index 0-4: key, 5-9: value. Stored as flat list. */
        @Volatile var headers: List<Pair<String, String>> = emptyList()

        /** Set from UI. Persist externally via SecureStorage. */
        fun updateHeaders(newHeaders: List<Pair<String, String>>) {
            headers = newHeaders.filter { it.first.isNotBlank() && it.second.isNotBlank() }.take(5)
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
        for ((key, value) in headers) {
            builder.addHeader(key, value)
        }
        return chain.proceed(builder.build())
    }
}
