package com.source.player.ui.viewmodel

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.source.player.data.files.FileOperationException
import com.source.player.data.files.FreeboxFileOperations
import com.source.player.data.freebox.FreeboxAudio
import com.source.player.data.freebox.FreeboxPermissionException
import com.source.player.data.freebox.FreeboxTaskTimeoutException
import com.source.player.data.freebox.FreeboxAuthException
import com.source.player.data.freebox.FreeboxClient
import com.source.player.data.freebox.FreeboxFolder
import com.source.player.data.freebox.FreeboxPaths
import com.source.player.data.freebox.FreeboxUnreachableException
import com.source.player.data.freebox.RemoteEntry
import com.source.player.data.mode.LibraryModeRepository
import com.source.player.service.PlaybackController
import com.source.player.ui.components.FolderPickerState
import com.source.player.ui.components.PickerCrumb
import com.source.player.ui.components.PickerFolder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.FileNotFoundException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the Freebox browser shows for the current folder. */
sealed interface FreeboxUiState {
    data object Loading : FreeboxUiState

    /**
     * [needsPairing]: the box refused us (never paired / revoked) — offer Settings, not Retry.
     * [musicFolderMissing]: the music folder itself is gone — offer the folder picker.
     */
    data class Error(
            val message: String,
            val retryable: Boolean,
            val needsPairing: Boolean = false,
            val musicFolderMissing: Boolean = false,
    ) : FreeboxUiState

    data class Content(val folders: List<RemoteEntry>, val tracks: List<RemoteEntry>) :
            FreeboxUiState
}

/** What the live Freebox browser is for. */
enum class FreeboxBrowseMode {
    /**
     * Folders tab in Stream mode: rooted at the music folder — the breadcrumb trail stops
     * there and [FreeboxViewModel.goUp] never climbs above it; the disks are never shown.
     */
    MUSIC_FOLDER,

    /** Music folder picker: rooted at the disks, free navigation, "Choose this folder". */
    PICK,
}

/**
 * Live browser over the Freebox: nothing is indexed, every folder is listed on demand
 * through [FreeboxClient.list] and kept in a small in-memory cache so going back is
 * instant. Only audio files are shown; playback queues the current folder's tracks.
 *
 * The [mode] comes from the [ARG_PICK] navigation argument (absent = [FreeboxBrowseMode.MUSIC_FOLDER]).
 */
