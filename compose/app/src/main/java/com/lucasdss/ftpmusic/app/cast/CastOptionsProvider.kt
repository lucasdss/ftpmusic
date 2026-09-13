package com.lucasdss.ftpmusic.app.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions = CastOptions.Builder()
        .setReceiverApplicationId("CC1AD845")
        .setStopReceiverApplicationWhenEndingSession(false)
        .setResumeSavedSession(true)
        .build()

    override fun getAdditionalSessionProviders(context: Context): MutableList<SessionProvider>? = null
}
