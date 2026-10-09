package com.source.player.data.mode

import com.source.player.data.freebox.FreeboxClient
import com.source.player.data.freebox.FreeboxFolder
import com.source.player.data.freebox.FreeboxReachability
import com.source.player.data.freebox.FreeboxReachability.Reach
import com.source.player.data.preferences.AppPreferences
import com.source.player.data.preferences.LibraryMode
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Problem (or activity) worth a banner on top of the library while in Stream mode. */
sealed interface StreamBanner {
    /** Box not reachable on the LAN nor remotely. */
    data object Unreachable : StreamBanner

    /** No token, or the box refuses it (revoked in Freebox OS): re-pairing fixes it. */
    data object PairingLost : StreamBanner

    /** The music folder listing answers "not found" (disk renamed, folder moved…). Nothing is purged. */
    data object MusicFolderMissing : StreamBanner

    /** Library indexing in progress — [message] e.g. "Indexing… 420 tracks". */
    data class Indexing(val message: String) : StreamBanner
}

/** Everything the UI needs to render the active library mode. */
data class LibraryModeStatus(
        val mode: LibraryMode,
        val paired: Boolean,
        /** The Freebox music folder (root of everything shown in Stream); null = not chosen yet. */
        val musicFolder: FreeboxFolder?,
        /** Always null in Local mode. */
        val banner: StreamBanner?,
) {
    val isStream: Boolean
        get() = mode == LibraryMode.STREAM

    /** Stream mode without a music folder: screens show "Choose your music folder". */
    val needsMusicFolder: Boolean
        get() = isStream && musicFolder == null
}

/**
 * Single owner of the Local / Stream library mode and of the Freebox music folder
 * (stored through [FreeboxClient.setStartFolder], same prefs as the former start folder).
 *
 * ViewModels filter their queries with `source.flatMapLatest { … }` so a mode switch
 * swaps every list at once; detail screens load by id and are never filtered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LibraryModeRepository
@Inject
constructor(
        private val prefs: AppPreferences,
        private val client: FreeboxClient,
        private val reachability: FreeboxReachability,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val mode: Flow<LibraryMode> = prefs.libraryMode

    /** `source` column value of the active mode — the filter of every library query. */
    val source: Flow<String> = mode.map { it.source }.distinctUntilChanged()

    val musicFolder: Flow<FreeboxFolder?> = client.startFolder

    /**
     * Indexing progress of the Freebox library, null when idle.
     * TODO(lot 3): back it with `FreeboxLibraryScanner.progressMessage` (data/freebox/library).
     */
    val indexingProgress: StateFlow<String?> = MutableStateFlow<String?>(null).asStateFlow()

    /** fbx url of the music folder last seen missing; null = present or not checked. */
    private val missingFolderUrl = MutableStateFlow<String?>(null)

    suspend fun currentSource(): String = mode.first().source

    suspend fun setMode(mode: LibraryMode) = prefs.setLibraryMode(mode)

    /**
     * Picker result: stores the music folder and switches to Stream. The current queue is
     * left alone. TODO(lot 3): FreeboxLibrarySync observes [FreeboxClient.startFolder] and
     * purges + rescans on change — nothing to call from here.
     */
    suspend fun chooseMusicFolder(folder: FreeboxFolder) {
        client.setStartFolder(folder)
        missingFolderUrl.value = null
        prefs.setLibraryMode(LibraryMode.STREAM)
    }

    /** The live browser listed the music folder root and got "not found". */
    fun reportMusicFolderMissing(url: String) {
        missingFolderUrl.value = url
    }

    /** The music folder root listed fine: clears a previous "missing" report. */
    fun reportMusicFolderFound(url: String) {
        missingFolderUrl.compareAndSet(url, null)
    }

    /**
     * Lists the music folder once per (mode, folder, reachability) change to detect a
     * folder that disappeared. Only in Stream, only when the box answers.
     */
    private val folderCheck: Flow<Unit> =
            combine(mode, musicFolder, reachability.state) { m, folder, reach ->
                        Triple(m, folder, reach)
                    }
                    .distinctUntilChanged()
                    .mapLatest { (m, folder, reach) ->
                        if (m != LibraryMode.STREAM || folder == null) return@mapLatest
                        if (reach != Reach.LAN && reach != Reach.REMOTE) return@mapLatest
                        try {
                            client.list(folder.url)
                            reportMusicFolderFound(folder.url)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: FileNotFoundException) {
                            reportMusicFolderMissing(folder.url)
                        } catch (e: Exception) {
                            // Network / auth trouble is reported by FreeboxReachability.
                        }
                    }

    private val statusFlow: Flow<LibraryModeStatus> =
            combine(
                    mode,
                    client.isPaired,
                    musicFolder,
                    reachability.state,
                    combine(missingFolderUrl, indexingProgress) { missing, indexing ->
                        missing to indexing
                    },
            ) { m, paired, folder, reach, (missing, indexing) ->
                val banner =
                        if (m != LibraryMode.STREAM) null
                        else
                                when {
                                    !paired || reach == Reach.NOT_PAIRED || reach == Reach.AUTH_REVOKED ->
                                            StreamBanner.PairingLost
                                    reach == Reach.UNREACHABLE -> StreamBanner.Unreachable
                                    folder != null && missing == folder.url ->
                                            StreamBanner.MusicFolderMissing
                                    indexing != null -> StreamBanner.Indexing(indexing)
                                    else -> null
                                }
                LibraryModeStatus(mode = m, paired = paired, musicFolder = folder, banner = banner)
            }

    /** null until the preferences have been read (avoids flashing the wrong library). */
    val status: StateFlow<LibraryModeStatus?> =
            channelFlow {
                        launch { folderCheck.collect {} }
                        statusFlow.collect { send(it) }
                    }
                    .distinctUntilChanged()
                    .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)
}
