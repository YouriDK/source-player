package com.source.player

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import java.io.File

@HiltAndroidApp
class SourceApplication : Application() {

        override fun onCreate() {
                super.onCreate()
                installCrashLogger()
        }

        /**
         * Persists the stack trace of any fatal crash to [CRASH_FILE] so MainActivity can
         * surface it on the next launch (copied to the clipboard). Release builds have no
         * logcat access from the device itself, so this is the only user-reachable crash
         * record without a USB debugging session.
         */
        private fun installCrashLogger() {
                val previous = Thread.getDefaultUncaughtExceptionHandler()
                Thread.setDefaultUncaughtExceptionHandler { thread, e ->
                        runCatching {
                                File(filesDir, CRASH_FILE)
                                        .writeText(
                                                "thread=${thread.name}\n" +
                                                        Log.getStackTraceString(e)
                                        )
                        }
                        previous?.uncaughtException(thread, e)
                }
        }

        companion object {
                const val CRASH_FILE = "last_crash.txt"
        }
}
