package com.lucasdss.ftpmusic.app.playback

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CastPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("ftpmusic_cast", Context.MODE_PRIVATE)

    var castFromPhone: Boolean
        get() = prefs.getBoolean("cast_from_phone", false)
        set(value) {
            prefs.edit().putBoolean("cast_from_phone", value).apply()
        }

    var useHttpForCast: Boolean
        get() = prefs.getBoolean("cast_use_http", true)
        set(value) {
            prefs.edit().putBoolean("cast_use_http", value).apply()
        }
}
