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

        // Per-field "Apply to Folder" toggles
        val applyArtistToFolder = MutableStateFlow(false)
        val applyAlbumToFolder = MutableStateFlow(false)
        val applyYearToFolder = MutableStateFlow(false)
        val applyGenreToFolder = MutableStateFlow(false)

        /** Songs in the same folder (excluding the current one), loaded once. */
        private val _folderSongs = MutableStateFlow<List<SongEntity>>(emptyList())
        val folderSongs: StateFlow<List<SongEntity>> = _folderSongs.asStateFlow()

        private val _saved = MutableStateFlow(false)
        val saved: StateFlow<Boolean> = _saved.asStateFlow()

        private val _toastMessage = MutableStateFlow<String?>(null)
        val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

        fun clearToast() {
                _toastMessage.value = null
        }

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

                        // Load folder siblings
                        _folderSongs.value =
                                songDao.getByFolderPath(s.folderPath).filter { it.id != songId }
                }
        }

        fun save() =
                viewModelScope.launch {
                        val original = _song.value ?: return@launch

                        val updatedTitle = title.value.trim().ifEmpty { original.title }
                        val updatedArtist = artist.value.trim().ifEmpty { original.artist }
                        val updatedAlbum = album.value.trim().ifEmpty { original.album }
                        val updatedYear = year.value.trim().toIntOrNull() ?: original.year
                        val updatedTrack =
                                trackNumber.value.trim().toIntOrNull() ?: original.trackNumber
                        val updatedGenre = genre.value.trim()

                        // Save the current song
                        songDao.update(
                                original.copy(
                                        title = updatedTitle,
                                        artist = updatedArtist,
                                        album = updatedAlbum,
                                        year = updatedYear,
                                        trackNumber = updatedTrack,
                                        genre = updatedGenre,
                                )
                        )

                        // Propagate to folder based on per-field toggles
                        val folderPath = original.folderPath
                        var propagatedCount = 0

                        if (applyArtistToFolder.value && updatedArtist != original.artist) {
                                songDao.updateArtistForFolder(updatedArtist, folderPath, songId)
                                propagatedCount++
                        }
                        if (applyAlbumToFolder.value && updatedAlbum != original.album) {
                                songDao.updateAlbumForFolder(updatedAlbum, folderPath, songId)
                                propagatedCount++
                        }
                        if (applyYearToFolder.value && updatedYear != original.year) {
                                songDao.updateYearForFolder(updatedYear, folderPath, songId)
                                propagatedCount++
                        }
                        if (applyGenreToFolder.value && updatedGenre != original.genre) {
                                songDao.updateGenreForFolder(updatedGenre, folderPath, songId)
                                propagatedCount++
                        }

                        if (propagatedCount > 0 && _folderSongs.value.isNotEmpty()) {
                                _toastMessage.value =
                                        "Applied to ${_folderSongs.value.size} songs in folder"
                        }

                        _saved.value = true
                }
}
