package com.source.player.data.files

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import com.source.player.data.db.dao.PlaylistDao
import com.source.player.data.db.dao.SongDao
import com.source.player.data.db.entity.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Move and delete for the Folders tab in Local mode, through MediaStore only (no
 * MANAGE_EXTERNAL_STORAGE):
 *
 * - Delete = [MediaStore.createTrashRequest]: the files go to the system trash (restorable
 *   from the Files app for ~30 days) after one system confirmation for the whole batch.
 * - Move = [MediaStore.createWriteRequest] (one system confirmation for the batch), then
 *   an update of each row's RELATIVE_PATH — MediaStore moves the file itself.
 *
 * Both need API 30+ ([isSupported]); below that the Folders tab doesn't offer them (see
 * FoldersViewModel). Requests are built off the main thread (binder IPC); the screen
 * launches the returned [IntentSender]s, like the tag editor does.
 *
 * The index is updated in place right after (rows dropped / repointed, playlists cleaned);
 * the MediaStore watcher's own debounced rescan then confirms it.
 */
@Singleton
class LocalFileOperations
@Inject
constructor(
        @ApplicationContext private val context: Context,
        private val songDao: SongDao,
        private val playlistDao: PlaylistDao,
) {
    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun trashRequest(songs: List<SongEntity>): IntentSender =
            withContext(Dispatchers.IO) {
                MediaStore.createTrashRequest(context.contentResolver, songs.map { uriOf(it) }, true).intentSender
            }

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun writeRequest(songs: List<SongEntity>): IntentSender =
            withContext(Dispatchers.IO) {
                MediaStore.createWriteRequest(context.contentResolver, songs.map { uriOf(it) }).intentSender
            }

    /** The user confirmed the trash request: drop the rows and their playlist entries. */
    suspend fun onTrashed(songs: List<SongEntity>): LibraryFileChanges =
            withContext(Dispatchers.IO + NonCancellable) {
                val ids = songs.map { it.id }
                ids.chunked(CHUNK).forEach {
                    songDao.deleteByIds(it)
                    playlistDao.deleteEntriesForSongs(it)
                }
                LibraryFileChanges(removedIds = ids.toSet())
            }

    /**
     * Why [songs] can't move into [destFolder] (an absolute folder path), or null when
     * they can. Checked before asking the user for write consent.
     */
    suspend fun checkMove(songs: List<SongEntity>, destFolder: String): String? {
        LocalMediaPaths.checkMoveTarget(songs.map { it.path }, destFolder)?.let { return it }
        val existing = withContext(Dispatchers.IO) { songDao.getByFolderPath(destFolder) }
                .map { it.path.substringAfterLast('/') }
        val clash = songs.firstOrNull { s -> existing.any { FileNames.sameName(it, s.path.substringAfterLast('/')) } }
        return clash?.let { "“${it.path.substringAfterLast('/')}” already exists in the destination folder" }
    }

    /** Result of [move]: what changed, and how many files MediaStore refused to move. */
    data class MoveOutcome(val changes: LibraryFileChanges, val failed: Int)

    /** Moves [songs] into [destFolder]; call once the write request was granted. */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun move(songs: List<SongEntity>, destFolder: String): MoveOutcome =
            withContext(Dispatchers.IO + NonCancellable) {
                val relative = LocalMediaPaths.relativePath(destFolder)
                        ?: return@withContext MoveOutcome(LibraryFileChanges(), songs.size)
                val resolver = context.contentResolver
                val updated = ArrayList<SongEntity>()
                var failed = 0
                for (song in songs) {
                    val uri = uriOf(song)
                    try {
                        val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, relative) }
                        if (resolver.update(uri, values, null, null) <= 0) {
                            failed++
                            continue
                        }
                        // MediaStore may rename on a clash: read back where the file landed.
                        val newPath = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
                                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                                ?: LocalMediaPaths.movedPath(song.path, destFolder)
                        updated += song.copy(path = newPath, folderPath = newPath.substringBeforeLast('/'))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Move of ${song.path} failed: ${e.javaClass.simpleName}: ${e.message}")
                        failed++
                    }
                }
                if (updated.isNotEmpty()) songDao.upsertAll(updated)
                MoveOutcome(
                        LibraryFileChanges(moved = updated.associate { it.id to SongRelocation(it.id, it.path) }),
                        failed,
                )
            }

    private fun uriOf(song: SongEntity) =
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)

    private companion object {
        const val TAG = "LocalFileOps"
        const val CHUNK = 500
    }
}
