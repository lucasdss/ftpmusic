package com.lucasdss.ftpmusic.app.di

import com.lucasdss.ftpmusic.app.BuildConfig
import com.lucasdss.ftpmusic.app.data.network.CustomHeadersInterceptor
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.math.BigInteger
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object DynamicBaseUrl {
    /**
     * Base URL of the configured Subsonic/Navidrome server. Blank = not
     * configured. The old device-local proxy default ("http://127.0.0.1:9999/")
     * is GONE — requests must never silently target a dead loopback proxy.
     */
    @Volatile var url: String = ""

    /** True once a server URL has been configured (blank = not configured). */
    fun isConfigured(): Boolean = url.isNotBlank()

    /**
     * True when the configured URL points at the device itself (127.0.0.1 /
     * localhost / 0.0.0.0). Almost always a stale dev proxy config; surfaced
     * as a warning, never blocked (adb-reverse dev setups are legitimate).
     */
    fun isLoopback(): Boolean = isLoopbackUrl(url)
}

/**
 * True when [url] points at the device itself (127.0.0.1 / localhost /
 * 0.0.0.0 / any 127.x). Used for live warnings on user-typed URLs.
 * java.net.URI so plain-JVM tests can exercise it (android.net.Uri is
 * "not mocked" off-device).
 */
fun isLoopbackUrl(url: String): Boolean {
    val host = try {
        java.net.URI(url.trimEnd('/')).host
    } catch (_: Exception) {
        null
    } ?: return false
    return host == "127.0.0.1" || host == "localhost" || host == "0.0.0.0" || host.startsWith("127.")
}

object SubsonicCredentials {
    @Volatile var username: String = ""

    @Volatile var password: String = ""
}

/**
 * Restores process-local request configuration from encrypted storage.
 *
 * Legacy entry point kept for callers/tests that only have a [SecureStorage].
 * Production paths use [ServerConfigStore], which publishes an observable
 * snapshot and never clears a working config on a transient read failure.
 */
fun restoreStoredServerConfiguration(storage: SecureStorage) {
    val url = storage.get(SecureStorage.KEY_URL).orEmpty().trimEnd('/')
    val username = storage.get(SecureStorage.KEY_USERNAME).orEmpty()
    val password = storage.get(SecureStorage.KEY_PASSWORD).orEmpty()
    DynamicBaseUrl.url = url
    SubsonicCredentials.username = username
    SubsonicCredentials.password = password
}

/**
 * Thrown by the OkHttp layer when no server URL is configured. The UI maps
 * this to a "connect your server" state — it must never reach a loopback or
 * placeholder address.
 */
class ServerNotConfiguredException :
    java.io.IOException("Server URL not configured — connect to your server in Settings")

/**
 * Auto-injects Subsonic auth params (u, t, s, v, c, f) into every request.
 * Existing query params are left untouched — callers that already pass auth
 * params (e.g. the workers) must not end up with duplicated `u`/`t`/`s` keys.
 */
class SubsonicAuthInterceptor : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val original = chain.request()
        val username = SubsonicCredentials.username
        if (username.isEmpty()) return chain.proceed(original)

        val salt = (100000..999999).random().toString()
        val token = md5("${SubsonicCredentials.password}$salt")

        val existing = original.url
        val builder = existing.newBuilder()
        fun addIfAbsent(name: String, value: String) {
            if (existing.queryParameter(name) == null) builder.addQueryParameter(name, value)
        }
        addIfAbsent("u", username)
        addIfAbsent("t", token)
        addIfAbsent("s", salt)
        addIfAbsent("v", "1.16.1")
        addIfAbsent("c", "ftpmusic")
        addIfAbsent("f", "json")

        return chain.proceed(original.newBuilder().url(builder.build()).build())
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return BigInteger(1, digest).toString(16).padStart(32, '0')
    }
}

class BaseUrlInterceptor : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        // No configured server → fail fast with a typed, UI-mappable error.
        // Never proceed to the blank/placeholder base, and never to a loopback
        // proxy (the old 127.0.0.1:9999 default is removed).
        if (!DynamicBaseUrl.isConfigured()) {
            throw ServerNotConfiguredException()
        }
        var request = chain.request()
        val newUrl = DynamicBaseUrl.url.toHttpUrlOrNull()
        if (newUrl != null) {
            val oldUrl = request.url
            request = request.newBuilder()
                .url(
                    newUrl.newBuilder()
                        .encodedPath(oldUrl.encodedPath)
                        .encodedQuery(oldUrl.encodedQuery)
                        .build(),
                )
                .build()
        }
        return try {
            val response = chain.proceed(request)
            ReachabilityStateHolder.onApiSuccess()
            response
        } catch (e: java.net.ConnectException) {
            ReachabilityStateHolder.onApiFailure()
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            ReachabilityStateHolder.onApiFailure()
            throw e
        } catch (e: java.net.UnknownHostException) {
            ReachabilityStateHolder.onApiFailure()
            throw e
        } catch (e: java.net.SocketException) {
            // DNS failures, network unreachable — real connectivity issues
            ReachabilityStateHolder.onApiFailure()
            throw e
        } catch (e: java.io.InterruptedIOException) {
            // Request cancelled (e.g., coroutine scope closed) — NOT a reachability issue
            throw e
        } catch (e: java.io.IOException) {
            // Other I/O errors (e.g., connection reset) — treat as unreachable
            ReachabilityStateHolder.onApiFailure()
            throw e
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(SubsonicAuthInterceptor())
        .addInterceptor(BaseUrlInterceptor())
        .addInterceptor(CustomHeadersInterceptor())
        .apply {
            if (BuildConfig.DEBUG) {
                // BASIC logs the full request line including auth query
                // params — debug-only, never in release builds.
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    },
                )
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideSecretStore(storage: SecureStorage): com.lucasdss.ftpmusic.app.data.security.SecretStore = storage

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, configStore: ServerConfigStore): Retrofit {
        // Hilt constructs Retrofit before injecting any API consumer. Restore
        // process-local config here so no Retrofit request can win startup race.
        // A transient storage failure leaves the previous config intact and the
        // store uninitialized, so Application/splash/service can retry.
        try {
            configStore.initialize()
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-network", "Server configuration restore failed: ${e.message}")
        }
        // BaseUrl is rewritten per-call by BaseUrlInterceptor; the placeholder
        // only satisfies Retrofit's builder (blank would throw at DI time).
        val base = DynamicBaseUrl.url.ifBlank { "http://unconfigured.invalid/" }
        return Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideSubsonicApi(retrofit: Retrofit): SubsonicApi = retrofit.create(SubsonicApi::class.java)
}
