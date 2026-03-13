package com.source.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.source.player.data.db.dao.PlaylistDao
import com.source.player.data.db.dao.SongDao
import com.source.player.data.db.entity.PlaylistEntity
import com.source.player.data.db.entity.SongEntity
import com.source.player.data.scanner.MediaScanner
import com.source.player.service.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel
@Inject
constructor(
        private val scanner: MediaScanner,
        private val songDao: SongDao,
        private val playlistDao: PlaylistDao,
        private val controller: PlaybackController,
) : ViewModel() {

        val currentSong =
                controller.currentSong.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        val songCount =
                songDao.getAllFlow()
                        .map { it.size }
                        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

        val playlists =
                playlistDao
                        .getAllFlow()
                        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        val recentlyAdded =
                songDao.getAllFlow()
                        .map { songs -> songs.sortedByDescending { it.dateAdded }.take(10) }
                        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        val quickPicks =
                songDao.getAllFlow()
                        .map { songs -> songs.shuffled().take(5) }
                        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        val continueListening =
                songDao.getAllFlow()
                        .map { songs -> songs.distinctBy { it.albumId }.take(5) }
                        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        /**
         * Exposes scanner progress message ("Scanning… 42 tracks found") or null when idle.
         * HomeScreen uses this to show a progress indicator while scanning.
         */
        val scanProgress =
                scanner.progressMessage.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        fun scanLibrary() = viewModelScope.launch { scanner.scan() }

        fun playSongs(songs: List<SongEntity>) {
                if (songs.isNotEmpty()) controller.setQueueFromEntities(songs, 0)
        }

        fun playSong(song: SongEntity) {
                controller.setQueueFromEntities(listOf(song), 0)
        }

        fun createPlaylist(name: String) =
                viewModelScope.launch { playlistDao.insert(PlaylistEntity(name = name)) }
}
