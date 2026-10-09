package com.source.player.data.freebox.library

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-disk album art for Freebox albums: `filesDir/freebox_art/<albumId>.jpg`. Bytes are
 * stored as fetched (JPEG, PNG or WebP) — Coil sniffs the real format, the extension is
 * cosmetic. All methods do blocking file IO: call them off the main thread.
 */
@Singleton
class FreeboxArtCache @Inject constructor(@ApplicationContext private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, DIR_NAME)

    fun fileFor(albumId: Long): File = File(dir, "$albumId$EXT")

    fun has(albumId: Long): Boolean = fileFor(albumId).let { it.isFile && it.length() > 0 }

    /** `file://` URI of the cached art, or null when none is cached. */
    fun uriFor(albumId: Long): String? =
        fileFor(albumId).takeIf { it.isFile && it.length() > 0 }?.let { "file://${it.absolutePath}" }

    /** Writes atomically (temp file + rename) so readers never see a half-written image. */
    fun write(albumId: Long, bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val target = fileFor(albumId)
        return runCatching {
            dir.mkdirs()
            val tmp = File(dir, "$albumId.${System.nanoTime()}$TMP_EXT")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                tmp.delete()
                false
            } else {
                true
            }
        }.getOrDefault(false)
    }

    /** Total size of the cache on disk, in bytes. */
    fun sizeBytes(): Long =
        dir.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L

    /** Deletes every cached image. */
    fun clear() {
        dir.deleteRecursively()
    }

    /**
     * Deletes the art of albums not in [albumIds] plus leftover temp files.
     * Returns the number of files removed.
     */
    fun retainOnly(albumIds: Set<Long>): Int {
        var removed = 0
        dir.listFiles()?.forEach { f ->
            val keep = f.isFile &&
                f.name.endsWith(EXT) &&
                f.name.removeSuffix(EXT).toLongOrNull()?.let { it in albumIds } == true
            if (!keep && f.delete()) removed++
        }
        return removed
    }

    private companion object {
        const val DIR_NAME = "freebox_art"
        const val EXT = ".jpg"
        const val TMP_EXT = ".tmp"
    }
}
