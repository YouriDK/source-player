package com.source.player.data.scanner

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Keeps the library in sync with the filesystem while the app is running.
 *
 * MediaStore notifies its observers whenever a track is added, removed or re-tagged —
 * by this app's tag editor, by another app, or by the system media scanner after a file
 * lands over USB/Bluetooth/download. Without this, the DB only ever changed when the
 * user manually hit "Scan", so edits made outside the app stayed invisible.
 *
 * Notifications arrive in bursts (a single file copy can fire a dozen), so they are
 * debounced: one scan runs [DEBOUNCE_MS] after the last event of a burst.
 */
@Singleton
class MediaStoreWatcher
@Inject
constructor(
        @ApplicationContext private val context: Context,
        private val scanner: MediaScanner,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var observer: ContentObserver? = null

    @OptIn(FlowPreview::class)
    fun start() {
        if (observer != null) return

        val handler = Handler(Looper.getMainLooper())
        val contentObserver =
                object : ContentObserver(handler) {
                    override fun onChange(selfChange: Boolean, uri: Uri?) {
                        // tryEmit on a conflated buffer: dropping a duplicate mid-burst is
                        // harmless, the debounced trailing event still triggers the scan.
                        changes.tryEmit(Unit)
                    }
                }

        // notifyForDescendants = true — item URIs (…/audio/media/1234) are children of
        // the collection URI and would otherwise not reach the observer.
        context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                /* notifyForDescendants = */ true,
                contentObserver,
        )
        observer = contentObserver

        scope.launch { changes.debounce(DEBOUNCE_MS).collect { scanner.scan() } }
    }

    fun stop() {
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
    }

    private companion object {
        const val DEBOUNCE_MS = 1_500L
    }
}
