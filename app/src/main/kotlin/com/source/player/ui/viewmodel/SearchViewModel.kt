package com.source.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.source.player.data.db.dao.AlbumDao
import com.source.player.data.db.dao.ArtistDao
import com.source.player.data.db.dao.SongDao
import com.source.player.data.db.entity.*
import com.source.player.service.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel
@Inject
constructor(
        private val songDao: SongDao,
        private val albumDao: AlbumDao,
        private val artistDao: ArtistDao,
        private val controller: PlaybackController,
) : ViewModel() {

  private val _query = MutableStateFlow("")
  val query = _query.asStateFlow()

  // 300ms debounce — avoids a DB hit on every keystroke
  private val debouncedQuery = _query.debounce(300).distinctUntilChanged()

  val songResults: StateFlow<List<SongEntity>> =
          debouncedQuery
                  .flatMapLatest { q ->
                    if (q.isBlank()) flowOf(emptyList())
                    else flow { emit(songDao.search(q)) }
                  }
                  .flowOn(Dispatchers.Default)
                  .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val albumResults: StateFlow<List<AlbumEntity>> =
          debouncedQuery
                  .flatMapLatest { q ->
                    if (q.isBlank()) flowOf(emptyList())
                    else flow { emit(albumDao.search(q)) }
                  }
                  .flowOn(Dispatchers.Default)
                  .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val artistResults: StateFlow<List<ArtistEntity>> =
          debouncedQuery
                  .flatMapLatest { q ->
                    if (q.isBlank()) flowOf(emptyList())
                    else flow { emit(artistDao.search(q)) }
                  }
                  .flowOn(Dispatchers.Default)
                  .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  fun onQueryChange(q: String) {
    _query.value = q
  }

  fun playSong(song: SongEntity, context: List<SongEntity>) {
    val items = context.map { it.toMediaItem() }
    val index = context.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
    controller.setQueue(items, index)
  }
}

private fun SongEntity.toMediaItem(): androidx.media3.common.MediaItem =
        androidx.media3.common.MediaItem.Builder()
                .setMediaId(id.toString())
                .setUri(path)
                .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(title)
                                .setArtist(artist)
                                .setAlbumTitle(album)
                                .setArtworkUri(albumArtUri?.let { android.net.Uri.parse(it) })
                                .build()
                )
                .build()
