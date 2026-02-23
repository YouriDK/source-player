package com.source.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.source.player.data.db.dao.AlbumDao
import com.source.player.data.db.dao.SongDao
import com.source.player.data.db.entity.AlbumEntity
import com.source.player.data.db.entity.SongEntity
import com.source.player.service.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*

@HiltViewModel
class AlbumDetailViewModel
@Inject
constructor(
        private val albumDao: AlbumDao,
        private val songDao: SongDao,
        private val controller: PlaybackController,
) : ViewModel() {
  private val albumId = MutableStateFlow<Long?>(null)

  val album: StateFlow<AlbumEntity?> =
          albumId
                  .flatMapLatest { id: Long? ->
                    if (id == null) {
                      flowOf<AlbumEntity?>(null)
                    } else {
                      flow<AlbumEntity?> { emit(albumDao.getById(id)) }
                    }
                  }
                  .stateIn(viewModelScope, SharingStarted.Lazily, null)

  val songs: StateFlow<List<SongEntity>> =
          albumId
                  .flatMapLatest { id ->
                    if (id == null) flowOf(emptyList()) else songDao.getByAlbum(id)
                  }
                  .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  fun load(id: Long) {
    albumId.value = id
  }

  fun playAll(songs: List<SongEntity>) {
    controller.setQueue(songs.map { it.toMediaItem() }, 0)
  }

  fun shuffle(songs: List<SongEntity>) {
    controller.setShuffleEnabled(true)
    controller.setQueue(songs.map { it.toMediaItem() }.shuffled(), 0)
  }

  fun playSongFromAlbum(song: SongEntity, all: List<SongEntity>) {
    val index = all.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
    controller.setQueue(all.map { it.toMediaItem() }, index)
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
