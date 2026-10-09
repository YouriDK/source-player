package com.source.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.source.player.data.db.dao.AlbumDao
import com.source.player.data.db.dao.ArtistDao
import com.source.player.data.db.dao.SongDao
import com.source.player.data.db.entity.*
import com.source.player.data.mode.LibraryModeRepository
import com.source.player.service.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel
@Inject
constructor(
        private val songDao: SongDao,
        private val albumDao: AlbumDao,
        private val artistDao: ArtistDao,
        private val controller: PlaybackController,
        libraryMode: LibraryModeRepository,
) : ViewModel() {

  private val _query = MutableStateFlow("")
  val query = _query.asStateFlow()

  // 300ms debounce — avoids a DB hit on every keystroke
  private val debouncedQuery = _query.debounce(300).distinctUntilChanged()

  /** Query + active library source: results never mix Local and Stream rows. */
  private val request: Flow<Pair<String, String>> =
          combine(debouncedQuery, libraryMode.source) { q, source -> q to source }

  val songResults: StateFlow<List<SongEntity>> =
          request
                  .flatMapLatest { (q, source) ->
                    if (q.isBlank()) flowOf(emptyList())
                    else flow { emit(songDao.searchBySource(q, source)) }
                  }
                  .flowOn(Dispatchers.Default)
                  .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val albumResults: StateFlow<List<AlbumEntity>> =
          request
                  .flatMapLatest { (q, source) ->
                    if (q.isBlank()) flowOf(emptyList())
                    else flow { emit(albumDao.searchBySource(q, source)) }
                  }
                  .flowOn(Dispatchers.Default)
                  .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  val artistResults: StateFlow<List<ArtistEntity>> =
          request
                  .flatMapLatest { (q, source) ->
                    if (q.isBlank()) flowOf(emptyList())
                    else flow { emit(artistDao.searchBySource(q, source)) }
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
