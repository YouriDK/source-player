package com.source.player.service

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.source.player.data.lastfm.LastFmRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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

  private var controller: MediaController? = null
  private var positionTickerJob: Job? = null
  private var scrobbleJob: Job? = null // cancelled on track change or pause

  init {
    connect()
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
    controller?.play()
  }
  fun pause() {
    controller?.pause()
  }
  fun seekTo(positionMs: Long) {
    controller?.seekTo(positionMs)
  }
  fun skipToNext() {
    controller?.seekToNextMediaItem()
  }
  fun skipToPrevious() {
    controller?.seekToPreviousMediaItem()
  }
  fun skipToQueueItem(index: Int) {
    controller?.seekTo(index, 0L)
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
      play()
    }
            ?: run { connect() }
  }

  fun setQueueFromEntities(
          songs: List<com.source.player.data.db.entity.SongEntity>,
          startIndex: Int = 0
  ) {
    val items =
            songs.map { song ->
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
            }
    setQueue(items, startIndex)
  }

  fun addToQueue(item: MediaItem) {
    controller?.addMediaItem(item)
  }

  fun addNextToQueue(song: com.source.player.data.db.entity.SongEntity) {
    val item =
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
    val insertIndex = (controller?.currentMediaItemIndex ?: 0) + 1
    controller?.addMediaItem(insertIndex, item)
  }

  // ---- Player.Listener ----

  private val playerListener =
          object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
              _isPlaying.value = isPlaying
              if (isPlaying) startPositionTicker()
              else {
                stopPositionTicker()
                scrobbleJob?.cancel() // don't scrobble if paused/stopped
              }
            }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
              _currentSong.value = item
              _queueIndex.value = controller?.currentMediaItemIndex ?: 0
              // Cancel any pending scrobble for the previous track
              scrobbleJob?.cancel()
              // Fire Now Playing + start scrobble timer for the new track
              item?.let { startScrobbleSession(it) }
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
    _isPlaying.value = player.isPlaying
    _currentSong.value = player.currentMediaItem
    _repeatMode.value = player.repeatMode
    _shuffleEnabled.value = player.shuffleModeEnabled
    _durationMs.value = player.duration.takeIf { it > 0 } ?: 0L
    _positionMs.value = player.currentPosition
    _queueIndex.value = player.currentMediaItemIndex
    // Rebuild queue list on reconnect
    val items = mutableListOf<MediaItem>()
    for (i in 0 until player.mediaItemCount) {
      items.add(player.getMediaItemAt(i))
    }
    _queueItems.value = items
    if (player.isPlaying) startPositionTicker()
  }

  private fun startPositionTicker() {
    positionTickerJob?.cancel()
    positionTickerJob =
            scope.launch {
              while (true) {
                controller?.let { c ->
                  _positionMs.value = c.currentPosition
                  val dur = c.duration
                  if (dur > 0) _durationMs.value = dur
                }
                delay(300L)
              }
            }
  }

  private fun stopPositionTicker() {
    positionTickerJob?.cancel()
    positionTickerJob = null
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
