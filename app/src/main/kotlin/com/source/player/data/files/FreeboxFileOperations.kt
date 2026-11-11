package com.source.player.data.files

import android.util.Log
import com.source.player.data.db.dao.PlaylistDao
import com.source.player.data.db.dao.SongDao
import com.source.player.data.db.entity.SOURCE_FREEBOX
import com.source.player.data.freebox.FreeboxClient
import com.source.player.data.freebox.FreeboxFolder
import com.source.player.data.freebox.FreeboxPaths
import com.source.player.data.freebox.RemoteEntry
import com.source.player.data.freebox.library.FreeboxLibraryScanner
import com.source.player.data.freebox.library.FreeboxLibrarySync
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** A move / delete refused before anything was touched; [message] is user-facing. */
class FileOperationException(message: String) : Exception(message)

/**
 * Move and delete for the Folders tab in Stream mode (Freebox music folder).
 *
 * - Delete never removes anything: entries are moved into [TRASH_DIR_NAME] at the root
 *   of the music folder (created on first use), with a " (n)" suffix when the trash
 *   already holds that name. The trash is a dot folder, so neither the Folders browser
 *   ([FreeboxClient.list]) nor the library scan ever shows or indexes it.
 * - Move goes through fs/mv in "skip" mode after a conflict check, so nothing is ever
 *   overwritten.
 *
 * Both work over the remote access (4G) as well as on the LAN. The index is updated in
 * place (rows dropped or relocated, playlists remapped) — no rescan.
 */
