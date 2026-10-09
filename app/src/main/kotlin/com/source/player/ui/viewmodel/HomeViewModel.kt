package com.source.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.source.player.data.db.dao.PlaylistDao
import com.source.player.data.db.dao.SongDao
import com.source.player.data.mode.LibraryModeRepository
import com.source.player.data.scanner.MediaScanner
import com.source.player.service.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel
@Inject
constructor(
        private val scanner: MediaScanner,
        private val songDao: SongDao,
        private val playlistDao: PlaylistDao,
        private val controller: PlaybackController,
        libraryMode: LibraryModeRepository,
) : ViewModel() {

        val currentSong =
                controller.currentSong.stateIn(
                        viewModelScope,
                        SharingStarted.WhileSubscribed(5_000),
                        null
                )

        // Everything below follows the active library mode (Local / Stream).
        private val source = libraryMode.source

        /** -1 until the active source has been counted (no empty-library flash at launch). */
        val songCount =
                source.flatMapLatest { songDao.countBySourceFlow(it) }
                        .distinctUntilChanged()
                        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1)

        val playlists =
                source.flatMapLatest { playlistDao.getAllBySourceFlow(it) }
                        .distinctUntilChanged()
                        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        val recentlyAdded =
                source.flatMapLatest { songDao.getRecentlyAddedBySource(10, it) }
                        .distinctUntilChanged()
                        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        // Quick picks: stable shuffle per session using a fixed random seed.
        // Shuffling the full library runs off the main thread.
        private val quickPickSeed = System.currentTimeMillis().toInt()
        val quickPicks =
                source.flatMapLatest { songDao.getAllBySourceFlow(it) }
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

        /** Freebox indexing progress, null when idle (Stream mode's counterpart of [scanProgress]). */
        val indexingProgress = libraryMode.indexingProgress

        fun scanLibrary() = viewModelScope.launch { scanner.scan() }

        fun playSongs(
                songs: List<com.source.player.data.db.entity.SongEntity>,
                startIndex: Int = 0
        ) {
                controller.setQueueFromEntities(songs, startIndex)
        }
}
