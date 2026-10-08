package com.source.player.ui.viewmodel

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.source.player.data.freebox.FreeboxAuthException
import com.source.player.data.freebox.FreeboxClient
import com.source.player.data.freebox.FreeboxFolder
import com.source.player.data.freebox.FreeboxPaths
import com.source.player.data.freebox.FreeboxUnreachableException
import com.source.player.data.freebox.RemoteEntry
import com.source.player.service.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.FileNotFoundException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the Freebox browser shows for the current folder. */
sealed interface FreeboxUiState {
    data object Loading : FreeboxUiState

    /** [needsPairing]: the box refused us (never paired / revoked) — offer Settings, not Retry. */
    data class Error(
            val message: String,
            val retryable: Boolean,
            val needsPairing: Boolean = false,
    ) : FreeboxUiState

    data class Content(val folders: List<RemoteEntry>, val tracks: List<RemoteEntry>) :
            FreeboxUiState
}

/**
 * Pairing state + box name only — for entry points (Folders root row) that must not
 * trigger a live listing the way [FreeboxViewModel] does.
 */
@HiltViewModel
class FreeboxStatusViewModel @Inject constructor(client: FreeboxClient) : ViewModel() {
    val isPaired: StateFlow<Boolean> =
            client.isPaired.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val boxName: StateFlow<String?> =
            client.boxName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * Live browser over the Freebox disks: nothing is indexed, every folder is listed on
 * demand through [FreeboxClient.list] and kept in a small in-memory cache so going back
 * is instant. Only audio files are shown; playback queues the current folder's tracks.
 */
@HiltViewModel
class FreeboxViewModel
@Inject
constructor(
        private val client: FreeboxClient,
        private val controller: PlaybackController,
) : ViewModel() {

    /** null until the token store has been read — the screen shows a spinner meanwhile. */
    val isPaired: StateFlow<Boolean?> =
            client.isPaired.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val boxName: StateFlow<String?> =
            client.boxName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val startFolder: StateFlow<FreeboxFolder?> =
            client.startFolder.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _currentFolder = MutableStateFlow<FreeboxFolder?>(null)
    /** null = the disks root. */
    val currentFolder: StateFlow<FreeboxFolder?> = _currentFolder.asStateFlow()

    private val _breadcrumbs = MutableStateFlow(listOf(ROOT))
    /** Root ("Freebox") first, current folder last. */
    val breadcrumbs: StateFlow<List<FreeboxFolder>> = _breadcrumbs.asStateFlow()

    private val _entries = MutableStateFlow<FreeboxUiState>(FreeboxUiState.Loading)
    val entries: StateFlow<FreeboxUiState> = _entries.asStateFlow()

    /** url → listing; access-ordered so the least recently visited folder is evicted. */
    private val cache =
            object : LinkedHashMap<String, FreeboxUiState.Content>(16, 0.75f, true) {
                override fun removeEldestEntry(
                        eldest: MutableMap.MutableEntry<String, FreeboxUiState.Content>?
                ): Boolean = size > CACHE_SIZE
            }

    private var loadJob: Job? = null
    private var started = false

    init {
        viewModelScope.launch {
            isPaired.collect { paired ->
                if (paired != true) return@collect
                if (!started) {
                    started = true
                    val start = runCatching { client.startFolder.first() }.getOrNull()
                    show(start, useCache = true)
                } else if (_entries.value is FreeboxUiState.Error) {
                    // Paired again (e.g. from Settings) after an auth failure.
                    show(_currentFolder.value, useCache = false)
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

    /** Back one level; false when already at the disks root (let the system handle Back). */
    fun goUp(): Boolean {
        val crumbs = _breadcrumbs.value
        if (crumbs.size <= 1) return false
        navigateTo(crumbs.size - 2)
        return true
    }

    fun refresh() = show(_currentFolder.value, useCache = false)

    fun retry() = show(_currentFolder.value, useCache = false)

    private fun show(folder: FreeboxFolder?, useCache: Boolean) {
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
                                content
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                errorState(e)
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

    // ---- Start folder ----

    /** Remembers the current folder as the browser's landing folder (root clears it). */
    fun setStartFolderHere() {
        val folder = _currentFolder.value
        viewModelScope.launch { client.setStartFolder(folder) }
    }

    fun clearStartFolder() {
        viewModelScope.launch { client.setStartFolder(null) }
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

    private fun breadcrumbsFor(folder: FreeboxFolder?): List<FreeboxFolder> {
        if (folder == null) return listOf(ROOT)
        val parts = folder.displayPath.split('/').filter { it.isNotEmpty() }
        val crumbs = ArrayList<FreeboxFolder>(parts.size + 1)
        crumbs += ROOT
        var path = ""
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
        val crumbs = _breadcrumbs.value
        val folderName = crumbs.lastOrNull()?.takeIf { crumbs.size > 1 }?.name
        // The folder above the current one, unless it is the root or a disk.
        val parentName = crumbs.getOrNull(crumbs.size - 2)?.takeIf { crumbs.size > 2 }?.name
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

        val AUDIO_EXTENSIONS =
                setOf(
                        "mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav", "wma", "aiff",
                        "aif",
                )
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
