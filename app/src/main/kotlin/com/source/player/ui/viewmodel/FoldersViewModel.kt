package com.source.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.source.player.data.db.dao.SongDao
import com.source.player.data.db.entity.SongEntity
import com.source.player.service.PlaybackController
import com.source.player.ui.screens.FolderItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class FoldersViewModel
@Inject
constructor(
        private val songDao: SongDao,
        private val controller: PlaybackController,
) : ViewModel() {

    // Current path being browsed — null = find the common root
    private val _currentPath = MutableStateFlow<String?>(null)
    val currentPath: StateFlow<String?> = _currentPath.asStateFlow()

    // Breadcrumb: list of (displayName, fullPath) pairs from root to current
    private val _breadcrumbs = MutableStateFlow<List<Pair<String, String?>>>(listOf("Root" to null))
    val breadcrumbs: StateFlow<List<Pair<String, String?>>> = _breadcrumbs.asStateFlow()

    private val allSongs: StateFlow<List<SongEntity>> =
            songDao.getAllFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Immediate subfolders of the current path. Shows the real filesystem tree from the music root.
     * Includes all intermediate directories even if they have no direct songs.
     */
    val subFolders: StateFlow<List<FolderItem>> =
            combine(allSongs, _currentPath) { songs, current ->
                        if (songs.isEmpty()) return@combine emptyList()

                        val root = current ?: computeRoot(songs)
                        val prefix = "$root/"

                        // Collect ALL unique folder paths under the current root
                        val allPaths =
                                songs
                                        .map { it.folderPath }
                                        .filter { it.startsWith(prefix) || it == root }
                                        .toSet()

                        // Get immediate child segments
                        val childSegments =
                                mutableMapOf<
                                        String,
                                        MutableSet<String>>() // fullPath -> set of sub-paths
                        val directSongCounts = mutableMapOf<String, Int>()
                        val totalSongCounts = mutableMapOf<String, Int>()

                        for (song in songs) {
                            if (!song.folderPath.startsWith(prefix) && song.folderPath != root)
                                    continue
                            val relative = song.folderPath.removePrefix(prefix)
                            if (relative.isEmpty())
                                    continue // song is directly in the root, no subfolder

                            val segment = relative.substringBefore("/")
                            val childFullPath = "$root/$segment"

                            // Track this child folder
                            childSegments.getOrPut(childFullPath) { mutableSetOf() }
                            totalSongCounts[childFullPath] =
                                    (totalSongCounts[childFullPath] ?: 0) + 1

                            if (song.folderPath == childFullPath) {
                                directSongCounts[childFullPath] =
                                        (directSongCounts[childFullPath] ?: 0) + 1
                            }

                            // Track unique sub-directories within this child
                            val afterSegment = relative.removePrefix(segment).removePrefix("/")
                            if (afterSegment.isNotEmpty()) {
                                val subSegment = afterSegment.substringBefore("/")
                                childSegments[childFullPath]?.add(subSegment)
                            }
                        }

                        childSegments
                                .map { (folderFullPath, subDirs) ->
                                    FolderItem(
                                            path = folderFullPath,
                                            name = folderFullPath.substringAfterLast("/"),
                                            songCount = directSongCounts[folderFullPath] ?: 0,
                                            subFolderCount = subDirs.size,
                                            totalSongCount = totalSongCounts[folderFullPath] ?: 0,
                                    )
                                }
                                .sortedBy { it.name }
                    }
                    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Direct songs at exactly the current path level */
    val songsInFolder: StateFlow<List<SongEntity>> =
            combine(allSongs, _currentPath) { songs, current ->
                        if (current == null) emptyList()
                        else songs.filter { it.folderPath == current }.sortedBy { it.title }
                    }
                    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Total direct-child song count at current level (for header display) */
    val totalSongsCount: StateFlow<Int> =
            combine(allSongs, _currentPath) { songs, current ->
                        if (current == null) 0
                        else
                                songs.count {
                                    it.folderPath.startsWith("$current/") ||
                                            it.folderPath == current
                                }
                    }
                    .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private fun computeRoot(songs: List<SongEntity>): String {
        if (songs.isEmpty()) return ""
        val paths = songs.map { it.folderPath }.filter { it.isNotEmpty() }
        if (paths.isEmpty()) return ""
        // Find the longest common prefix path segment
        var common = paths.first()
        for (path in paths) {
            while (!path.startsWith(common)) {
                common = common.substringBeforeLast("/")
            }
        }
        return common
    }

    fun navigateTo(path: String, name: String) {
        _currentPath.value = path
        val current = _breadcrumbs.value.toMutableList()
        // Pop any breadcrumbs past this path (in case user went back then forward)
        val existingIndex = current.indexOfFirst { it.second == path }
        if (existingIndex >= 0) {
            _breadcrumbs.value = current.subList(0, existingIndex + 1)
        } else {
            current.add(name to path)
            _breadcrumbs.value = current
        }
    }

    fun navigateToRoot() {
        _currentPath.value = null
        _breadcrumbs.value = listOf("Root" to null)
    }

    fun popBack() {
        val crumbs = _breadcrumbs.value
        if (crumbs.size <= 1) return
        navigateToBreadcrumb(crumbs.size - 2)
    }

    fun navigateToBreadcrumb(index: Int) {
        val crumbs = _breadcrumbs.value
        if (index >= crumbs.size) return
        val (_, path) = crumbs[index]
        _currentPath.value = path
        _breadcrumbs.value = crumbs.subList(0, index + 1)
    }

    fun playAll() {
        val current = _currentPath.value ?: return
        val songs =
                allSongs.value
                        .filter {
                            it.folderPath.startsWith("$current/") || it.folderPath == current
                        }
                        .sortedBy { it.title }
        if (songs.isNotEmpty()) {
            // PlaybackController.setQueueFromEntities runs on Main internally
            viewModelScope.launch { controller.setQueueFromEntities(songs, 0) }
        }
    }

    fun shuffleAll() {
        val current = _currentPath.value ?: return
        val songs =
                allSongs.value
                        .filter {
                            it.folderPath.startsWith("$current/") || it.folderPath == current
                        }
                        .shuffled()
        if (songs.isNotEmpty()) {
            viewModelScope.launch { controller.setQueueFromEntities(songs, 0) }
        }
    }

    fun playSong(song: SongEntity) {
        val current = _currentPath.value ?: return
        val queue = allSongs.value.filter { it.folderPath == current }.sortedBy { it.title }
        val startIndex = queue.indexOf(song).coerceAtLeast(0)
        // Launch on Main scope — PlaybackController requires Main thread
        viewModelScope.launch { controller.setQueueFromEntities(queue, startIndex) }
    }
}
