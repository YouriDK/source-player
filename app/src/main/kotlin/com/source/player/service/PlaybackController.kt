package com.source.player.service

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.source.player.data.db.dao.SongDao
import com.source.player.data.db.entity.SongEntity
import com.source.player.data.lastfm.LastFmRepository
import com.source.player.data.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * PlaybackController — single source of truth for playback state in the UI.
 *
 * Wraps MediaController (cross-process IPC) and exposes StateFlows that ViewModels collect.
 *
 * State updates arrive via Player.Listener events — zero polling.
 */
@Singleton
class PlaybackController
@Inject
constructor(
        @ApplicationContext private val context: Context,
        private val lastFm: LastFmRepository,
        private val sonosManager: SonosManager,
        private val songDao: SongDao,
        private val prefs: AppPreferences,
) {
  private val supervisorJob = SupervisorJob()
  private val scope = CoroutineScope(supervisorJob + Dispatchers.Main)

  private val _currentSong = MutableStateFlow<MediaItem?>(null)
  private val _isPlaying = MutableStateFlow(false)
  private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
  private val _shuffleEnabled = MutableStateFlow(false)
  private val _positionMs = MutableStateFlow(0L)
  private val _durationMs = MutableStateFlow(0L)
  private val _queueItems = MutableStateFlow<List<MediaItem>>(emptyList())
  private val _queueIndex = MutableStateFlow(0)
  private val _playbackError = MutableStateFlow<String?>(null)

  val currentSong = _currentSong.asStateFlow()
  val isPlaying = _isPlaying.asStateFlow()
  val repeatMode = _repeatMode.asStateFlow()
  val shuffleEnabled = _shuffleEnabled.asStateFlow()
  val positionMs = _positionMs.asStateFlow()
  val durationMs = _durationMs.asStateFlow()
  val queueItems = _queueItems.asStateFlow()
  val queueIndex = _queueIndex.asStateFlow()
  val playbackError = _playbackError.asStateFlow()

  fun clearError() {
    _playbackError.value = null
  }

  // ---- Sonos passthroughs for the UI layer ----

  /** Current Sonos volume (0..100) or null when Sonos is not active / not yet queried. */
  val sonosVolume: StateFlow<Int?> = sonosManager.sonosVolume

  /** Whether a Sonos device is currently active (for showing the Sonos volume slider). */
  val sonosActive: StateFlow<SonosDevice?> = sonosManager.activeDevice

  fun setSonosVolume(level: Int) {
    val device = sonosManager.activeDevice.value ?: return
    scope.launch(Dispatchers.IO) { sonosManager.setVolume(device, level) }
  }

  /** Clean up resources. Called when the application is being destroyed. */
  fun release() {
    positionTickerJob?.cancel()
    scrobbleJob?.cancel()
    controller?.removeListener(playerListener)
    controller?.release()
    controller = null
    supervisorJob.cancel()
  }

  private var controller: MediaController? = null
  private var positionTickerJob: Job? = null
  private var scrobbleJob: Job? = null // cancelled on track change or pause
  /** Tracks the mediaId of the last track pushed to the active Sonos device. */
  private var lastSonosTrackId: String? = null
  /** Single-flight guard so one trackEnded event never advances twice. */
  private val advancing = AtomicBoolean(false)

  init {
    connect()

    // Pre-populate UI state flows from the saved queue without waiting for the
    // MediaController → Service bind. Ensures the Queue screen is populated on
    // cold start even if the service hasn't connected yet.
    scope.launch { hydrateSavedQueueIntoUiState() }

    // If a Sonos session was active last time the app ran, try to rebind silently
    // and mirror whatever the speaker is currently playing back into our state.
    scope.launch { attemptSonosResume() }

    // Auto-advance to next track when Sonos finishes playing
    scope.launch {
      sonosManager.trackEnded.collect { advanceSonosQueue() }
    }

    // Keep _isPlaying in sync with Sonos transport state
    scope.launch {
      sonosManager.sonosPlaying.collect { playing ->
        if (sonosManager.activeDevice.value != null) {
          _isPlaying.value = playing
        }
      }
    }

    // While Sonos is active, poll position/duration so the seekbar moves and the
    // current-track metadata stays fresh (covers the case where the Sonos advances
    // via its own controls or another app is controlling it).
    scope.launch {
      sonosManager.activeDevice.collectLatest { device ->
        if (device == null) return@collectLatest
        while (true) {
          val info = sonosManager.getPositionInfo(device)
          if (info != null) {
            _positionMs.value = info.relTimeMs
            if (info.durationMs > 0) _durationMs.value = info.durationMs
          }
          delay(2_000L)
        }
      }
    }
  }

  /**
   * Cold-start hydration: reads the persisted queue (CSV of song ids) from DataStore
   * and publishes it to [_queueItems] / [_queueIndex] / [_positionMs] / [_currentSong]
   * so the UI shows it immediately. The queue is pushed to the controller lazily on
   * the first [play] call.
   */
  private suspend fun hydrateSavedQueueIntoUiState() {
    if (!prefs.restoreState.first()) return
    if (_queueItems.value.isNotEmpty()) return // controller already populated us
    val idsCsv = prefs.savedQueueJson.first().orEmpty()
    if (idsCsv.isBlank()) return
    val ids = idsCsv.split(",").mapNotNull { it.toLongOrNull() }
    if (ids.isEmpty()) return

    val songs =
            withContext(Dispatchers.IO) {
              val byId = songDao.getByIds(ids).associateBy { it.id }
              ids.mapNotNull { byId[it] }
            }
    if (songs.isEmpty()) return

    val items = songs.map { songEntityToMediaItem(it) }
    val idx = prefs.savedQueueIndex.first().coerceIn(0, items.lastIndex)
    val pos = prefs.savedQueuePosition.first()

    // Only publish if nothing else has populated us in the meantime.
    if (_queueItems.value.isEmpty()) {
      _queueItems.value = items
      _queueIndex.value = idx
      _positionMs.value = pos
      _currentSong.value = items.getOrNull(idx)
    }
  }

  private fun connect() {
    val token = SessionToken(context, ComponentName(context, SourcePlaybackService::class.java))
    // Build the MediaController asynchronously using the future + listener pattern
    val future = MediaController.Builder(context, token).buildAsync()
    future.addListener(
            {
              try {
                controller = future.get()
                controller?.addListener(playerListener)
                controller?.let { syncState(it) }
              } catch (e: Exception) {
                // Connection failed — service not started yet, will reconnect on first play
              }
            },
            { scope.launch { it.run() } },
    )
  }

  // ---- Public API ----

  fun play() {
    // Lazily push a hydrated (cold-start) queue to the controller if it's still empty.
    maybePushHydratedQueueToController()

    val sonos = sonosManager.activeDevice.value
    if (sonos != null) {
      val currentId = _currentSong.value?.mediaId
      if (currentId != null && currentId == lastSonosTrackId) {
        // Same track already on Sonos — just resume
        scope.launch(Dispatchers.IO) { sonosManager.resume(sonos) }
      } else {
        // Track changed or never sent — push it to Sonos
        scope.launch(Dispatchers.IO) { sendCurrentToSonos(sonos) }
      }
    } else {
      controller?.play() ?: run {
        // Controller not yet bound — ensure the service is up, then try again shortly.
        context.startForegroundService(
                android.content.Intent(context, SourcePlaybackService::class.java)
        )
        connect()
      }
    }
  }

  private fun maybePushHydratedQueueToController() {
    val c = controller ?: return
    if (c.mediaItemCount != 0) return
    val items = _queueItems.value
    if (items.isEmpty()) return
    val idx = _queueIndex.value.coerceIn(0, items.lastIndex)
    val pos = _positionMs.value
    c.setMediaItems(items, idx, pos)
    c.prepare()
  }

  fun pause() {
    val sonos = sonosManager.activeDevice.value
    if (sonos != null) {
      scope.launch(Dispatchers.IO) { sonosManager.pause(sonos) }
    } else {
      controller?.pause()
    }
  }

  fun seekTo(positionMs: Long) {
    controller?.seekTo(positionMs)
  }

  fun skipToNext() {
    val c = controller ?: return
    val sonos = sonosManager.activeDevice.value
    if (sonos != null) {
      if (!c.hasNextMediaItem()) return
      c.seekToNextMediaItem()
      val next = c.currentMediaItem ?: return
      scope.launch(Dispatchers.IO) { sendMediaItemToSonos(sonos, next) }
    } else {
      c.seekToNextMediaItem()
    }
  }

  fun skipToPrevious() {
    val c = controller ?: return
    val sonos = sonosManager.activeDevice.value
    if (sonos != null) {
      c.seekToPreviousMediaItem()
      val prev = c.currentMediaItem ?: return
      scope.launch(Dispatchers.IO) { sendMediaItemToSonos(sonos, prev) }
    } else {
      c.seekToPreviousMediaItem()
    }
  }

  fun skipToQueueItem(index: Int) {
    val c = controller ?: return
    c.seekTo(index, 0L)
    val sonos = sonosManager.activeDevice.value
    if (sonos != null) {
      val item = c.currentMediaItem ?: return
      scope.launch(Dispatchers.IO) { sendMediaItemToSonos(sonos, item) }
    }
  }

  fun setRepeatMode(mode: Int) {
    controller?.repeatMode = mode
    _repeatMode.value = mode
  }

  fun setShuffleEnabled(enabled: Boolean) {
    controller?.shuffleModeEnabled = enabled
    _shuffleEnabled.value = enabled
  }

  fun setQueue(items: List<MediaItem>, startIndex: Int = 0) {
    context.startForegroundService(
            android.content.Intent(context, SourcePlaybackService::class.java)
    )
    controller?.apply {
      setMediaItems(items, startIndex, 0L)
      prepare()
      val sonos = sonosManager.activeDevice.value
      if (sonos != null) {
        // Don't play locally — push the selected track to Sonos explicitly,
        // no reliance on onMediaItemTransition timing.
        val startItem = items.getOrNull(startIndex) ?: return@apply
        scope.launch(Dispatchers.IO) { sendMediaItemToSonos(sonos, startItem) }
      } else {
        play()
      }
    }
            ?: run { connect() }
  }

  fun setQueueFromEntities(
          songs: List<SongEntity>,
          startIndex: Int = 0
  ) {
    setQueue(songs.map { songEntityToMediaItem(it) }, startIndex)
  }

  fun addToQueue(item: MediaItem) {
    controller?.addMediaItem(item)
  }

  fun addNextToQueue(song: SongEntity) {
    val insertIndex = (controller?.currentMediaItemIndex ?: 0) + 1
    controller?.addMediaItem(insertIndex, songEntityToMediaItem(song))
  }

  private fun songEntityToMediaItem(song: SongEntity): MediaItem =
          MediaItem.Builder()
                  .setMediaId(song.id.toString())
                  .setUri(song.path)
                  .setMediaMetadata(
                          androidx.media3.common.MediaMetadata.Builder()
                                  .setTitle(song.title)
                                  .setArtist(song.artist)
                                  .setAlbumTitle(song.album)
                                  .setArtworkUri(
                                          song.albumArtUri?.let { android.net.Uri.parse(it) }
                                  )
                                  .build()
                  )
                  .build()

  // ---- Player.Listener ----

  private val playerListener =
          object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
              // When Sonos is active, ExoPlayer is paused — don't overwrite _isPlaying
              if (sonosManager.activeDevice.value != null) return
              _isPlaying.value = isPlaying
              if (isPlaying) startPositionTicker()
              else {
                stopPositionTicker()
                scrobbleJob?.cancel() // don't scrobble if paused/stopped
                persistState() // capture pause point
              }
            }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
              _currentSong.value = item
              _queueIndex.value = controller?.currentMediaItemIndex ?: 0
              // Cancel any pending scrobble for the previous track
              scrobbleJob?.cancel()
              // Fire Now Playing + start scrobble timer for the new track
              item?.let { startScrobbleSession(it) }
              persistState()
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
              _repeatMode.value = repeatMode
            }
            override fun onShuffleModeEnabledChanged(enabled: Boolean) {
              _shuffleEnabled.value = enabled
            }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
              val items = mutableListOf<MediaItem>()
              for (i in 0 until (controller?.mediaItemCount ?: 0)) {
                items.add(controller!!.getMediaItemAt(i))
              }
              _queueItems.value = items
              _durationMs.value = controller?.duration?.takeIf { it > 0 } ?: 0L
              persistState()
            }
            override fun onEvents(player: Player, events: Player.Events) {
              _positionMs.value = player.currentPosition
            }
            override fun onPlayerError(error: PlaybackException) {
              val songTitle = controller?.currentMediaItem?.mediaMetadata?.title ?: "Unknown"
              val reason =
                      when (error.errorCode) {
                        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "File not found"
                        PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
                                "No permission to read file"
                        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                                "Unsupported format"
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Network error"
                        else -> "Playback error"
                      }
              _playbackError.value = "Can't play \"$songTitle\": $reason"
              // Try to skip to the next song
              controller?.let { c ->
                if (c.hasNextMediaItem()) {
                  c.seekToNextMediaItem()
                  c.prepare()
                  c.play()
                }
              }
            }
          }

  private fun syncState(player: Player) {
    _repeatMode.value = player.repeatMode
    _shuffleEnabled.value = player.shuffleModeEnabled
    if (player.mediaItemCount > 0) {
      // Controller has its own queue — mirror it into our flows.
      _isPlaying.value = player.isPlaying
      _currentSong.value = player.currentMediaItem
      _durationMs.value = player.duration.takeIf { it > 0 } ?: 0L
      _positionMs.value = player.currentPosition
      _queueIndex.value = player.currentMediaItemIndex
      val items = mutableListOf<MediaItem>()
      for (i in 0 until player.mediaItemCount) {
        items.add(player.getMediaItemAt(i))
      }
      _queueItems.value = items
      if (player.isPlaying) startPositionTicker()
    } else {
      // Controller is empty — keep whatever UI state we already hydrated from DataStore,
      // then push it down to the controller so it becomes the source of truth.
      scope.launch {
        hydrateSavedQueueIntoUiState()
        maybePushHydratedQueueToController()
      }
    }
  }

  // ---- Queue persistence ----

  private fun persistState() {
    // Don't overwrite a valid saved queue with an empty one (fires during teardown
    // or when the controller briefly reports 0 items between queue swaps).
    if (_queueItems.value.isEmpty()) return
    val ids = _queueItems.value.mapNotNull { it.mediaId.toLongOrNull() }.joinToString(",")
    if (ids.isBlank()) return
    val index = controller?.currentMediaItemIndex ?: 0
    val pos = controller?.currentPosition ?: 0L
    scope.launch { prefs.saveQueueState(ids, index, pos) }
  }

  private fun startPositionTicker() {
    positionTickerJob?.cancel()
    positionTickerJob =
            scope.launch {
              while (true) {
                withContext(Dispatchers.Main) {
                  controller?.let { c ->
                    _positionMs.value = c.currentPosition
                    val dur = c.duration
                    if (dur > 0) _durationMs.value = dur
                  }
                }
                delay(300L)
              }
            }
  }

  private fun stopPositionTicker() {
    positionTickerJob?.cancel()
    positionTickerJob = null
  }

  // ---- Sonos session management ----

  /**
   * Switches playback to the given Sonos device. Pauses local ExoPlayer and
   * sends the currently playing track to the Sonos speaker.
   */
  fun activateSonos(device: SonosDevice) {
    controller?.pause() // stop local audio, keep queue intact
    sonosManager.activate(device)
    scope.launch(Dispatchers.IO) {
      sendCurrentToSonos(device)
    }
  }

  /** Stops Sonos playback and returns to local ExoPlayer output. */
  fun deactivateSonos() {
    sonosManager.deactivate()
    lastSonosTrackId = null
    // ExoPlayer stays paused — user can press play to resume locally
  }

  /**
   * On cold start: if the user was streaming to a Sonos when the app was killed,
   * rediscover + rebind the speaker and pull its current track/state into our
   * UI flows. Does NOT start new playback.
   */
  private suspend fun attemptSonosResume() {
    val savedId = prefs.sonosActiveId.first() ?: return
    sonosManager.discover()
    val device =
            withTimeoutOrNull(6_000L) {
              sonosManager.devices.first { list -> list.any { it.id == savedId } }.first {
                it.id == savedId
              }
            }
                    ?: return

    // Silent rebind — no SetAVTransportURI/Play, just resume monitoring.
    sonosManager.reactivateWithoutPlay(device)

    val pos = sonosManager.getPositionInfo(device) ?: return
    val transport = sonosManager.getTransportState(device)

    val song =
            pos.trackUri?.let { uri ->
              withContext(Dispatchers.IO) { resolveSongFromSonosUri(uri) }
            }
    if (song != null) {
      val item = songEntityToMediaItem(song)
      _currentSong.value = item
      lastSonosTrackId = item.mediaId
      // Align the queue index with the hydrated queue if possible.
      val hydrated = _queueItems.value
      val idx = hydrated.indexOfFirst { it.mediaId == item.mediaId }
      if (idx >= 0) _queueIndex.value = idx
    }
    _positionMs.value = pos.relTimeMs
    if (pos.durationMs > 0) _durationMs.value = pos.durationMs
    _isPlaying.value = (transport == "PLAYING")
  }

  private suspend fun resolveSongFromSonosUri(uri: String): SongEntity? {
    // Our LocalAudioHttpServer URL is http://{ip}:{port}/{token}/{urlEncodedPath}.
    // Extract the path after the token and decode it.
    val path =
            runCatching {
              val afterScheme = uri.substringAfter("://", "").ifEmpty { return null }
              val afterHost = afterScheme.substringAfter("/", "").ifEmpty { return null }
              val afterToken = afterHost.substringAfter("/", "")
              if (afterToken.isBlank()) return null
              val decoded = java.net.URLDecoder.decode(afterToken, "UTF-8")
              if (decoded.startsWith("/")) decoded else "/$decoded"
            }
                    .getOrNull()
                    ?: return null
    return songDao.getByPath(path)
  }

  private suspend fun sendCurrentToSonos(device: SonosDevice) {
    val song = _currentSong.value ?: return
    sendMediaItemToSonos(device, song)
  }

  private suspend fun sendMediaItemToSonos(device: SonosDevice, item: MediaItem) {
    val uri = item.localConfiguration?.uri?.toString() ?: return
    val path = if (uri.startsWith("file://")) uri.removePrefix("file://") else uri
    val title = item.mediaMetadata.title?.toString() ?: "Unknown Track"
    lastSonosTrackId = item.mediaId
    sonosManager.playFile(device, path, title)
  }

  private fun advanceSonosQueue() {
    if (!advancing.compareAndSet(false, true)) return
    scope.launch {
      try {
        val device = sonosManager.activeDevice.value ?: return@launch
        val c = controller ?: return@launch
        if (!c.hasNextMediaItem()) return@launch
        c.seekToNextMediaItem()
        val next = c.currentMediaItem ?: return@launch
        withContext(Dispatchers.IO) { sendMediaItemToSonos(device, next) }
      } finally {
        advancing.set(false)
      }
    }
  }

  /**
   * Fires "now playing" immediately, then schedules a scrobble. Per Last.fm spec: scrobble after
   * max(30s, 50% of track duration).
   */
  private fun startScrobbleSession(item: MediaItem) {
    val meta = item.mediaMetadata
    val artist = meta.artist?.toString() ?: return // artist is required
    val track = meta.title?.toString() ?: return // title is required
    val album = meta.albumTitle?.toString()
    val durationMs = controller?.duration?.takeIf { it > 0 }
    val durationSec = durationMs?.let { (it / 1000).toInt() } ?: 0
    val startTimestamp = System.currentTimeMillis() / 1000L // Unix epoch seconds

    scrobbleJob =
            scope.launch(Dispatchers.IO) {
              // 1. Notify "now playing" immediately
              lastFm.nowPlaying(artist, track, album, durationSec)

              // 2. Wait until the scrobble threshold is reached
              val threshold =
                      if (durationMs != null && durationMs > 0) {
                        maxOf(30_000L, durationMs / 2)
                      } else {
                        30_000L // Unknown duration — use minimum
                      }
              delay(threshold)

              // 3. Submit scrobble
              lastFm.scrobble(artist, track, album, startTimestamp, durationSec)
            }
  }
}
