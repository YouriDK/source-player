package com.source.player.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class PlaylistDetailViewModel
@Inject
constructor(
        private val playlistDao: PlaylistDao,
        private val songDao: SongDao,
        private val controller: PlaybackController,
        savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val playlistId: Long = checkNotNull(savedStateHandle["playlistId"])

  private val _playlist = MutableStateFlow<PlaylistEntity?>(null)
  val playlist: StateFlow<PlaylistEntity?> = _playlist.asStateFlow()

  val songs: StateFlow<List<SongEntity>> =
          playlistDao
                  .getSongsForPlaylist(playlistId)
                  .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  /** All songs in library (for the add-song picker) */
  val allSongs: StateFlow<List<SongEntity>> =
          songDao.getAllFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  /** Filtered songs for the add-song picker (excludes already-in-playlist songs) */
  val filteredSongs: StateFlow<List<SongEntity>> =
          combine(allSongs, songs, _searchQuery) { all, existing, query ->
                    val existingIds = existing.map { it.id }.toSet()
                    all.filter { song ->
                      song.id !in existingIds &&
                              (query.isBlank() ||
                                      song.title.contains(query, ignoreCase = true) ||
                                      song.artist.contains(query, ignoreCase = true) ||
                                      song.album.contains(query, ignoreCase = true))
                    }
                  }
                  .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  private val _toastMessage = MutableStateFlow<String?>(null)
  val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

  fun clearToast() {
    _toastMessage.value = null
  }

  init {
    viewModelScope.launch { _playlist.value = playlistDao.getById(playlistId) }
  }

  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun addSongToPlaylist(songId: Long) =
          viewModelScope.launch {
            val position = (playlistDao.maxPosition(playlistId) ?: -1) + 1
            playlistDao.addSongToPlaylist(
                    PlaylistSongEntity(
                            playlistId = playlistId,
                            songId = songId,
                            position = position,
                    )
            )
            val song = songDao.getById(songId)
            _toastMessage.value = "Added \"${song?.title ?: "song"}\""
          }

  fun removeSongFromPlaylist(songId: Long) =
          viewModelScope.launch { playlistDao.removeSongFromPlaylist(playlistId, songId) }

  fun playSongs(startIndex: Int = 0) {
    val list = songs.value
    if (list.isNotEmpty()) controller.setQueueFromEntities(list, startIndex)
  }

  fun shuffleSongs() {
    val list = songs.value.shuffled()
    if (list.isNotEmpty()) controller.setQueueFromEntities(list, 0)
  }
}
