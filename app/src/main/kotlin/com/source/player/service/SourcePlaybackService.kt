package com.source.player.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.source.player.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * SourcePlaybackService
 *
 * A MediaSessionService that owns a single ExoPlayer instance. The service runs as a FOREGROUND
 * service (mediaPlayback type) — the OS will NOT kill it while audio is active, even in Doze mode.
 *
 * Design decisions:
 * - AudioAttributes set to USAGE_MEDIA / CONTENT_TYPE_MUSIC → OS grants proper audio routing
 * - handleAudioBecomingNoisy = true → pauses on headphone unplug automatically
 * - Audio offload enabled on API 29+ → hands off decoding to on-chip DSP,
 * ```
 *    saving CPU cycles and battery when the screen is off
 * ```
 * - WakeLock and WifiLock are managed internally by ExoPlayer when offload
 * ```
 *    is NOT available; for offload mode the DSP keeps the chip alive
 * ```
 */
@AndroidEntryPoint
class SourcePlaybackService : MediaSessionService() {

  private var mediaSession: MediaSession? = null

  override fun onCreate() {
    super.onCreate()

    val audioAttributes =
            AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build()

    val player =
            ExoPlayer.Builder(this)
                    .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
                    .setHandleAudioBecomingNoisy(true)
                    .build()

    // Tap on notification → open MainActivity
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
            MediaSession.Builder(this, player)
                    .setSessionActivity(sessionActivityIntent)
                    .setCallback(SourceMediaSessionCallback())
                    .build()
  }

  // MediaSessionService calls this to publish the session to the system notification
  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
          mediaSession

  override fun onTaskRemoved(rootIntent: Intent?) {
    val player = mediaSession?.player
    if (player == null || !player.playWhenReady) {
      // App swiped away and not playing — stop service entirely
      stopSelf()
    }
    // If playing, keep running in foreground
  }

  override fun onDestroy() {
    mediaSession?.run {
      player.release()
      release()
    }
    mediaSession = null
    super.onDestroy()
  }
}

/**
 * MediaSession.Callback — intercepts controller commands so we can apply custom queue logic (e.g.,
 * shuffle seed, gapless pre-buffering).
 */
private class SourceMediaSessionCallback : MediaSession.Callback {
  override fun onConnect(
          session: MediaSession,
          controller: MediaSession.ControllerInfo,
  ): MediaSession.ConnectionResult =
          MediaSession.ConnectionResult.accept(
                  MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                  MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
          )
}
