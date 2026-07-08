package com.source.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.source.player.data.db.dao.PlaylistDao
import com.source.player.data.db.dao.SongDao
import com.source.player.data.scanner.MediaScanner
import com.source.player.service.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
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
                controller.currentSong.stateIn(
                        viewModelScope,
                        SharingStarted.WhileSubscribed(5_000),
                        null
                )

        val songCount =
                songDao.countFlow()
                        .distinctUntilChanged()
                        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

        val playlists =
                playlistDao
                        .getAllFlow()
                        .distinctUntilChanged()
                        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        val recentlyAdded =
                songDao.getRecentlyAdded(10)
                        .distinctUntilChanged()
                        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        // Quick picks: stable shuffle per session using a fixed random seed.
        // Shuffling the full library runs off the main thread.
        private val quickPickSeed = System.currentTimeMillis().toInt()
        val quickPicks =
                songDao.getAllFlow()
                        .distinctUntilChanged()
                        .map { it.shuffled(kotlin.random.Random(quickPickSeed)).take(10) }
                        .flowOn(Dispatchers.Default)
                        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        /**
         * Exposes scanner progress message ("Scanning… 42 tracks found") or null when idle.
         * HomeScreen uses this to show a progress indicator while scanning.
         */
        val scanProgress =
                scanner.progressMessage.stateIn(
                        viewModelScope,
                        SharingStarted.WhileSubscribed(5_000),
                        null
                )

        fun scanLibrary() = viewModelScope.launch { scanner.scan() }

        fun playSongs(
                songs: List<com.source.player.data.db.entity.SongEntity>,
                startIndex: Int = 0
        ) {
                controller.setQueueFromEntities(songs, startIndex)
        }
}
