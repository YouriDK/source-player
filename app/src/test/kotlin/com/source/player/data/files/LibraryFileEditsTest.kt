package com.source.player.data.files

import com.source.player.data.db.entity.SOURCE_FREEBOX
import com.source.player.data.db.entity.SOURCE_LOCAL
import com.source.player.data.db.entity.SongEntity
import com.source.player.data.freebox.RemoteEntry
import com.source.player.data.freebox.library.FreeboxIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryFileEditsTest {

    private val root = "fbx://Disque dur/Musique"

    private fun fbxSong(url: String) = SongEntity(
        id = FreeboxIds.song(url), title = "t", artist = "a", album = "al", albumId = 1, artistId = 2,
        duration = 1000, path = url, trackNumber = 1, year = 0, genre = "", dateAdded = 0,
        albumArtUri = null, folderPath = url.substringBeforeLast('/'), size = 10,
        source = SOURCE_FREEBOX, remoteMtime = 42,
    )

    private fun entry(url: String, dir: Boolean) =
        RemoteEntry(name = url.substringAfterLast('/'), url = url, isDirectory = dir, size = 0, lastModified = 0)

    // ── Queue decisions ──────────────────────────────────────────────────

    @Test fun removedIdLeavesQueue() =
        assertEquals(QueueDecision.Remove, LibraryFileEdits.decide("12", "/x.mp3", LibraryFileChanges(removedIds = setOf(12))))

    @Test fun unrelatedItemIsKept() =
        assertEquals(QueueDecision.Keep, LibraryFileEdits.decide("13", "/x.mp3", LibraryFileChanges(removedIds = setOf(12))))

    @Test fun localMoveKeepsIdAndChangesUri() =
        assertEquals(
            QueueDecision.Relocate("12", "/storage/emulated/0/Music/B/x.mp3"),
            LibraryFileEdits.decide(
                "12", null,
                LibraryFileChanges(moved = mapOf(12L to SongRelocation(12, "/storage/emulated/0/Music/B/x.mp3"))),
            ),
        )

    @Test fun browserItemUnderTrashedFolderLeavesQueue() =
        assertEquals(
            QueueDecision.Remove,
            LibraryFileEdits.decide("$root/A/B/x.flac", null, LibraryFileChanges(removedUrls = setOf("$root/A"))),
        )

    @Test fun siblingFolderWithSamePrefixIsKept() =
        assertEquals(
            QueueDecision.Keep,
            LibraryFileEdits.decide("$root/AB/x.flac", null, LibraryFileChanges(removedUrls = setOf("$root/A"))),
        )

    @Test fun browserItemFollowsMovedFolder() =
        assertEquals(
            QueueDecision.Relocate("$root/C/A/x.flac", "$root/C/A/x.flac"),
            LibraryFileEdits.decide("$root/A/x.flac", null, LibraryFileChanges(movedUrls = mapOf("$root/A" to "$root/C/A"))),
        )

    @Test fun indexedFreeboxItemGetsTheNewId() {
        val newUrl = "$root/C/x.flac"
        val old = FreeboxIds.song("$root/x.flac")
        assertEquals(
            QueueDecision.Relocate(FreeboxIds.song(newUrl).toString(), newUrl),
            LibraryFileEdits.decide(old.toString(), "$root/x.flac", LibraryFileChanges(movedUrls = mapOf("$root/x.flac" to newUrl))),
        )
    }

    // ── Index edits ──────────────────────────────────────────────────────

    @Test fun songsUnderFolderOnly() {
        val songs = listOf(fbxSong("$root/A/1.flac"), fbxSong("$root/A/B/2.flac"), fbxSong("$root/AB/3.flac"))
        assertEquals(
            listOf("$root/A/1.flac", "$root/A/B/2.flac"),
            LibraryFileEdits.freeboxSongsUnder(songs, "$root/A").map { it.path },
        )
    }

    @Test fun songsUnderIgnoresLocalRows() {
        val local = fbxSong("$root/A/1.flac").copy(source = SOURCE_LOCAL)
        assertEquals(emptyList<SongEntity>(), LibraryFileEdits.freeboxSongsUnder(listOf(local), "$root/A"))
    }

    @Test fun relocatedSongGetsNewPathFolderAndId() {
        val moved = LibraryFileEdits.relocateFreeboxSong(fbxSong("$root/A/B/2.flac"), "$root/A", "$root/Z/A")
        assertEquals("$root/Z/A/B/2.flac", moved.path)
        assertEquals("$root/Z/A/B", moved.folderPath)
        assertEquals(FreeboxIds.song("$root/Z/A/B/2.flac"), moved.id)
        assertEquals(42, moved.remoteMtime) // kept: the next scan reuses the row
    }

    // ── Freebox move checks ──────────────────────────────────────────────

    @Test fun validFreeboxMove() =
        assertNull(FreeboxFileOperations.checkMoveTarget(listOf(entry("$root/A/x.flac", false)), "$root/B", root))

    @Test fun moveIntoSameFolderIsRefused() =
        assertNotNull(FreeboxFileOperations.checkMoveTarget(listOf(entry("$root/A/x.flac", false)), "$root/A", root))

    @Test fun moveFolderIntoItselfIsRefused() =
        assertNotNull(FreeboxFileOperations.checkMoveTarget(listOf(entry("$root/A", true)), "$root/A/B", root))

    @Test fun moveOutsideMusicFolderIsRefused() =
        assertNotNull(FreeboxFileOperations.checkMoveTarget(listOf(entry("$root/A/x.flac", false)), "fbx://Disque dur", root))

    @Test fun moveIntoTrashIsRefused() =
        assertNotNull(
            FreeboxFileOperations.checkMoveTarget(
                listOf(entry("$root/A/x.flac", false)), FreeboxFileOperations.trashUrl(root), root,
            ),
        )

    @Test fun trashSitsAtMusicRoot() = assertEquals("$root/.corbeille", FreeboxFileOperations.trashUrl(root))
}
