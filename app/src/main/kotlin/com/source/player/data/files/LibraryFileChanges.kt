package com.source.player.data.files

import com.source.player.data.db.entity.SOURCE_FREEBOX
import com.source.player.data.db.entity.SongEntity
import com.source.player.data.freebox.FreeboxPaths
import com.source.player.data.freebox.library.FreeboxIds

/** New identity of a song row after its file moved. */
data class SongRelocation(val newId: Long, val newPath: String)

/**
 * What a move / delete from the Folders tab changed, in the terms the queue and the
 * index need. Freebox entries are matched by url (a moved or trashed folder carries
 * everything below it); indexed rows also by id.
 */
data class LibraryFileChanges(
    val removedIds: Set<Long> = emptySet(),
    /** Trashed Freebox urls (files or folders). */
    val removedUrls: Set<String> = emptySet(),
    /** Indexed rows that moved: old id → new id + path. */
    val moved: Map<Long, SongRelocation> = emptyMap(),
    /** Moved Freebox urls (files or folders): old url → new url. */
    val movedUrls: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = removedIds.isEmpty() && removedUrls.isEmpty() && moved.isEmpty() && movedUrls.isEmpty()
}

/** What happens to one queue item. */
sealed interface QueueDecision {
    data object Keep : QueueDecision
    data object Remove : QueueDecision
    data class Relocate(val mediaId: String, val uri: String) : QueueDecision
}

/** Pure rules keeping the play queue and the index in line with moved / trashed files. */
object LibraryFileEdits {

    /**
     * Fate of a queue item. [mediaId] is either a Room song id (library items) or the
     * fbx:// url itself (live Freebox browser items); [uri] may be null on the controller
     * side of the session.
     */
    fun decide(mediaId: String, uri: String?, changes: LibraryFileChanges): QueueDecision {
        val id = mediaId.toLongOrNull()
        if (id != null) {
            if (id in changes.removedIds) return QueueDecision.Remove
            changes.moved[id]?.let { return QueueDecision.Relocate(it.newId.toString(), it.newPath) }
        }
        val url = mediaId.takeIf { FreeboxPaths.isFreeboxUrl(it) }
            ?: uri?.takeIf { FreeboxPaths.isFreeboxUrl(it) }
            ?: return QueueDecision.Keep
        if (changes.removedUrls.any { FreeboxPaths.isSameOrUnder(url, it) }) return QueueDecision.Remove
        for ((from, to) in changes.movedUrls) {
            if (FreeboxPaths.isSameOrUnder(url, from)) {
                val newUrl = relocateFreeboxUrl(url, from, to)
                val newId = if (id != null) FreeboxIds.song(newUrl).toString() else newUrl
                return QueueDecision.Relocate(newId, newUrl)
            }
        }
        return QueueDecision.Keep
    }

    /** [url] (== [from] or below it) rewritten under [to]. */
    fun relocateFreeboxUrl(url: String, from: String, to: String): String {
        val path = FreeboxPaths.toDisplayPath(url)
        val fromPath = FreeboxPaths.toDisplayPath(from)
        val toPath = FreeboxPaths.toDisplayPath(to)
        return FreeboxPaths.toUrl(toPath + path.removePrefix(fromPath))
    }

    /** Indexed Freebox songs that live at [url] (a file) or anywhere below it (a folder). */
    fun freeboxSongsUnder(songs: List<SongEntity>, url: String): List<SongEntity> =
        songs.filter { it.source == SOURCE_FREEBOX && FreeboxPaths.isSameOrUnder(it.path, url) }

    /**
     * [song] after its file (or a folder above it) moved from [from] to [to]: new path,
     * folder and id (Freebox ids hash the url, so the next scan finds the same row).
     */
    fun relocateFreeboxSong(song: SongEntity, from: String, to: String): SongEntity {
        val newPath = relocateFreeboxUrl(song.path, from, to)
        return song.copy(
            id = FreeboxIds.song(newPath),
            path = newPath,
            folderPath = FreeboxPaths.parentUrl(newPath),
        )
    }
}
