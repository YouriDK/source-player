package com.source.player.ui.viewmodel

import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.source.player.data.db.dao.SongDao
import com.source.player.data.db.entity.SongEntity
import com.source.player.data.tag.AudioTagWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

@HiltViewModel
class TagEditorViewModel
@Inject
constructor(
        private val songDao: SongDao,
        private val tagWriter: AudioTagWriter,
        @ApplicationContext private val context: Context,
        savedStateHandle: SavedStateHandle,
) : ViewModel() {

        private val songId: Long = checkNotNull(savedStateHandle["songId"])

        private val _song = MutableStateFlow<SongEntity?>(null)
        val song: StateFlow<SongEntity?> = _song.asStateFlow()

        // Editable fields
        val title = MutableStateFlow("")
        val artist = MutableStateFlow("")
        val album = MutableStateFlow("")
        val year = MutableStateFlow("")
        val trackNumber = MutableStateFlow("")
        val genre = MutableStateFlow("")

        // Per-field "Apply to Folder" toggles
        val applyArtistToFolder = MutableStateFlow(false)
        val applyAlbumToFolder = MutableStateFlow(false)
        val applyYearToFolder = MutableStateFlow(false)
        val applyGenreToFolder = MutableStateFlow(false)

        /** Songs in the same folder (excluding the current one), loaded once. */
        private val _folderSongs = MutableStateFlow<List<SongEntity>>(emptyList())
        val folderSongs: StateFlow<List<SongEntity>> = _folderSongs.asStateFlow()

        private val _saved = MutableStateFlow(false)
        val saved: StateFlow<Boolean> = _saved.asStateFlow()

        private val _toastMessage = MutableStateFlow<String?>(null)
        val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

        /**
         * One-shot consent requests for the UI to launch (API 30+ requires the user to approve
         * modifying media files via a system dialog). The screen collects this, launches the
         * IntentSender, and calls [onConsentResult] with the outcome.
         */
        private val _consentRequest = Channel<IntentSender>(Channel.BUFFERED)
        val consentRequest = _consentRequest.receiveAsFlow()

        /** Work deferred until the user answers the consent dialog. */
        private var pendingWrite: (suspend () -> Unit)? = null

        fun clearToast() {
                _toastMessage.value = null
        }

        init {
                viewModelScope.launch {
                        val s = songDao.getById(songId) ?: return@launch
                        _song.value = s
                        title.value = s.title
                        artist.value = s.artist
                        album.value = s.album
                        year.value = if (s.year > 0) s.year.toString() else ""
                        trackNumber.value = if (s.trackNumber > 0) s.trackNumber.toString() else ""
                        genre.value = s.genre

                        // Load folder siblings
                        _folderSongs.value =
                                songDao.getByFolderPath(s.folderPath).filter { it.id != songId }
                }
        }

        fun save() =
                viewModelScope.launch {
                        val original = _song.value ?: return@launch

                        val updatedTitle = title.value.trim().take(500).ifEmpty { original.title }
                        val updatedArtist = artist.value.trim().take(300).ifEmpty { original.artist }
                        val updatedAlbum = album.value.trim().take(300).ifEmpty { original.album }
                        val updatedYear = year.value.trim().toIntOrNull()?.coerceIn(0, 9999) ?: original.year
                        val updatedTrack =
                                trackNumber.value.trim().toIntOrNull()?.coerceIn(0, 9999) ?: original.trackNumber
                        val updatedGenre = genre.value.trim().take(200)

                        // ── Build the per-file write plan (only fields that actually changed) ──
                        val plan = mutableMapOf<Long, AudioTagWriter.TagEdits>()

                        val currentEdits =
                                AudioTagWriter.TagEdits(
                                        title = updatedTitle.takeIf { it != original.title },
                                        artist = updatedArtist.takeIf { it != original.artist },
                                        album = updatedAlbum.takeIf { it != original.album },
                                        year = updatedYear.takeIf { it != original.year },
                                        trackNumber = updatedTrack.takeIf { it != original.trackNumber },
                                        genre = updatedGenre.takeIf { it != original.genre },
                                )
                        if (!currentEdits.isEmpty) plan[original.id] = currentEdits

                        for (s in _folderSongs.value) {
                                var e = AudioTagWriter.TagEdits()
                                if (applyArtistToFolder.value && updatedArtist != s.artist)
                                        e = e.copy(artist = updatedArtist)
                                if (applyAlbumToFolder.value && updatedAlbum != s.album)
                                        e = e.copy(album = updatedAlbum)
                                if (applyYearToFolder.value && updatedYear != s.year)
                                        e = e.copy(year = updatedYear)
                                if (applyGenreToFolder.value && updatedGenre != s.genre)
                                        e = e.copy(genre = updatedGenre)
                                if (!e.isEmpty) plan[s.id] = e
                        }

                        // Nothing to persist — treat as a no-op success.
                        if (plan.isEmpty()) {
                                _saved.value = true
                                return@launch
                        }

                        // The actual persistence, run now (API ≤29) or after consent (API 30+).
                        val songsById =
                                (_folderSongs.value + original).associateBy { it.id }
                        val doWrite: suspend () -> Unit = {
                                applyToFiles(plan, songsById)
                                applyToDb(
                                        original,
                                        updatedTitle,
                                        updatedArtist,
                                        updatedAlbum,
                                        updatedYear,
                                        updatedTrack,
                                        updatedGenre,
                                )
                                _saved.value = true
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                // API 30+: ask the user to approve modifying these files first.
                                pendingWrite = doWrite
                                val uris = plan.keys.map { tagWriter.contentUriFor(it) }
                                // createWriteRequest does binder IPC — keep it off the main thread.
                                val request =
                                        withContext(Dispatchers.IO) {
                                                MediaStore.createWriteRequest(
                                                        context.contentResolver,
                                                        uris,
                                                )
                                        }
                                _consentRequest.send(request.intentSender)
                        } else {
                                doWrite()
                        }
                }

        /** Called by the screen after the system consent dialog resolves (API 30+). */
        fun onConsentResult(granted: Boolean) =
                viewModelScope.launch {
                        val work = pendingWrite ?: return@launch
                        pendingWrite = null
                        if (granted) {
                                work()
                        } else {
                                _toastMessage.value = "Permission denied — tags not saved"
                        }
                }

        /** Writes the tag edits into each file; surfaces a toast if any file fails. */
        private suspend fun applyToFiles(
                plan: Map<Long, AudioTagWriter.TagEdits>,
                songsById: Map<Long, SongEntity>,
        ) {
                // At most 2 files written concurrently — parallel enough to hide I/O latency
                // without hammering the disk.
                val semaphore = Semaphore(2)
                val failures =
                        withContext(Dispatchers.IO) {
                                plan.map { (id, edits) ->
                                                async {
                                                        val path =
                                                                songsById[id]?.path
                                                                        ?: return@async false
                                                        semaphore.withPermit {
                                                                tagWriter
                                                                        .writeTags(id, path, edits)
                                                                        .isFailure
                                                        }
                                                }
                                        }
                                        .awaitAll()
                                        .count { it }
                        }
                if (failures > 0) {
                        _toastMessage.value = "Couldn't update $failures file(s)"
                }
        }

        /** Mirrors the edits into the Room DB so the UI updates immediately, without a rescan. */
        private suspend fun applyToDb(
                original: SongEntity,
                updatedTitle: String,
                updatedArtist: String,
                updatedAlbum: String,
                updatedYear: Int,
                updatedTrack: Int,
                updatedGenre: String,
        ) {
                songDao.update(
                        original.copy(
                                title = updatedTitle,
                                artist = updatedArtist,
                                album = updatedAlbum,
                                year = updatedYear,
                                trackNumber = updatedTrack,
                                genre = updatedGenre,
                        )
                )

                val folderPath = original.folderPath
                var propagatedCount = 0

                if (applyArtistToFolder.value && updatedArtist != original.artist) {
                        songDao.updateArtistForFolder(updatedArtist, folderPath, songId)
                        propagatedCount++
                }
                if (applyAlbumToFolder.value && updatedAlbum != original.album) {
                        songDao.updateAlbumForFolder(updatedAlbum, folderPath, songId)
                        propagatedCount++
                }
                if (applyYearToFolder.value && updatedYear != original.year) {
                        songDao.updateYearForFolder(updatedYear, folderPath, songId)
                        propagatedCount++
                }
                if (applyGenreToFolder.value && updatedGenre != original.genre) {
                        songDao.updateGenreForFolder(updatedGenre, folderPath, songId)
                        propagatedCount++
                }

                if (propagatedCount > 0 && _folderSongs.value.isNotEmpty()) {
                        _toastMessage.value = "Applied to ${_folderSongs.value.size} songs in folder"
                }
        }
}
