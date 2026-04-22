package com.source.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.source.player.data.db.dao.AlbumDao
import com.source.player.data.db.dao.ArtistDao
import com.source.player.data.db.dao.SongDao
import com.source.player.data.db.entity.AlbumEntity
import com.source.player.data.db.entity.ArtistEntity
import com.source.player.data.db.entity.SongEntity
import com.source.player.service.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*

@HiltViewModel
class ArtistDetailViewModel
@Inject
constructor(
        private val artistDao: ArtistDao,
        private val albumDao: AlbumDao,
        private val songDao: SongDao,
        private val controller: PlaybackController,
) : ViewModel() {
    private val artistId = MutableStateFlow<Long?>(null)

    val artist: StateFlow<ArtistEntity?> =
            artistId
                    .flatMapLatest { id: Long? ->
                        if (id == null) flowOf<ArtistEntity?>(null)
                        else flow<ArtistEntity?> { emit(artistDao.getById(id)) }
                    }
                    .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val songs: StateFlow<List<SongEntity>> =
            artistId
                    .flatMapLatest { id ->
                        if (id == null) flowOf(emptyList()) else songDao.getByArtist(id)
                    }
                    .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Albums by this artist, derived from their songs. */
    val albums: StateFlow<List<AlbumEntity>> =
            songs
                    .map { list ->
                        val ids = list.map { it.albumId }.distinct()
                        ids.mapNotNull { albumDao.getById(it) }
                    }
                    .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun load(id: Long) {
        artistId.value = id
    }

    fun playAll() = controller.setQueueFromEntities(songs.value, 0)

    fun shuffle() {
        controller.setShuffleEnabled(true)
        controller.setQueueFromEntities(songs.value.shuffled(), 0)
    }

    fun playSong(song: SongEntity) {
        val list = songs.value
        val idx = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        controller.setQueueFromEntities(list, idx)
    }
}
