package com.source.player.data.files

/**
 * Pure path rules for moving on-device tracks through MediaStore (API 30+): a move is
 * an update of the row's RELATIVE_PATH, which stays on the same storage volume and must
 * start with one of the folders MediaStore accepts for audio files.
 */
object LocalMediaPaths {

    /**
     * Top-level folders MediaStore accepts for audio rows (Environment.DIRECTORY_*).
     * Anything else (Android/, DCIM/, a custom root folder…) is rejected by the provider.
     */
    val AUDIO_TOP_DIRS: Set<String> = setOf(
        "Music", "Podcasts", "Audiobooks", "Ringtones", "Alarms", "Notifications", "Recordings", "Download",
    )

    private val VOLUME_ROOT = Regex("^(/storage/emulated/\\d+|/storage/[^/]+)(?=/|$)")

    /** "/storage/emulated/0/Music/A/b.mp3" → "/storage/emulated/0"; null outside /storage. */
    fun volumeRoot(path: String): String? =
        VOLUME_ROOT.find(path)?.groupValues?.get(1)?.takeUnless { it == "/storage/emulated" }

    /** MediaStore RELATIVE_PATH of [folderPath]: "/storage/emulated/0/Music/A" → "Music/A/". */
    fun relativePath(folderPath: String): String? {
        val root = volumeRoot(folderPath) ?: return null
        val rest = folderPath.removePrefix(root).trim('/')
        return if (rest.isEmpty()) null else "$rest/"
    }

    /** True when [folderPath] can receive audio files through MediaStore. */
    fun isAllowedDestination(folderPath: String): Boolean {
        val rel = relativePath(folderPath) ?: return false
        val top = rel.substringBefore('/')
        return AUDIO_TOP_DIRS.any { it.equals(top, ignoreCase = true) }
    }

    /**
     * Why the files [paths] can't move into [destFolder], or null when they can: same
     * storage volume, a folder MediaStore accepts for audio, not where they already are.
     */
    fun checkMoveTarget(paths: List<String>, destFolder: String): String? {
        val destVolume = volumeRoot(destFolder) ?: return "This folder can't receive music files"
        if (!isAllowedDestination(destFolder)) {
            return "Android only allows music files under Music, Download, Podcasts, Audiobooks, Ringtones…"
        }
        for (path in paths) {
            if (volumeRoot(path) != destVolume) return "Files can't be moved to another storage volume"
            if (path.substringBeforeLast('/') == destFolder.trimEnd('/')) {
                return "“${path.substringAfterLast('/')}” is already in this folder"
            }
        }
        return null
    }

    /** Path of the file [path] once moved into [destFolder] (MediaStore may still rename it). */
    fun movedPath(path: String, destFolder: String): String =
        destFolder.trimEnd('/') + "/" + path.substringAfterLast('/')
}
