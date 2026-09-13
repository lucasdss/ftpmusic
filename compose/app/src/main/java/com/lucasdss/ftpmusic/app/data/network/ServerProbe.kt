package com.lucasdss.ftpmusic.app.data.network

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Probes a candidate server without touching the active configuration.
 *
 * Building a throwaway Retrofit keeps the global base URL / credentials
 * untouched, so a failed "test connection" or connect attempt can never leave
 * the app pointing at a broken server.
 */
@Singleton
class ServerProbe @Inject constructor() {

    /** Throws on any connection/auth failure; returns normally on success. */
    suspend fun ping(serverUrl: String, username: String, password: String) {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        val api = Retrofit.Builder()
            .baseUrl(serverUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SubsonicApi::class.java)
        val auth = SubsonicAuthHelper().buildAuthParams(username, password)
        api.ping(username, auth["t"]!!, auth["s"]!!)
    }
}
