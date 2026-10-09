package com.source.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.source.player.data.db.dao.SongDao
import com.source.player.data.mode.LibraryModeRepository
import com.source.player.service.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel
@Inject
constructor(
        private val controller: PlaybackController,
        private val songDao: SongDao,
        private val libraryMode: LibraryModeRepository,
) : ViewModel() {

        // WhileSubscribed: this ViewModel is instantiated per destination (Library, Player,
        // MiniPlayerBar, details) — Eager sharing kept every instance's collectors (including
        // the 300ms position stream) alive for back-stack entries that weren't even visible.
        private val sharing = SharingStarted.WhileSubscribed(5_000)

        val currentSong = controller.currentSong.stateIn(viewModelScope, sharing, null)
        val isPlaying = controller.isPlaying.stateIn(viewModelScope, sharing, false)
        val positionMs = controller.positionMs.stateIn(viewModelScope, sharing, 0L)
        val durationMs = controller.durationMs.stateIn(viewModelScope, sharing, 0L)
        val repeatMode =
                controller.repeatMode.stateIn(viewModelScope, sharing, Player.REPEAT_MODE_OFF)
        val shuffleEnabled = controller.shuffleEnabled.stateIn(viewModelScope, sharing, false)
        val queueItems = controller.queueItems.stateIn(viewModelScope, sharing, emptyList())
        val queueIndex = controller.queueIndex.stateIn(viewModelScope, sharing, 0)

        /** Room DB song ID extracted from the current MediaItem's mediaId field */
        val currentSongId =
                controller
                        .currentSong
                        .map { it?.mediaId?.toLongOrNull() }
                        .stateIn(viewModelScope, sharing, null)

        val playbackError = controller.playbackError.stateIn(viewModelScope, sharing, null)

        val sonosActive =
                controller.sonosActive.map { it != null }.stateIn(viewModelScope, sharing, false)
        val sonosVolume = controller.sonosVolume.stateIn(viewModelScope, sharing, null)

        /** Whether the active source (Local / Stream) has any track — gates "Shuffle all". */
        val hasTracks =
                libraryMode.source
                        .flatMapLatest { songDao.countBySourceFlow(it) }
                        .map { it > 0 }
                        .distinctUntilChanged()
                        .stateIn(viewModelScope, sharing, false)

        /** Queues every track of the active source in random order and starts playing. */
        fun shuffleAll() =
                viewModelScope.launch {
                        val songs =
                                withContext(Dispatchers.IO) {
                                        songDao.getAllBySource(libraryMode.currentSource())
                                }
                        if (songs.isEmpty()) return@launch
                        val shuffled = withContext(Dispatchers.Default) { songs.shuffled() }
                        controller.setQueueFromEntities(shuffled, 0)
                }

        fun setSonosVolume(level: Int) = controller.setSonosVolume(level)

        fun clearError() = controller.clearError()

        fun play() = controller.play()
        fun pause() = controller.pause()
        fun seekTo(posMs: Long) = controller.seekTo(posMs)
        fun dismissPlayback() = controller.stopAndClear()
        fun skipToNext() = controller.skipToNext()
        fun skipToPrevious() = controller.skipToPrevious()
        fun skipToQueueItem(index: Int) = controller.skipToQueueItem(index)
        fun setShuffleEnabled(v: Boolean) = controller.setShuffleEnabled(v)
        fun cycleRepeatMode() {
                val next =
                        when (controller.repeatMode.value) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                        }
                controller.setRepeatMode(next)
        }
}
