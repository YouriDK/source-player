package com.source.player.ui.viewmodel

import android.content.IntentSender
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.source.player.data.db.dao.PlaylistDao
import com.source.player.data.db.dao.SongDao
import com.source.player.data.db.entity.PlaylistEntity
import com.source.player.data.db.entity.SOURCE_LOCAL
import com.source.player.data.db.entity.SongEntity
import com.source.player.data.files.LibraryFileChanges
import com.source.player.data.files.LocalFileOperations
import com.source.player.data.files.LocalMediaPaths
import com.source.player.data.preferences.AppPreferences
import com.source.player.service.PlaybackController
import com.source.player.ui.components.FolderPickerState
import com.source.player.ui.components.PickerCrumb
import com.source.player.ui.components.PickerFolder
import com.source.player.ui.screens.FolderItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class FoldersViewModel
@Inject
constructor(
        private val songDao: SongDao,
        private val playlistDao: PlaylistDao,
        private val controller: PlaybackController,
        private val prefs: AppPreferences,
        private val fileOps: LocalFileOperations,
) : ViewModel() {

    // Current path being browsed — null = find the common root
    private val _currentPath = MutableStateFlow<String?>(null)
    val currentPath: StateFlow<String?> = _currentPath.asStateFlow()

    // Breadcrumb: list of (displayName, fullPath) pairs from root to current
    private val _breadcrumbs = MutableStateFlow<List<Pair<String, String?>>>(listOf("Root" to null))
    val breadcrumbs: StateFlow<List<Pair<String, String?>>> = _breadcrumbs.asStateFlow()

    private val allSongs: StateFlow<List<SongEntity>> =
            songDao.getAllBySourceFlow(SOURCE_LOCAL)
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Immediate subfolders of the current path. Shows the real filesystem tree from the music root.
     * Includes all intermediate directories even if they have no direct songs.
     */
    val subFolders: StateFlow<List<FolderItem>> =
            combine(allSongs, _currentPath) { songs, current ->
                        if (songs.isEmpty()) return@combine emptyList()

                        val root = current ?: computeRoot(songs)
                        val prefix = "$root/"

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
                    .flowOn(Dispatchers.Default)
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Direct songs at exactly the current path level */
    val songsInFolder: StateFlow<List<SongEntity>> =
            combine(allSongs, _currentPath) { songs, current ->
                        if (current == null) emptyList()
                        else songs.filter { it.folderPath == current }.sortedBy { it.title }
                    }
                    .flowOn(Dispatchers.Default)
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
                    .flowOn(Dispatchers.Default)
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

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

    init {
        // Reopen on the folder the user was browsing before the app was killed.
        viewModelScope.launch {
            if (!prefs.restoreState.first()) return@launch
            val saved = prefs.lastFolderPath.first() ?: return@launch
            val songs = allSongs.first { it.isNotEmpty() }
            val restored =
                    withContext(Dispatchers.Default) {
                        val root = computeRoot(songs)
                        val valid =
                                saved.startsWith("$root/") &&
                                        songs.any {
                                            it.folderPath == saved ||
                                                    it.folderPath.startsWith("$saved/")
                                        }
                        if (!valid) return@withContext null
                        val crumbs = mutableListOf<Pair<String, String?>>("Root" to null)
                        var cumulative = root
                        for (segment in saved.removePrefix("$root/").split("/")) {
                            cumulative = "$cumulative/$segment"
                            crumbs.add(segment to cumulative)
                        }
                        crumbs
                    }
            // Don't override a navigation the user already made while we were waiting.
            if (restored != null && _currentPath.value == null) {
                _breadcrumbs.value = restored
                _currentPath.value = saved
            } else if (restored == null) {
                prefs.setLastFolderPath(null)
            }
        }
    }

    private fun persistPath(path: String?) {
        viewModelScope.launch { prefs.setLastFolderPath(path) }
    }

    // ---- Selection, move, delete (songs of the current folder) ----

    /**
     * Move / delete go through MediaStore write and trash requests, which only exist on
     * API 30+. Below that, selection mode is not offered at all: the legacy storage
     * permission would allow it, but only as a permanent delete with no system trash.
     */
    val canEditFiles: Boolean = fileOps.isSupported

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    /** Ids of the selected songs; empty = not selecting. */
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-shot snackbar messages. */
    val messages = _messages.receiveAsFlow()

    private val _consentRequests = Channel<IntentSender>(Channel.BUFFERED)
    /** System confirmation dialogs (trash / write requests) for the screen to launch. */
    val consentRequests = _consentRequests.receiveAsFlow()

    /** Work waiting for the user's answer to the system dialog. */
    private var pendingConsent: (suspend () -> Unit)? = null

    private val _picker = MutableStateFlow<FolderPickerState?>(null)
    val picker: StateFlow<FolderPickerState?> = _picker.asStateFlow()
    private var pickerPath: String? = null

    fun startSelection(song: SongEntity) {
        if (!canEditFiles || _busy.value) return
        _selection.value = _selection.value + song.id
    }

    fun toggleSelection(song: SongEntity) {
        if (_busy.value) return
        val current = _selection.value
        _selection.value = if (song.id in current) current - song.id else current + song.id
    }

    fun clearSelection() {
        if (!_busy.value) _selection.value = emptySet()
    }

    private fun selectedSongs(): List<SongEntity> {
        val ids = _selection.value
        return songsInFolder.value.filter { it.id in ids }
    }

    /** Asks Android to move the selection to the system trash (one dialog for the batch). */
    fun deleteSelected() {
        val songs = selectedSongs()
        if (songs.isEmpty() || _busy.value || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        viewModelScope.launch {
            requestConsent(
                    request = { fileOps.trashRequest(songs) },
                    onGranted = {
                        val changes = fileOps.onTrashed(songs)
                        finish(changes, if (songs.size == 1) "Moved to the trash" else "${songs.size} songs moved to the trash")
                    },
            )
        }
    }

    fun openMovePicker() {
        val songs = selectedSongs()
        if (songs.isEmpty() || _busy.value) return
        val volume = LocalMediaPaths.volumeRoot(songs.first().path)
        if (volume == null || songs.any { LocalMediaPaths.volumeRoot(it.path) != volume }) {
            viewModelScope.launch { _messages.send("Select songs from a single storage volume") }
            return
        }
        loadPicker(volume)
    }

    fun pickerOpen(folder: PickerFolder) = loadPicker(folder.key)

    fun pickerCrumb(index: Int) {
        _picker.value?.crumbs?.getOrNull(index)?.let { loadPicker(it.key) }
    }

    fun dismissPicker() {
        _picker.value = null
        pickerPath = null
    }

    fun confirmMove() {
        val dest = pickerPath ?: return
        val songs = selectedSongs()
        if (songs.isEmpty() || _busy.value || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        dismissPicker()
        viewModelScope.launch {
            val refusal = fileOps.checkMove(songs, dest)
            if (refusal != null) {
                _messages.send(refusal)
                return@launch
            }
            requestConsent(
                    request = { fileOps.writeRequest(songs) },
                    onGranted = {
                        val outcome = fileOps.move(songs, dest)
                        val moved = songs.size - outcome.failed
                        val name = dest.substringAfterLast('/')
                        val message =
                                when {
                                    outcome.failed == 0 && moved == 1 -> "Moved to $name"
                                    outcome.failed == 0 -> "$moved songs moved to $name"
                                    moved == 0 -> "Android refused to move the files"
                                    else -> "$moved moved to $name, ${outcome.failed} failed"
                                }
                        finish(outcome.changes, message)
                    },
            )
        }
    }

    /** Called by the screen once the system dialog answered. */
    fun onConsentResult(granted: Boolean) {
        val work = pendingConsent ?: return
        pendingConsent = null
        if (!granted) {
            _busy.value = false
            viewModelScope.launch { _messages.send("Cancelled — nothing was changed") }
            return
        }
        viewModelScope.launch {
            try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send("Couldn't change the files: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun requestConsent(request: suspend () -> IntentSender, onGranted: suspend () -> Unit) {
        _busy.value = true
        try {
            val sender = request()
            pendingConsent = onGranted
            _consentRequests.send(sender)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _busy.value = false
            _messages.send("Android refused the request: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun finish(changes: LibraryFileChanges, message: String) {
        controller.applyFileChanges(changes)
        _selection.value = emptySet()
        _messages.send(message)
    }

    private fun loadPicker(path: String) {
        val songs = selectedSongs()
        val volume = LocalMediaPaths.volumeRoot(path) ?: return
        pickerPath = path
        val folders = allSongs.value.mapTo(HashSet()) { it.folderPath }
        val crumbs = ArrayList<PickerCrumb>()
        crumbs += PickerCrumb(volume, volumeLabel(volume))
        var cumulative = volume
        for (segment in path.removePrefix(volume).split('/').filter { it.isNotEmpty() }) {
            cumulative = "$cumulative/$segment"
            crumbs += PickerCrumb(cumulative, segment)
        }
        val refusal = LocalMediaPaths.checkMoveTarget(songs.map { it.path }, path)
        _picker.value =
                FolderPickerState(
                        crumbs = crumbs,
                        folders =
                                LocalMediaPaths.childFolders(folders, path).map {
                                    PickerFolder(it, it.substringAfterLast('/'))
                                },
                        canChoose = refusal == null,
                        hint = refusal,
                )
    }

    private fun volumeLabel(volume: String): String =
            if (volume.startsWith("/storage/emulated/")) "Phone" else volume.substringAfterLast('/')

    fun navigateTo(path: String, name: String) {
        _selection.value = emptySet()
        _currentPath.value = path
        persistPath(path)
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
        _selection.value = emptySet()
        _currentPath.value = null
        persistPath(null)
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
        _selection.value = emptySet()
        val (_, path) = crumbs[index]
        _currentPath.value = path
        persistPath(path)
        _breadcrumbs.value = crumbs.subList(0, index + 1)
    }

    fun playAll() {
        val current = _currentPath.value ?: return
        viewModelScope.launch {
            val songs =
                    withContext(Dispatchers.Default) {
                        allSongs.value
                                .filter {
                                    it.folderPath.startsWith("$current/") ||
                                            it.folderPath == current
                                }
                                .sortedBy { it.title }
                    }
            if (songs.isNotEmpty()) controller.setQueueFromEntities(songs, 0)
        }
    }

    fun shuffleAll() {
        val current = _currentPath.value ?: return
        viewModelScope.launch {
            val songs =
                    withContext(Dispatchers.Default) {
                        allSongs.value
                                .filter {
                                    it.folderPath.startsWith("$current/") ||
                                            it.folderPath == current
                                }
                                .shuffled()
                    }
            if (songs.isNotEmpty()) controller.setQueueFromEntities(songs, 0)
        }
    }

    fun playSong(song: SongEntity) {
        val current = _currentPath.value ?: return
        viewModelScope.launch {
            val queue =
                    withContext(Dispatchers.Default) {
                        allSongs.value.filter { it.folderPath == current }.sortedBy { it.title }
                    }
            val startIndex = queue.indexOf(song).coerceAtLeast(0)
            controller.setQueueFromEntities(queue, startIndex)
        }
    }

    // This browser only exists in Local mode (Stream shows the Freebox music folder), so
    // its "add folder to playlist" sheet deals with Local playlists only.
    val playlists =
            playlistDao
                    .getAllBySourceFlow(SOURCE_LOCAL)
                    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { playlistDao.insert(PlaylistEntity(name = name, source = SOURCE_LOCAL)) }
    }

    fun addFolderToPlaylist(playlistId: Long) {
        val current = _currentPath.value ?: return
        viewModelScope.launch {
            val songs =
                    withContext(Dispatchers.Default) {
                        allSongs.value
                                .filter {
                                    it.folderPath.startsWith("$current/") ||
                                            it.folderPath == current
                                }
                                .sortedBy { it.title }
                    }
            if (songs.isEmpty()) return@launch
            val currentMax = playlistDao.maxPosition(playlistId) ?: -1
            val entries =
                    songs.mapIndexed { i, song ->
                        com.source.player.data.db.entity.PlaylistSongEntity(
                                playlistId = playlistId,
                                songId = song.id,
                                position = currentMax + 1 + i
                        )
                    }
            playlistDao.addSongsToPlaylist(entries)
        }
    }
}
