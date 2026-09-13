package com.lucasdss.ftpmusic.app.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.lucasdss.ftpmusic.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of a secret read that distinguishes "key absent" from "read failed".
 * A failed read (e.g. keystore momentarily unavailable, locked credential
 * storage) must never be treated as "no configuration" — that would silently
 * wipe working in-memory state.
 */
sealed interface SecretRead {
    data class Ok(val value: String) : SecretRead

    data object Missing : SecretRead

    data class Failed(val cause: Exception) : SecretRead
}

/** Minimal key-value secret storage. Implemented by [SecureStorage]. */
interface SecretStore {
    fun get(key: String): String?

    fun read(key: String): SecretRead

    fun put(key: String, value: String)

    /** Write several keys in one durable transaction (no torn state). */
    fun putAll(values: Map<String, String>)

    fun remove(key: String)
}

@Singleton
class SecureStorage @Inject constructor(@ApplicationContext private val context: Context) : SecretStore {
    private val prefs by lazy {
        try {
            val key = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "ftpmusic_secure",
                key,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            // Key invalidated (e.g. after force-stop + reinstall) — delete and recreate
            android.util.Log.w("ftpmusic-secure", "Encrypted prefs corrupted, recreating: ${e.message}")
            try {
                context.deleteSharedPreferences("ftpmusic_secure")
            } catch (_: Exception) {}
            val key = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "ftpmusic_secure",
                key,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }

    override fun put(key: String, value: String) {
        try {
            // commit() (not apply()) — credentials must survive an immediate
            // process kill after "Settings saved".
            prefs.edit().putString(key, value).commit()
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-secure", "put($key) failed: ${e.javaClass.simpleName}")
        }
    }

    override fun putAll(values: Map<String, String>) {
        if (values.isEmpty()) return
        try {
            val editor = prefs.edit()
            values.forEach { (k, v) -> editor.putString(k, v) }
            editor.commit()
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-secure", "putAll failed: ${e.javaClass.simpleName}")
        }
    }

    override fun get(key: String): String? = when (val result = read(key)) {
        is SecretRead.Ok -> result.value
        SecretRead.Missing -> null
        is SecretRead.Failed -> null
    }

    override fun read(key: String): SecretRead = try {
        val value = prefs.getString(key, null)
        if (BuildConfig.IMAGE_DIAGNOSTICS) {
            android.util.Log.w(
                "ftpmusic-secure",
                "[diag] read($key) -> ${if (value == null) "MISSING" else "ok(len=${value.length})"}",
            )
        }
        if (value == null) SecretRead.Missing else SecretRead.Ok(value)
    } catch (e: Exception) {
        if (BuildConfig.IMAGE_DIAGNOSTICS) {
            android.util.Log.w(
                "ftpmusic-secure",
                "[diag] read($key) FAILED: ${e.javaClass.simpleName}: ${e.message}",
            )
        }
        SecretRead.Failed(e)
    }

    override fun remove(key: String) {
        try {
            prefs.edit().remove(key).commit()
        } catch (e: Exception) {
            android.util.Log.w("ftpmusic-secure", "remove($key) failed: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Purge credentials stored by the REMOVED Remote Library Management
     * (yt-dlp) feature. Keys may linger in encrypted prefs from pre-1.0.0
     * configurations; the feature is gone, so delete them on startup.
     * Guarded no-op — storage failures must never crash startup.
     */
    fun removeRemoteSourceKeys() {
        listOf(
            "remote_source_header_name",
            "remote_source_header_value",
            "remote_source_url",
            "api_base_path",
            // Pre-1.0.0 legacy key names (first Play release resets versioning)
            "yt_header_name",
            "yt_header_value",
            "yt_server_url",
        ).forEach { remove(it) }
    }

    companion object {
        const val KEY_PASSWORD = "server_password"
        const val KEY_URL = "server_url"
        const val KEY_USERNAME = "server_username"
        const val KEY_QUEUE_JOURNAL_CAP = "queue_journal_cap"
        const val KEY_CONTINUOUS_PLAY_ENABLED = "continuous_play_enabled"
        const val KEY_QUOTA_MB = "quota_mb"
        const val KEY_COVER_ART_QUOTA_MB = "cover_art_quota_mb"
        const val KEY_AUDIO_CACHE_MAX_BYTES = "audio_cache_max_bytes"
        const val KEY_SYNC_INTERVAL_HOURS = "sync_interval_hours"
        const val KEY_PREFER_ITUNES_ART = "prefer_itunes_art"
        const val KEY_LASTFM_API_KEY = "lastfm_api_key"
        const val KEY_QUEUE_OVERWRITE_BEHAVIOR = "queue_overwrite_behavior"

        /** Software offline mode toggle — persisted so it survives process death. */
        const val KEY_OFFLINE_MODE = "offline_mode"

        // v43: Home section visibility toggles (Settings → Home & Favorites)
        const val KEY_HOME_SHOW_PLAYLISTS = "home_show_playlists"
        const val KEY_HOME_SHOW_FAV_ARTISTS = "home_show_fav_artists"
        const val KEY_HOME_SHOW_FAV_ALBUMS = "home_show_fav_albums"
        const val KEY_HOME_SHOW_FAV_RADIO = "home_show_fav_radio"

        // v46: Playback notifications feature toggle (Settings → Notifications).
        // OFF keeps the FGS-satisfying minimal notification, without media
        // controls/artwork (lock-screen controls disappear).
        const val KEY_PLAYBACK_NOTIFICATIONS = "playback_notifications"

        // v47: "Download on Wi-Fi only" persisted so auto-cache/downloads keep
        // respecting it across process death (DownloadManager reads it at start).
        const val KEY_DOWNLOAD_MOBILE_DATA = "download_mobile_data"
    }
}
