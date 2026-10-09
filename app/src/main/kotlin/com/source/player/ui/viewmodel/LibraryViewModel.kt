package com.source.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.source.player.data.db.dao.*
import com.source.player.data.db.entity.*
import com.source.player.data.mode.LibraryModeRepository
import com.source.player.data.scanner.MediaScanner
import com.source.player.service.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LibraryTab { Songs, Albums, Artists, Playlists, Genres }

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
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
        private val libraryMode: LibraryModeRepository,
        private val scanner: MediaScanner,
) : ViewModel() {
        /**
         * Track count of the active source; -1 until counted, so an empty library state
         * never flashes at launch before the first query answers.
         */
        val songCount: StateFlow<Int> =
                libraryMode.source
                        .flatMapLatest { songDao.countBySourceFlow(it) }
                        .distinctUntilChanged()
                        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1)

        /** Local scan progress ("Scanning… 42 tracks found"), null when idle. */
        val scanProgress: StateFlow<String?> =
                scanner.progressMessage.stateIn(
                        viewModelScope,
                        SharingStarted.WhileSubscribed(5_000),
                        null,
                )

        /** Stream indexing progress, null when idle (Stream's counterpart of [scanProgress]). */
        val indexingProgress: StateFlow<String?> = libraryMode.indexingProgress

        fun scanLibrary() = viewModelScope.launch { scanner.scan() }

        // Every list follows the active library mode (Local / Stream): flatMapLatest drops
        // the previous source's query as soon as the mode changes.
        // distinctUntilChanged: Room re-emits on ANY table invalidation, even when the
        // query result is identical — dedupe before the filter pipelines below re-run.
        val songs =
                libraryMode.source
                        .flatMapLatest { songDao.getAllBySourceFlow(it) }
                        .distinctUntilChanged()
                        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        val albums =
                libraryMode.source
                        .flatMapLatest { albumDao.getAllBySourceFlow(it) }
                        .distinctUntilChanged()
                        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        val artists =
                libraryMode.source
                        .flatMapLatest { artistDao.getAllBySourceFlow(it) }
                        .distinctUntilChanged()
                        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        val playlists =
                libraryMode.source
                        .flatMapLatest { playlistDao.getAllBySourceFlow(it) }
                        .distinctUntilChanged()
                        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        val genres =
                libraryMode.source
                        .flatMapLatest { genreDao.getAllBySourceFlow(it) }
                        .distinctUntilChanged()
                        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        // ── Search merge state ────────────────────────────────────────────
        private val _activeTab = MutableStateFlow(LibraryTab.Artists)
        val activeTab: StateFlow<LibraryTab> = _activeTab.asStateFlow()

        private val _query = MutableStateFlow("")
        val query: StateFlow<String> = _query.asStateFlow()

        // Debounced query — avoids thrashing filtering on fast typing.
        private val debouncedQuery = _query.debounce(100).distinctUntilChanged()

        // flowOn(Default): filtering the full library must not run on the main thread.
        val filteredSongs: StateFlow<List<SongEntity>> =
                combine(debouncedQuery, songs) { q, all ->
                        if (q.isBlank()) all
                        else all.filter { it.title.contains(q, true) || it.artist.contains(q, true) }
                }
                        .flowOn(Dispatchers.Default)
                        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        val filteredAlbums: StateFlow<List<AlbumEntity>> =
                combine(debouncedQuery, albums) { q, all ->
                        if (q.isBlank()) all else all.filter { it.title.contains(q, true) }
                }
                        .flowOn(Dispatchers.Default)
                        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        val filteredArtists: StateFlow<List<ArtistEntity>> =
                combine(debouncedQuery, artists) { q, all ->
                        if (q.isBlank()) all else all.filter { it.name.contains(q, true) }
                }
                        .flowOn(Dispatchers.Default)
                        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        val filteredPlaylists: StateFlow<List<PlaylistEntity>> =
                combine(debouncedQuery, playlists) { q, all ->
                        if (q.isBlank()) all else all.filter { it.name.contains(q, true) }
                }
                        .flowOn(Dispatchers.Default)
                        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        val filteredGenres: StateFlow<List<GenreEntity>> =
                combine(debouncedQuery, genres) { q, all ->
                        if (q.isBlank()) all else all.filter { it.name.contains(q, true) }
                }
                        .flowOn(Dispatchers.Default)
                        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        fun onQueryChange(new: String) {
                _query.value = new
        }

        fun onTabChange(tab: LibraryTab) {
                if (_activeTab.value != tab) {
                        _activeTab.value = tab
                        // Per SEARCH_MERGE.md §4: clear query on tab change so the
                        // placeholder text refreshes and results never look mismatched.
                        _query.value = ""
                }
        }

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

        fun shuffleAllSongs() = viewModelScope.launch {
                val shuffled = withContext(Dispatchers.Default) {
                        songs.value.shuffled()
                }
                if (shuffled.isNotEmpty()) {
                        controller.setQueue(shuffled.map { it.toMediaItem() }, 0)
                }
        }

        fun createPlaylist(name: String) =
                viewModelScope.launch {
                        if (name.isBlank()) return@launch
                        // Playlists belong to the mode they were created in.
                        playlistDao.insert(
                                PlaylistEntity(name = name, source = libraryMode.currentSource())
                        )
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
