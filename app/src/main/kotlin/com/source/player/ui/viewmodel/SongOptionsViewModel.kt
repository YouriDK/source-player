package com.source.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.source.player.data.db.dao.PlaylistDao
import com.source.player.data.db.dao.SongDao
import com.source.player.data.db.entity.PlaylistEntity
import com.source.player.data.db.entity.PlaylistSongEntity
import com.source.player.data.db.entity.SongEntity
import com.source.player.service.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SongOptionsViewModel
@Inject
constructor(
        private val playlistDao: PlaylistDao,
        private val songDao: SongDao,
        private val controller: PlaybackController,
) : ViewModel() {

  val playlists: StateFlow<List<PlaylistEntity>> =
          playlistDao.getAllFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  private val _addedToPlaylistId = MutableStateFlow<Long?>(null)
  val addedToPlaylistId: StateFlow<Long?> = _addedToPlaylistId.asStateFlow()

  fun playSong(song: SongEntity) {
    controller.setQueueFromEntities(listOf(song), 0)
  }

  fun playNext(song: SongEntity) {
    controller.addNextToQueue(song)
  }

  fun addToQueue(song: SongEntity) {
    val item =
            androidx.media3.common.MediaItem.Builder()
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
    controller.addToQueue(item)
  }

  fun addSongToPlaylist(songId: Long, playlistId: Long) =
          viewModelScope.launch {
            val position = (playlistDao.maxPosition(playlistId) ?: -1) + 1
            playlistDao.addSongToPlaylist(
                    PlaylistSongEntity(
                            playlistId = playlistId,
                            songId = songId,
                            position = position
                    )
            )
            _addedToPlaylistId.value = playlistId
          }
}
