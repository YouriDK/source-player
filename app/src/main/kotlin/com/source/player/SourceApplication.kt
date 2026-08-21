package com.source.player

import android.app.Application
import android.util.Log
import com.source.player.data.scanner.MediaStoreWatcher
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class SourceApplication : Application() {

        /**
         * Injected at the Application level so the MediaStore observer lives for the whole
         * process, not just while a particular screen is composed — a file dropped in while
         * the user sits on Settings still refreshes the library.
         */
        @Inject lateinit var mediaStoreWatcher: MediaStoreWatcher

        override fun onCreate() {
                super.onCreate()
                installCrashLogger()
                mediaStoreWatcher.start()
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
