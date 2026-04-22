package com.source.player.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.android.gms.cast.framework.CastContext
import com.source.player.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * SourcePlaybackService — owns both [ExoPlayer] (local) and [CastPlayer] (Chromecast).
 *
 * When a Cast session becomes available the active player in the [MediaSession] is swapped
 * from [ExoPlayer] to [CastPlayer], seamlessly transferring the current queue and position.
 * When the session ends it switches back the same way.
 *
 * Audio files are served to the Chromecast via [LocalAudioHttpServer].
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class SourcePlaybackService : MediaSessionService() {

    @Inject lateinit var localAudioHttpServer: LocalAudioHttpServer
    @Inject lateinit var sonosManager: SonosManager

    private val volumeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var mediaSession: MediaSession? = null
    private var localPlayer: ExoPlayer? = null
    private var castPlayer: CastPlayer? = null
    /** Sonos-aware wrappers — reused so identity checks in transferTo still hold. */
    private var localWrapped: SonosAwarePlayer? = null
    private var castWrapped: SonosAwarePlayer? = null

    // Source-of-truth queue with original file:// URIs (never HTTP-converted)
    private var originalQueue: List<MediaItem> = emptyList()
    private var originalQueueIndex: Int = 0

    override fun onCreate() {
        super.onCreate()

        val audioAttributes =
                AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build()

        localPlayer =
                ExoPlayer.Builder(this)
                        .setAudioAttributes(audioAttributes, true)
                        .setHandleAudioBecomingNoisy(true)
                        .build()

        // Initialize Cast — may throw if Play Services unavailable; fall back gracefully
        try {
            val castContext = CastContext.getSharedInstance(this)
            castPlayer =
                    CastPlayer(castContext).apply {
                        setSessionAvailabilityListener(
                                object : SessionAvailabilityListener {
                                    override fun onCastSessionAvailable() {
                                        transferTo(castWrapped ?: return)
                                    }

                                    override fun onCastSessionUnavailable() {
                                        transferTo(localWrapped ?: return)
                                    }
                                }
                        )
                    }
        } catch (_: Exception) {
            // Play Services not available — cast is simply disabled
        }

        // Wrap so MediaSession volume calls route to Sonos when active.
        localWrapped = SonosAwarePlayer(localPlayer!!, sonosManager, volumeScope)
        castWrapped = castPlayer?.let { SonosAwarePlayer(it, sonosManager, volumeScope) }

        val initialPlayer: Player =
                if (castPlayer?.isCastSessionAvailable == true) castWrapped!! else localWrapped!!

        val sessionActivityIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                        },
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )

        mediaSession =
                MediaSession.Builder(this, initialPlayer)
                        .setSessionActivity(sessionActivityIntent)
                        .setCallback(SourceMediaSessionCallback())
                        .build()
    }

    // ---- Player transfer ----

    private fun transferTo(target: Player) {
        val current = mediaSession?.player ?: return
        if (current === target) return

        val positionMs = current.currentPosition
        val itemIndex = current.currentMediaItemIndex
        val playWhenReady = current.playWhenReady

        // Keep original queue up-to-date
        originalQueue = (0 until current.mediaItemCount).map { current.getMediaItemAt(it) }
        originalQueueIndex = itemIndex

        current.stop()

        val items =
                if (target === castWrapped) {
                    // Chromecast needs HTTP URLs — start server and convert
                    localAudioHttpServer.start()
                    originalQueue.map { item ->
                        val uri = item.localConfiguration?.uri?.toString() ?: return@map item
                        val httpUrl =
                                if (uri.startsWith("file://") || uri.startsWith("/")) {
                                    val path = uri.removePrefix("file://")
                                    localAudioHttpServer.getUrl(path) ?: return@map item
                                } else uri
                        item.buildUpon().setUri(httpUrl).build()
                    }
                } else {
                    // Back to ExoPlayer: restore original file:// URIs
                    originalQueue
                }

        target.setMediaItems(items, itemIndex.coerceAtLeast(0), positionMs.coerceAtLeast(0))
        target.playWhenReady = playWhenReady
        target.prepare()

        mediaSession?.setPlayer(target)
    }

    // ---- MediaSessionService ----

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
            mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        castPlayer?.release()
        localAudioHttpServer.stop()
        mediaSession = null
        volumeScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }
}

private class SourceMediaSessionCallback : MediaSession.Callback {
    override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        // Accept all connections with default player commands.
        // System UI, media buttons, Bluetooth, and our own app all need access.
        // Session commands are restricted to prevent arbitrary custom command execution.
        return MediaSession.ConnectionResult.accept(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
        )
    }
}