@HiltViewModel
class FreeboxViewModel
@Inject
constructor(
        private val client: FreeboxClient,
        private val controller: PlaybackController,
        private val libraryMode: LibraryModeRepository,
        private val fileOps: FreeboxFileOperations,
        savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val mode: FreeboxBrowseMode =
            if (savedStateHandle.get<Boolean>(ARG_PICK) == true) FreeboxBrowseMode.PICK
            else FreeboxBrowseMode.MUSIC_FOLDER

    /** null until the token store has been read — the screen shows a spinner meanwhile. */
    val isPaired: StateFlow<Boolean?> =
            client.isPaired.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val boxName: StateFlow<String?> =
            client.boxName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Top of the browser: the music folder in [FreeboxBrowseMode.MUSIC_FOLDER], null (the
     * disks) in [FreeboxBrowseMode.PICK].
     */
    private var root: FreeboxFolder? = null

    private val _currentFolder = MutableStateFlow<FreeboxFolder?>(null)
    /** null = the disks root (picker only). */
    val currentFolder: StateFlow<FreeboxFolder?> = _currentFolder.asStateFlow()

    private val _breadcrumbs = MutableStateFlow(listOf(ROOT))
    /** Browser root first (music folder, or "Freebox" = the disks), current folder last. */
    val breadcrumbs: StateFlow<List<FreeboxFolder>> = _breadcrumbs.asStateFlow()

    private val _suggestions = MutableStateFlow<List<FreeboxFolder>>(emptyList())
    /** Picker: "Music" / "Musique" folders found at the root of a disk. */
    val suggestions: StateFlow<List<FreeboxFolder>> = _suggestions.asStateFlow()

    private val _chosen = MutableStateFlow(false)
    /** Picker: true once a music folder has been stored — the screen pops itself. */
    val chosen: StateFlow<Boolean> = _chosen.asStateFlow()

    private val _entries = MutableStateFlow<FreeboxUiState>(FreeboxUiState.Loading)
    val entries: StateFlow<FreeboxUiState> = _entries.asStateFlow()

    /** url → listing; access-ordered so the least recently visited folder is evicted. */
    private val cache =
            object : LinkedHashMap<String, FreeboxUiState.Content>(16, 0.75f, true) {
                override fun removeEldestEntry(
                        eldest: MutableMap.MutableEntry<String, FreeboxUiState.Content>?
                ): Boolean = size > CACHE_SIZE
            }

    // ---- Selection (Folders tab only) ----

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    /** Urls of the selected folders / tracks of the current folder; empty = not selecting. */
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val _busy = MutableStateFlow(false)
    /** A move / delete is running on the box. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-shot snackbar messages (results and errors of move / delete). */
    val messages = _messages.receiveAsFlow()

    private val _picker = MutableStateFlow<FolderPickerState?>(null)
    /** Move destination picker; null when closed. */
    val picker: StateFlow<FolderPickerState?> = _picker.asStateFlow()
    private var pickerUrl: String? = null
    private var pickerJob: Job? = null

    private var loadJob: Job? = null
    private var suggestionsJob: Job? = null
    private var started = false

    init {
        viewModelScope.launch {
            when (mode) {
                FreeboxBrowseMode.PICK ->
                        isPaired.collect { paired ->
                            if (paired != true) return@collect
                            if (!started) {
                                started = true
                                show(null, useCache = true)
                                loadSuggestions()
                            } else if (_entries.value is FreeboxUiState.Error) {
                                // Paired again (e.g. from Settings) after an auth failure.
                                show(_currentFolder.value, useCache = false)
                            }
                        }
                FreeboxBrowseMode.MUSIC_FOLDER ->
                        combine(client.isPaired, client.startFolder) { paired, folder ->
                                    paired to folder
                                }
                                .distinctUntilChanged()
                                .collect { (paired, folder) ->
                                    // No folder yet: the Folders tab shows "Choose your music folder".
                                    if (!paired || folder == null) return@collect
                                    if (folder.url != root?.url) {
                                        // New music folder: forget everything listed under the old one.
                                        root = folder
                                        cache.clear()
                                        show(folder, useCache = false)
                                    } else if (_entries.value is FreeboxUiState.Error) {
                                        show(_currentFolder.value, useCache = false)
                                    }
                                }
            }
        }
    }

    // ---- Navigation ----

    fun open(folder: FreeboxFolder) = show(folder, useCache = true)

    fun open(entry: RemoteEntry) {
        if (entry.isDirectory) open(entry.toFolder())
    }

    fun navigateTo(breadcrumbIndex: Int) {
        val target = _breadcrumbs.value.getOrNull(breadcrumbIndex) ?: return
        show(target.takeUnless { it.url == ROOT.url }, useCache = true)
    }

    /** Back one level; false when already at the browser root (let the system handle Back). */
    fun goUp(): Boolean {
        val crumbs = _breadcrumbs.value
        if (crumbs.size <= 1) return false
        navigateTo(crumbs.size - 2)
        return true
    }

    fun refresh() = show(_currentFolder.value, useCache = false)

    fun retry() = show(_currentFolder.value, useCache = false)

    private fun show(requested: FreeboxFolder?, useCache: Boolean) {
        // Never above the root: the music folder is the top of everything in Stream.
        val folder = if (isUnderRoot(requested)) requested else root
        if (folder?.url != _currentFolder.value?.url) _selection.value = emptySet()
        _currentFolder.value = folder
        _breadcrumbs.value = breadcrumbsFor(folder)
        val key = folder?.url ?: ROOT.url
        loadJob?.cancel()
        val cached = if (useCache) cache[key] else null
        if (cached != null) {
            _entries.value = cached
            return
        }
        _entries.value = FreeboxUiState.Loading
        loadJob =
                viewModelScope.launch {
                    _entries.value =
                            try {
                                val listing =
                                        withContext(Dispatchers.IO) {
                                            client.list(folder?.url)
                                        }
                                val content =
                                        withContext(Dispatchers.Default) { toContent(listing) }
                                cache[key] = content
                                if (isMusicFolderRoot(folder)) libraryMode.reportMusicFolderFound(key)
                                content
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                if (e is FileNotFoundException && isMusicFolderRoot(folder)) {
                                    libraryMode.reportMusicFolderMissing(key)
                                    FreeboxUiState.Error(
                                            "Your music folder was moved, renamed or its disk is unplugged.",
                                            retryable = true,
                                            musicFolderMissing = true,
                                    )
                                } else {
                                    errorState(e)
                                }
                            }
                }
    }

    // ---- Playback ----

    fun playTrack(track: RemoteEntry) {
        val tracks = currentTracks()
        val index = tracks.indexOfFirst { it.url == track.url }.coerceAtLeast(0)
        if (tracks.isEmpty()) return
        controller.setQueue(tracks.map { it.toMediaItem() }, index)
    }

    fun playAll() {
        val tracks = currentTracks()
        if (tracks.isNotEmpty()) controller.setQueue(tracks.map { it.toMediaItem() }, 0)
    }

    fun shuffleAll() {
        val tracks = currentTracks().shuffled()
        if (tracks.isNotEmpty()) controller.setQueue(tracks.map { it.toMediaItem() }, 0)
    }

    fun playNext(track: RemoteEntry) = controller.addNextToQueue(track.toMediaItem())

    fun addToQueue(track: RemoteEntry) = controller.addToQueue(track.toMediaItem())

    fun addFolderToQueue() = currentTracks().forEach { controller.addToQueue(it.toMediaItem()) }

    // ---- Selection, move, delete ----

    /** Long press: enters selection mode with [entry] selected (Folders tab only). */
    fun startSelection(entry: RemoteEntry) {
        if (mode != FreeboxBrowseMode.MUSIC_FOLDER || _busy.value) return
        _selection.value = _selection.value + entry.url
    }

    fun toggleSelection(entry: RemoteEntry) {
        if (_busy.value) return
        val current = _selection.value
        _selection.value = if (entry.url in current) current - entry.url else current + entry.url
    }

    fun clearSelection() {
        if (!_busy.value) _selection.value = emptySet()
    }

    private fun selectedEntries(): List<RemoteEntry> {
        val content = _entries.value as? FreeboxUiState.Content ?: return emptyList()
        val selected = _selection.value
        return (content.folders + content.tracks).filter { it.url in selected }
    }

    /** Moves the selection to the music folder's trash (.corbeille); confirmed by the screen. */
    fun deleteSelected() {
        val musicRoot = root ?: return
        val entries = selectedEntries()
        if (entries.isEmpty() || _busy.value) return
        runFileOperation(
                success = { if (entries.size == 1) "Moved to the trash" else "${entries.size} items moved to the trash" },
        ) { fileOps.trash(entries, musicRoot) }
    }

    fun openMovePicker() {
        val musicRoot = root ?: return
        if (_selection.value.isEmpty() || _busy.value) return
        loadPicker(musicRoot.url)
    }

    fun pickerOpen(folder: PickerFolder) {
        if (folder.enabled) loadPicker(folder.key)
    }

    fun pickerCrumb(index: Int) {
        val crumb = _picker.value?.crumbs?.getOrNull(index) ?: return
        loadPicker(crumb.key)
    }

    fun dismissPicker() {
        pickerJob?.cancel()
        _picker.value = null
        pickerUrl = null
    }

    fun confirmMove() {
        val musicRoot = root ?: return
        val dest = pickerUrl ?: return
        val entries = selectedEntries()
        if (entries.isEmpty() || _busy.value) return
        dismissPicker()
        val destName = FreeboxPaths.name(dest).ifEmpty { musicRoot.name }
        runFileOperation(
                success = { if (entries.size == 1) "Moved to $destName" else "${entries.size} items moved to $destName" },
        ) { fileOps.move(entries, dest, musicRoot) }
    }

    private fun loadPicker(url: String) {
        val musicRoot = root ?: return
        pickerUrl = url
        val crumbs = breadcrumbsFor(FreeboxFolder(url, FreeboxPaths.toDisplayPath(url), FreeboxPaths.name(url)))
                .map { PickerCrumb(it.url, it.name) }
        val selected = _selection.value
        val check = FreeboxFileOperations.checkMoveTarget(selectedEntries(), url, musicRoot.url)
        _picker.value = FolderPickerState(crumbs = crumbs, folders = emptyList(), loading = true, canChoose = check == null, hint = check)
        pickerJob?.cancel()
        pickerJob =
                viewModelScope.launch {
                    val next =
                            try {
                                val listing = withContext(Dispatchers.IO) { client.list(url) }
                                val folders =
                                        listing.filter { it.isDirectory }
                                                .sortedWith(compareBy(NaturalOrder) { it.name })
                                                .map { PickerFolder(it.url, it.name, enabled = it.url !in selected) }
                                _picker.value?.copy(folders = folders, loading = false)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                _picker.value?.copy(loading = false, error = operationMessage(e), canChoose = false)
                            }
                    if (pickerUrl == url) _picker.value = next
                }
    }

    private fun runFileOperation(
            success: () -> String,
            block: suspend () -> com.source.player.data.files.LibraryFileChanges,
    ) {
        _busy.value = true
        viewModelScope.launch {
            try {
                val changes = block()
                controller.applyFileChanges(changes)
                _selection.value = emptySet()
                _messages.send(success())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(operationMessage(e))
            } finally {
                _busy.value = false
                // Whatever happened on the box, show its real state.
                cache.clear()
                show(_currentFolder.value, useCache = false)
            }
        }
    }

    private fun operationMessage(e: Exception): String =
            when (e) {
                is FileOperationException, is FreeboxPermissionException -> e.message ?: "Operation refused"
                is FreeboxTaskTimeoutException -> "The Freebox is still working on it — refresh in a moment"
                is FreeboxAuthException -> "Not connected to the Freebox — pair it in Settings"
                is FreeboxUnreachableException -> "Can't reach the Freebox"
                is FileNotFoundException -> "An item no longer exists — the list was refreshed"
                else -> "Freebox error: ${e.message ?: e.javaClass.simpleName}"
            }

    // ---- Music folder picker ----

    /** Picker: stores [folder] as the music folder and switches to Stream mode. */
    fun choose(folder: FreeboxFolder) {
        if (mode != FreeboxBrowseMode.PICK || _chosen.value) return
        viewModelScope.launch {
            libraryMode.chooseMusicFolder(folder)
            _chosen.value = true
        }
    }

    /** Picker: "Choose this folder" on the current screen (not offered on the disks). */
    fun chooseCurrent() {
        _currentFolder.value?.let { choose(it) }
    }

    /**
     * Looks for a "Music" / "Musique" folder at the root of each disk. Disk listings land
     * in the cache, so opening a disk afterwards is instant. Failures are silent: the
     * picker works without suggestions.
     */
    private fun loadSuggestions() {
        suggestionsJob?.cancel()
        suggestionsJob =
                viewModelScope.launch {
                    val diskListings: List<Pair<RemoteEntry, List<RemoteEntry>>> =
                            try {
                                withContext(Dispatchers.IO) {
                                    val disks = client.list(null).filter { it.isDirectory }
                                    coroutineScope {
                                        disks.map { disk ->
                                                    async {
                                                        runCatching { client.list(disk.url) }
                                                                .getOrNull()
                                                                ?.let { disk to it }
                                                    }
                                                }
                                                .awaitAll()
                                                .filterNotNull()
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                emptyList()
                            }
                    // Back on the main thread: the cache is not thread-safe.
                    val found = ArrayList<FreeboxFolder>()
                    for ((disk, listing) in diskListings) {
                        val content = withContext(Dispatchers.Default) { toContent(listing) }
                        cache.putIfAbsent(disk.url, content)
                        content.folders
                                .filter { it.name.lowercase() in SUGGESTED_NAMES }
                                .mapTo(found) { it.toFolder() }
                    }
                    _suggestions.value = found
                }
    }

    // ---- Helpers ----

    private fun currentTracks(): List<RemoteEntry> =
            (_entries.value as? FreeboxUiState.Content)?.tracks.orEmpty()

    private fun toContent(listing: List<RemoteEntry>): FreeboxUiState.Content {
        val byName = compareBy<RemoteEntry, String>(NaturalOrder) { it.name }
        val folders = listing.filter { it.isDirectory }.sortedWith(byName)
        val tracks =
                listing
                        .filter {
                            !it.isDirectory &&
                                    it.name.substringAfterLast('.', "").lowercase() in
                                            AUDIO_EXTENSIONS
                        }
                        .sortedWith(byName)
        return FreeboxUiState.Content(folders, tracks)
    }

    private fun errorState(e: Exception): FreeboxUiState.Error =
            when (e) {
                is FreeboxAuthException ->
                        FreeboxUiState.Error(
                                "Not connected to the Freebox — pair it in Settings",
                                retryable = false,
                                needsPairing = true,
                        )
                is FreeboxUnreachableException ->
                        FreeboxUiState.Error("Can't reach the Freebox", retryable = true)
                is FileNotFoundException ->
                        FreeboxUiState.Error("Folder not found", retryable = true)
                else ->
                        FreeboxUiState.Error(
                                e.message ?: e.javaClass.simpleName,
                                retryable = true,
                        )
            }

    /** True when [folder] is [root] or below it (anything goes when root = the disks). */
    private fun isUnderRoot(folder: FreeboxFolder?): Boolean {
        val top = root ?: return true
        if (folder == null) return false
        return folder.displayPath == top.displayPath ||
                folder.displayPath.startsWith(top.displayPath + "/")
    }

    private fun isMusicFolderRoot(folder: FreeboxFolder?): Boolean =
            mode == FreeboxBrowseMode.MUSIC_FOLDER && folder != null && folder.url == root?.url

    /** Root crumb first: [ROOT] (the disks) in the picker, the music folder otherwise. */
    private fun breadcrumbsFor(folder: FreeboxFolder?): List<FreeboxFolder> {
        val top = root
        if (folder == null) return listOf(top ?: ROOT)
        val crumbs = ArrayList<FreeboxFolder>()
        var path: String
        val parts: List<String>
        if (top == null) {
            crumbs += ROOT
            path = ""
            parts = folder.displayPath.split('/').filter { it.isNotEmpty() }
        } else {
            crumbs += top
            path = top.displayPath
            parts =
                    folder.displayPath
                            .removePrefix(top.displayPath)
                            .split('/')
                            .filter { it.isNotEmpty() }
        }
        for (part in parts) {
            path = if (path.isEmpty()) part else "$path/$part"
            crumbs += FreeboxFolder(url = FreeboxPaths.toUrl(path), displayPath = path, name = part)
        }
        return crumbs
    }

    /**
     * Cheap placeholder metadata from the folder layout (…/Artist/Album/track.ext):
     * ExoPlayer replaces it with the real tags once it has read the file.
     */
    private fun RemoteEntry.toMediaItem(): MediaItem {
        val folder = _currentFolder.value
        val folderName = folder?.name
        // The folder above the current one, unless it is a disk or the disks root.
        val parentName =
                folder
                        ?.let { FreeboxPaths.parentUrl(it.url) }
                        ?.takeIf { FreeboxPaths.toDisplayPath(it).contains('/') }
                        ?.let { FreeboxPaths.name(it) }
        val extras = Bundle().apply { putLong("sizeBytes", size) }
        val metadata =
                MediaMetadata.Builder()
                        .setTitle(name.substringBeforeLast('.', name))
                        .setArtist(parentName ?: folderName)
                        .setAlbumTitle(folderName)
                        .setExtras(extras)
                        .build()
        return MediaItem.Builder()
                .setMediaId(url)
                .setUri(url)
                .setMediaMetadata(metadata)
                .build()
    }

    private fun RemoteEntry.toFolder(): FreeboxFolder =
            FreeboxFolder(url = url, displayPath = FreeboxPaths.toDisplayPath(url), name = name)

    companion object {
        val ROOT = FreeboxFolder(url = FreeboxPaths.SCHEME, displayPath = "", name = "Freebox")

        private const val CACHE_SIZE = 50

        /** Navigation argument: true opens the browser as the music folder picker. */
        const val ARG_PICK = "pick"

        /** Folder names offered as the music folder when found at the root of a disk. */
        private val SUGGESTED_NAMES = setOf("music", "musique")

        val AUDIO_EXTENSIONS = FreeboxAudio.AUDIO_EXTENSIONS
    }
}

/** Case-insensitive "natural" order: "Disc 2" before "Disc 10". */
private object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val si = i
                val sj = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val na = a.substring(si, i).trimStart('0')
                val nb = b.substring(sj, j).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val cmp = na.compareTo(nb)
                if (cmp != 0) return cmp
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        val rest = (a.length - i) - (b.length - j)
        return if (rest != 0) rest else a.compareTo(b, ignoreCase = true)
    }
}
