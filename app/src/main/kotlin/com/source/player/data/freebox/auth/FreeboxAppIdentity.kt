package com.source.player.data.freebox.auth

import android.os.Build
import com.source.player.BuildConfig

/** Identity sent to the box at pairing and login (shown on the Freebox display). */
object FreeboxAppIdentity {
    const val APP_ID = "com.source.player"
    const val APP_NAME = "Source"
    val APP_VERSION: String = BuildConfig.VERSION_NAME
    val DEVICE_NAME: String get() = Build.MODEL.ifBlank { "Android phone" }
}
