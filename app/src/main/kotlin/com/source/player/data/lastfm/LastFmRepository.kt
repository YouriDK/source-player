package com.source.player.data.lastfm

import android.util.Log
import com.source.player.data.preferences.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.firstOrNull

/**
 * LastFmRepository
 *
 * Coordinates between LastFmApi and AppPreferences.
 * - login() authenticates and persists the session key.
 * - nowPlaying() / scrobble() guard against disabled scrobbling or missing credentials.
 */
@Singleton
class LastFmRepository
@Inject
constructor(
        private val api: LastFmApi,
        private val prefs: AppPreferences,
) {

  // ---- Auth ----

  /** Returns the saved username on success, or throws LastFmException on failure. */
  suspend fun login(username: String, password: String): String {
    val sessionKey = api.getMobileSession(username, password)
    prefs.setLastFmCredentials(username, sessionKey)
    return username
  }

  suspend fun clearCredentials() {
    prefs.setLastFmCredentials("", "")
  }

  // ---- Playback reporting ----

  /**
   * Notify Last.fm about the currently scrobbling track. Silently swallowed on network errors so
   * playback is never interrupted.
   */
  suspend fun nowPlaying(
          artist: String,
          track: String,
          album: String?,
          durationSec: Int,
  ) {
    val sessionKey = sessionKeyOrNull() ?: return
    try {
      api.updateNowPlaying(sessionKey, artist, track, album, durationSec)
    } catch (e: Exception) {
      Log.w("LastFm", "nowPlaying failed: ${e.message}")
    }
  }

  /** Submit a scrobble. Silently swallowed on network errors. */
  suspend fun scrobble(
          artist: String,
          track: String,
          album: String?,
          timestamp: Long,
          durationSec: Int,
  ) {
    val sessionKey = sessionKeyOrNull() ?: return
    try {
      api.scrobble(sessionKey, artist, track, album, timestamp, durationSec)
      Log.d("LastFm", "Scrobbled: $artist – $track")
    } catch (e: Exception) {
      Log.w("LastFm", "scrobble failed: ${e.message}")
    }
  }

  private suspend fun sessionKeyOrNull(): String? {
    val scrobblingEnabled = prefs.scrobbling.firstOrNull() ?: false
    if (!scrobblingEnabled) return null
    val token = prefs.lastFmToken.firstOrNull()
    return token?.takeIf { it.isNotBlank() }
  }

  /**
   * Fetches the best album art URL from Last.fm. Returns null silently on any network/parsing
   * error.
   */
  suspend fun fetchAlbumArt(artist: String, album: String): String? {
    return try {
      api.getAlbumArtUrl(artist, album)
    } catch (e: Exception) {
      Log.w("LastFm", "fetchAlbumArt failed for $artist – $album: ${e.message}")
      null
    }
  }
}
