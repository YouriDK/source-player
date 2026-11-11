package com.source.player.data.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamesTest {

    @Test fun freeNameIsKept() =
        assertEquals("song.flac", FileNames.uniqueName("song.flac", setOf("other.flac"), isDirectory = false))

    @Test fun clashGetsSuffixBeforeExtension() =
        assertEquals("song (1).flac", FileNames.uniqueName("song.flac", setOf("song.flac"), isDirectory = false))

    @Test fun suffixSkipsTakenNumbers() =
        assertEquals(
            "song (3).flac",
            FileNames.uniqueName("song.flac", setOf("song.flac", "song (1).flac", "song (2).flac"), isDirectory = false),
        )

    @Test fun clashIsCaseInsensitive() =
        assertEquals("Song (1).MP3", FileNames.uniqueName("Song.MP3", setOf("song.mp3"), isDirectory = false))

    @Test fun folderNameKeepsItsDots() =
        assertEquals("Vol. 2 (1)", FileNames.uniqueName("Vol. 2", setOf("Vol. 2"), isDirectory = true))

    @Test fun dotFileIsNotSplit() =
        assertEquals(".hidden (1)", FileNames.uniqueName(".hidden", setOf(".hidden"), isDirectory = false))

    @Test fun nameWithoutExtension() =
        assertEquals("README (1)", FileNames.uniqueName("README", setOf("README"), isDirectory = false))

    @Test fun trashKeepsNamesNotInTrash() =
        assertEquals(
            listOf("a.mp3", "b.mp3"),
            FileNames.trashNames(
                listOf("a.mp3" to false, "b.mp3" to false),
                trashNames = setOf("c.mp3"),
                siblingNames = setOf("a.mp3", "b.mp3"),
            ),
        )

    @Test fun trashClashAvoidsTrashAndSiblings() =
        // "a (1).mp3" exists next to the file, "a.mp3" in the trash → "a (2).mp3".
        assertEquals(
            listOf("a (2).mp3"),
            FileNames.trashNames(
                listOf("a.mp3" to false),
                trashNames = setOf("a.mp3"),
                siblingNames = setOf("a.mp3", "a (1).mp3"),
            ),
        )

    @Test fun trashBatchNamesNeverCollide() {
        val names = FileNames.trashNames(
            listOf("Album" to true, "album" to true),
            trashNames = setOf("Album"),
            siblingNames = setOf("Album", "album"),
        )
        assertEquals(2, names.map { it.lowercase() }.toSet().size)
        assertTrue(names.none { it.equals("Album", ignoreCase = true) })
    }
}
