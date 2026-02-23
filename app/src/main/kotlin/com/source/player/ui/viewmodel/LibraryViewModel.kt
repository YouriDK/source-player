package com.source.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.source.player.data.db.dao.*
import com.source.player.data.db.entity.*
import com.source.player.service.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel
@Inject
constructor(
        private val songDao: SongDao,
        private val albumDao: AlbumDao,
        private val artistDao: ArtistDao,
        private val playlistDao: PlaylistDao,
        private val genreDao: GenreDao,
        private val controller: PlaybackController,
) : ViewModel() {
        val songs = songDao.getAllFlow().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        val albums =
                albumDao.getAllFlow().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        val artists =
                artistDao.getAllFlow().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        val playlists =
                playlistDao.getAllFlow().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        val genres =
                genreDao.getAllFlow().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        fun playSongsFromIndex(songs: List<SongEntity>, index: Int) {
                val items = songs.map { it.toMediaItem() }
                controller.setQueue(items, index)
        }

        fun playAllSongs() {
                val allSongs = songs.value
                if (allSongs.isNotEmpty()) {
                        controller.setQueue(allSongs.map { it.toMediaItem() }, 0)
                }
        }

        fun shuffleAllSongs() {
                val allSongs = songs.value.shuffled()
                if (allSongs.isNotEmpty()) {
                        controller.setQueue(allSongs.map { it.toMediaItem() }, 0)
                }
        }

        fun createPlaylist(name: String) =
                viewModelScope.launch {
                        if (name.isNotBlank()) playlistDao.insert(PlaylistEntity(name = name))
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
