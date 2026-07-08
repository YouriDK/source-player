package com.source.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.source.player.service.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class PlayerViewModel
@Inject
constructor(
        private val controller: PlaybackController,
) : ViewModel() {

        // WhileSubscribed: this ViewModel is instantiated per destination (Library, Player,
        // Queue, MiniPlayerBar) — Eager sharing kept every instance's collectors (including
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

        fun setSonosVolume(level: Int) = controller.setSonosVolume(level)

        fun clearError() = controller.clearError()

        fun play() = controller.play()
        fun pause() = controller.pause()
        fun seekTo(posMs: Long) = controller.seekTo(posMs)
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