@Singleton
class FreeboxFileOperations
@Inject
constructor(
        private val client: FreeboxClient,
        private val scanner: FreeboxLibraryScanner,
        private val sync: FreeboxLibrarySync,
        private val songDao: SongDao,
        private val playlistDao: PlaylistDao,
) {

    /** Moves [entries] (all inside [musicRoot]) to the music folder's trash. */
    suspend fun trash(entries: List<RemoteEntry>, musicRoot: FreeboxFolder): LibraryFileChanges =
            withContext(Dispatchers.IO) {
                if (entries.isEmpty()) return@withContext LibraryFileChanges()
                checkNotScanning()
                val trashUrl = trashUrl(musicRoot.url)
                for (e in entries) checkInsideMusicFolder(e.url, musicRoot.url)

                ensureTrashFolder(musicRoot.url)
                val inTrash = client.listAll(trashUrl).mapTo(HashSet()) { it.name }
                // A clashing entry is renamed in place first: its new name must be free
                // next to it too.
                val siblings = HashSet<String>()
                for (parent in entries.map { FreeboxPaths.parentUrl(it.url) }.toSet()) {
                    client.listAll(parent).mapTo(siblings) { it.name }
                }
                val targets = FileNames.trashNames(entries.map { it.name to it.isDirectory }, inTrash, siblings)

                // fs/mv can't rename on the way: entries whose name is taken in the trash
                // are renamed in place, then everything moves in one task.
                val renamed = ArrayList<Pair<String, String>>() // new url → original name
                val toMove = ArrayList<String>()
                try {
                    entries.forEachIndexed { i, entry ->
                        if (targets[i] == entry.name) {
                            toMove += entry.url
                        } else {
                            val newUrl = client.rename(entry.url, targets[i])
                            renamed += newUrl to entry.name
                            toMove += newUrl
                        }
                    }
                    client.move(toMove, trashUrl)
                } catch (e: Exception) {
                    withContext(NonCancellable) { rollbackRenames(renamed) }
                    throw e
                }

                val removedIds = withIndexUpdate {
                    val all = songDao.getAllBySource(SOURCE_FREEBOX)
                    val ids = entries.flatMap { LibraryFileEdits.freeboxSongsUnder(all, it.url) }.map { it.id }.distinct()
                    ids.chunked(CHUNK).forEach { playlistDao.deleteEntriesForSongs(it) }
                    scanner.applyFileChanges(ids, emptyList())
                    ids
                }
                LibraryFileChanges(
                        removedIds = removedIds.orEmpty().toSet(),
                        removedUrls = entries.mapTo(HashSet()) { it.url },
                )
            }

    /** Moves [entries] into [destUrl]; both inside [musicRoot], never into the trash. */
    suspend fun move(entries: List<RemoteEntry>, destUrl: String, musicRoot: FreeboxFolder): LibraryFileChanges =
            withContext(Dispatchers.IO) {
                if (entries.isEmpty()) return@withContext LibraryFileChanges()
                checkNotScanning()
                checkMoveTarget(entries, destUrl, musicRoot.url)?.let { throw FileOperationException(it) }

                val destNames = client.listAll(destUrl).map { it.name }
                val clash = entries.firstOrNull { e -> destNames.any { FileNames.sameName(it, e.name) } }
                if (clash != null) {
                    throw FileOperationException("“${clash.name}” already exists in the destination folder")
                }

                client.move(entries.map { it.url }, destUrl)

                val movedUrls = entries.associate { it.url to FreeboxPaths.child(destUrl, it.name) }
                val moved = withIndexUpdate {
                    val all = songDao.getAllBySource(SOURCE_FREEBOX)
                    val relocations = LinkedHashMap<Long, com.source.player.data.db.entity.SongEntity>()
                    for ((from, to) in movedUrls) {
                        for (song in LibraryFileEdits.freeboxSongsUnder(all, from)) {
                            relocations[song.id] = LibraryFileEdits.relocateFreeboxSong(song, from, to)
                        }
                    }
                    playlistDao.remapSongs(relocations.mapValues { it.value.id })
                    scanner.applyFileChanges(relocations.keys.toList(), relocations.values.toList())
                    relocations.mapValues { SongRelocation(it.value.id, it.value.path) }
                }
                LibraryFileChanges(moved = moved.orEmpty(), movedUrls = movedUrls)
            }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun checkNotScanning() {
        if (sync.isScanning.value) {
            throw FileOperationException("The Freebox library is being indexed — try again when it's done")
        }
    }

    private suspend fun ensureTrashFolder(rootUrl: String) {
        fun List<RemoteEntry>.trashEntry() = firstOrNull { FileNames.sameName(it.name, TRASH_DIR_NAME) }
        val existing = client.listAll(rootUrl).trashEntry()
        if (existing != null) {
            if (!existing.isDirectory) {
                throw FileOperationException("“$TRASH_DIR_NAME” in your music folder is a file, not a folder")
            }
            return
        }
        try {
            client.mkdir(rootUrl, TRASH_DIR_NAME)
        } catch (e: IOException) {
            // Created meanwhile (another device)? Fine; otherwise report the real failure.
            if (client.listAll(rootUrl).trashEntry()?.isDirectory != true) throw e
        }
    }

    private suspend fun rollbackRenames(renamed: List<Pair<String, String>>) {
        for ((url, originalName) in renamed) {
            try {
                client.rename(url, originalName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not restore the name of $url: ${e.message}")
            }
        }
    }

    /** Index updates are best effort: the files already moved, a rescan heals a failure. */
    private suspend fun <T> withIndexUpdate(block: suspend () -> T): T? =
            try {
                withContext(NonCancellable) { block() }
            } catch (e: Exception) {
                Log.w(TAG, "Index update after a file operation failed: ${e.javaClass.simpleName}: ${e.message}")
                null
            }

    companion object {
        private const val TAG = "FreeboxFileOps"
        private const val CHUNK = 500

        /** Trash folder created at the root of the music folder. */
        const val TRASH_DIR_NAME = ".corbeille"

        fun trashUrl(musicRootUrl: String): String = FreeboxPaths.child(musicRootUrl, TRASH_DIR_NAME)

        private fun checkInsideMusicFolder(url: String, rootUrl: String) {
            if (!FreeboxPaths.isSameOrUnder(url, rootUrl) || FreeboxPaths.isSameOrUnder(rootUrl, url)) {
                throw FileOperationException("Only items inside your music folder can be changed")
            }
            if (FreeboxPaths.isSameOrUnder(url, trashUrl(rootUrl))) {
                throw FileOperationException("Items already in the trash can't be changed from here")
            }
        }

        /**
         * Why [entries] can't move into [destUrl], or null when they can: destination
         * outside the music folder or in the trash, entry already there, folder into itself.
         */
        fun checkMoveTarget(entries: List<RemoteEntry>, destUrl: String, rootUrl: String): String? {
            if (!FreeboxPaths.isSameOrUnder(destUrl, rootUrl)) return "Choose a folder inside your music folder"
            if (FreeboxPaths.isSameOrUnder(destUrl, trashUrl(rootUrl))) return "Use Delete to move items to the trash"
            for (e in entries) {
                if (!FreeboxPaths.isSameOrUnder(e.url, rootUrl) || FreeboxPaths.isSameOrUnder(rootUrl, e.url)) {
                    return "Only items inside your music folder can be moved"
                }
                if (FreeboxPaths.toDisplayPath(FreeboxPaths.parentUrl(e.url)) == FreeboxPaths.toDisplayPath(destUrl)) {
                    return "“${e.name}” is already in this folder"
                }
                if (e.isDirectory && FreeboxPaths.isSameOrUnder(destUrl, e.url)) {
                    return "A folder can't be moved into itself"
                }
            }
            return null
        }
    }
}
