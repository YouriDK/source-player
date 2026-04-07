package com.source.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.source.player.data.db.dao.PlaylistDao
import com.source.player.data.db.dao.SongDao
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
                        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        // Use getAddedSince(0) to get all songs sorted by dateAdded DESC, then take 10
        val recentlyAdded =
                songDao.getAddedSince(0L)
                        .map { it.take(10) }
                        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        // Quick picks: stable shuffle per session using a fixed random seed
        private val quickPickSeed = System.currentTimeMillis().toInt()
        val quickPicks =
                songDao.getAllFlow()
                        .map { it.shuffled(kotlin.random.Random(quickPickSeed)).take(10) }
                        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        /**
         * Exposes scanner progress message ("Scanning… 42 tracks found") or null when idle.
         * HomeScreen uses this to show a progress indicator while scanning.
         */
        val scanProgress =
                scanner.progressMessage.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        fun scanLibrary() = viewModelScope.launch { scanner.scan() }

        fun playSongs(
                songs: List<com.source.player.data.db.entity.SongEntity>,
                startIndex: Int = 0
        ) {
                controller.setQueueFromEntities(songs, startIndex)
        }
}
