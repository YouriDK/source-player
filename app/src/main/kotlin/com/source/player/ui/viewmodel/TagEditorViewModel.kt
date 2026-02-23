package com.source.player.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.source.player.data.db.dao.SongDao
import com.source.player.data.db.entity.SongEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class TagEditorViewModel
@Inject
constructor(
        private val songDao: SongDao,
        savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val songId: Long = checkNotNull(savedStateHandle["songId"])

  private val _song = MutableStateFlow<SongEntity?>(null)
  val song: StateFlow<SongEntity?> = _song.asStateFlow()

  // Editable fields
  val title = MutableStateFlow("")
  val artist = MutableStateFlow("")
  val album = MutableStateFlow("")
  val year = MutableStateFlow("")
  val trackNumber = MutableStateFlow("")
  val genre = MutableStateFlow("")

  private val _saved = MutableStateFlow(false)
  val saved: StateFlow<Boolean> = _saved.asStateFlow()

  init {
    viewModelScope.launch {
      val s = songDao.getById(songId) ?: return@launch
      _song.value = s
      title.value = s.title
      artist.value = s.artist
      album.value = s.album
      year.value = if (s.year > 0) s.year.toString() else ""
      trackNumber.value = if (s.trackNumber > 0) s.trackNumber.toString() else ""
      genre.value = s.genre
    }
  }

  fun save() =
          viewModelScope.launch {
            val original = _song.value ?: return@launch
            val updated =
                    original.copy(
                            title = title.value.trim().ifEmpty { original.title },
                            artist = artist.value.trim().ifEmpty { original.artist },
                            album = album.value.trim().ifEmpty { original.album },
                            year = year.value.trim().toIntOrNull() ?: original.year,
                            trackNumber = trackNumber.value.trim().toIntOrNull()
                                            ?: original.trackNumber,
                            genre = genre.value.trim(),
                    )
            songDao.update(updated)
            _saved.value = true
          }
}
