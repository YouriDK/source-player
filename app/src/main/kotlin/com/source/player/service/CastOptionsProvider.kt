package com.source.player.service

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions

/**
 * Registers this app with the Cast SDK using Google's Default Media Receiver.
 * This allows streaming audio to any Chromecast device on the local network
 * without needing to register a custom Cast app.
 */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
            CastOptions.Builder()
                    // CC1AD845 = Google's Default Media Receiver, supports audio streaming
                    .setReceiverApplicationId("CC1AD845")
                    .setCastMediaOptions(
                            CastMediaOptions.Builder()
                                    .setMediaSessionEnabled(false) // We manage MediaSession ourselves
                                    .build()
                    )
                    .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
