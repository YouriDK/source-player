package com.source.player.data.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMediaPathsTest {

    @Test fun primaryVolumeRoot() =
        assertEquals("/storage/emulated/0", LocalMediaPaths.volumeRoot("/storage/emulated/0/Music/A/b.mp3"))

    @Test fun sdCardVolumeRoot() =
        assertEquals("/storage/1A2B-3C4D", LocalMediaPaths.volumeRoot("/storage/1A2B-3C4D/Music/b.mp3"))

    @Test fun noVolumeOutsideStorage() = assertNull(LocalMediaPaths.volumeRoot("/data/local/b.mp3"))

    @Test fun relativePathOfFolder() =
        assertEquals("Music/A/B/", LocalMediaPaths.relativePath("/storage/emulated/0/Music/A/B"))

    @Test fun volumeRootHasNoRelativePath() = assertNull(LocalMediaPaths.relativePath("/storage/emulated/0"))

    @Test fun musicAndDownloadAreAllowed() {
        assertTrue(LocalMediaPaths.isAllowedDestination("/storage/emulated/0/Music"))
        assertTrue(LocalMediaPaths.isAllowedDestination("/storage/emulated/0/Download/x"))
    }

    @Test fun otherTopFoldersAreRefused() {
        assertFalse(LocalMediaPaths.isAllowedDestination("/storage/emulated/0/DCIM/x"))
        assertFalse(LocalMediaPaths.isAllowedDestination("/storage/emulated/0"))
    }

    @Test fun validMove() =
        assertNull(
            LocalMediaPaths.checkMoveTarget(
                listOf("/storage/emulated/0/Music/A/b.mp3"),
                "/storage/emulated/0/Music/B",
            ),
        )

    @Test fun sameFolderIsRefused() =
        assertNotNull(
            LocalMediaPaths.checkMoveTarget(listOf("/storage/emulated/0/Music/A/b.mp3"), "/storage/emulated/0/Music/A"),
        )

    @Test fun otherVolumeIsRefused() =
        assertNotNull(
            LocalMediaPaths.checkMoveTarget(listOf("/storage/1A2B-3C4D/Music/b.mp3"), "/storage/emulated/0/Music/A"),
        )

    @Test fun movedPathKeepsFileName() =
        assertEquals(
            "/storage/emulated/0/Music/B/b.mp3",
            LocalMediaPaths.movedPath("/storage/emulated/0/Music/A/b.mp3", "/storage/emulated/0/Music/B/"),
        )
}
